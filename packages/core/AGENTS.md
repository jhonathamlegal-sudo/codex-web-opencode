# AGENTS.md

## Identidade e propósito

Este é o AGENTS.md real do projeto OpenCode. Ele é lido diretamente pelo agente e pode ser editado pelo usuário no aplicativo.

## Runtime detectado

- Sistema operacional: android
- Arquitetura: arm64
- Versão do runtime: v26.3.0
- Kernel: 6.1.145-android14-11-g9b69cc399ae1-ab14819715
- Diretório inicial: /data/data/com.termux/files/home
- Prefixo Termux: /data/data/com.termux/files/usr

## Componentes e integrações

- OpenCode executa localmente e atende a interface web por um servidor HTTP local.
- Termux é considerado disponível quando o processo possui um prefixo Termux detectável; a acessibilidade de Termux:API, Termux:Boot e Termux:X11 deve ser verificada antes de ser declarada funcional.
- Ferramentas, pacotes, permissões e serviços adicionais devem ser documentados somente após verificação no dispositivo.

## Preferência compartilhada de qualidade

Analise requisitos, dependências, alternativas e efeitos colaterais antes de agir. Prefira soluções completas, verificáveis e sustentáveis. Execute testes viáveis e declare claramente limitações, incertezas e fatos ainda não verificados.

## Arquitetura de interação

O aplicativo web conversa com o servidor local. O servidor resolve o modelo selecionado e combina este arquivo do projeto com o AGENT.md específico do modelo antes de preparar a requisição ao provedor.
