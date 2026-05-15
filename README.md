# API de Cartões de Crédito (Java 21 / Spring Boot)

API desenvolvida em **Java 21** e **Spring Boot 3** utilizando **Arquitetura de Portas e Adaptadores (Hexagonal)**, para cadastro e consulta segura de números de cartão de crédito. Utiliza criptografia ponta-a-ponta (AES-128) para armazenamento e hash (HMAC-SHA256) para buscas com complexidade O(1).

O banco de dados utilizado é o **Apache Cassandra**.

## Requisitos
- Docker e Docker Compose instalados.

## Instalação e Execução

O projeto conta com um arquivo `docker-compose.yml` que sobe o banco de dados e a aplicação simultaneamente de forma automatizada.

1. Na raiz do projeto, execute:
   ```bash
   docker-compose up -d --build
   ```
2. A aplicação ficará exposta na porta **8080** (HTTP). O Cassandra pode demorar alguns segundos a mais para ficar "Healthy", aguarde até que os dois containers estejam operacionais.

## Utilização da API

Como todas as rotas (exceto a de login) são protegidas, o fluxo deve sempre começar pela geração do Token JWT.

### 1. Autenticação (Gerar o Token JWT)
Essa rota é aberta. Aceita qualquer usuário e senha desde que não sejam vazios.

**Requisição:**
```bash
curl -X POST http://localhost:8080/api/auth \
-H "Content-Type: application/json" \
-d '{"username": "admin", "password": "123"}'
```

