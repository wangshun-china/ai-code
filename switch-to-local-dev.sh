#!/usr/bin/env sh

# Stop and remove CodeCraft production containers, then start local dev Docker services.
# This script intentionally does not remove Docker volumes.

set -eu

PROJECT_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DEV_COMPOSE_FILE="$PROJECT_ROOT/docker-compose.dev.yml"

log() {
  printf '\n==> %s\n' "$1"
}

die() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
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

log "Prepare local directories"
mkdir -p "$PROJECT_ROOT/tmp/code_output" "$PROJECT_ROOT/tmp/code_deploy"

log "Stop production compose stack if deploy directory exists"
try_compose_down "$PROJECT_ROOT/code-craft-prod"
try_compose_down "$PROJECT_ROOT/../code-craft-prod"
if [ -n "${HOME:-}" ]; then
  try_compose_down "$HOME/code-craft-prod"
fi

log "Remove CodeCraft production/dev containers to free ports"
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

log "Start local dev Docker services"
cd "$PROJECT_ROOT"
docker_compose -f "$DEV_COMPOSE_FILE" up -d --build

log "Current CodeCraft containers"
docker ps --filter "name=code-craft" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

cat <<'EOF'

Local dev Docker services are up.

Typical next steps:
  - Backend services: run from IDE or Maven with local profile
  - Frontend: cd code-craft-frontend && npm run dev
  - Nacos: http://localhost:8848/nacos
  - Node Builder: http://localhost:8020/health

EOF
