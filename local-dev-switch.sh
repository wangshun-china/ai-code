#!/usr/bin/env bash

# Switch CodeCraft to local development infrastructure.
# The script only sets local defaults for missing variables; existing shell
# environment values always win.

if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -Eeuo pipefail

PROJECT_NAME="code-craft"
PROJECT_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DEV_COMPOSE_FILE="$PROJECT_ROOT/docker-compose.dev.yml"

log() {
  printf '\n==> %s\n' "$1"
}

die() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

ensure_env() {
  name="$1"
  default_value="$2"
  if [ -z "${!name:-}" ]; then
    export "$name=$default_value"
    printf 'Set default %s=%s\n' "$name" "$default_value"
  else
    printf 'Keep existing %s\n' "$name"
  fi
}

docker_compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    die "Docker Compose is not available. Install docker compose plugin or docker-compose."
  fi
}

try_compose_down() {
  dir="$1"
  if [ -f "$dir/docker-compose.yml" ]; then
    log "Compose down: $dir"
    (
      cd "$dir"
      docker_compose down --remove-orphans || true
    )
  fi
}

remove_container_if_exists() {
  name="$1"
  if docker container inspect "$name" >/dev/null 2>&1; then
    printf 'Removing container: %s\n' "$name"
    docker stop -t 10 "$name" >/dev/null 2>&1 || true
    docker rm -f "$name" >/dev/null 2>&1 || true
  fi
}

command -v docker >/dev/null 2>&1 || die "Docker is not installed or not in PATH."
[ -f "$DEV_COMPOSE_FILE" ] || die "Missing dev compose file: $DEV_COMPOSE_FILE"

log "Load local development defaults"
ensure_env CODE_CRAFT_MYSQL_ROOT_PASSWORD "root123456"
ensure_env CODE_CRAFT_MYSQL_DATABASE "ai_code_gen"
ensure_env CODE_CRAFT_MYSQL_USERNAME "ai_code_gen_user"
ensure_env CODE_CRAFT_MYSQL_PASSWORD "12345678"
ensure_env CODE_CRAFT_MYSQL_PORT "3307"
ensure_env CODE_CRAFT_NACOS_USERNAME "nacos"
ensure_env CODE_CRAFT_NACOS_PASSWORD "nacos"
ensure_env CODE_CRAFT_NACOS_AUTH_TOKEN "U2VjcmV0S2V5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5"

log "Prepare local directories"
mkdir -p "$PROJECT_ROOT/tmp/code_output" "$PROJECT_ROOT/tmp/code_deploy"

log "Stop deployed CodeCraft compose stacks if present"
try_compose_down "$PROJECT_ROOT/code-craft-prod"
try_compose_down "$PROJECT_ROOT/../code-craft-prod"
if [ -n "${HOME:-}" ]; then
  try_compose_down "$HOME/code-craft-prod"
fi

log "Remove CodeCraft containers to free local ports"
for container in \
  code-craft-nginx \
  code-craft-frontend \
  code-craft-user-service \
  code-craft-app-service \
  code-craft-screenshot-service \
  code-craft-node-builder \
  code-craft-nacos \
  code-craft-nacos-db-init \
  code-craft-redis \
  code-craft-mysql
do
  remove_container_if_exists "$container"
done

log "Start CodeCraft local middleware"
cd "$PROJECT_ROOT"
docker_compose -f "$DEV_COMPOSE_FILE" up -d --build

log "Running CodeCraft containers"
docker ps --filter "name=$PROJECT_NAME" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

cat <<'EOF'

Local development middleware is up.

Typical next steps:
  - Backend services: run from IDE or Maven with local profile
  - Frontend: cd code-craft-frontend && npm run dev
  - Nacos: http://localhost:8848/nacos
  - Node Builder: http://localhost:8020/health

EOF
