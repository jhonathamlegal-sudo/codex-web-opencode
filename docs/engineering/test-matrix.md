# Matriz de testes

| Comando | Resultado | Evidência/limitação |
|---|---|---|
| `bun install --frozen-lockfile` | CONCLUÍDO | 2.101 pacotes instalados |
| `bun run --cwd packages/app build` | CONCLUÍDO | Vite terminou; há avisos de CSS, Sentry dinâmico e chunks grandes |
| `bun run check` | FALHOU | oxlint abortou em `oxc_allocator` no Android arm64, código 134 |
| `bun run typecheck` | FALHOU | Turborepo não suporta Android arm64 |
| `bun run --cwd packages/ai typecheck` | FALHOU | TypeScript 7 não encontra `@typescript/typescript-android-arm64` |
| `bun run --cwd packages/server build` | FALHOU | falha no `bun tsc` pela mesma dependência Android arm64 |
| `bun run --cwd packages/app build` após a tela de instruções | CONCLUÍDO | Vite terminou; avisos não bloqueantes de CSS, Sentry dinâmico e chunks grandes |
| `GET/PUT /api/instructions/*` no Termux | CONCLUÍDO | global, perfil por modelo, histórico e restauração responderam HTTP 200 |
| teste de baseline com core/project-agents e core/model-agent | CONCLUÍDO | estado persistido do servidor confirmou ambos os sources e o marcador do perfil por modelo |
| inicialização por `android/codex-web/termux-start.sh` | CONCLUÍDO | servidor Bun iniciou em `127.0.0.1:4096` e `/api/info` respondeu HTTP 200 |
| geração real com perfil de modelo | BLOQUEADO | APInex respondeu HTTP 503 exigindo daily check-in |
| `cd packages/ai && bun test test/provider/openai-compatible-chat.test.ts test/provider/openai-compatible-responses.test.ts` | CONCLUÍDO | 94 testes, 0 falhas, 309 expectativas |
| `GET https://apinex.bond/v1/models` com chave | CONCLUÍDO | HTTP 200; catálogo consultado |
| `POST .../chat/completions` com `free/deepseek-v4-flash` | BLOQUEADO | HTTP 402; ID não confirmado como gratuito na tabela |
| `POST .../chat/completions` com `free/gpt-6-luna` | CONCLUÍDO | HTTP 200, resposta `OK`, `cost_tokens: 0` |
| validação do provider contra `ConfigProvider.Info` | CONCLUÍDO | schema real aceitou a configuração |
| validação do arquivo contra `Config.Info` | CONCLUÍDO | configuração completa aceita pelo schema |
| testes de sessão/provider/E2E | NÃO TESTADO | servidor/CLI limitados no Android e streaming ainda pendente |

O script de testes na raiz não deve ser usado; o próprio repositório orienta executar testes a partir do pacote afetado.
