# Segurança

Esta é a versão em português da política do projeto. Para o mecanismo oficial de segurança do GitHub, consulte [`SECURITY.md`](SECURITY.md).

## Reporte privado

Use [GitHub Security Advisories](https://github.com/jhonathamlegal-sudo/codex-web-opencode/security/advisories/new). Não publique chaves, tokens, senhas ou provas destrutivas em issues.

## Regras essenciais

- Nunca versione credenciais ou arquivos `.env`.
- Guarde a chave do provedor apenas em `~/.config/opencode/apinex.env` com `chmod 600`.
- O APK não deve conter credenciais fixas.
- Mantenha o servidor em `127.0.0.1:4096`.
- Revogue imediatamente qualquer segredo exposto.

Veja a política completa em [`SECURITY.md`](SECURITY.md).
