# PH Shoes – User Account Service

Spring Boot microservice for **account creation** with **email verification**.
Local dev uses **LocalStack (DynamoDB)** and a local **inbox (MailHog)**.

##  Run (Dev)
```bash
git clone <repo-url>
cd <repo-folder>/ph-shoes-services

# start local infra + service
docker compose -f docker-compose.yml up -d --build useraccounts
# Don't rebuild all
docker compose -f docker-compose.yml up -d useraccounts
```

### Live reload
- The runtime image does not support live reload; rebuild the image after code changes.
- If you prefer seeing the logs in the foreground while coding, run:
  ```bash
  docker compose -f docker-compose.yml up useraccounts
  ```
  and leave that terminal open while you work.

* Service: [http://localhost:8082](http://localhost:8082)
* MailHog (dev inbox): [http://localhost:8025](http://localhost:8025)
* DynamoDB Admin: [http://localhost:8001](http://localhost:8001)
* LocalStack (AWS edge): [http://localhost:4566](http://localhost:4566)

To stop:

```bash
docker compose -f docker-compose.yml down
```

## 🔌 Endpoints

Base path: `/api/v1/user-accounts`

| Method | Path                   | Purpose                                      |
| -----: | ---------------------- | -------------------------------------------- |
|   POST | `/`                    | Create account (preferred)                   |
|   POST | `/register`            | Legacy alias for older clients (still open)  |
|    GET | `/verify?token=...`    | Verify via email link                        |
|   POST | `/verification/resend` | Resend verification email                    |

> In dev, emails are captured by **MailHog** (open the UI to see the message + link).

## ⏱️ Rate limiting

- The service now uses the shared `ApiRateLimiter` from `ph-shoes-starter-services-common-web` to throttle email-triggering endpoints.
- Configuration lives under `phshoes.api.rate-limit` in `application*.yml`. It defines global/per-IP/per-user caps plus per-route overrides (e.g., account creation and `/verify/email/resend`).
- Dev profile keeps the numbers low so you can exercise the limiter quickly; `application.yml` and `application-prod.yml` raise the ceilings for staging/prod.
- Update those YAML blocks if SES quota or traffic patterns change; no code changes are required.

## Dev Images & Ports

| Service           | Image                          | Ports (host) |
| ----------------- | ------------------------------ | ------------ |
| Account Service   | built from `Dockerfile`        | 8082 → 8082  |
| LocalStack        | `localstack/localstack:latest` | 4566 → 4566  |
| DynamoDB Admin    | `aaronshaf/dynamodb-admin`     | 8001 → 8001  |
| MailHog (SMTP/UI) | `mailhog/mailhog:latest`       | 1025, 8025   |
