# Codex Web / OpenCode — edição Termux

Esta é uma edição personalizada do [OpenCode](https://github.com/anomalyco/opencode)
com interface web local, persistência de instruções `AGENTS.md` e launcher
Android para preparar o ambiente no Termux.

> Este repositório não é o projeto oficial OpenCode nem é afiliado aos seus
> mantenedores. Alterações, APKs e suporte aqui são específicos desta edição.

## O que existe aqui

- servidor OpenCode local para a interface web;
- edição persistente do `~/opencode/AGENTS.md`;
- perfis `AGENT.md` por modelo em `~/.config/opencode/models/`;
- APK `Codex Web` com instalador guiado de Termux, Termux:API, Termux:Boot e
  Termux:Widget;
- bootstrap que clona este repositório, instala dependências e compila o
  cliente web;
- autenticação local entre APK e servidor por senha aleatória.

## Instalação pelo Android

O APK está em [`android/codex-web/build/CodexWeb-debug.apk`](android/codex-web/build/CodexWeb-debug.apk).

1. Instale e abra o APK.
2. Autorize as instalações solicitadas pelo Android.
3. Abra o Termux uma vez para concluir a inicialização.
4. Toque em **Verificar e instalar** e aguarde as confirmações do sistema.
5. O launcher baixa o bootstrap, clona este repositório em `~/opencode`,
   instala `git`, `curl`, `bun`, dependências e o build web.
6. Habilite `allow-external-apps=true` em `~/.termux/termux.properties` se o
   Termux bloquear comandos externos; depois reinicie o Termux.

O Android exige confirmação manual para cada APK. O launcher não instala
aplicativos silenciosamente.

## Configuração do provedor

Chaves nunca ficam no GitHub nem dentro do APK. No celular que executará o
servidor, crie o arquivo privado:

```bash
mkdir -p ~/.config/opencode
printf '%s\n' "APINEX_API_KEY='COLOQUE_A_SUA_CHAVE_AQUI'" \
  > ~/.config/opencode/apinex.env
chmod 600 ~/.config/opencode/apinex.env
```

Substitua o placeholder somente no aparelho destino. Não faça commit desse
arquivo e não envie a chave pelo chat, issue ou pull request.

## Instalação manual no Termux

```bash
pkg update -y
pkg install -y git curl bun
git clone https://github.com/jhonathamlegal-sudo/codex-web-opencode.git ~/opencode
cd ~/opencode
bun install --frozen-lockfile
bun run --cwd packages/app build
```

Para iniciar o servidor com autenticação local:

```bash
export OPENCODE_SERVER_PASSWORD="escolha-uma-senha-local"
bun packages/cli/src/codex-web-server.ts
```

O launcher usa `127.0.0.1:4096`; ele não publica o servidor na internet.

## Desenvolvimento

Requisitos: Termux ou Linux, Git e Bun 1.3 ou superior.

```bash
bun install
bun run check
```

Testes direcionados devem ser executados no pacote afetado:

```bash
cd packages/core
bun run test
bun typecheck
```

Para modificar a API pública, gere novamente os clientes a partir de
`packages/client`; não edite arquivos gerados manualmente.

Para compilar o APK, use JDK, Android SDK e Build Tools:

```bash
ANDROID_SDK_ROOT=/caminho/android-sdk android/codex-web/build.sh
```

O artefato será criado em `android/codex-web/build/CodexWeb-debug.apk`.

## Arquitetura resumida

```text
APK Android
  ├─ instala componentes oficiais do F-Droid com confirmação do Android
  ├─ envia RUN_COMMAND ao Termux
  └─ abre a interface web local

Termux
  ├─ baixa o bootstrap do GitHub quando necessário
  ├─ clona ~/opencode
  ├─ instala dependências e compila packages/app
  └─ executa o servidor em 127.0.0.1:4096
```

O `AGENTS.md` global real fica em `~/opencode/AGENTS.md`. Perfis específicos
de modelo ficam em `~/.config/opencode/models/<provedor>/<modelo>/AGENT.md`.

## Limites conhecidos

- o APK não inclui nem descobre automaticamente a chave do provedor;
- o Android pode exigir autorização para instalar APKs e comandos externos;
- o cliente web depende de rede para clonar o repositório e baixar modelos;
- o servidor é local ao aparelho; acesso remoto entre celulares não é ativado;
- alterações locais não são sobrescritas pelo bootstrap automático.

## Contribuição e segurança

Leia [CONTRIBUTING.md](CONTRIBUTING.md) antes de abrir um pull request. Para
falhas de segurança, siga [SECURITY.md](SECURITY.md) e não publique detalhes
exploráveis em uma issue.
