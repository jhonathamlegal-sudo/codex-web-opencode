# Modelo de segurança

## Controles existentes

- O servidor local inicia com autenticação e usa `ServerAuth` para proteger rotas.
- Projetos, worktrees, sessões, localização e permissões são tratados no lado servidor.
- Credenciais são resolvidas pelo Core, e o cliente não deve receber segredos do provider.
- Ferramentas e plugins executam dentro das regras de autorização existentes.

## APInex

A chave deve ser fornecida por variável de ambiente/credencial do servidor, nunca por configuração versionada, URL de navegador ou código client-side. Logs e mensagens de erro devem redigir Authorization, chaves e corpos sensíveis.

## Riscos a verificar

- SSRF ou base URL configurável apontando para rede local;
- exposição de conteúdo de arquivo em anexos ou tool calls;
- path traversal em arquivos recebidos do provider;
- permissões insuficientes para agentes/plugins;
- streaming que repasse cabeçalhos ou mensagens de erro sensíveis;
- configuração que permita escolher provider externo fora da APInex.

## Estado

**PARCIAL.** O modelo de segurança existente foi localizado e preservado. Os testes específicos de segredo, SSRF, traversal, redaction e exclusividade APInex ainda não foram implementados.
