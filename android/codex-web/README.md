# Codex Web para Termux

Este diretório contém o APK Android e o script que inicia o servidor web do
OpenCode no Termux. O APK também tem um instalador guiado: detecta Termux,
Termux:API, Termux:Boot e Termux:Widget e oferece os APKs oficiais do F-Droid
por meio do instalador do próprio Android.

O Android sempre pede confirmação explícita para cada APK. Isso é uma
limitação de segurança do sistema, não um erro do aplicativo. Depois da
instalação, o launcher prepara `git`, `curl`, `bun`, dependências do projeto e
o build do cliente web quando necessário. Atualizações Git só são feitas em um
checkout limpo que tenha um `origin`; alterações locais nunca são sobrescritas.

## Preparação única no Termux

1. Abra o APK e permita que ele instale os componentes solicitados.
2. Abra o Termux uma vez para concluir a inicialização.
3. No Termux, execute `termux-setup-storage` se quiser usar arquivos do
   armazenamento compartilhado.
4. Em `~/.termux/termux.properties`, habilite:

   ```text
   allow-external-apps=true
   ```

5. Reinicie o Termux depois de alterar essa opção.
6. Deixe o repositório em `~/opencode` e configure o arquivo de ambiente
   APInex em `~/.config/opencode/apinex.env`. O APK não recebe nem armazena a
   chave do provedor.

Na primeira inicialização, o APK mostra a etapa real do bootstrap (instalando,
clonando, compilando e iniciando). O botão **Abrir chat** só é liberado depois
de duas verificações autenticadas: `/api/info` e uma leitura de sessões. Os
botões **Ver log** e **Copiar log** obtêm as últimas linhas de
`~/.cache/codex-web/server.log` diretamente do Termux.

Para manter o servidor vivo, o script ativa `termux-wake-lock` e o watchdog
verifica o processo a cada poucos segundos. Além de testar `/api/info`, ele
testa `/api/session`; três falhas consecutivas reiniciam um servidor que ficou
vivo, mas travado. O log diferencia saída com erro interno de um processo que
desapareceu com `SIGKILL`/status 137, que pode indicar Android, OOM ou outro
encerramento externo. Se o Android matar também o watchdog, a próxima execução
registra a recuperação a partir do estado persistente.

No card do instalador há o botão para abrir a configuração de bateria do
Termux. Confirme **Sem restrições**/exclusão da otimização para o Termux; o
Android não permite que um APK desative essa proteção silenciosamente.

Se `~/opencode` existir, mas estiver incompleto, o launcher oferece **Reparar
com backup seguro**. O diretório atual é apenas renomeado para
`~/opencode-user-backup-AAAAmmdd-HHmmss` antes de um novo clone; nenhum arquivo
do usuário é apagado. Se a chave estiver ausente, a instrução aparece no
próprio launcher e pode ser copiada.

Se `~/opencode` não existir, o launcher informa isso no log em
`~/.cache/codex-web/server.log`; o APK não embute o repositório inteiro e não
pode inventar uma URL de clone que não foi configurada.

O APK não contém a chave APInex. Ela permanece no armazenamento privado do
Termux e é lida pelo script de inicialização.

## Diagnóstico rápido

Os arquivos de estado ficam em `~/.cache/codex-web/`:

- `status`: fase atual e tempo decorrido do bootstrap, separados por `|`;
- `server.log`: instalação, compilação, crashes, sinais e health checks;
- `server-watchdog.pid`, `server.pid` e `watchdog-heartbeat`: supervisão do
  processo.

O servidor só é considerado online quando as duas rotas locais respondem com
HTTP 200 usando a senha local do launcher.

## Construção

`build.sh` usa um JDK, o Android SDK e o Build Tools já instalados ou
configurados pelas variáveis `ANDROID_SDK_ROOT` e `ANDROID_HOME`. O artefato
é criado em `build/CodexWeb-debug.apk`.
