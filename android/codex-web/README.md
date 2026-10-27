# Codex Web 1.0.3 BETA

Launcher Android para o OpenCode rodando localmente no Termux. O APK inclui os
quatro APKs oficiais fornecidos do F-Droid e os scripts de bootstrap, para que
um celular limpo não dependa de arquivos do aparelho do desenvolvedor.

## Fluxo em um celular limpo

1. O launcher detecta Termux, Termux:API, Termux:Boot e Termux:Widget.
2. Se algum estiver ausente, abre a autorização de instalação do Android e
   instala cada APK individualmente. O Android sempre exige confirmação e pode
   recusar um pacote assinado por outra origem; o launcher não desinstala nem
   apaga nada automaticamente.
3. Depois que todos os componentes forem confirmados, o launcher abre o Termux
   uma vez para concluir a inicialização e envia o bootstrap em segundo plano.
   A tela do Codex Web volta para a frente; não é necessário digitar comandos.
4. O bootstrap cria/atualiza '~/opencode', instala 'git', 'curl' e 'bun',
   executa 'bun install --frozen-lockfile' com scripts, compila a interface se
   a fonte mudou e inicia o servidor local.
5. O chat só é liberado depois que '/api/info' e
   '/api/session?limit=1' respondem com a autenticação local correta.

O APK não pode conceder silenciosamente permissões especiais do Android.
Na primeira execução podem ser necessárias as confirmações de “Permitir desta
fonte”, “Executar comandos no Termux” e a exclusão do Termux da otimização de
bateria. O launcher mostra cada ação na tela.

## Segurança e preservação

Os APKs embarcados são verificados por SHA-256 e pelo certificado oficial
F-Droid antes da instalação. Se uma compilação não tiver os assets locais, o
fallback baixa os mesmos nomes de versão do repositório F-Droid e repete as
duas verificações. A origem do projeto e a revisão inicial são fixadas no APK;
o bootstrap não baixa scripts executáveis aleatórios da internet.

O projeto é instalado em '~/opencode'. Um checkout existente é preservado:
- origem Git diferente não recebe 'pull';
- alterações locais pausam a atualização;
- checkout quebrado só é reparado após mover o diretório inteiro para
  '~/opencode-user-backup-AAAAmmdd-HHmmss';
- nenhum 'reset --hard', limpeza ou exclusão automática é usado.

A chave APInex fica em '~/.config/opencode/apinex.env' com modo 600. O launcher
não coloca a chave em argumentos do shell nem no log. O servidor escuta em
'127.0.0.1' e o WebView aceita HTTP somente para '127.0.0.1'/'localhost'.

## Persistência e recuperação

O bootstrap ativa 'termux-wake-lock' quando disponível e cria
'~/.termux/boot/codex-web-start.sh' para a inicialização pelo Termux:Boot.
Também grava:

- '~/.cache/codex-web/status': 'fase|mensagem|segundos';
- '~/.cache/codex-web/server.log': instalação, clone, build, crash e health check;
- 'server.pid', 'server-watchdog.pid' e 'watchdog-heartbeat';
- 'server-password' e hashes com modo 600.

O watchdog verifica '/api/info' e '/api/session?limit=1'. Três falhas com o
processo ainda vivo são tratadas como zumbi; ele reinicia, aguarda o processo
filho e aplica atraso progressivo para evitar loop de crash. Status 137/9 é
registrado como possível Android/OOM/SIGKILL externo; outros códigos são
registrados como falha interna.

## Compilação e validação

No checkout em Termux:

    cd ~/opencode-publish/android/codex-web
    ANDROID_SDK_ROOT="$HOME/.android-sdk" bash build.sh

Saída:

    android/codex-web/build/CodexWeb-debug.apk

O script compila Java, empacota os assets, assina e executa 'apksigner verify'.
O APK debug é próprio para teste, não uma assinatura de release pública.

A verificação de um aparelho completamente limpo ainda precisa ser feita em um
celular/emulador descartável: o código cobre a sequência, mas o Android pode
variar na autorização de instalação, inicialização inicial do Termux,
restrições de bateria e permissão RUN_COMMAND.