**Resposta de Sucesso (200 OK):**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```
*(Copie esse `access_token` para usar no cabeçalho `Authorization: Bearer <TOKEN>` das próximas chamadas).*

---

### 2. Inserção de Cartão Único
Insere um único cartão no banco de dados. O cartão deve ser numérico e conter entre 13 e 19 dígitos.

**Requisição:**
```bash
curl -X POST http://localhost:8080/api/cards \
-H "Content-Type: application/json" \
-H "Authorization: Bearer SEU_TOKEN_AQUI" \
-d '{"card_number": "4456897999999999"}'
```

**Resposta de Sucesso (201 Created):**
```json
{
  "message": "Cartão cadastrado com sucesso.",
  "id": "e45b3c2d-9f1a-4b2e-8c3d-7f6a5b4c3d2e"
}
```

**Respostas de Erro:**
- **422 Unprocessable Entity:** Se o formato for inválido.
- **409 Conflict:** Se o cartão já existir.

---

### 3. Inserção em Lote (Upload de Arquivo TXT)
Envia o arquivo posicional em lote para processamento.

**Requisição:**
```bash
curl -X POST http://localhost:8080/api/cards/batch \
-H "Authorization: Bearer SEU_TOKEN_AQUI" \
-F "file=@/caminho/completo/para/o/seu/arquivo/exemplo.txt"
```

**Resposta de Sucesso (201 Created):**
```json
{
  "message": "Processamento em lote finalizado.",
  "inseridos": 8,
  "duplicados": 2,
  "erros": 0
}
```

---

### 4. Consulta de Cartão (Checagem)
Verifica se o cartão informado existe na base de dados (busca `O(1)` no Cassandra).

**Requisição:**
```bash
curl -X GET "http://localhost:8080/api/cards/check?card_number=4456897999999999" \
-H "Authorization: Bearer SEU_TOKEN_AQUI"
```

**Resposta de Sucesso - Existe (200 OK):**
```json
{
  "exists": true,
  "id": "e45b3c2d-9f1a-4b2e-8c3d-7f6a5b4c3d2e"
}
```

**Resposta - Não Existe (404 Not Found):**
```json
{
  "exists": false
}
```

---

## Segurança

A API foi projetada com múltiplas camadas de segurança para proteger dados sensíveis em trânsito e em repouso.

### Autenticação JWT (JSON Web Token)
- Todas as rotas, exceto `/api/auth` e os endpoints do Swagger, exigem um token JWT válido no cabeçalho `Authorization: Bearer <TOKEN>`.
- Os tokens são assinados com o algoritmo **HMAC-SHA256** (`HS256`) via a biblioteca `auth0/java-jwt`, usando um segredo configurável via variável de ambiente (`JWT_SECRET`).
- O tempo de expiração padrão é de **24 horas** (`expiration-ms: 86400000`), configurável via `application.yml`.
- O filtro `JwtAuthFilter` (`OncePerRequestFilter`) intercepta cada requisição, valida o token e popula o `SecurityContext` do Spring, garantindo que a identidade do usuário esteja disponível durante todo o ciclo da requisição.

### Gerenciamento de Sessão Stateless
- A aplicação é completamente **stateless**: nenhuma sessão HTTP é criada ou mantida pelo servidor (`SessionCreationPolicy.STATELESS`).
- A autenticação é revalidada a cada requisição por meio do token JWT, eliminando riscos associados ao gerenciamento de sessões (e.g., session fixation, session hijacking).

### Criptografia em Repouso (AES-128)
- Os números de cartão **nunca são armazenados em texto plano** no banco de dados.
- Antes de persistir, cada número é criptografado com **AES-128** via a porta de domínio `CryptoPort`, implementada na camada de infraestrutura.
- A chave AES é injetada via variável de ambiente (`AES_KEY`), mantendo-a fora do código-fonte.

### Hashing para Consultas (HMAC-SHA256)
- Para permitir buscas eficientes sem expor o número do cartão, é gerado um **hash HMAC-SHA256** do número antes de qualquer operação de leitura ou escrita.
- O `card_hash` é usado como **chave de partição** no Cassandra, garantindo consultas em tempo constante **O(1)** sem necessidade de descriptografar os dados armazenados.
- O segredo do HMAC é configurável via variável de ambiente (`HMAC_SECRET`).

### Suporte a TLS/SSL
- A aplicação está configurada para aceitar conexões **HTTPS** na porta `8443`, utilizando um keystore no formato **PKCS12** (`keystore.p12`).
- A senha do keystore é injetada via variável de ambiente (`SSL_KEYSTORE_PASSWORD`), sem hardcoding de credenciais.

### Filtro de Auditoria (AuditLogFilter)
- O `AuditLogFilter` (`OncePerRequestFilter`) registra em log **todas as requisições de entrada e respostas de saída**, incluindo método HTTP, URI, status de resposta e tempo de processamento em milissegundos.
- Isso garante rastreabilidade completa de toda a atividade da API, fundamental para detecção de anomalias e investigação de incidentes.

### Spring Security Filter Chain
- A cadeia de filtros é configurada em `SecurityConfig`, garantindo a ordem correta de execução: `AuditLogFilter` → `JwtAuthFilter` → lógica de negócio.
- O CSRF está desabilitado intencionalmente, pois a API é stateless e baseada em tokens — cenário onde a proteção CSRF é desnecessária e impraticável.

---

## Arquitetura e Decisões
- **Hexagonal Architecture:** O pacote `domain` não tem dependência com o Spring. Ele se comunica com a aplicação via `ports`.
- **Global Exception Handler:** Utilização de `@ControllerAdvice` para retornar respostas JSON padronizadas e limpas para todos os erros mapeados (`422`, `409`, `500`).
- **Performance:** Leitura de arquivos em lote usa `BufferedReader` em Stream com bufferização de até 1.000 registros por ciclo. No banco de dados, o Cassandra realiza o particionamento pelo `card_hash` servindo consultas escaláveis infinitamente sem necessidade primária de cache.
- **Segurança:** O cartão nunca é persistido em texto plano. Recebe um HASH (para consultas exatas em tempo real) e é criptografado via AES (para armazenamento inativo). O filtro `AuditLogFilter` garante registro de entrada e saída.