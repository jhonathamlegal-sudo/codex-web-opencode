# Perfis persistentes de instruções

## Estado

Implementado no servidor e na aplicação web, com teste HTTP real no Termux.
O teste de geração end-to-end chegou ao provedor APInex, mas foi bloqueado por
`daily check-in required`; portanto a inclusão do perfil no prompt foi validada
no código carregado pelo servidor, mas a resposta do modelo não pôde ser
observada nesta execução.

## Arquivos

- `~/opencode/AGENTS.md`: o arquivo real do projeto, lido pelo OpenCode e editável pelo aplicativo.
- `~/.config/opencode/models/<provider>/<model>/AGENT.md`: perfil independente
  de um modelo. Os segmentos são codificados para impedir colisão ou travessia
  de caminho.
- `~/.config/opencode/instruction-history/`: versões anteriores criadas antes
  de uma alteração.

## Fluxo

1. `ModelInstructions` aponta para `~/opencode/AGENTS.md` e só o cria se ele ainda não existir.
2. A tela **Instruções** consulta e grava o escopo global ou o modelo escolhido.
3. O histórico permite restauração; restaurar também cria uma nova versão de
   recuperação.
4. `SessionContext.select` resolve o modelo selecionado e carrega o `AGENT.md`
   correspondente como fonte individual de instruções.
5. A fonte individual é posicionada depois das instruções do projeto,
   mantendo a customização do modelo no contexto final sem substituir o
   AGENTS.md do projeto.
6. A seleção de sessão relê o AGENTS.md diretamente antes de montar o baseline; isso mantém a edição do aplicativo efetiva mesmo quando o file watcher nativo está desativado no Termux.

## API

- `GET/PUT /api/instructions/global`
- `GET/PUT /api/instructions/model/:providerID/:modelID`
- `GET /api/instructions/history`
- `POST /api/instructions/restore`

Todos os endpoints usam a autenticação existente do servidor e não expõem a
chave APInex ao cliente.
