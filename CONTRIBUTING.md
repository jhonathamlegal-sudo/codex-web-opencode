# Contribuindo

Obrigado por contribuir com esta edição do OpenCode para Termux e Android. O projeto combina o servidor OpenCode, a interface web e o launcher Android; mudanças pequenas, reproduzíveis e verificáveis são preferíveis a alterações amplas sem testes.

## Antes de começar

Leia o [`README.md`](README.md), o [`AGENTS.md`](AGENTS.md) e o [`SECURITY.md`](SECURITY.md). Nunca envie chaves de API, tokens, senhas, arquivos `.env`, sessões, logs com credenciais ou dados pessoais.

## Ambiente

```bash
git clone https://github.com/jhonathamlegal-sudo/codex-web-opencode.git
cd codex-web-opencode
bun install --frozen-lockfile
```

Crie uma branch para cada mudança:

```bash
git switch -c docs/minha-mudanca
```

## Verificação

Antes de abrir um pull request, execute o que for relevante para a área alterada:

```bash
git diff --check
bun run check
cd packages/core && bun run test && bun typecheck
```

Para alterações no cliente/API pública, regenere os artefatos quando necessário:

```bash
cd packages/client
bun run generate
```

Para o launcher Android e o instalador Termux:

```bash
cd android/codex-web
bash -n termux-start.sh
./gradlew assembleDebug
```

O APK de depuração é gerado em `android/codex-web/build/`. A instalação no Android continua sujeita à confirmação do PackageInstaller; isso não deve ser contornado pelo aplicativo.

## Regras do launcher

- O repositório público usado pelo bootstrap deve ser explícito e revisável.
- O launcher não deve embutir chave de provedor, token do GitHub ou senha fixa.
- O bootstrap pode instalar dependências no Termux, mas não deve apagar ou sobrescrever um checkout não vazio.
- Falhas de rede, clonagem, instalação e inicialização devem aparecer no log e produzir uma mensagem compreensível na interface.
- Mudanças no `AGENTS.md`, nos perfis `AGENT.md` dos modelos ou na persistência devem incluir validação ponta a ponta.

## Commits e pull requests

Use mensagens claras, por exemplo `docs: explicar o bootstrap do Termux` ou `fix(android): tratar Termux recém-instalado`. No pull request, descreva o problema, a solução, os comandos executados e as limitações conhecidas. Para mudanças Android, informe versão do Android/Termux e se o fluxo foi testado em instalação limpa.

Vulnerabilidades não devem ser abertas como issue pública; siga o [`SECURITY.md`](SECURITY.md).
