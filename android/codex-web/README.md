# Codex Web 1.0.3 BETA para Termux

Este diretório contém o launcher Android e o bootstrap do servidor local. O
fluxo reduz a instalação manual sem fingir que o Android permite instalações
silenciosas ou que o Termux pode ser controlado antes de sua primeira
inicialização.

## Fluxo do usuário

1. Abra o APK e toque em **Instalar e configurar**.
2. O launcher consulta Termux, Termux:API, Termux:Boot e Termux:Widget.
3. Cada componente ausente é baixado por HTTPS da API/repositório oficial do
   F-Droid, verificado pelo PackageManager e enviado ao instalador oficial do
   Android individualmente.
4. O Android pede confirmação para cada APK. O próximo só é preparado depois
   que o pacote anterior foi confirmado como instalado.
5. Se o APK for fechado durante uma confirmação, a etapa pendente é recuperada
   ao reabrir. Conflitos de assinatura nunca desinstalam nem apagam o Termux.
6. Depois dos componentes, o launcher abre o Termux e envia o bootstrap em
   segundo plano quando RUN_COMMAND estiver autorizado.
7. O bootstrap ativa termux-wake-lock, verifica a origem Git, preserva
   alterações locais, instala somente as dependências reais do projeto,
   compila quando necessário e inicia o watchdog.
8. O chat só é aberto depois de /api/info e /api/session?limit=1 responderem
   com autenticação local válida.
9. Se faltar a chave APInex, o launcher mostra um campo mascarado. A chave é
   validada em https://apinex.bond/v1/models e enviada ao Termux por
   RUN_COMMAND_STDIN, nunca por argumento do shell ou log. Ela é salva em
   ~/.config/opencode/apinex.env com modo 600.

## Primeira inicialização do Termux

O Android pode exigir que o Termux seja aberto uma vez para concluir seu
bootstrap. O launcher aguarda esse passo, mas não pode inventar um shell
pronto. Para automação autorizada, conceda:

- permissão Android **Executar comandos no Termux** ao Codex Web;
- allow-external-apps=true em ~/.termux/termux.properties;
- exclusão do Termux da otimização de bateria quando o fabricante encerrar
  processos em segundo plano.

Se a autorização externa não estiver disponível, o launcher entra em modo
assistido e explica a ação necessária.

## Componentes

- **Termux**: obrigatório para executar o servidor local.
- **Termux:API**: opcional, útil para recursos de integração Android.
- **Termux:Boot**: opcional, somente para inicialização após reinício.
- **Termux:Widget**: opcional, para atalhos do Termux.

A instalação permanece individual e confirmada pelo Android. Pacotes de
origens diferentes podem ter assinaturas incompatíveis; nesse caso, o usuário
deve resolver a migração manualmente, sem perda automática de dados.

## Git e preservação de dados

A origem configurada pelo APK é:

https://github.com/jhonathamlegal-sudo/codex-web-opencode.git

O bootstrap instala em ~/opencode. Se o diretório não existir, ele clona a
origem. Se existir, exige um checkout Git com origin compatível. Um remote
diferente gera repository_conflict; o diretório não é apagado. Alterações
locais pausam a atualização automática e são preservadas. O botão de reparo
move o diretório para um backup com timestamp antes de clonar novamente.

O checkout publicável do projeto fica em ~/opencode-publish; ele não deve ser
confundido com um checkout antigo do runtime. O APK sempre baixa o script de
bootstrap versionado no GitHub antes de executá-lo.

## Diagnóstico

O estado persistente fica em ~/.cache/codex-web/:

- status: fase|mensagem|segundos;
- server.log: dependências, clone, build, crash e health checks;
- server.pid: processo Bun;
- server-watchdog.pid: watchdog;
- watchdog-heartbeat: último pulso;
- server-password: senha local do servidor, modo 600.

O watchdog testa /api/info e /api/session a cada ciclo. Três falhas com o
processo ainda vivo são classificadas como servidor zumbi e provocam uma
reinicialização controlada. Status 137/9 é registrado como possível Android,
OOM ou SIGKILL externo; exit diferente de zero é registrado como falha
interna.

## Compilação no Termux

No checkout do projeto:

    cd ~/opencode-publish/android/codex-web
    ANDROID_SDK_ROOT="$HOME/.android-sdk" bash build.sh

O APK debug é gerado em:

android/codex-web/build/CodexWeb-debug.apk

A compilação deve ser validada com apksigner verify. Um APK debug não é uma
release assinada para distribuição pública.

## Limitações honestas

- O Android sempre pode exigir toque do usuário para instalar APKs.
- O Termux pode exigir inicialização manual e concessão de permissões.
- A instalação automática em segundo plano depende de RUN_COMMAND,
  allow-external-apps e políticas do fabricante.
- A chave APInex só é validada quando a rede e o endpoint oficial estão
  disponíveis.
- A disponibilidade final do servidor precisa ser confirmada no aparelho;
  compilar o APK não prova que o Termux conseguiu executar o bootstrap.
