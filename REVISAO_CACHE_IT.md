# Revisao e Cache de IT

## Objetivo

Este documento registra como o sistema deve se comportar quando uma IT recebe uma nova revisao e onde atacar caso, no futuro, o assistente passe a responder com conteudo antigo.

## Regra de negocio esperada

Para a mesma IT e o mesmo setor:

1. Se a pergunta documental ja existir no cache e a versao do documento nao mudou:
   - a resposta deve vir do `assistant_cache`
   - a IA nao deve ser consultada novamente

2. Se a IT mudou de revisao ou o arquivo PDF mudou:
   - o cache antigo nao pode ser servido
   - a IA deve gerar uma resposta nova com base na IT atual
   - a entrada antiga do cache deve ser atualizada para a nova versao, evitando duplicacao desnecessaria

## Como o sistema decide isso hoje

### 1. Identificacao da versao do documento

Arquivo:
- `src/main/java/com/wlilan/backend_assistent/assistant/AssistantCacheService.java`

Metodo:
- `resolveDocumentVersion(ItEntity selectedIt)`

Hoje a versao logica do documento e calculada com base em:
- `CACHE_NAMESPACE`
- caminho absoluto do arquivo
- tamanho do arquivo
- data/hora de ultima modificacao do PDF

Se qualquer um desses itens mudar, o `documentVersion` muda e o cache antigo deixa de bater.

### 2. Revisao da IT

Arquivos:
- `src/main/java/com/wlilan/backend_assistent/it/it/usecases/UploadItFileUseCase.java`
- `src/main/java/com/wlilan/backend_assistent/assistant/AssistantDocumentIndexService.java`

Fluxo atual:
- o upload tenta extrair metadados do proprio PDF
- a revisao do PDF tem prioridade
- o nome do arquivo e apenas fallback

Se a revisao exibida estiver errada, os pontos para investigar primeiro sao:
- extracão de metadados do PDF em `AssistantDocumentIndexService.extractDocumentMetadata(...)`
- fallback no `UploadItFileUseCase.upsertItRecord(...)`

### 3. Busca do cache

Arquivo:
- `src/main/java/com/wlilan/backend_assistent/assistant/AssistantService.java`

Fluxo:
- o `AssistantService.ask(...)` calcula:
  - `setorAtivo`
  - `intent`
  - `normalizedQuestion`
  - `documentVersion`
  - `cacheModelKey`
- depois consulta o cache antes da IA

Se a resposta antiga estiver voltando indevidamente, revisar:
- `buildNormalizedQuestion(...)`
- `shouldUseDatabaseCache(...)`
- `assistantCacheService.findCachedResponse(...)`

### 4. Atualizacao da linha de cache em nova revisao

Arquivo:
- `src/main/java/com/wlilan/backend_assistent/assistant/AssistantCacheService.java`

Metodo:
- `saveResponse(...)`

Comportamento esperado:
- se a pergunta ja existe para a mesma IT/setor/intencao
- e a revisao mudou
- a resposta nova deve sobrescrever a entrada reaproveitavel, em vez de criar varias linhas equivalentes

Se comecar a duplicar ou manter conteudo velho, revisar:
- `findReusableCachedEntry(...)`
- `findFirstByItIdAndSetorAndIntentAndNormalizedQuestionOrderByUpdatedAtDesc(...)`
- `findTop100ByItIdAndSetorAndIntentOrderByUpdatedAtDesc(...)`

## Sinais de falha e ponto de ataque

### Cenario A: mesma pergunta, mesma IT, mesma revisao, mas nao veio do banco

Investigar:
- normalizacao da pergunta em `AssistantService.buildNormalizedQuestion(...)`
- tolerancia de similaridade em `AssistantCacheService.similarityScore(...)`
- setor ativo enviado na request

### Cenario B: mesma pergunta, mesma IT, revisao nova, mas voltou resposta antiga

Investigar:
- `AssistantCacheService.resolveDocumentVersion(...)`
- data de modificacao/tamanho do PDF
- persistencia correta da nova revisao no `ItEntity`

### Cenario C: revisao nova gerou resposta nova, mas criou varias linhas duplicadas

Investigar:
- `AssistantCacheService.saveResponse(...)`
- `findReusableCachedEntry(...)`

### Cenario D: revisao exibida no assistente nao bate com o PDF

Investigar:
- `AssistantDocumentIndexService.extractDocumentMetadata(...)`
- `UploadItFileUseCase.upsertItRecord(...)`
- nome do arquivo sendo usado como fallback indevido

## O que nao fazer para testar

- Nao subir IT real da empresa em modelo externo apenas para simular revisao
- Nao confiar apenas no nome do arquivo como fonte de revisao
- Nao afrouxar o cache semelhante a ponto de misturar perguntas diferentes

## Estrategia segura de validacao futura

Se um dia for necessario validar a revisao sem expor documento sensivel:

1. usar uma IT interna ja homologada no mesmo formato que o assistente responde bem
2. alterar uma frase pequena e verificavel no ambiente controlado da empresa
3. subir a nova revisao
4. repetir exatamente a mesma pergunta documental
5. validar:
   - se nao veio cache velho
   - se a resposta nova foi gerada
   - se a linha reutilizavel do cache foi atualizada

## Resumo

Hoje, se a revisao mudar corretamente no PDF/arquivo:
- o cache antigo nao deve ser servido
- a IA deve responder de novo
- o cache deve ser atualizado para a nova versao

Se isso quebrar no futuro, o ponto de ataque principal e:
- `AssistantCacheService.resolveDocumentVersion(...)`
- `AssistantCacheService.saveResponse(...)`
- `UploadItFileUseCase.upsertItRecord(...)`
- `AssistantDocumentIndexService.extractDocumentMetadata(...)`
