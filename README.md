# PH Shoes – User Account Service

Spring Boot microservice for **account creation** with **email verification**.
Local dev uses **LocalStack (DynamoDB)** and a local **inbox (MailHog)**.

## 🚀 Run (Dev)

```bash
git clone <repo-url>
cd <repo-folder>

# start local infra + service
docker compose -f docker-compose.dev.yml up -d --build
```

* Service: [http://localhost:8080](http://localhost:8080)
* MailHog (dev inbox): [http://localhost:8025](http://localhost:8025)
* DynamoDB Admin: [http://localhost:8001](http://localhost:8001)
* LocalStack (AWS edge): [http://localhost:4566](http://localhost:4566)

To stop:

```bash
docker compose -f docker-compose.dev.yml down
```

## 🔌 Endpoints

Base path: `/api/v1/user-accounts`

| Method | Path                   | Purpose                   |
| -----: | ---------------------- | ------------------------- |
|   POST | `/register`            | Create account            |
|    GET | `/verify?token=...`    | Verify via email link     |
|   POST | `/verification/resend` | Resend verification email |

> In dev, emails are captured by **MailHog** (open the UI to see the message + link).

## 🐳 Dev Images & Ports

| Service           | Image                          | Ports (host) |
| ----------------- | ------------------------------ | ------------ |
| Account Service   | built from `DockerfileDev`     | 8080 → 8080  |
| LocalStack        | `localstack/localstack:latest` | 4566 → 4566  |
| DynamoDB Admin    | `aaronshaf/dynamodb-admin`     | 8001 → 8001  |
| MailHog (SMTP/UI) | `mailhog/mailhog:latest`       | 1025, 8025   |
