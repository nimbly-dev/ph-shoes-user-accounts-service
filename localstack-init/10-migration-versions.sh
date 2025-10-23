#!/usr/bin/env bash
set -euo pipefail

AWS="awslocal --region ${AWS_DEFAULT_REGION:-ap-southeast-1}"
TABLE_PREFIX="${TABLE_PREFIX:-}"
VERSIONS_TABLE="${TABLE_PREFIX}migration_versions"

echo "[init] ensuring DynamoDB table: ${VERSIONS_TABLE}"
# create-table is idempotent-ish; ignore “ResourceInUseException”
$AWS dynamodb create-table \
  --table-name "${VERSIONS_TABLE}" \
  --billing-mode PAY_PER_REQUEST \
  --attribute-definitions AttributeName=service,AttributeType=S \
  --key-schema AttributeName=service,KeyType=HASH \
  >/dev/null 2>&1 || true

# Optionally seed a row for this service (only if SERVICE_NAME is provided)
if [[ -n "${SERVICE_NAME:-}" ]]; then
  INITIAL_VERSION="${INITIAL_VERSION:-0.0.0}"
  echo "[init] ensuring migration_versions row for service=${SERVICE_NAME} at ${INITIAL_VERSION}"
  $AWS dynamodb put-item --table-name "${VERSIONS_TABLE}" --item "{
    \"service\":        {\"S\":\"${SERVICE_NAME}\"},
    \"currentVersion\": {\"S\":\"${INITIAL_VERSION}\"},
    \"updatedAt\":      {\"S\":\"1970-01-01T00:00:00Z\"}
  }" >/dev/null 2>&1 || true
fi
