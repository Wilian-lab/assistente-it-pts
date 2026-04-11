# Revisao Tecnica - 2026-04-11

## O que foi limpo hoje

- Remocao do endpoint de limpeza global de cache do assistente.
- Remocao do fluxo de frontend que disparava limpeza global de cache.
- Restricao do cache do assistente para consultas documentais por setor.
- Remocao de metodos antigos sem uso em `AssistantResponseFormatter`.
- Remocao do metodo residual `clearCache()` em `AssistantMaintenanceService`.
- Remocao da chave hardcoded do OpenRouter em `application.properties`.
- Inclusao de artefatos de runtime no `.gitignore`.

## Pendencias recomendadas

### Seguranca

- Remover credenciais hardcoded de ambiente local em `application.properties`:
  - `spring.datasource.password`
  - `app.admin.password`
- Revisar se existe algum outro segredo versionado fora de `application-local.properties`.
- Avaliar rotacao da chave do Gemini se ela tiver sido exposta durante testes.

### Cache do assistente

- Adicionar TTL no `assistant_cache` para evitar crescimento indefinido.
- Criar limpeza seletiva por setor e por IT, sem voltar a expor limpeza global.
- Criar endpoint ou tela de inspecao de cache por setor para auditoria funcional.
- Melhorar a normalizacao semantica da pergunta para aumentar `cache hit` em variacoes equivalentes.

### Observabilidade

- Registrar metricas por provider:
  - latencia
  - falhas
  - cache hit rate
  - setor
  - modelo usado
- Registrar quando a resposta veio do cache vs consulta nova.

### Robustez

- Cobrir com testes automatizados os cenarios:
  - mesma pergunta no mesmo setor reaproveita cache
  - mesma pergunta em setor diferente nao reaproveita cache
  - conversa livre nao grava em cache documental
  - mudanca de versao da IT invalida cache antigo

### UX e produto

- Avaliar se o selo visual de origem da resposta deve permanecer em producao ou ficar apenas em modo teste.
- Refinar a resposta documental para usar blocos curtos de forma consistente sem endurecer demais o modelo.

## Observacoes

- A regra de negocio atual foi preservada.
- A arquitetura segue com uma tabela unica `assistant_cache`, mas o lookup agora depende de setor.
- Se o volume crescer muito, o proximo passo tecnico natural e adicionar expiracao e monitoramento antes de pensar em mudar a estrutura fisica.
