#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$HOME/opencode"
REPOSITORY_URL="${OPENCODE_REPOSITORY_URL:-https://github.com/jhonathamlegal-sudo/codex-web-opencode.git}"
EXPECTED_COMMIT="${OPENCODE_REPOSITORY_COMMIT:-}"
LOG_DIR="$HOME/.cache/codex-web"
LOG_FILE="$LOG_DIR/server.log"
STATUS_FILE="$LOG_DIR/status"
WATCHDOG_PID_FILE="$LOG_DIR/server-watchdog.pid"
SERVER_PID_FILE="$LOG_DIR/server.pid"
HEARTBEAT_FILE="$LOG_DIR/watchdog-heartbeat"
PASSWORD_FILE="$LOG_DIR/server-password"
CONFIG_FILE="$ROOT/opencode.jsonc"
CONFIG_HASH_FILE="$LOG_DIR/config-hash"
BOOTSTRAP_STAMP="$LOG_DIR/bootstrap-stamp"
DEPENDENCY_STAMP="$LOG_DIR/dependency-stamp"
WATCHDOG_CACHE="$LOG_DIR/termux-watchdog.sh"
BOOT_SCRIPT="$HOME/.termux/boot/codex-web-start.sh"
STARTED_AT="$(date +%s)"
REPAIR_REQUESTED="${OPENCODE_REPAIR:-0}"
CLONED_NOW=0
mkdir -p "$LOG_DIR"

if [ -z "${OPENCODE_SERVER_PASSWORD:-}" ] && [ -s "$PASSWORD_FILE" ]; then
  OPENCODE_SERVER_PASSWORD="$(cat "$PASSWORD_FILE")"
  export OPENCODE_SERVER_PASSWORD
fi

if [ -s "$HEARTBEAT_FILE" ]; then
  previous_heartbeat="$(cat "$HEARTBEAT_FILE" 2>/dev/null || echo 0)"
  if [ "$previous_heartbeat" -gt 0 ] 2>/dev/null; then
    heartbeat_age=$(($(date +%s) - previous_heartbeat))
    if [ "$heartbeat_age" -gt 30 ]; then
      printf '[%s] watchdog anterior desapareceu sem encerramento; possível Android/OOM ou encerramento externo (heartbeat %ss)\n' \
        "$(date '+%Y-%m-%d %H:%M:%S')" "$heartbeat_age" >> "$LOG_FILE"
    fi
  fi
fi

write_status() {
  phase="$1"
  message="$2"
  now="$(date +%s)"
  elapsed=$((now - STARTED_AT))
  printf '%s|%s|%s\n' "$phase" "$message" "$elapsed" > "$STATUS_FILE.tmp"
  mv -f "$STATUS_FILE.tmp" "$STATUS_FILE"
}

fail() {
  write_status "$1" "$2"
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$2" >> "$LOG_FILE"
  exit 1
}

printf '[%s] bootstrap requested\n' "$(date '+%Y-%m-%d %H:%M:%S')" >> "$LOG_FILE"
write_status checking "Verificando o ambiente Termux…"

if command -v termux-wake-lock >/dev/null 2>&1; then
  termux-wake-lock >> "$LOG_FILE" 2>&1 || printf '[%s] termux-wake-lock falhou; continuando\n' "$(date '+%Y-%m-%d %H:%M:%S')" >> "$LOG_FILE"
  printf '[%s] wake lock ativo\n' "$(date '+%Y-%m-%d %H:%M:%S')" >> "$LOG_FILE"
else
  printf '[%s] termux-wake-lock indisponível; instale/atualize o Termux\n' "$(date '+%Y-%m-%d %H:%M:%S')" >> "$LOG_FILE"
fi

if ! command -v bun >/dev/null 2>&1 || ! command -v git >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
  write_status installing_dependencies "Instalando dependências do Termux…"
  if ! command -v pkg >/dev/null 2>&1; then
    fail dependency_error "Gerenciador pkg não encontrado no Termux"
  fi
  pkg update -y >> "$LOG_FILE" 2>&1 || true
  pkg install -y git curl bun >> "$LOG_FILE" 2>&1 || fail dependency_error "Não foi possível instalar git, curl e bun"
fi

