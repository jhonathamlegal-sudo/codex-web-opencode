# Problemas conhecidos

1. O catálogo local exclui modelos pagos e modelos `free/*` marcados como “Somente por assinatura”. A consulta atual da APInex retornou 11 IDs gratuitos configurados; não retornou um `GLM 5.6`, portanto esse modelo não foi inventado no catálogo.
2. Streaming e ferramentas da APInex permanecem não testados.
3. `bun run typecheck` e o build do servidor falham porque as dependências nativas atuais não incluem o pacote TypeScript Android arm64.
4. `bun run check` aborta no oxlint/oxc_allocator em Android arm64.
5. O build web passa, mas produz avisos de CSS `::highlight`, import dinâmico de Sentry e chunks grandes.
6. Ainda não foi executado E2E real com servidor, navegador e APInex.
7. A configuração `opencode.jsonc` foi aplicada e validada pelo schema; a chave permanece apenas no ambiente privado do Termux.
8. A geração end-to-end com modelo gratuito foi bloqueada pela APInex com `Daily check-in required`; é necessário concluir o check-in no site do provedor antes de validar uma resposta real.
9. O editor persistente de `AGENTS.md` e `AGENT.md` e seus endpoints responderam HTTP 200 no Termux; o conteúdo transitório usado no teste foi removido.

Nenhum desses itens deve ser descrito como resolvido até haver execução verificável.
