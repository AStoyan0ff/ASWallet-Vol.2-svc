# ASWallet-Vol.2-svc

Standalone **Transfer Risk Assessment** microservice for the [ASWallet-Vol.2](https://github.com/AStoyan0ff/ASWallet-Vol.2) main application.

The main app calls this service on **transfer confirmation** via **Spring Cloud OpenFeign**.  
Admins review flagged transfers from `/admin/risk-reviews` in the main app.

## Stack

- Java 21
- Spring Boot 4.0.6
- Spring Data JPA
- MySQL (`as_wallet_svc`) in dev, H2 in tests
- Port **8081**

## Quick start

### 1. Database

```sql
CREATE DATABASE IF NOT EXISTS as_wallet_svc
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 2. Configuration

Default settings: `src/main/resources/application.properties`

```properties
spring.datasource.password=${DB_PASSWORD:changeme}
app.risk.threshold.review=40
app.risk.threshold.block=70
```

Or set an environment variable:

```powershell
$env:DB_PASSWORD = "your_mysql_password"
```

JPA `ddl-auto=update` creates/updates tables on startup.

### 3. Run

Start **before** or together with the main app on port 8080:

```powershell
cd D:\Projects\ASWallet-Vol.2-svc
mvn spring-boot:run
```

Service listens on `http://localhost:8081`.

> There is no homepage at `/`. Use the API paths below (e.g. `GET /api/risk-assessments?status=PENDING`).

## Integration with main app

| Trigger | Feign call | Effect |
|---------|------------|--------|
| User confirms transfer | `POST /api/risk-assessments` | Score transfer, persist assessment |
| Admin opens risk reviews | `GET /api/risk-assessments?status=PENDING` | List pending reviews |
| Admin approves / rejects | `PATCH /api/risk-assessments/{id}/review` | Update assessment status |

Main app configuration (`ASWallet-Vol.2`):

```properties
app.risk-service.enabled=true
app.risk-service.base-url=http://localhost:8081
app.risk-service.fail-open=true
spring.cloud.openfeign.httpclient.hc5.enabled=true
```

| Decision | Main app behaviour |
|----------|-------------------|
| `ALLOW` | Transfer proceeds as PENDING |
| `REVIEW` | Transfer proceeds; assessment stays `PENDING` for admin |
| `BLOCK` | Transfer rejected; user sees error message |

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/risk-assessments` | Evaluate transfer risk and persist result |
| `GET` | `/api/risk-assessments/{id}` | Get assessment by id |
| `GET` | `/api/risk-assessments?status=PENDING` | List assessments by status (`APPROVED`, `REJECTED`, `PENDING`) |
| `PATCH` | `/api/risk-assessments/{id}/review` | Admin approve/reject a pending review |

### Create assessment

```bash
curl -X POST http://localhost:8081/api/risk-assessments \
  -H "Content-Type: application/json" \
  -d '{
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
curl -X PATCH http://localhost:8081/api/risk-assessments/{id}/review \
  -H "Content-Type: application/json" \
  -d '{
    "status": "APPROVED",
    "reviewedBy": "admin"
  }'
```

## Risk scoring rules

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

## Domain model

| Entity | Table | Purpose |
|--------|-------|---------|
| `TransferRiskAssessment` | `transfer_risk_assessments` | Persisted risk score, decision, reasons, review metadata |

Optional field `transactionRef` (UUID) is reserved for linking to a main-app `Transaction` id (not yet wired).

## Tests

```powershell
mvn test
```

Test classes:

- `RiskScoringServiceTest` — scoring rules
- `RiskAssessmentServiceTest` — service logic with mocked repository
- `RiskAssessmentControllerWebMvcTest` — REST layer

## Repository layout

This microservice lives in a **separate repository / folder** next to the main app:

```
D:\Projects\
├── ASWallet-Vol.2/          ← main app (:8080, DB as_wallet)
└── ASWallet-Vol.2-svc/      ← this service (:8081, DB as_wallet_svc)
```
