# API de Cartões de Crédito

API REST para cadastro e consulta segura de números de cartão de crédito.  
Desenvolvida em **Java 21 + Spring Boot 3.2** seguindo **Arquitetura Hexagonal (Ports & Adapters)**.

---

## Como executar

```bash
docker-compose up -d --build
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

> O Cassandra pode levar ~30s para iniciar. A API só sobe após o banco estar saudável.

---

## Endpoints

### 1. Gerar token JWT

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

### 2. Inserir cartão

```bash
curl -X POST http://localhost:8080/api/cards \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -d '{"card_number": "4456897999999999"}'
```

**Respostas:**
- `201` — Cartão cadastrado com sucesso
- `409` — Cartão já existe
- `422` — Formato inválido

---

### 3. Inserir em lote (arquivo TXT)

Arquivo com um número de cartão por linha.

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

### 4. Consultar cartão

```bash
curl -X GET "http://localhost:8080/api/cards/check?card_number=4456897999999999" \
  -H "Authorization: Bearer SEU_TOKEN"
```

**Respostas:**
- `200` — `{ "exists": true, "id": "..." }`
- `404` — `{ "exists": false }`

---

## Estrutura do Projeto

```
adapters/
  in/web/          ← Controllers REST
  out/persistence/ ← Persistência no Cassandra
  out/crypto/      ← Criptografia (AES-GCM + HMAC)
application/
  services/        ← Casos de uso (CardService)
domain/
  model/           ← Entidades de domínio
  ports/in/        ← Interfaces de entrada (CardUseCase)
  ports/out/       ← Interfaces de saída (CardRepositoryPort, CryptoPort)
infrastructure/
  config/          ← Configurações (Spring Security, JWT, OpenAPI, Cassandra)
```

---

## Segurança

- **AES-128-GCM** — número do cartão criptografado antes de persistir; cada entrada tem um IV aleatório único.
- **HMAC-SHA256** — hash usado como chave de busca no Cassandra; permite lookup O(1) sem expor o número real.
- **JWT stateless** — token emitido no `/api/auth`, validado em cada requisição; nenhuma sessão mantida no servidor.
- **Audit log** — todas as requisições são registradas (método, URI, status, tempo de resposta).

---

## Stack

| Tecnologia | Função |
|---|---|
| Java 21 | Runtime |
| Spring Boot 3.2 | Framework web |
| Apache Cassandra | Banco de dados |
| Spring Security + Auth0 java-jwt | Autenticação JWT |
| springdoc-openapi 2.5 | Swagger UI / OpenAPI 3 |
| Lombok | Redução de boilerplate |
| Maven | Build |
| Docker Compose | Containerização |