if [ ! -f "$ROOT/packages/cli/package.json" ]; then
  if [ -z "$REPOSITORY_URL" ]; then
    fail clone_error "O repositório não foi encontrado e OPENCODE_REPOSITORY_URL não foi configurada"
  fi
  if [ -e "$ROOT" ] && [ -n "$(find "$ROOT" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]; then
    if [ "$REPAIR_REQUESTED" = "1" ]; then
      write_status repairing "Criando backup seguro do opencode quebrado…"
      BACKUP_ROOT="$HOME/opencode-user-backup-$(date +%Y%m%d-%H%M%S)"
      mv "$ROOT" "$BACKUP_ROOT" || fail repair_error "Não foi possível preservar o diretório opencode existente"
      printf '[%s] backup preservado em %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$BACKUP_ROOT" >> "$LOG_FILE"
    else
      fail repair_required "~/opencode existe, mas está incompleto. O reparo seguro criará um backup antes do clone."
    fi
  fi
  mkdir -p "$(dirname "$ROOT")"
  write_status cloning "Clonando o projeto do GitHub…"
  git clone --depth 1 "$REPOSITORY_URL" "$ROOT" >> "$LOG_FILE" 2>&1 || fail clone_error "Falha ao clonar o repositório GitHub"
  CLONED_NOW=1
fi

if [ ! -f "$ROOT/packages/cli/package.json" ] || [ ! -f "$ROOT/package.json" ] || [ ! -f "$ROOT/bun.lock" ]; then
  fail repository_conflict "O checkout não contém um projeto OpenCode completo"
fi
if [ ! -d "$ROOT/.git" ]; then
  fail repository_conflict "O diretório $ROOT não é um checkout Git válido"
fi

normalize_repo_url() {
  printf '%s' "$1" | sed 's#/$##; s#\.git$##'
}

ORIGIN_URL="$(git -C "$ROOT" remote get-url origin 2>/dev/null || true)"
if [ -z "$ORIGIN_URL" ]; then
  fail repository_conflict "O checkout $ROOT não possui um remote origin configurado"
fi
REMOTE_MATCH=1
if [ -n "$REPOSITORY_URL" ] && [ "$(normalize_repo_url "$ORIGIN_URL")" != "$(normalize_repo_url "$REPOSITORY_URL")" ]; then
  REMOTE_MATCH=0
  write_status local_checkout "Checkout local válido preservado; remote diferente da origem do launcher"
  printf '[%s] remote diferente da origem configurada; nenhuma atualização automática será feita\n' \
    "$(date '+%Y-%m-%d %H:%M:%S')" >> "$LOG_FILE"
fi

if [ "$CLONED_NOW" = 1 ] && [ -n "$EXPECTED_COMMIT" ]; then
  ACTUAL_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || true)"
  if [ "$ACTUAL_COMMIT" != "$EXPECTED_COMMIT" ]; then
    fail repository_integrity "O clone não corresponde à revisão verificada pelo APK"
  fi
fi

if [ -f "$HOME/.config/opencode/apinex.env" ]; then
  # shellcheck disable=SC1091
  . "$HOME/.config/opencode/apinex.env"
fi

if [ -z "${APINEX_API_KEY:-}" ]; then
  fail key_missing "APINEX_API_KEY ausente em ~/.config/opencode/apinex.env"
fi

if [ "$REMOTE_MATCH" = 1 ] && [ "$CLONED_NOW" = 0 ] \
    && git -C "$ROOT" diff --quiet && git -C "$ROOT" diff --cached --quiet; then
  write_status updating "Verificando atualizações do projeto…"
  git -C "$ROOT" pull --ff-only >> "$LOG_FILE" 2>&1 || \
    printf '[%s] atualização automática não aplicada; mantendo checkout local íntegro\n' "$(date '+%Y-%m-%d %H:%M:%S')" >> "$LOG_FILE"
elif [ "$CLONED_NOW" = 0 ]; then
  write_status local_changes "Alterações locais ou origem diferente preservadas; atualização pausada"
  printf '[%s] atualização automática pausada; nenhum reset, descarte ou pull de origem diferente foi executado\n' \
    "$(date '+%Y-%m-%d %H:%M:%S')" >> "$LOG_FILE"
fi

if [ ! -f "$CONFIG_FILE" ]; then
  fail config_missing "Configuração não encontrada em $CONFIG_FILE"
fi

hash_files() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$@" | sha256sum | awk '{print $1}'
  else
    cksum "$@" | cksum | awk '{print $1 ":" $2}'
  fi
}

