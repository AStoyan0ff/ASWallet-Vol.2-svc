<p align="center">
  <img
    src="https://readme-typing-svg.demolab.com?font=Orbitron&size=42&duration=2500&pause=1000&color=E53935&center=true&vCenter=true&width=700&lines=%F0%9F%92%B0+ASWallet-Vol.2-svc+%F0%9F%92%B0"
    alt="ASWallet-Vol.2-svc"
  />
</p>

---

<p align="center">
  <img src="src/main/resources/static/images/screenShot.png" width="520" alt="">
</p>

---

<p align="center">
  Transfer Risk Assessment — REST Microservice for ASWallet-Vol.2
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?logo=springboot">
  <img src="https://img.shields.io/badge/MySQL-Database-blue?logo=mysql">
  <img src="https://img.shields.io/badge/Port-8081-blue">
  <img src="https://img.shields.io/badge/Security-API%20Key-success">
</p>

---

Standalone **Transfer Risk Assessment** microservice for [ASWallet-Vol.2](https://github.com/AStoyan0ff/ASWallet-Vol.2).

Consumed by the main app via **Spring Cloud OpenFeign** on transfer confirm and admin risk review.

---

## Table of Contents

1. [Project Inventory](#project-inventory)
2. [Overview](#overview)
3. [Tech Stack](#tech-stack)
4. [Domain Model](#domain-model)
5. [Risk Scoring](#risk-scoring)
6. [REST API](#rest-api)
7. [Integration with Main App](#integration-with-main-app)
8. [Security](#security)
9. [Static Assets](#static-assets)
10. [Configuration](#configuration)
11. [Getting Started](#getting-started)
12. [Testing](#testing)
13. [Source Inventory](#source-inventory)
14. [Spring Advanced Checklist](#spring-advanced-checklist)
15. [Author](#author)

---

## Project Inventory

| Area                     | Count                                         |
|--------------------------|-----------------------------------------------|
| Java source files (main) | **19**                                        |
| REST controllers         | **1**                                         |
| Services                 | **2**                                         |
| JPA entities             | **1**                                         |
| Repositories             | **1**                                         |
| DTOs                     | **3**                                         |
| Enums                    | **3**                                         |
| Custom exceptions        | **2**                                         |
| Test classes             | **4**                                         |
| Test methods             | **+-30**                                      |
| Static files             | **3** (`index.html`, `home.css`, `hello.png`) |
| Line coverage            | **70%+** ✅                                    |

---

## Overview

| Property | Value                                            |
|----------|--------------------------------------------------|
| Artifact | `ASWallet-Vol.2-svc` v1.0.0                      |
| Port     | `8081`                                           |
| Database | MySQL `as_wallet_svc` (H2 in tests)              |
| Consumer | ASWallet-Vol.2 main app (`RiskAssessmentClient`) |

**Responsibilities:**

- Score transfer risk from context sent by main app
- Persist `TransferRiskAssessment` with `transactionRef` (wallet transaction UUID)
- Expose admin review API (approve/reject status updates)
- List and bulk-delete manual reviews (`decision=REVIEW`)

---

## Tech Stack

| Layer       | Technology              |
|-------------|-------------------------|
| Language    | Java 21                 |
| Framework   | Spring Boot 4.0.6       |
| API         | Spring Web (REST)       |
| Persistence | Spring Data JPA         |
| Database    | MySQL / H2 (tests)      |
| Validation  | Jakarta Bean Validation |
| Build       | Maven                   |
| Utilities   | Lombok                  |

---

## Domain Model

### Entity

| Entity                   | Table                       | Fields (key)                                                                                                                             |
|--------------------------|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `TransferRiskAssessment` | `transfer_risk_assessments` | UUID id, `transactionRef`, usernames, amount, `riskScore`, `riskLevel`, `decision`, `status`, `reasons` (JSON), `reviewedBy`, timestamps |

### Enums (3)

| Enum               | Values                            |
|--------------------|-----------------------------------|
| `RiskDecision`     | `ALLOW`, `REVIEW`, `BLOCK`        |
| `RiskLevel`        | `LOW`, `MEDIUM`, `HIGH`           |
| `AssessmentStatus` | `PENDING`, `APPROVED`, `REJECTED` |

### Decision → status on create

| Decision | Initial status |
|----------|----------------|
| `ALLOW`  | `APPROVED`     |
| `REVIEW` | `PENDING`      |
| `BLOCK`  | `REJECTED`     |

---

## Risk Scoring

Implemented in `RiskScoringService`.

### Rules

| Rule                              | Points                          |
|-----------------------------------|---------------------------------|
| Amount > 80% of sender balance    | +30                             |
| Would exceed daily withdraw limit | +40                             |
| ≥ 3 transfers today               | +20                             |
| Night hours (23:00–05:59)         | +15                             |
| First transfer to receiver        | +10                             |
| Receiver has no bank card         | +25                             |
| Non-ACTIVE account                | immediate **BLOCK** (score 100) |

### Thresholds

```properties
app.risk.threshold.review=40
app.risk.threshold.block=70
```

| Score | Decision | Status     |
|-------|----------|------------|
| 0–39  | `ALLOW`  | `APPROVED` |
| 40–69 | `REVIEW` | `PENDING`  |
| 70+   | `BLOCK`  | `REJECTED` |

---

## REST API

Base: `/api/risk-assessments`

| Method   | Path                                   | Description                                        |
|----------|----------------------------------------|----------------------------------------------------|
| `POST`   | `/api/risk-assessments`                | Create assessment                                  |
| `GET`    | `/api/risk-assessments/{id}`           | Get by id                                          |
| `GET`    | `/api/risk-assessments`                | List by `status` (default `PENDING`) or `decision` |
| `GET`    | `/api/risk-assessments/manual-reviews` | All `decision=REVIEW` (history)                    |
| `PATCH`  | `/api/risk-assessments/{id}/review`    | Approve/reject pending review                      |
| `DELETE` | `/api/risk-assessments`                | Bulk delete by `status` or `decision`              |
| `DELETE` | `/api/risk-assessments/manual-reviews` | Delete all manual reviews                          |

### Create

```bash
curl -X POST http://localhost:8081/api/risk-assessments 
  -H "Content-Type: application/json" 
  -H "X-API-Key: aswallet-dev-api-key" 
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

### Review

```bash
curl -X PATCH http://localhost:8081/api/risk-assessments/{id}/review 
  -H "Content-Type: application/json" 
  -H "X-API-Key: aswallet-dev-api-key" 
  -d '{"status": "APPROVED", "reviewedBy": "admin"}'
```

Only `decision=REVIEW` + `status=PENDING` can be reviewed.

---

## Integration with Main App

### Feign mapping

| Main app action         | Feign call                                    |
|-------------------------|-----------------------------------------------|
| Transfer confirm        | `POST /api/risk-assessments`                  |
| Admin risk reviews page | `GET /api/risk-assessments/manual-reviews`    |
| Admin approve/reject    | `PATCH /api/risk-assessments/{id}/review`     |
| Admin delete all        | `DELETE /api/risk-assessments/manual-reviews` |
| Load single assessment  | `GET /api/risk-assessments/{id}`              |

### Main app properties

```properties
app.risk-service.enabled=true
app.risk-service.base-url=http://localhost:8081
app.risk-service.fail-open=true
app.risk-service.api-key=${RISK_SERVICE_API_KEY:aswallet-dev-api-key}
spring.cloud.openfeign.httpclient.hc5.enabled=true
```

### Wallet behavior (main app)

| MS decision | Wallet `Transaction.status`          |
|-------------|--------------------------------------|
| `ALLOW`     | `PENDING` → scheduler completes      |
| `REVIEW`    | `PENDING_RISK_REVIEW` → admin action |
| `BLOCK`     | Transfer not created                 |

---

## Security

### API key (service-to-service)

All `/api/**` endpoints require:

```http
X-API-Key: <aswallet-dev-api-key>
```

| Side         | Property                   | Default (local)        |
|--------------|----------------------------|------------------------|
| Microservice | `app.security.api-key`     | `aswallet-dev-api-key` |
| Main app     | `app.risk-service.api-key` | `aswallet-dev-api-key` |

Same env override for both apps:

- Splash `/` and static assets stay public
- Main app sends the key via Feign `RiskServiceFeignConfig`
- Filter: `SVC.Security.ApiKeyAuthFilter`

### Manual check

```powershell
# Expect 401
curl http://localhost:8081/api/risk-assessments/manual-reviews

# Expect 200 / []
curl http://localhost:8081/api/risk-assessments/manual-reviews -H "X-API-Key: aswallet-dev-api-key"
```

---

## Static Assets

Minimal landing page at **`GET /`** (not the primary integration surface):

| File | Purpose |
|------|---------|
| `static/index.html` | Simple splash: “ASWallet Risk Service Port: 8081” |
| `static/css/home.css` | Full-viewport image layout |
| `static/images/hello.png` | Background image |

API integration uses `/api/risk-assessments/*`.

---

## Configuration

```properties
spring.application.name=ASWallet-Vol.2-svc
server.port=8081

spring.datasource.url=jdbc:mysql://localhost:3306/as_wallet_svc?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=${DB_PASSWORD:change-me}

spring.jpa.hibernate.ddl-auto=update

app.risk.threshold.review=40
app.risk.threshold.block=70
app.security.api-key=${RISK_SERVICE_API_KEY:aswallet-dev-api-key}
```

---

## Getting Started

```sql
CREATE DATABASE IF NOT EXISTS as_wallet_svc;
```

```powershell
$env:DB_PASSWORD = "your_mysql_password"
mvn spring-boot:run
```

- API: **http://localhost:8081/api/risk-assessments/manual-reviews**
- Splash: **http://localhost:8081/**

Start before or with main app on `:8080`.

---

## Testing

```powershell
mvn test
```

| Test class | Type | Focus |
|------------|------|-------|
| `RiskScoringServiceTest` | Unit | Scoring rules (5 tests) |
| `RiskAssessmentServiceTest` | Unit | Service logic (9 tests) |
| `RiskAssessmentServiceIntegrationTest` | Integration | H2 full stack (3 tests) |
| `RiskAssessmentControllerWebMvcTest` | API | REST endpoints + API key 401 (13 tests) |

**~30** test methods total. Target 70%+ line coverage ✅.

---

## Source Inventory

```
src/main/java/SVC/
├── ASWalletSvcApplication.java
├── Controllers/
│   └── RiskAssessmentController.java
├── Services/
│   ├── RiskAssessmentService.java      # CRUD, review, list/delete by status/decision
│   └── RiskScoringService.java         # Rule engine + thresholds
├── Models/
│   └── TransferRiskAssessment.java
├── Repositories/
│   └── TransferRiskAssessmentRepository.java
├── DTOs/
│   ├── CreateRiskAssessmentRequest.java
│   ├── ReviewRiskAssessmentRequest.java
│   └── RiskAssessmentResponse.java
├── Enums/
│   ├── AssessmentStatus.java
│   ├── RiskDecision.java
│   └── RiskLevel.java
├── Exceptions/
│   ├── InvalidReviewStateException.java
│   └── RiskAssessmentNotFoundException.java
├── Security/
│   └── ApiKeyAuthFilter.java
└── GlobalExceptionHandler/
    └── GlobalExceptionHandler.java

src/main/resources/
├── application.properties
└── static/
    ├── index.html
    ├── css/home.css
    └── images/hello.png/screenShot.png

src/test/java/SVC/
├── Controllers/RiskAssessmentControllerWebMvcTest.java
└── Services/
    ├── RiskScoringServiceTest.java
    ├── RiskAssessmentServiceTest.java
    └── RiskAssessmentServiceIntegrationTest.java
```

---

## Spring Advanced Checklist

| Requirement                     | Status |
|---------------------------------|--------|
| Separate Spring Boot app        | ✅ |
| Separate database               | ✅ |
| ≥ 1 domain entity               | ✅ |
| ≥ 2 functionalities from UI     | ✅ assess + admin review |
| ≥ 1 GET + ≥ 2 POST/PATCH/DELETE | ✅ |
| Feign from main app             | ✅ |
| Validation + error handling     | ✅ |
| Logging                         | ✅ |
| 77% ++ test coverage            | ✅ |
| API key on `/api/**`            | ✅ `ApiKeyAuthFilter` |

---

## Author

Part of **ASWallet-Vol.2** by **AStoyanoff®**
