#!/data/data/com.termux/files/usr/bin/bash
set -eu

ROOT="$HOME/opencode"
REPOSITORY_URL="${OPENCODE_REPOSITORY_URL:-}"
LOG_DIR="$HOME/.cache/codex-web"
LOG_FILE="$LOG_DIR/server.log"
PID_FILE="$LOG_DIR/server-watchdog.pid"
PASSWORD_FILE="$LOG_DIR/server-password"
CONFIG_FILE="$ROOT/opencode.jsonc"
CONFIG_HASH_FILE="$LOG_DIR/config-hash"
BOOTSTRAP_STAMP="$LOG_DIR/bootstrap-stamp"
mkdir -p "$LOG_DIR"

if ! command -v bun >/dev/null 2>&1 || ! command -v git >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
  if ! command -v pkg >/dev/null 2>&1; then
    printf '%s\n' "Gerenciador pkg não encontrado no Termux" >> "$LOG_FILE"
    exit 1
  fi
  printf '%s\n' "Instalando dependências Termux (git, curl e bun)…" >> "$LOG_FILE"
  pkg update -y >> "$LOG_FILE" 2>&1 || true
  pkg install -y git curl bun >> "$LOG_FILE" 2>&1 || {
    printf '%s\n' "Não foi possível instalar as dependências Termux" >> "$LOG_FILE"
    exit 1
  }
fi

if [ ! -d "$ROOT/packages/cli" ]; then
  if [ -z "$REPOSITORY_URL" ]; then
    printf '%s\n' "O repositório não foi encontrado em $ROOT e OPENCODE_REPOSITORY_URL não foi configurada" >> "$LOG_FILE"
    exit 1
  fi
  if [ -e "$ROOT" ] && [ -n "$(find "$ROOT" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]; then
    printf '%s\n' "$ROOT existe e não está vazio; clone automático cancelado para preservar arquivos" >> "$LOG_FILE"
    exit 1
  fi
  mkdir -p "$(dirname "$ROOT")"
  printf '%s\n' "Clonando o projeto do GitHub…" >> "$LOG_FILE"
  git clone --depth 1 "$REPOSITORY_URL" "$ROOT" >> "$LOG_FILE" 2>&1 || {
    printf '%s\n' "Falha ao clonar o repositório GitHub" >> "$LOG_FILE"
    exit 1
  }
fi

if [ ! -f "$CONFIG_FILE" ]; then
  printf '%s\n' "Configuração não encontrada em $CONFIG_FILE" >> "$LOG_FILE"
  exit 1
fi

if [ -f "$HOME/.config/opencode/apinex.env" ]; then
  # shellcheck disable=SC1091
  . "$HOME/.config/opencode/apinex.env"
fi

if [ -z "${APINEX_API_KEY:-}" ]; then
  printf '%s\n' "APINEX_API_KEY não configurada em ~/.config/opencode/apinex.env" >> "$LOG_FILE"
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  PROJECT_HASH="$(sha256sum "$ROOT/package.json" "$ROOT/bun.lock" 2>/dev/null | sha256sum | awk '{print $1}')"
else
  PROJECT_HASH="$(cksum "$ROOT/package.json" "$ROOT/bun.lock" 2>/dev/null | cksum | awk '{print $1 ":" $2}')"
fi

# Update only a clean checkout with an origin. Local edits are never overwritten.
if git -C "$ROOT" remote get-url origin >/dev/null 2>&1 \
    && git -C "$ROOT" diff --quiet \
    && git -C "$ROOT" diff --cached --quiet; then
  git -C "$ROOT" pull --ff-only >> "$LOG_FILE" 2>&1 || \
    printf '%s\n' "Atualização automática ignorada; mantendo a versão local" >> "$LOG_FILE"
fi

if [ ! -s "$BOOTSTRAP_STAMP" ] || [ "$(cat "$BOOTSTRAP_STAMP")" != "$PROJECT_HASH" ] \
    || [ ! -d "$ROOT/packages/app/dist" ]; then
  printf '%s\n' "Instalando dependências e compilando o cliente web…" >> "$LOG_FILE"
  cd "$ROOT"
  if [ -f bun.lock ]; then bun install --frozen-lockfile >> "$LOG_FILE" 2>&1; else bun install >> "$LOG_FILE" 2>&1; fi
  bun run --cwd packages/app build >> "$LOG_FILE" 2>&1
  printf '%s\n' "$PROJECT_HASH" > "$BOOTSTRAP_STAMP"
  chmod 600 "$BOOTSTRAP_STAMP"
fi

if command -v sha256sum >/dev/null 2>&1; then
  CONFIG_HASH="$(sha256sum "$CONFIG_FILE" | awk '{print $1}')"
else
  CONFIG_HASH="$(cksum "$CONFIG_FILE" | awk '{print $1 ":" $2}')"
fi

stop_existing_server() {
  if [ -s "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    old_pid="$(cat "$PID_FILE")"
    kill "$old_pid" 2>/dev/null || true
    pkill -P "$old_pid" 2>/dev/null || true
  fi

  # The watchdog can leave Bun alive briefly after its parent exits.
  if command -v pgrep >/dev/null 2>&1; then
    for server_pid in $(pgrep -f 'bun packages/cli/src/codex-web-server.ts' 2>/dev/null || true); do
      kill "$server_pid" 2>/dev/null || true
    done
  fi

  sleep 1
}

SERVER_READY=0
if curl -fsS --connect-timeout 1 -u "opencode:${OPENCODE_SERVER_PASSWORD:-}" \
    http://127.0.0.1:4096/api/info >/dev/null 2>&1; then
  SERVER_READY=1
fi

if [ -s "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  if [ -s "$PASSWORD_FILE" ] && [ "$(cat "$PASSWORD_FILE")" = "${OPENCODE_SERVER_PASSWORD:-}" ] \
      && [ -s "$CONFIG_HASH_FILE" ] && [ "$(cat "$CONFIG_HASH_FILE")" = "$CONFIG_HASH" ]; then
    exit 0
  fi
  stop_existing_server
elif [ "$SERVER_READY" = 1 ]; then
  if [ -s "$CONFIG_HASH_FILE" ] && [ "$(cat "$CONFIG_HASH_FILE")" = "$CONFIG_HASH" ]; then
    exit 0
  fi
  stop_existing_server
fi

cd "$ROOT"
printf '%s\n' "${OPENCODE_SERVER_PASSWORD:-}" > "$PASSWORD_FILE"
printf '%s\n' "$CONFIG_HASH" > "$CONFIG_HASH_FILE"
chmod 600 "$PASSWORD_FILE"
chmod 600 "$CONFIG_HASH_FILE"
# Android/Bun can crash in the native file-watcher and FFF layers after the
# server has been alive for a while. The web client does not need those layers
# to send chat requests, so keep them disabled on the mobile launcher.
nohup bash -c '
  while true; do
    server_password="$(cat "$1")"
    env OPENCODE_SERVER_PASSWORD="$server_password" \
      OPENCODE_FILEWATCHER_DISABLE=1 \
      OPENCODE_DISABLE_FILEWATCHER=1 \
      OPENCODE_DISABLE_FFF=1 \
      bun packages/cli/src/codex-web-server.ts >> "$2" 2>&1 < /dev/null
    status=$?
    printf "server exited with status %s; restarting in 2s\\n" "$status" >> "$2"
    sleep 2
  done
' -- "$PASSWORD_FILE" "$LOG_FILE" \
  >> "$LOG_FILE" 2>&1 < /dev/null &
printf '%s\n' "$!" > "$PID_FILE"
