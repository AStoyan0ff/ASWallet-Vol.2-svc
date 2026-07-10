<h1 align="center">
  💳 ASWallet-Vol.2-svc 💳
</h1>


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
CREATE DATABASE IF NOT EXISTS as_wallet_svc;
```

### 2. Configuration

Default settings: `src/main/resources/application.properties`

```properties
spring.datasource.password=${DB_PASSWORD:change me}
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
mvn spring-boot:run
```

Service listens on `http://localhost:8081`.

> There is no homepage at `/`. Use the API paths below (e.g. `GET /api/risk-assessments?status=PENDING`).

## Integration with main app

| Trigger | Feign call | Effect |
|---------|------------|--------|
| User confirms transfer | `POST /api/risk-assessments` | Score transfer, persist assessment (with `transactionRef`) |
| Admin opens risk reviews | `GET /api/risk-assessments?status=PENDING` | List pending reviews |
| Admin loads single review | `GET /api/risk-assessments/{id}` | Fetch assessment before approve/reject |
| Admin approves / rejects | `PATCH /api/risk-assessments/{id}/review` | Update assessment status |
| Admin delete all reviews | `DELETE /api/risk-assessments?status=PENDING` | Remove all pending assessments |

Main app configuration (`ASWallet-Vol.2`):

```properties
app.risk-service.enabled=true
app.risk-service.base-url=http://localhost:8081
app.risk-service.fail-open=true
spring.cloud.openfeign.httpclient.hc5.enabled=true
```

| Decision | Microservice status | Main app behaviour |
|----------|---------------------|-------------------|
| `ALLOW` | `APPROVED` | Transfer saved as `PENDING`; scheduler completes after ~5 s |
| `REVIEW` | `PENDING` | Transfer saved as `PENDING_RISK_REVIEW`; held until admin action |
| `BLOCK` | `REJECTED` | Transfer rejected before creation; user sees error |

Admin **Approve** → main app completes wallet transfer → PATCH assessment to `APPROVED`.  
Admin **Reject** or **Delete all** → main app refunds sender / cancels transfer → PATCH or bulk DELETE in MS.

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/risk-assessments` | Evaluate transfer risk and persist result |
| `GET` | `/api/risk-assessments/{id}` | Get assessment by id |
| `GET` | `/api/risk-assessments?status=PENDING` | List assessments by status (`APPROVED`, `REJECTED`, `PENDING`) |
| `PATCH` | `/api/risk-assessments/{id}/review` | Admin approve/reject a pending review |
| `DELETE` | `/api/risk-assessments?status=PENDING` | Delete all assessments with given status (204 No Content) |

### Create assessment

```bash
curl -X POST http://localhost:8081/api/risk-assessments \
  -H "Content-Type: application/json" \
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
curl -X PATCH http://localhost:8081/api/risk-assessments/{id}/review \
  -H "Content-Type: application/json" \
  -d '{
    "status": "APPROVED",
    "reviewedBy": "admin"
  }'
```

### Delete all pending (admin bulk clear)

```bash
curl -X DELETE "http://localhost:8081/api/risk-assessments?status=PENDING"
```

Returns **204 No Content**. Main app calls this after rejecting/refunding each linked transfer.

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

Field **`transactionRef`** (UUID) links an assessment to the main-app `Transaction` id. The main app sends it on `POST` so admin approve/reject/clear can update the correct wallet transfer.

## Tests

```powershell
mvn test
```

Test classes:

- `RiskScoringServiceTest` — scoring rules
- `RiskAssessmentServiceTest` — service logic with mocked repository
- `RiskAssessmentControllerWebMvcTest` — REST layer (incl. `DELETE ?status=PENDING`)

## Repository layout

This microservice lives in a **separate repository / folder** next to the main app:

```
D:\Projects\
├── ASWallet-Vol.2/          ← main app (:8080, DB as_wallet)
└── ASWallet-Vol.2-svc/      ← this service (:8081, DB as_wallet_svc)
```

## Recent changes (v2.0.1)

### API

- **`DELETE /api/risk-assessments?status=…`** — bulk delete by status (used by admin **Delete all reviews** in main app)
- **`GET /api/risk-assessments/{id}`** — fetch single assessment (Feign `getAssessment`)
- **`transactionRef`** wired from main app on create; stored on `TransferRiskAssessment` and returned in responses

### Integration behaviour (with main app)

- `REVIEW` assessments stay `PENDING` in MS until admin PATCH; wallet transfer stays `PENDING_RISK_REVIEW`
- `BLOCK` assessments are `REJECTED` immediately; main app never creates the transfer
- Bulk DELETE supports clearing orphaned/stale pending reviews after wallet-side refunds

### Tests

- `RiskAssessmentControllerWebMvcTest` extended for DELETE endpoint