DEPENDENCY_HASH="$(hash_files "$ROOT/package.json" "$ROOT/bun.lock")"
if [ ! -s "$DEPENDENCY_STAMP" ] || [ "$(cat "$DEPENDENCY_STAMP")" != "$DEPENDENCY_HASH" ] \
    || [ ! -d "$ROOT/node_modules" ]; then
  write_status installing "Instalando dependências do OpenCode…"
  cd "$ROOT"
  if [ -f bun.lock ]; then
    bun install --frozen-lockfile >> "$LOG_FILE" 2>&1 || fail dependency_error "Falha ao instalar as dependências do OpenCode"
  else
    bun install >> "$LOG_FILE" 2>&1 || fail dependency_error "Falha ao instalar as dependências do OpenCode"
  fi
  printf '%s\n' "$DEPENDENCY_HASH" > "$DEPENDENCY_STAMP"
  chmod 600 "$DEPENDENCY_STAMP"
fi

SOURCE_HASH="$({
  git -C "$ROOT" rev-parse HEAD
  git -C "$ROOT" diff --no-ext-diff --binary
  git -C "$ROOT" diff --cached --no-ext-diff --binary
  git -C "$ROOT" ls-files --others --exclude-standard | while IFS= read -r file; do
    case "$file" in
      packages/*|android/codex-web/*|opencode.jsonc|package.json|bun.lock)
        [ -f "$ROOT/$file" ] && sha256sum "$ROOT/$file" ;;
    esac
  done
} | sha256sum | awk '{print $1}')"

if [ ! -s "$BOOTSTRAP_STAMP" ] || [ "$(cat "$BOOTSTRAP_STAMP")" != "$SOURCE_HASH" ] \
    || [ ! -f "$ROOT/packages/app/dist/index.html" ]; then
  write_status compiling "Compilando a interface web…"
  cd "$ROOT"
  if command -v timeout >/dev/null 2>&1; then
    timeout --signal=TERM --kill-after=8s 300s bun run --cwd packages/app build >> "$LOG_FILE" 2>&1 || {
      if [ ! -f "$ROOT/packages/app/dist/index.html" ]; then
        fail build_error "Falha ou timeout ao compilar a interface web"
      fi
      printf '[%s] build atingiu timeout, mas dist/index.html existe; continuando\n' "$(date '+%Y-%m-%d %H:%M:%S')" >> "$LOG_FILE"
    }
  else
    bun run --cwd packages/app build >> "$LOG_FILE" 2>&1 || fail build_error "Falha ao compilar a interface web"
  fi
  printf '%s\n' "$SOURCE_HASH" > "$BOOTSTRAP_STAMP"
  chmod 600 "$BOOTSTRAP_STAMP"
fi

if command -v sha256sum >/dev/null 2>&1; then
  CONFIG_HASH="$(sha256sum "$CONFIG_FILE" | awk '{print $1}')"
else
  CONFIG_HASH="$(cksum "$CONFIG_FILE" | awk '{print $1 ":" $2}')"
fi

process_args() {
  local pid="$1"
  tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || ps -o args= -p "$pid" 2>/dev/null || true
}
is_server_pid() {
  local pid="$1"
  kill -0 "$pid" 2>/dev/null || return 1
  case "$(process_args "$pid")" in
    *"$ROOT/packages/cli/src/codex-web-server.ts"*) return 0 ;;
    *) return 1 ;;
  esac
}
is_watchdog_pid() {
  local pid="$1"
  kill -0 "$pid" 2>/dev/null || return 1
  case "$(process_args "$pid")" in
    *"termux-watchdog.sh"*) return 0 ;;
    *) return 1 ;;
  esac
}

stop_existing_server() {
  if [ -s "$WATCHDOG_PID_FILE" ]; then
    old_pid="$(cat "$WATCHDOG_PID_FILE")"
    if is_watchdog_pid "$old_pid"; then kill "$old_pid" 2>/dev/null || true; fi
  fi
  if [ -s "$SERVER_PID_FILE" ]; then
    old_pid="$(cat "$SERVER_PID_FILE")"
    if is_server_pid "$old_pid"; then
      kill "$old_pid" 2>/dev/null || true
      sleep 1
      kill -9 "$old_pid" 2>/dev/null || true
    fi
  fi
  rm -f "$WATCHDOG_PID_FILE" "$SERVER_PID_FILE"
  sleep 1
}

health_check() {
  password="${OPENCODE_SERVER_PASSWORD:-}"
  curl -fsS --connect-timeout 1 --max-time 4 -u "opencode:$password" \
    http://127.0.0.1:4096/api/info -o "$LOG_DIR/health-info.tmp" >/dev/null 2>&1 \
    && [ -s "$LOG_DIR/health-info.tmp" ] \
    && curl -fsS --connect-timeout 1 --max-time 4 -u "opencode:$password" \
      'http://127.0.0.1:4096/api/session?limit=1' -o /dev/null >/dev/null 2>&1
}

SERVER_READY=0
if health_check; then SERVER_READY=1; fi
WATCHDOG_ALIVE=0
if [ -s "$WATCHDOG_PID_FILE" ] && is_watchdog_pid "$(cat "$WATCHDOG_PID_FILE")"; then WATCHDOG_ALIVE=1; fi

if [ "$WATCHDOG_ALIVE" = 1 ]; then
  if [ -s "$PASSWORD_FILE" ] && [ "$(cat "$PASSWORD_FILE")" = "${OPENCODE_SERVER_PASSWORD:-}" ] \
      && [ -s "$CONFIG_HASH_FILE" ] && [ "$(cat "$CONFIG_HASH_FILE")" = "$CONFIG_HASH" ] \
      && [ "$SERVER_READY" = 1 ]; then
    write_status online "Servidor online"
    exit 0
  fi
  stop_existing_server
elif [ "$SERVER_READY" = 1 ] && [ -s "$CONFIG_HASH_FILE" ] && [ "$(cat "$CONFIG_HASH_FILE")" = "$CONFIG_HASH" ]; then
  old_server_pid="$(pgrep -f "$ROOT/packages/cli/src/codex-web-server.ts" 2>/dev/null | head -n 1 || true)"
  if [ -n "$old_server_pid" ] && is_server_pid "$old_server_pid"; then
    printf '%s\n' "$old_server_pid" > "$SERVER_PID_FILE"
  else
    stop_existing_server
  fi
else
  stop_existing_server
fi

install_boot_hook() {
  mkdir -p "$(dirname "$BOOT_SCRIPT")"
  tmp="$BOOT_SCRIPT.tmp"
  {
    printf '%s\n' '#!/data/data/com.termux/files/usr/bin/bash'
    printf '%s\n' 'set -eu'
    printf 'export OPENCODE_REPOSITORY_URL=%q\n' "$REPOSITORY_URL"
    if [ -n "$EXPECTED_COMMIT" ]; then printf 'export OPENCODE_REPOSITORY_COMMIT=%q\n' "$EXPECTED_COMMIT"; fi
    printf '%s\n' 'if [ -s "$HOME/.cache/codex-web/server-password" ]; then export OPENCODE_SERVER_PASSWORD="$(cat "$HOME/.cache/codex-web/server-password")"; fi'
    printf '%s\n' 'exec bash "$HOME/.cache/codex-web/termux-start.sh"'
  } > "$tmp"
  chmod 700 "$tmp"
  mv -f "$tmp" "$BOOT_SCRIPT"
}

cd "$ROOT"
printf '%s\n' "${OPENCODE_SERVER_PASSWORD:-}" > "$PASSWORD_FILE"
printf '%s\n' "$CONFIG_HASH" > "$CONFIG_HASH_FILE"
chmod 600 "$PASSWORD_FILE" "$CONFIG_HASH_FILE"
install_boot_hook
write_status starting "Iniciando o servidor local…"

WATCHDOG_SCRIPT="$WATCHDOG_CACHE"
if [ ! -f "$WATCHDOG_SCRIPT" ]; then
  WATCHDOG_SCRIPT="$ROOT/android/codex-web/termux-watchdog.sh"
fi
if [ ! -f "$WATCHDOG_SCRIPT" ]; then
  fail watchdog_missing "Watchdog não encontrado no cache nem no checkout"
fi
chmod 700 "$WATCHDOG_SCRIPT"

nohup bash "$WATCHDOG_SCRIPT" "$PASSWORD_FILE" "$LOG_FILE" "$WATCHDOG_PID_FILE" \
  "$SERVER_PID_FILE" "$HEARTBEAT_FILE" "$STATUS_FILE" "$ROOT" "$STARTED_AT" \
  >> "$LOG_FILE" 2>&1 < /dev/null &
printf '%s\n' "$!" > "$WATCHDOG_PID_FILE"
printf '[%s] watchdog iniciado pid=%s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$(cat "$WATCHDOG_PID_FILE")" >> "$LOG_FILE"
