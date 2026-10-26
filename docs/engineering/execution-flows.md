# Fluxos de execução

## Servidor

1. A CLI recebe `serve`.
2. `ServerProcess` inicializa o servidor HTTP com autenticação.
3. As rotas são compostas pelos serviços do Core.
4. O cliente web consulta informação, projetos e sessões.
5. Eventos da sessão são transmitidos para a UI.

## Chat

1. O composer envia uma mensagem associada a uma sessão.
2. O Core valida sessão, projeto, agente, modelo e permissões.
3. O resolver obtém provider, modelo e credencial.
4. O provider converte a solicitação para o protocolo escolhido.
5. O servidor persiste o resultado e publica eventos incrementais.
6. A UI atualiza a mensagem e o estado de execução.

## Anexos e arquivos

Anexos entram pelo fluxo existente de composer/attachments e são associados à mensagem antes da chamada ao modelo. O escopo de localização e as permissões do servidor devem continuar valendo para arquivos, projetos e worktrees. Não foi introduzido bypass para facilitar APInex.

## Ferramentas e agentes

Agentes e ferramentas são resolvidos pelo Core e pela camada de plugins/configuração. O provider remoto não recebe autorização implícita para acessar o sistema de arquivos: a execução de ferramentas permanece no lado controlado pelo servidor, sujeita a permissões e aprovação.

## APInex

O fluxo planejado é `APINEX_API_KEY` -> credencial do provider APInex -> provider OpenAI-compatible -> `https://apinex.bond/v1` -> streaming/resposta -> eventos da sessão. A consulta de modelos, a chamada autenticada e o streaming ainda não foram executados porque a chave APInex não foi fornecida nesta etapa.
