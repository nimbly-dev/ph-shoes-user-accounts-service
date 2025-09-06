## 📦 Project Setup

### Prerequisites

* [Docker](https://www.docker.com/) installed and running
* [Docker Compose](https://docs.docker.com/compose/) installed

---

## 🚀 Running the Project Locally

1. Clone the repository:

   ```bash
   git clone <repo-url>
   cd <repo-folder>
   ```

2. Start the development environment with LocalStack:

   ```bash
   docker compose -f docker-compose.dev.yml up --build
   ```

3. Once started:

    * LocalStack will run locally and emulate AWS services (such as **DynamoDB**).
    * Your project will be available at [http://localhost:3000](http://localhost:3000) (if it runs a web server).
    * You can inspect LocalStack’s web interface at [http://localhost:4566](http://localhost:4566) (default LocalStack port).

---

## 🐳 What’s in `docker-compose.dev.yml`?

The `docker-compose.dev.yml` file sets up the following services:

* **localstack** – Runs [LocalStack](https://localstack.cloud/), an emulator for AWS services such as S3, DynamoDB, SQS, and more. This lets you test cloud integrations without needing real AWS resources.
* **application** – The Microservice service itself, configured to connect to the LocalStack endpoint for local development.

By running the above command, Docker will start both services and automatically link your app to LocalStack.

---

## ⚡ Quick Commands

* **Start environment**

  ```bash
  docker compose -f docker-compose.dev.yml up --build
  ```

* **Stop environment**

  ```bash
  docker compose -f docker-compose.dev.yml down
  ```

* **Rebuild without cache**

  ```bash
  docker compose -f docker-compose.dev.yml build --no-cache
  ```
