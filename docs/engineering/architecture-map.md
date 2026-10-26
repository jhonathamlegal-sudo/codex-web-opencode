# Mapa de arquitetura

## Visão geral

```text
CLI (packages/cli)
  -> comando serve
  -> ServerProcess (packages/server)
  -> rotas HTTP/RPC e autenticação
  -> serviços Core (sessão, projeto, banco, credenciais, permissões)
  -> Protocol/Client
  -> App Solid/Vite (packages/app)
```

## Inicialização

`packages/cli/src/index.ts` registra a CLI e os handlers. O handler `serve` delega a `ServerProcess.run`. O servidor inicia autenticado, define host/porta, cria as rotas e publica informações do servidor em `/api/info`. `packages/server/src/routes.ts` compõe banco, bus, projeto, worktree, sessão, instância, credenciais, permissões, localização, workspace e reinício de sessão.

## Sessão e interface

A aplicação web usa o cliente/protocolo gerado para conversar com o servidor. O fluxo de sessão passa por criação/seleção de projeto, carregamento de mensagens, composer, anexos, seleção de agente/modelo e transmissão de eventos. A UI existente é funcional e persistente; não foi criada uma tela fictícia para esta auditoria.

## Resolução de modelo

```text
configuração do provider
  -> ProviderConfig
  -> ModelResolver (core)
  -> catálogo/credencial
  -> provider nativo da packages/ai
  -> protocolo OpenAI-compatible
  -> endpoint remoto
```

O provider nativo `openai-compatible` aceita `baseURL`, chave opcional, nome do provider e opções. O resolver aplica credenciais e valida a URL base. A proposta APInex reutiliza esse caminho, evitando duplicar protocolo ou criar um adaptador paralelo.

## Limites confirmados

O servidor não deve ser reiniciado durante desenvolvimento. Alterações públicas em protocolo/HTTP exigem regeneração do cliente. A dependência segue Schema -> Core/Protocol -> Server; o cliente não depende diretamente de Core/Server.
