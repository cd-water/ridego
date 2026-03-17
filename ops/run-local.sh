#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OPS="$ROOT/ops"
RUN_DIR="$OPS/.run"
COMPOSE=(docker compose -f "$OPS/docker-compose.yml")
SERVICES=(identity trip dispatch location billing gateway)

port_of() {
  case "$1" in
    gateway) echo 8080 ;;
    identity) echo 8081 ;;
    trip) echo 8082 ;;
    dispatch) echo 8083 ;;
    location) echo 8084 ;;
    billing) echo 8085 ;;
  esac
}

pid_file() { echo "$RUN_DIR/$1.pid"; }

is_running() {
  local file
  file="$(pid_file "$1")"
  [ -f "$file" ] && kill -0 "$(cat "$file")" 2>/dev/null
}

port_in_use() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing dependency: $1" >&2
    exit 1
  }
}

start() {
  local name=$1
  local jar="$ROOT/services/$name/target/$name-0.0.1-SNAPSHOT.jar"
  [ -f "$jar" ] || {
    echo "missing $jar; run 'bash ./mvnw -DskipTests package' first" >&2
    exit 1
  }
  if is_running "$name"; then
    echo "$name already running"
    return 0
  fi
  if port_in_use "$(port_of "$name")"; then
    echo "port $(port_of "$name") is already in use; stop the other instance first" >&2
    exit 1
  fi
  local env_args=()
  if [ "$name" = "trip" ]; then
    env_args=(MATCHING_WINDOW_SECONDS="${MATCHING_WINDOW_SECONDS:-20}")
  fi
  env "${env_args[@]}" nohup java -jar "$jar" >"$RUN_DIR/$name.log" 2>&1 &
  echo $! >"$(pid_file "$name")"
  echo "started $name (pid $!)"
}

wait_healthy() {
  local name=$1
  local url="http://localhost:$(port_of "$name")/actuator/health"
  for _ in $(seq 1 60); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$name healthy on $(port_of "$name")"
      return 0
    fi
    if ! is_running "$name"; then
      echo "$name exited; see $RUN_DIR/$name.log" >&2
      return 1
    fi
    sleep 1
  done
  echo "$name not healthy after 60s; see $RUN_DIR/$name.log" >&2
  return 1
}

stop_one() {
  local name=$1 file pid
  file="$(pid_file "$name")"
  [ -f "$file" ] || return 0
  pid="$(cat "$file")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 20); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.5
    done
    kill -9 "$pid" 2>/dev/null || true
    echo "stopped $name"
  fi
  rm -f "$file"
}

case "${1:-}" in
  up)
    require docker
    require java
    require curl
    mkdir -p "$RUN_DIR"
    echo "starting infrastructure..."
    "${COMPOSE[@]}" up -d --wait
    echo "building jars..."
    (cd "$ROOT" && bash ./mvnw -q -DskipTests package)
    for service in "${SERVICES[@]}"; do
      start "$service"
    done
    for service in "${SERVICES[@]}"; do
      wait_healthy "$service"
    done
    echo "RideGo is up: http://localhost:8080/api/v1 (rider1/ridego, driver1/ridego)"
    ;;
  down)
    for service in "${SERVICES[@]}"; do
      stop_one "$service"
    done
    echo "applications stopped; infrastructure left running"
    ;;
  status)
    for service in "${SERVICES[@]}"; do
      if is_running "$service"; then
        echo "$service running on $(port_of "$service")"
      else
        echo "$service stopped"
      fi
    done
    "${COMPOSE[@]}" ps
    ;;
  *)
    echo "usage: $0 {up|down|status}" >&2
    exit 2
    ;;
esac
