# Provider APInex

## Evidência disponível

O site público da APInex declara compatibilidade com APIs OpenAI e Anthropic, modelos de múltiplas famílias e cobrança por uso. A documentação pública referencia `POST /v1/chat/completions`, `POST /v1/messages` e endpoints de ferramentas. Com a credencial fornecida, `GET /v1/models` retornou HTTP 200. A configuração local foi restringida aos 10 modelos `free/*` que aparecem como disponíveis, excluindo os itens marcados como “Somente por assinatura”.

## Decisão de integração

**DECIDIDO:** usar o provider nativo `openai-compatible` de OpenCode com base URL APInex. Não será criado fallback para OpenAI, Anthropic ou outro fornecedor.

O schema atual usa `providers`, `package` e `settings`. A configuração aplicada:

```jsonc
{
  "providers": {
    "apinex": {
      "name": "APInex — modelos gratuitos disponíveis",
      "env": ["APINEX_API_KEY"],
      "package": "@opencode/ai/providers/openai-compatible",
      "settings": { "baseURL": "https://apinex.bond/v1" }
    }
  }
}
```

O trecho é uma representação da configuração aplicada. A credencial é resolvida pelo mecanismo de integração usando `APINEX_API_KEY`; somente os 10 IDs gratuitos disponíveis foram registrados no catálogo local. A chave não foi gravada no repositório.

## Ainda não validado

- confirmação da cota gratuita após o limite diário;
- IDs, capacidades, limites e modalidades dos modelos;
- streaming SSE, tool calls, anexos e erros;
- custo, saldo, rate limits e disponibilidade;
- compatibilidade exata entre o catálogo APInex e o schema de modelos do OpenCode.

## Critério de aceite

Validar streaming, erro de autenticação, chamada de ferramenta e um fluxo de sessão pela UI. A chave não deve ser commitada, exibida em logs ou incorporada ao bundle do navegador; nesta execução ela ficou apenas na sessão SSH.
