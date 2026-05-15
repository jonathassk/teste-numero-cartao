# API de Cartões de Crédito (Java 21 / Spring Boot)

API REST para cadastro e consulta segura de números de cartão de crédito.
Desenvolvida em **Java 21 + Spring Boot 3.2** seguindo **Arquitetura Hexagonal (Ports & Adapters)**.

---

## Índice

1. [Como executar](#como-executar)
2. [Endpoints](#endpoints)
3. [Arquitetura](#arquitetura)
4. [Padrões de Projeto](#padrões-de-projeto)
5. [Banco de Dados](#banco-de-dados)
6. [Segurança](#segurança)
7. [Autenticação](#autenticação)
8. [Processamento em Lote](#processamento-em-lote)
9. [Ferramentas e Libs](#ferramentas-e-libs)
10. [Containerização](#containerização)

---

## Como executar

```bash
docker-compose up -d --build
```

A API ficará disponível em `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

> O Cassandra pode levar ~30s para ficar `Healthy`. O `depends_on: condition: service_healthy` garante que a API só sobe após o banco estar pronto.

---

## Endpoints

### 1. Autenticação — gerar token JWT

```bash
curl -X POST http://localhost:8080/api/auth \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "123"}'
```

**Resposta (200):**
```json
{ "access_token": "eyJhbGci..." }
```

Use o token no header `Authorization: Bearer <TOKEN>` nas demais chamadas.

---

### 2. Inserção de cartão único

```bash
curl -X POST http://localhost:8080/api/cards \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -d '{"card_number": "4456897999999999"}'
```

**Resposta (201):** `{ "message": "Cartão cadastrado com sucesso.", "id": "..." }`  
**409:** cartão já existe · **422:** formato inválido

---

### 3. Inserção em lote (arquivo TXT)

```bash
curl -X POST http://localhost:8080/api/cards/batch \
  -H "Authorization: Bearer SEU_TOKEN" \
  -F "file=@exemplo.txt"
```

**Resposta (201):**
```json
{ "message": "Processamento finalizado.", "inseridos": 8, "duplicados": 2, "erros": 0 }
```

---

### 4. Consulta de cartão

```bash
curl -X GET "http://localhost:8080/api/cards/check?card_number=4456897999999999" \
  -H "Authorization: Bearer SEU_TOKEN"
```

**200:** `{ "exists": true, "id": "..." }` · **404:** `{ "exists": false }`

---

## Arquitetura

### Hexagonal (Ports & Adapters)

```
adapters/
  in/web/          ← HTTP (Controllers REST)
  out/persistence/ ← Cassandra (CardPersistenceAdapter)
  out/crypto/      ← AES-GCM + HMAC (CryptoAdapter)
application/
  services/        ← Casos de uso (CardService)
domain/
  model/           ← Entidades puras (sem anotações de framework)
  ports/in/        ← Contrato de entrada  (CardUseCase)
  ports/out/       ← Contratos de saída   (CardRepositoryPort, CryptoPort)
infrastructure/
  config/          ← Spring beans, JWT, Security, OpenAPI, Cassandra
```

`CardService` depende **apenas de interfaces**. Nenhuma classe de domínio ou aplicação importa Cassandra, Spring Data ou qualquer lib de infraestrutura.

| | **Hexagonal** | Layered (tradicional) | Clean Architecture |
|---|---|---|---|
| **Testabilidade** | ✅ Alta — ports são mockáveis | ⚠️ Média — acoplamento com DB | ✅ Alta |
| **Troca de infra** | ✅ Trivial (só o adapter muda) | ❌ Trabalhosa | ✅ Trivial |
| **Curva de aprendizado** | ⚠️ Moderada | ✅ Baixa | ❌ Alta |
| **Overhead de código** | ⚠️ Mais interfaces | ✅ Menos arquivos | ❌ Muito mais camadas |
| **Adequação ao projeto** | ✅ Múltiplos adapters (HTTP, cripto, DB) | ❌ Insuficiente | ⚠️ Overkill para este escopo |

---

## Padrões de Projeto

| Padrão | Onde | Descrição |
|---|---|---|
| **Adapter (GoF)** | `CryptoAdapter`, `CardPersistenceAdapter` | Implementam ports do domínio isolando tecnologias externas |
| **Strategy (implícito via Port)** | `CryptoPort` | Contrato injetável — possível trocar por HSM/Vault sem tocar no use case |
| **Template Method** | `processBatchFile` | Define fluxo fixo (ler → validar → hashear → salvar), delega detalhes a métodos privados |
| **Filter Chain** | `JwtAuthFilter`, `AuditLogFilter` | `OncePerRequestFilter` plugado na `SecurityFilterChain`, separa autenticação da lógica de negócio |
| **Factory (via DI)** | `BeanConfig` | Spring IoC como fábrica de adapters concretos — `CardService` recebe as implementações sem conhecê-las |

---

## Banco de Dados

### Apache Cassandra

Escolhido pela **escrita massiva em lote** e pelo modelo **sem single point of failure** para escala horizontal.

| | **Cassandra** | PostgreSQL | MySQL | Redis |
|---|---|---|---|---|
| **Escrita em lote** | ✅ Excelente | ⚠️ Boa com COPY | ⚠️ Boa | ✅ (em memória) |
| **Escalabilidade horizontal** | ✅ Nativa | ❌ Complexa | ❌ Complexa | ✅ Com cluster |
| **Queries complexas (JOIN)** | ❌ Sem suporte | ✅ Completa | ✅ Completa | ❌ |
| **Consistência** | ⚠️ Eventual por padrão | ✅ ACID | ✅ ACID | ⚠️ Limitada |
| **Operação local/dev** | ⚠️ Pesado | ✅ Leve | ✅ Leve | ✅ Leve |

> **Trade-off aceito:** sem JOINs ou transações distribuídas. Para este domínio (inserção + lookup por hash O(1)), o modelo de coluna larga do Cassandra é ideal.

**Spring Data Cassandra** em vez do driver nativo: ganha Repository abstraction e `@Table`/`@PrimaryKey`, reduzindo boilerplate sem perder controle.

---

## Segurança

### AES-128-GCM — criptografia em repouso

O número do cartão é **sempre criptografado antes de persistir**. Cada gravação gera um IV aleatório de 12 bytes concatenado ao ciphertext — cada entrada é única mesmo para o mesmo número.

| | **AES-GCM** | AES-CBC | RSA | bcrypt |
|---|---|---|---|---|
| **Autenticação do dado (AEAD)** | ✅ Tag 128 bits | ❌ | ✅ Via padding | N/A |
| **Descriptografável** | ✅ | ✅ | ✅ | ❌ One-way |
| **Performance** | ✅ Nativo JVM | ✅ Boa | ❌ Lenta | ❌ Lenta |
| **Vulnerabilidade** | ✅ Sem padding oracle | ⚠️ Padding oracle | ❌ Overhead | N/A |

### HMAC-SHA256 — lookup seguro sem exposição do PAN

O hash HMAC é a **chave de partição** no Cassandra: verifica existência e permite busca O(1) sem descriptografar.

| | **HMAC-SHA256** | SHA-256 puro | bcrypt/argon2 | Índice em texto plano |
|---|---|---|---|---|
| **Resistência a rainbow table** | ✅ Chave secreta | ❌ | ✅ | ❌ |
| **Performance** | ✅ Microsegundos | ✅ | ❌ Segundos | ✅ |
| **Determinístico (lookup)** | ✅ | ✅ | ❌ Salt aleatório | ✅ |
| **Sem salt extra** | ✅ | ❌ Precisa de salt | ✅ | ❌ |

### Auditoria — AuditLogFilter

`OncePerRequestFilter` que registra método, URI, status e tempo de resposta em **todas** as requisições. Abordagem de filtro Servlet em vez de AOP (`@Around`) por ser mais simples e sem dependência de aspecto adicional.

---

## Autenticação

### JWT Stateless com Auth0 `java-jwt`

Token emitido via `POST /api/auth`, validado em cada request pelo `JwtAuthFilter`. Nenhuma sessão é mantida no servidor (`SessionCreationPolicy.STATELESS`).

| | **JWT (stateless)** | Session (stateful) | OAuth2/OIDC |
|---|---|---|---|
| **Escalabilidade** | ✅ Sem estado no servidor | ❌ Requer session store | ✅ |
| **Revogação imediata** | ❌ Até expirar (24h) | ✅ | ✅ Token introspection |
| **Complexidade** | ✅ Baixa | ✅ Baixa | ❌ Alta |
| **Adequação** | ✅ APIs REST internas | ✅ Apps web tradicionais | ✅ Multi-tenant/federado |

**Auth0 `java-jwt`** em vez de `spring-security-oauth2-resource-server`: mais direto para geração + validação manual sem servidor de autorização externo.

> **Trade-off:** sem revogação antes da expiração. Mitigável com blacklist em Redis (fora do escopo).

---

## Processamento em Lote

### Buffer manual com `BufferedReader` (sem Spring Batch)

Lê o arquivo linha a linha, acumula até 1.000 registros (`BATCH_SIZE = 1000`) e persiste em bloco. Retorna contadores de `inseridos`, `duplicados` e `erros`.

| | **Batch manual** | Spring Batch | Kafka Streams |
|---|---|---|---|
| **Setup** | ✅ Zero config | ❌ JobRepository, Steps | ❌ Muito complexo |
| **Observabilidade** | ⚠️ Básica | ✅ Job metadata, listeners | ✅ |
| **Restart / retry** | ❌ Não suportado | ✅ Checkpoint por step | ✅ |
| **Escala** | ⚠️ Single thread | ✅ Paralelo, particionado | ✅ Distribuído |
| **Adequação** | ✅ Arquivo único, escopo do desafio | ⚠️ Overkill | ❌ Overkill |

---

## Ferramentas e Libs

| Ferramenta | Função | Alternativa | Motivo da escolha |
|---|---|---|---|
| **Java 21** | Runtime | Java 17, Kotlin | LTS mais recente; Virtual Threads disponíveis |
| **Spring Boot 3.2** | Framework | Quarkus, Micronaut | Ecossistema maduro, integração nativa com Cassandra |
| **Spring Data Cassandra** | Persistência | Driver Datastax nativo | Repository abstraction, menos boilerplate |
| **Spring Security** | Autenticação/Autorização | Shiro, filtro manual | Integração nativa com filter chain e DI |
| **Auth0 java-jwt 4.4** | Emissão/validação JWT | spring-security-oauth2 | Simples, sem servidor de autorização externo |
| **springdoc-openapi 2.5** | Swagger UI / OpenAPI 3 | springfox (descontinuado) | Compatível com Spring Boot 3.x |
| **Lombok** | Redução de boilerplate | Records Java 21 | `@Builder`, `@Slf4j`; Records não suportam herança |
| **Maven** | Build | Gradle | Padrão consolidado no ecossistema Spring |
| **JCA (javax.crypto)** | AES-GCM + HMAC | Bouncy Castle | Zero dependência extra; JDK já inclui os algoritmos |

---

## Containerização

### Docker + Docker Compose

Dois serviços: `cassandra` (com healthcheck via `nodetool statusgossip`) e `api` (com `depends_on: condition: service_healthy`).

| | **Docker Compose** | Kubernetes | Bare metal |
|---|---|---|---|
| **Facilidade local** | ✅ | ❌ Complexo | ✅ |
| **Orquestração produção** | ⚠️ Limitada | ✅ Completa | ❌ Manual |
| **Health checks** | ✅ | ✅ Liveness/Readiness | N/A |
| **Adequação** | ✅ Dev / staging | ✅ Produção | ❌ |

Segredos (`JWT_SECRET`, `AES_KEY`, `HMAC_SECRET`) são injetados via **variáveis de ambiente** — compatível com `.env` local e secret managers em produção (AWS Secrets Manager, Vault, GCP Secret Manager).

---

## Resumo dos Trade-offs

| Decisão | Ganho principal | Custo principal |
|---|---|---|
| Hexagonal Architecture | Testabilidade e isolamento total de infra | Mais classes e interfaces |
| Apache Cassandra | Escrita massiva e escala horizontal nativa | Sem ACID; queries limitadas (sem JOIN) |
| AES-GCM | Dados recuperáveis + autenticação integrada (AEAD) | Gerenciamento de IV e chave AES |
| HMAC para lookup | Busca O(1) sem expor o PAN | Chave HMAC é segredo crítico e não pode rotacionar facilmente |
| JWT stateless | Sem sessão no servidor; escala trivialmente | Sem revogação antes da expiração de 24h |
| Batch manual | Zero dependência; controle total do fluxo | Sem restart, checkpoint ou paralelismo automático |
| JCA nativo (sem Bouncy Castle) | Sem dependência extra; aprovado por FIPS | Algoritmos limitados ao que o JDK suporta |