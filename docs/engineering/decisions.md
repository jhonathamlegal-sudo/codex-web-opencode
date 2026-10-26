# Decisões técnicas

## D-001 — Repositório upstream

Usar o código oficial de OpenCode como base, preservando a arquitetura e as regras existentes. A branch de trabalho é `apinex-web`.

## D-002 — Provider único

APInex é o único fornecedor autorizado nesta entrega. Não haverá fallback silencioso nem credencial de outro fornecedor.

## D-003 — Reutilizar OpenAI-compatible

A integração deve reutilizar `@opencode/ai/providers/openai-compatible`, porque a camada já suporta base URL, autenticação e o protocolo de chat correspondente.

## D-004 — Segredo no servidor

A chave ficará em variável de ambiente/credencial do processo servidor. Ela nunca será commitada ou enviada para a aplicação web.

## D-005 — Sem catálogo inventado

Nenhum modelo, preço ou capacidade será apresentado como real sem resposta autenticada da APInex.

## D-006 — Limitação do ambiente

Falhas de oxlint, Turborepo e TypeScript nativos em Android arm64 serão reportadas como bloqueios de validação do ambiente até haver toolchain compatível.
