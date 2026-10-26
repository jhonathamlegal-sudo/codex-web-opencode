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

Se `~/opencode` não existir, o launcher informa isso no log em
`~/.cache/codex-web/server.log`; o APK não embute o repositório inteiro e não
pode inventar uma URL de clone que não foi configurada.

O APK não contém a chave APInex. Ela permanece no armazenamento privado do
Termux e é lida pelo script de inicialização.

## Construção

`build.sh` usa um JDK, o Android SDK e o Build Tools já instalados ou
configurados pelas variáveis `ANDROID_SDK_ROOT` e `ANDROID_HOME`. O artefato
é criado em `build/CodexWeb-debug.apk`.
