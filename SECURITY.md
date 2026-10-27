# Segurança

<div align="center">

**Relate em privado · revogue segredos expostos · não execute provas destrutivas**

</div>

> [!WARNING]
> O agente executa no Termux com os arquivos, processos e rede disponíveis ao usuário. Este projeto não é um sandbox de segurança.

## Reportar vulnerabilidade

Abra um aviso privado no GitHub:

**[Criar aviso de segurança](https://github.com/jhonathamlegal-sudo/codex-web-opencode/security/advisories/new)**

Inclua somente o necessário:

| Informação | Exemplo |
| --- | --- |
| Impacto | leitura de arquivo, execução de comando, exposição de credencial |
| Reprodução | passos mínimos e não destrutivos |
| Alcance | versão, commit, Android e Termux afetados |
| Mitigação | correção ou redução de risco conhecida |

Não publique uma chave ativa, dados pessoais ou uma prova de conceito destrutiva em issue, pull request ou screenshot.

## Segredos

| Nunca versionar | Onde configurar localmente |
| --- | --- |
| APINEX/API keys | `~/.config/opencode/apinex.env` |
| Tokens do GitHub | gerenciador de credenciais do próprio dispositivo |
| Senhas e cookies | armazenamento privado do serviço |
| `.env` e dumps de sessão | fora do checkout público |

```bash
mkdir -p ~/.config/opencode
chmod 700 ~/.config/opencode
chmod 600 ~/.config/opencode/apinex.env
```

O APK público não deve conter chave APINEX, token do GitHub ou senha fixa. Se uma credencial for exposta, revogue-a e gere outra imediatamente; apagar o arquivo não remove o segredo do histórico.

## Rede e instalação

- O servidor usado pelo launcher deve escutar em `127.0.0.1:4096`.
- A senha HTTP deve ser aleatória por execução e não deve aparecer em logs compartilhados.
- Não exponha a porta diretamente à internet sem uma camada de acesso autenticada.
- O Android deve pedir confirmação para cada APK; essa proteção não pode ser contornada.
- Revise scripts baixados e mantenha Android, Termux e complementos atualizados.

## APKs do F-Droid

Quando o build usa APKs locais, valide o arquivo antes de adicioná-lo:

```bash
file caminho/componente.apk
unzip -tq caminho/componente.apk
sha256sum caminho/componente.apk
```

O instalador valida o pacote esperado antes de abrir a sessão do `PackageInstaller`. Não substitua um APK por arquivo de origem desconhecida.

## Escopo

Este documento cobre o código deste repositório, o launcher Android e o bootstrap Termux. Problemas exclusivamente no Android, Termux upstream, GitHub, provedor de modelos ou servidores MCP de terceiros devem ser reportados também ao mantenedor correspondente.

Consulte a versão em português em [`SEGURANÇA.md`](SEGURAN%C3%87A.md).
