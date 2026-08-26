#!/usr/bin/env bash
set -Eeuo pipefail

STORE="${1:-}"
SCOPE="${2:-api}"

if [[ "$STORE" != "mongo" && "$STORE" != "oracle" ]]; then
  echo "Usage: $0 <mongo|oracle> [api|ui|all]" >&2
  exit 2
fi
if [[ "$SCOPE" != "api" && "$SCOPE" != "ui" && "$SCOPE" != "all" ]]; then
  echo "Usage: $0 <mongo|oracle> [api|ui|all]" >&2
  exit 2
fi

PROJECT="petstore-e2e-${STORE}"
export E2E_ISOLATED=true
export E2E_STORE="$STORE"
export E2E_COMPOSE_PROJECT="$PROJECT"
export E2E_ORACLE_USERNAME=petstore
export E2E_ORACLE_PASSWORD=petstore_local_only
export ORACLE_USERNAME="$E2E_ORACLE_USERNAME"
export ORACLE_PASSWORD="$E2E_ORACLE_PASSWORD"
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=admin
export SUPPLIER_USERNAME=supplier
export SUPPLIER_PASSWORD=supplier

if [[ "$STORE" == "mongo" ]]; then
  export APP_PORT=18080
  export MONGO_PORT=37017
else
  export APP_PORT=18081
  export ORACLE_PORT=2521
fi
export BASE_URL="http://localhost:${APP_PORT}"

DOCKER_SERVER_ARCH="$(docker version --format '{{.Server.Arch}}')"
case "$DOCKER_SERVER_ARCH" in
  arm64|aarch64) export DOCKER_DEFAULT_PLATFORM=linux/arm64 ;;
  amd64|x86_64) export DOCKER_DEFAULT_PLATFORM=linux/amd64 ;;
esac

SUCCEEDED=false
cleanup() {
  local report_failure="${1:-true}"
  if [[ "$report_failure" == "true" && "$SUCCEEDED" != "true" ]]; then
    echo "E2E failed; collecting ${STORE} service logs before cleanup..." >&2
    docker compose --project-name "$PROJECT" --profile "$STORE" logs --no-color >&2 || true
  fi
  docker compose --project-name "$PROJECT" --profile "$STORE" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
on_exit() {
  local run_status=$?
  trap - EXIT
  cleanup true
  exit "$run_status"
}
trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cleanup false
docker compose --project-name "$PROJECT" --profile "$STORE" up --build -d --wait --wait-timeout 360

if [[ -n "${E2E_GREP:-}" ]]; then
  case "$SCOPE" in
    api) npx playwright test e2e/api-contract.spec.js --grep "$E2E_GREP" ;;
    ui) npx playwright test e2e/petstore.spec.js --grep "$E2E_GREP" ;;
    all) npx playwright test --grep "$E2E_GREP" ;;
  esac
else
  case "$SCOPE" in
    api) npx playwright test e2e/api-contract.spec.js ;;
    ui) npx playwright test e2e/petstore.spec.js ;;
    all) npx playwright test ;;
  esac
fi

SUCCEEDED=true
