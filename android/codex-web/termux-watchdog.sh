#!/data/data/com.termux/files/usr/bin/bash
set -u

PASSWORD_FILE="$1"
LOG_FILE="$2"
WATCHDOG_PID_FILE="$3"
SERVER_PID_FILE="$4"
HEARTBEAT_FILE="$5"
STATUS_FILE="$6"
ROOT="$7"
STARTED_AT="$8"
SERVER_URL="http://127.0.0.1:4096"
FAILURES=0
SERVER_PID=""

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >> "$LOG_FILE"; }
write_status() {
  phase="$1"
  message="$2"
  now="$(date +%s)"
  elapsed=$((now - STARTED_AT))
  printf '%s|%s|%s\n' "$phase" "$message" "$elapsed" > "$STATUS_FILE.tmp"
  mv -f "$STATUS_FILE.tmp" "$STATUS_FILE"
}
healthy() {
  password="$(cat "$PASSWORD_FILE" 2>/dev/null || true)"
  curl -fsS --connect-timeout 1 --max-time 4 -u "opencode:$password" \
    "$SERVER_URL/api/info" -o "$STATUS_FILE.info.tmp" >/dev/null 2>&1 \
    && [ -s "$STATUS_FILE.info.tmp" ] \
    && curl -fsS --connect-timeout 1 --max-time 4 -u "opencode:$password" \
      "$SERVER_URL/api/session?limit=1" -o /dev/null >/dev/null 2>&1
}

printf '%s\n' "$$" > "$WATCHDOG_PID_FILE"
trap 'rm -f "$HEARTBEAT_FILE" "$STATUS_FILE.info.tmp"; exit 0' INT TERM HUP
log "watchdog ativo; health check em /api/info + /api/session"

while true; do
  date +%s > "$HEARTBEAT_FILE"
  if [ -z "$SERVER_PID" ] || ! kill -0 "$SERVER_PID" 2>/dev/null; then
    if [ -n "$SERVER_PID" ]; then
      exit_status=0
      wait "$SERVER_PID" 2>/dev/null || exit_status=$?
      if [ "$exit_status" -eq 137 ] || [ "$exit_status" -eq 9 ]; then
        log "processo do servidor desapareceu com status $exit_status; possível Android/OOM/SIGKILL externo"
      elif [ "$exit_status" -ne 0 ]; then
        log "crash interno ou sinal no servidor; exit_status=$exit_status"
      else
        log "servidor encerrou normalmente; watchdog vai iniciar novamente"
      fi
    fi
    password="$(cat "$PASSWORD_FILE" 2>/dev/null || true)"
    log "iniciando Bun server"
    env OPENCODE_SERVER_PASSWORD="$password" \
      OPENCODE_FILEWATCHER_DISABLE=1 \
      OPENCODE_DISABLE_FILEWATCHER=1 \
      OPENCODE_DISABLE_FFF=1 \
      bun "$ROOT/packages/cli/src/codex-web-server.ts" >> "$LOG_FILE" 2>&1 < /dev/null &
    SERVER_PID="$!"
    printf '%s\n' "$SERVER_PID" > "$SERVER_PID_FILE"
    write_status starting "Servidor iniciado; aguardando health check…"
    FAILURES=0
    sleep 3
  fi

  if healthy; then
    if [ "$FAILURES" -gt 0 ]; then log "health check recuperado após $FAILURES falha(s)"; fi
    FAILURES=0
    write_status online "Servidor online"
  else
    FAILURES=$((FAILURES + 1))
    if kill -0 "$SERVER_PID" 2>/dev/null; then alive="sim"; else alive="não"; fi
    log "health check falhou ($FAILURES/3); processo_alive=$alive"
    write_status checking "Verificando servidor… falha $FAILURES/3"
    if [ "$FAILURES" -ge 3 ]; then
      if kill -0 "$SERVER_PID" 2>/dev/null; then
        log "watchdog detectou servidor vivo mas zumbi; reiniciando após três health checks incompletos"
        write_status zombie_restart "Servidor zumbi detectado; reiniciando…"
        kill "$SERVER_PID" 2>/dev/null || true
        sleep 1
        kill -9 "$SERVER_PID" 2>/dev/null || true
      fi
      SERVER_PID=""
      rm -f "$SERVER_PID_FILE"
      FAILURES=0
    fi
  fi
  sleep 5
done
