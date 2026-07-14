<h1 align="center">
    ASWallet-Vol.2-svc 
</h1>

<p align="center">
  Transfer Risk Assessment — REST Microservice for ASWallet-Vol.2
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?logo=springboot">
  <img src="https://img.shields.io/badge/MySQL-Database-blue?logo=mysql">
  <img src="https://img.shields.io/badge/Port-8081-blue">
  <img src="https://img.shields.io/badge/Security-None%20(optional)-yellow">
</p>

Standalone **Transfer Risk Assessment** microservice for the [ASWallet-Vol.2](https://github.com/AStoyan0ff/ASWallet-Vol.2) main application.

The main app calls this service on **transfer confirmation** via **Spring Cloud OpenFeign**.  
Admins review flagged transfers from `/admin/risk-reviews` in the main app.

---

## Table of Contents

1. [Overview](#overview)
2. [Tech Stack](#tech-stack)
3. [Domain Model](#domain-model)
4. [Risk Scoring](#risk-scoring)
5. [REST API](#rest-api)
6. [Integration with Main App](#integration-with-main-app)
7. [Security](#security)
8. [Configuration](#configuration)
9. [Getting Started](#getting-started)
10. [Testing](#testing)
11. [Project Structure](#project-structure)
12. [Spring Advanced Checklist](#spring-advanced-checklist)
13. [Planned Work](#planned-work)

---

## Overview

| Property | Value |
|----------|-------|
| Port | `8081` |
| Database | MySQL `as_wallet_svc` (H2 in tests) |
| Main consumer | ASWallet-Vol.2 via OpenFeign (`RiskAssessmentClient`) |
| Purpose | Score transfer risk; persist assessments; support admin manual review |

**What this service does:**

- Receives transfer context from main app (amount, balances, limits, time of day, etc.)
- Calculates risk score and returns `ALLOW`, `REVIEW`, or `BLOCK`
- Stores `TransferRiskAssessment` with `transactionRef` linking to main-app transaction UUID
- Exposes admin review API (approve/reject) consumed by main app
- Supports listing and bulk-deleting manual reviews (`decision=REVIEW`)

**What this service does not do:**

- No user-facing UI
- No Spring Security (optional per SoftUni assignment — see [Security](#security))
- No direct wallet/balance changes (main app owns wallet state)

---

## Tech Stack

| Layer       | Technology |
|-------------|------------|
| Language    | Java 21 |
| Framework   | Spring Boot 4.0.6 |
| API         | Spring Web (REST) |
| Persistence | Spring Data JPA, Hibernate |
| Database    | MySQL `as_wallet_svc` (dev), H2 (tests) |
| Validation  | Jakarta Bean Validation |
| Build       | Maven |
| Utilities   | Lombok |

---

## Domain Model

### Entity

| Entity | Table | Purpose |
|--------|-------|---------|
| `TransferRiskAssessment` | `transfer_risk_assessments` | Risk score, decision, reasons, review metadata |

UUID primary key. Field **`transactionRef`** links to main-app `Transaction.id`.

### Enums

| Enum | Values |
|------|--------|
| `RiskDecision` | `ALLOW`, `REVIEW`, `BLOCK` |
| `RiskLevel` | `LOW`, `MEDIUM`, `HIGH` |
| `AssessmentStatus` | `PENDING`, `APPROVED`, `REJECTED` |

### Decision → status mapping (on create)

| Decision | Initial status |
|----------|----------------|
| `ALLOW` | `APPROVED` |
| `REVIEW` | `PENDING` |
| `BLOCK` | `REJECTED` |

---

## Risk Scoring

### Rules

| Rule | Points |
|------|--------|
| Amount > 80% of sender balance | +30 |
| Would exceed daily withdraw limit | +40 |
| ≥ 3 transfers today | +20 |
| Night hours (23:00–05:59) | +15 |
| First transfer to receiver | +10 |
| Receiver has no bank card | +25 |
| Non-ACTIVE account | immediate **BLOCK** (score 100) |

### Thresholds (configurable)

```properties
app.risk.threshold.review=40
app.risk.threshold.block=70
```

| Score | Decision | Initial status |
|-------|----------|----------------|
| 0–39 | `ALLOW` | `APPROVED` |
| 40–69 | `REVIEW` | `PENDING` |
| 70+ | `BLOCK` | `REJECTED` |

---

## REST API

Base path: `/api/risk-assessments`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/risk-assessments` | Evaluate transfer risk and persist result |
| `GET` | `/api/risk-assessments/{id}` | Get assessment by id |
| `GET` | `/api/risk-assessments` | List by `status` (default `PENDING`) or `decision` |
| `GET` | `/api/risk-assessments/manual-reviews` | List all `decision=REVIEW` (any status) |
| `PATCH` | `/api/risk-assessments/{id}/review` | Admin approve/reject a pending review |
| `DELETE` | `/api/risk-assessments` | Bulk delete by `status` or `decision` (204) |
| `DELETE` | `/api/risk-assessments/manual-reviews` | Delete all `decision=REVIEW` (204) |

> No homepage at `/`. Use API paths above.

### Create assessment

```bash
curl -X POST http://localhost:8081/api/risk-assessments 
  -H "Content-Type: application/json" 
  -d '{
    "transactionRef": "550e8400-e29b-41d4-a716-446655440000",
    "senderUsername": "Plamen",
    "receiverUsername": "Georgi",
    "amount": 200.00,
    "senderBalance": 500.00,
    "withdrawnToday": 100.00,
    "dailyLimit": 500.00,
    "transfersTodayCount": 2,
    "receiverHasBankCard": true,
    "newReceiver": false,
    "accountStatus": "ACTIVE",
    "hourOfDay": 23
  }'
```

Example response:

```json
{
  "id": "…",
  "transactionRef": "550e8400-e29b-41d4-a716-446655440000",
  "senderUsername": "Plamen",
  "receiverUsername": "Georgi",
  "amount": 200.00,
  "riskScore": 55,
  "riskLevel": "MEDIUM",
  "decision": "REVIEW",
  "status": "PENDING",
  "reasons": ["Transfer was initiated during night hours."],
  "createdAt": "2026-07-07T23:00:00"
}
```

### Review (admin)

Only assessments with `decision=REVIEW` and `status=PENDING` can be reviewed.

```bash
curl -X PATCH http://localhost:8081/api/risk-assessments/{id}/review 
  -H "Content-Type: application/json" 
  -d '{
    "status": "APPROVED",
    "reviewedBy": "admin"
  }'
```

### List manual reviews (history)

```bash
curl http://localhost:8081/api/risk-assessments/manual-reviews
```

Returns all assessments with `decision=REVIEW` regardless of `status` (pending, approved, rejected). Main app uses this for admin history view.

### Delete all manual reviews

```bash
curl -X DELETE http://localhost:8081/api/risk-assessments/manual-reviews
```

Returns **204 No Content**. Main app calls this after rejecting/refunding pending linked transfers.

---

## Integration with Main App

### Feign triggers

| Trigger | Feign call | Effect |
|---------|------------|--------|
| User confirms transfer | `POST /api/risk-assessments` | Score transfer, persist assessment |
| Admin opens risk reviews | `GET /api/risk-assessments/manual-reviews` | List all manual reviews (history) |
| Admin loads single review | `GET /api/risk-assessments/{id}` | Fetch before approve/reject |
| Admin approves / rejects | `PATCH /api/risk-assessments/{id}/review` | Update assessment status |
| Admin delete all reviews | `DELETE /api/risk-assessments/manual-reviews` | Clear manual review records |

### Main app configuration

```properties
app.risk-service.enabled=true
app.risk-service.base-url=http://localhost:8081
app.risk-service.fail-open=true
spring.cloud.openfeign.httpclient.hc5.enabled=true
```

### Decision flow

| Decision | MS status | Main app behaviour |
|----------|-----------|-------------------|
| `ALLOW` | `APPROVED` | Transfer `PENDING` → scheduler completes (~5 s) |
| `REVIEW` | `PENDING` | Transfer `PENDING_RISK_REVIEW` → held until admin action |
| `BLOCK` | `REJECTED` | Transfer rejected before creation |

Admin **Approve** → main app completes wallet transfer → PATCH → `APPROVED`.  
Admin **Reject** / **Delete all** → main app refunds sender → PATCH or bulk DELETE.

When microservice is down and `fail-open=true`, main app allows transfer without check (demo mode).

---

## Security

### Current state

**No authentication or authorization** on REST endpoints. Any client reaching `:8081` can call the API.

This is **acceptable for the SoftUni Spring Advanced assignment**:

> *Security and Roles — required only for the Main application. The REST microservice(s) may implement security, but it is optional.*

Main app protects `/admin/risk-reviews` with `ROLE_ADMIN`, but direct API access to `:8081` bypasses that guard.

### Planned (future initiative)

| Item | Approach |
|------|----------|
| Service-to-service auth | Shared API key: `app.risk-service.api-key=${RISK_SERVICE_API_KEY}` |
| Main app | Feign request interceptor sends `X-API-Key` header |
| Microservice | Servlet filter or Spring Security stateless config validates key |
| Fail-open policy | Configurable modes when svc unreachable (`allow` / `block` / `review`) |

See main app README **Planned Work** for details.

---

## Configuration

`src/main/resources/application.properties`:

```properties
spring.application.name=ASWallet-Vol.2-svc
server.port=8081

spring.datasource.url=jdbc:mysql://localhost:3306/as_wallet_svc?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=${DB_PASSWORD:change-me}

spring.jpa.hibernate.ddl-auto=update

app.risk.threshold.review=40
app.risk.threshold.block=70
```

Environment variable:

```powershell
$env:DB_PASSWORD = "your_mysql_password"
```

---

## Getting Started

### 1. Database

```sql
CREATE DATABASE IF NOT EXISTS as_wallet_svc;
```

### 2. Run

Start before or together with main app on port 8080:

```powershell
mvn spring-boot:run
```

Service: **http://localhost:8081** -- Greetings for you!

### 3. Verify

```powershell
curl http://localhost:8081/api/risk-assessments/manual-reviews
```

Expected: `[]` or list of assessments.

---

## Testing

```powershell
mvn test
```

| Test class | Type | Focus |
|------------|------|-------|
| `RiskScoringServiceTest` | Unit | Scoring rules and thresholds |
| `RiskAssessmentServiceTest` | Unit | Service logic (mocked repository) |
| `RiskAssessmentServiceIntegrationTest` | Integration | Full stack with H2 |
| `RiskAssessmentControllerWebMvcTest` | API | REST endpoints incl. manual-reviews, decision filter, DELETE |

Target: **70%+ line coverage** ✅

---

## Project Structure

```
ASWallet-Vol.2-svc/
├── pom.xml
├── README.md
└── src/
    ├── main/java/SVC/
    │   ├── ASWalletSvcApplication.java
    │   ├── Controllers/
    │   │   └── RiskAssessmentController.java
    │   ├── Services/
    │   │   ├── RiskAssessmentService.java
    │   │   └── RiskScoringService.java
    │   ├── Models/
    │   │   └── TransferRiskAssessment.java
    │   ├── Repositories/
    │   │   └── TransferRiskAssessmentRepository.java
    │   ├── DTOs/
    │   ├── Enums/
    │   ├── Exceptions/
    │   └── GlobalExceptionHandler/
    ├── main/resources/
    │   └── application.properties
    └── test/java/SVC/
        ├── Controllers/RiskAssessmentControllerWebMvcTest.java
        └── Services/
            ├── RiskScoringServiceTest.java
            ├── RiskAssessmentServiceTest.java
            └── RiskAssessmentServiceIntegrationTest.java
```

### Repository layout (with main app)

```
D:\Projects\
├── ASWallet-Vol.2/          ← main app (:8080, DB as_wallet)
└── ASWallet-Vol.2-svc/      ← this service (:8081, DB as_wallet_svc)
```

---

## Spring Advanced Checklist

| Requirement | Status |
|-------------|--------|
| Separate Spring Boot app | ✅ |
| Separate database | ✅ `as_wallet_svc` |
| ≥ 1 domain entity | ✅ `TransferRiskAssessment` |
| ≥ 2 valid functionalities from UI | ✅ assess + admin review |
| ≥ 1 GET + ≥ 2 POST/PATCH/DELETE from main | ✅ |
| Feign consumed by main app | ✅ |
| Validation + error handling | ✅ |
| Logging on functionalities | ✅ |
| 70% test coverage | ✅ |
| Spring Security | ⏭️ optional (not implemented) |

---

## Planned Work

| Item | Notes |
|------|-------|
| **API key authentication** | `X-API-Key` header; env-based shared secret; Feign interceptor in main app |
| **Bind to localhost only** | Alternative quick hardening for dev (`server.address=127.0.0.1`) |
| **Health/actuator endpoint** | Optional readiness probe for main-app fail-open logic |
| **Outbox for failed assessments** | Support `fail-open=review` when svc was down at transfer time |

---

## Author

Part of **ASWallet-Vol.2** by **AStoyanoff®**
