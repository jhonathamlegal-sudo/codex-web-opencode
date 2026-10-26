# Segurança

Este repositório é público e esta edição executa o OpenCode no Termux. O agente pode receber acesso aos arquivos, processos e rede disponíveis ao usuário do dispositivo. O sistema não é um sandbox de segurança.

## Reportar uma vulnerabilidade

Use um aviso privado do GitHub:

<https://github.com/jhonathamlegal-sudo/codex-web-opencode/security/advisories/new>

Inclua apenas os detalhes necessários para reproduzir o problema, o impacto, a versão/commit afetado e uma correção sugerida, se houver. Não publique chaves, dados pessoais ou uma prova de conceito destrutiva. Se o formulário não estiver disponível, abra uma comunicação privada pelos canais do mantenedor; não use uma issue pública para um segredo ativo.

## Segredos e configuração

- Nunca faça commit de chaves de API, tokens do GitHub, senhas, cookies, arquivos `.env` ou dumps de sessão.
- Configure a chave do provedor somente no dispositivo, por exemplo em `~/.config/opencode/apinex.env`, com permissões restritas (`chmod 600`).
- O APK e o script público não devem conter a chave APINEX nem credenciais do GitHub.
- Se uma credencial for exposta, revogue-a e gere outra imediatamente; apagar o arquivo não remove o segredo do histórico.
- Ao compartilhar logs, redija valores de `Authorization`, cookies, chaves e senhas.

## Rede local e Termux

O servidor local deve escutar em `127.0.0.1:4096` quando usado pelo launcher. A senha HTTP deve ser aleatória por execução e não deve ser publicada. Não exponha a porta diretamente à internet; use uma camada de acesso autenticada e compreenda o risco antes de alterar o bind.

O Android pode pedir confirmação para instalar o Termux e seus complementos. Essa confirmação é intencional. Mantenha Android, Termux e os complementos atualizados e revise scripts baixados antes de executá-los.

O bootstrap clona uma URL pública explícita e não deve apagar um diretório de projeto não vazio. Como o repositório é público, qualquer pessoa pode baixar e inspecionar seu conteúdo; não coloque material privado no código, no APK ou na documentação.

## Escopo e limitações

Este documento cobre o código deste repositório, o launcher Android e o bootstrap Termux. Vulnerabilidades exclusivamente no provedor de modelos, no GitHub, no Android, no Termux upstream, em servidores MCP de terceiros ou em um dispositivo fisicamente comprometido devem ser reportadas ao mantenedor correspondente, além de serem informadas aqui quando afetarem este projeto.
