# Inventário do repositório

Data da auditoria: 2026-10-22

## Estado

**PARCIAL.** O repositório oficial foi clonado em `~/opencode`, na branch de trabalho `apinex-web`, a partir do branch upstream `dev`. A contagem completa dos arquivos foi executada, mas a leitura detalhada foi concentrada nos pontos de entrada, servidor, núcleo, cliente, aplicação web, IA, plugins e configuração.

## Identidade e contagem

- Repositório: `https://github.com/anomalyco/opencode.git`
- Commit auditado: `b6c0327e1899b70681ebb511667d9a5467624829`
- Versão declarada no `package.json`: `2.1.2`
- Arquivos rastreados: `8.575`
- TypeScript/TSX: `5.107`
- JSON/JSONC: `518`
- Markdown: `229`
- Candidatos a testes por nome: `1.745`
- Gerenciador esperado pelo projeto: Bun `1.4.2`; ambiente disponível: Bun `1.4.3` em Android arm64

## Áreas relevantes

| Área | Local | Papel | Estado da leitura |
|---|---|---|---|
| Aplicação web | `packages/app` | UI Solid/Vite, sessões, composer, anexos, modelos e agentes | CONCLUÍDO para o fluxo principal |
| Servidor | `packages/server` | HTTP, autenticação, rotas e serviços | CONCLUÍDO para o fluxo principal |
| Núcleo | `packages/core` | sessões, projetos, permissões, credenciais e resolução de modelos | CONCLUÍDO para os caminhos usados |
| IA | `packages/ai` | protocolos e providers | CONCLUÍDO para OpenAI-compatible |
| Cliente/protocolo | `packages/client`, `packages/protocol` | contratos e RPC | PARCIAL |
| CLI | `packages/cli` | entrada e comando `serve` | CONCLUÍDO |
| UI compartilhada | `packages/ui`, `packages/session-ui` | componentes e estado de sessão | PARCIAL |
| Plugins | `packages/plugin` | descoberta e execução de plugins | PARCIAL |
| SDKs | `packages/sdk`, `sdks` | consumo externo e geração | NÃO TESTADO |

## Dependências e geração

`bun install --frozen-lockfile` terminou com sucesso, instalando 2.101 pacotes. Arquivos gerados, `node_modules` e artefatos de build não fazem parte da contagem funcional. A regra do projeto exige regenerar o cliente quando contratos públicos de servidor/protocolo forem alterados; essa alteração ainda não foi feita.

## Lacunas

Não foi afirmado que cada arquivo foi lido integralmente. A auditoria de APInex ainda depende de chave válida para consultar modelos e validar streaming, ferramentas, limites e erros reais.
