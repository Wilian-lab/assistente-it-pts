# Seguranca do Sistema - 2026-04-11

## Diagnostico Geral

O sistema ja possui uma base funcional relevante de seguranca:
- autenticacao stateless com JWT
- senhas com `BCrypt`
- segregacao de leitura por `setor` em ITs, PTS e cache do assistente
- endpoints administrativos protegidos por `ROLE_ADMIN`
- acesso a ITs e PTS filtrado pelo `setorAtivo` do usuario autenticado

Mesmo assim, o nivel atual ainda nao e adequado para subir em producao sem reforcos.

Classificacao geral atual:
- **Seguranca funcional:** media
- **Seguranca para producao:** media-baixa
- **Segregacao por setor:** boa em leitura, incompleta em administracao
- **Robustez para escala inicial:** boa para teste/piloto, ainda precisa endurecimento para operacao real

## O Que Ja Esta Bom

### 1. Autenticacao e senha

Arquivos principais:
- [SecurityConfig.java](src/main/java/com/wlilan/backend_assistent/Security/SecurityConfig.java)
- [AuthUseCase.java](src/main/java/com/wlilan/backend_assistent/usuario/useCases/AuthUseCase.java)
- [TokenService.java](src/main/java/com/wlilan/backend_assistent/Security/TokenService.java)
- [UsuarioEntity.java](src/main/java/com/wlilan/backend_assistent/usuario/UsuarioEntity.java)

Pontos positivos:
- senha e armazenada com `BCryptPasswordEncoder`
- login exige email, senha e setor
- token tem expiracao configuravel
- backend roda sem sessao de servidor (`STATELESS`)
- rotas criticas de CRUD administrativo exigem autenticacao

### 2. Segregacao por setor

Arquivos principais:
- [ServiceUseCase.java](src/main/java/com/wlilan/backend_assistent/usuario/useCases/ServiceUseCase.java)
- [GetItByIdUseCase.java](src/main/java/com/wlilan/backend_assistent/it/it/usecases/GetItByIdUseCase.java)
- [GetAllItUseCase.java](src/main/java/com/wlilan/backend_assistent/it/it/usecases/GetAllItUseCase.java)
- [PtsQueryService.java](src/main/java/com/wlilan/backend_assistent/pts/PtsQueryService.java)
- [AssistantCacheService.java](src/main/java/com/wlilan/backend_assistent/assistant/AssistantCacheService.java)

Pontos positivos:
- ITs sao consultadas por `id + setor`
- PTS e filtrado por `setor`
- cache do assistente ja usa `setor` na chave logica
- usuario comum nao pode pertencer a varios setores

### 3. Estrutura de crescimento inicial

Para um cenario como:
- administrador da moagem/secagem cadastrando 20 usuarios
- administrador da refinaria cadastrando 30 usuarios
- cada setor com centenas de ITs

O sistema tende a funcionar de forma estavel no curto prazo porque:
- as consultas principais ja filtram por setor
- o repositorio de IT nao mistura registros entre setores
- PTS e cache do assistente tambem seguem esse recorte

## Riscos Reais Se Subir Em Producao Hoje

## Critico

### 1. Credenciais e segredos versionados no repositorio

Arquivo:
- [application.properties](src/main/resources/application.properties)

Problemas atuais:
- `spring.datasource.password=admin`
- `security.token.secret=Bck-End#`
- `app.admin.password=admin12345`
- `app.admin.email=admin@teste.com`

Impacto:
- qualquer pessoa com acesso ao repositorio ou build consegue inferir credenciais locais
- o segredo JWT atual e fraco e previsivel
- bootstrap de admin com senha padrao e um risco serio

Se isso for para producao sem ajuste:
- invasao por credencial padrao
- falsificacao de token caso o segredo seja exposto
- reutilizacao indevida de conta administrativa

### 2. Registro publico de usuario ainda esta aberto

Arquivos:
- [SecurityConfig.java](src/main/java/com/wlilan/backend_assistent/Security/SecurityConfig.java)
- [AuthController.java](src/main/java/com/wlilan/backend_assistent/usuario/controllers/AuthController.java)
- [UsuarioController.java](src/main/java/com/wlilan/backend_assistent/usuario/controllers/UsuarioController.java)

Problemas atuais:
- `POST /auth/register` esta com `permitAll`
- `POST /usuario` esta com `permitAll`

Impacto:
- qualquer pessoa que alcance a API pode tentar criar contas
- isso abre vetor para abuso, spam de usuarios e ocupacao indevida da base

Para producao, isso precisa mudar para uma destas estrategias:
- somente admin cria usuarios
- ou convite controlado
- ou fluxo de aprovacao interno

### 3. Administrador pode criar usuario em setor arbitrario

Arquivos:
- [AdminUserController.java](src/main/java/com/wlilan/backend_assistent/usuario/controllers/AdminUserController.java)
- [ServiceUseCase.java](src/main/java/com/wlilan/backend_assistent/usuario/useCases/ServiceUseCase.java)

Problema atual:
- o admin autenticado pode enviar `setores` livres no payload
- o backend nao obriga o cadastro a ficar dentro do `setorAtivo` do administrador atual

Impacto:
- um admin de um setor pode cadastrar usuarios em outro setor
- isso fere segregacao operacional e governanca

### 4. Upload de IT/PTS aceita setor do request sem vincular ao admin autenticado

Arquivo:
- [ItController.java](src/main/java/com/wlilan/backend_assistent/it/Controller/ItController.java)

Problema atual:
- `uploadPts` e `uploadPdf` recebem `@RequestParam("setor")`
- o controller nao cruza isso com o `setorAtivo` do admin autenticado

Impacto:
- um admin pode subir arquivos em setor que nao deveria administrar
- isso pode misturar documentos entre areas

## Alto

### 5. SecurityFilter registra informacao demais no log

Arquivo:
- [SecurityFilter.java](src/main/java/com/wlilan/backend_assistent/Security/SecurityFilter.java)

Problema atual:
- o filtro loga metodo e URI de toda requisicao
- loga existencia do header Authorization
- loga prefixo do token
- loga sujeito e setor

Impacto:
- vaza metadados sensiveis no servidor
- aumenta risco em ambiente com observabilidade compartilhada
- gera ruido e custo de log

Recomendacao:
- remover log de prefixo de token
- reduzir logs de autenticacao para `debug`
- nao logar identificadores sensiveis por padrao

### 6. Token nao revalida associacao do setor com o usuario a cada requisicao

Arquivos:
- [TokenService.java](src/main/java/com/wlilan/backend_assistent/Security/TokenService.java)
- [SecurityFilter.java](src/main/java/com/wlilan/backend_assistent/Security/SecurityFilter.java)

Problema atual:
- o JWT traz `setorAtivo`
- o filtro aplica esse setor ao usuario carregado por email
- mas nao verifica explicitamente se o usuario ainda possui acesso a esse setor no momento da requisicao

Impacto:
- se o usuario perder acesso a um setor, um token antigo ainda pode continuar valido ate expirar

### 7. `ddl-auto=update` em producao e arriscado

Arquivo:
- [application.properties](src/main/resources/application.properties)

Problema atual:
- `spring.jpa.hibernate.ddl-auto=update`

Impacto:
- mudancas involuntarias no schema em startup
- comportamento dificil de auditar
- risco maior em ambiente com dados reais

### 8. `show-sql=true` em ambiente real exporia dados demais

Arquivo:
- [application.properties](src/main/resources/application.properties)

Problema atual:
- `spring.jpa.show-sql=true`

Impacto:
- loga consultas
- pode expor estrutura, valores e padrao de acesso
- gera volume de log desnecessario

## Medio

### 9. Cache do assistente pode crescer indefinidamente

Arquivos:
- [AssistantCacheEntry.java](src/main/java/com/wlilan/backend_assistent/assistant/AssistantCacheEntry.java)
- [AssistantCacheService.java](src/main/java/com/wlilan/backend_assistent/assistant/AssistantCacheService.java)
- [AssistantCacheRepository.java](src/main/java/com/wlilan/backend_assistent/assistant/AssistantCacheRepository.java)

Estado atual:
- o cache documental ja esta segmentado por setor
- isso esta correto do ponto de vista funcional
- mas ainda nao existe TTL nem politica de expiracao

Impacto:
- crescimento gradual da tabela `assistant_cache`
- perda de previsibilidade de armazenamento com o tempo
- mais custo de manutencao e auditoria

Observacao:
- manter tudo em uma tabela so **nao e o problema principal** agora
- o problema principal e falta de expiracao, observabilidade e limpeza seletiva

### 10. Falta limitacao de taxa para login e operacoes sensiveis

Estado atual:
- nao encontrei mecanismo de rate limit ou bloqueio progressivo

Impacto:
- brute force em login
- abuso de endpoints administrativos
- sobrecarga por automacao simples

### 11. Falta auditoria de acoes administrativas

Exemplos que deveriam ser auditados:
- criacao de usuario
- exclusao de usuario
- upload de IT
- upload de PTS
- mudanca de treinamento
- reindexacao do assistente

Impacto:
- investigacao dificil em caso de incidente
- baixa rastreabilidade operacional

## Banco de Dados e Excesso de Informacao

## O que hoje pode ficar salvo

### Usuario

Tabela principal:
- `usuario`

Campos relevantes:
- nome
- email
- senha hash
- role
- setores
- historico de treinamento basico

Avaliacao:
- adequado para operacao
- nao vi armazenamento de senha em texto puro
- isso esta correto

### Assistente

Tabela principal:
- `assistant_cache`

O que hoje ela guarda:
- IT
- setor
- pergunta normalizada
- versao do documento
- modelo/modo
- resposta final cacheada

Avaliacao:
- faz sentido do ponto de vista de economia de tokens
- nao parece guardar a conversa inteira do usuario no banco do backend do assistente documental
- isso e bom

Ponto de atencao:
- ainda falta TTL
- ainda falta politica de retencao
- ainda falta inspecao/auditoria por setor

### PTS

Tabelas principais:
- `pts_record`
- `pts_file`

Avaliacao:
- armazenamento funcional e coerente com o produto
- setorizacao existe
- risco maior esta mais em crescimento e governanca do que em vazamento por desenho atual

## Avaliacao de Escala por Setor

## Cenario citado

- moagem/secagem: 20 usuarios
- refinaria: 30 usuarios
- centenas de ITs por setor

## O sistema quebra?

### Curto prazo

Nao deve quebrar apenas por esse volume.

Motivos:
- consultas principais ja usam `setor`
- ITs sao buscadas por setor
- PTS e por setor
- cache documental ja usa setor na chave
- autenticacao e stateless

### Medio prazo

Pode comecar a sofrer se continuar crescendo sem controle em:
- `assistant_cache`
- logs excessivos
- indexacao documental sem politica de reprocessamento monitorada
- ausencia de metricas

## O que precisa melhorar para esse crescimento ser seguro

### Prioridade 1
- remover segredos do codigo versionado
- fechar registro publico
- travar operacoes administrativas ao setor do admin autenticado
- reforcar segredo JWT com valor forte e externo ao repositorio

### Prioridade 2
- adicionar rate limiting no login
- reduzir logs sensiveis
- desativar `show-sql` em ambiente real
- trocar `ddl-auto=update` por migracoes controladas

### Prioridade 3
- criar TTL para `assistant_cache`
- auditoria por setor
- metricas de uso por provider, setor e cache hit
- trilha de auditoria para acoes administrativas

## O Que Daria Errado Em Producao Se Subisse Hoje

Resumo direto:

1. risco de criacao indevida de usuarios porque o cadastro publico ainda esta aberto
2. risco de administrador operar fora do proprio setor em criacao de usuarios e upload de arquivos
3. risco de exposicao por segredos versionados e senha padrao de admin
4. risco de token continuar valendo para setor removido ate expirar
5. risco de vazar metadado sensivel por log excessivo
6. risco de crescimento sem controle do cache do assistente

## Recomendacao de Endurecimento Antes de Producao

### Bloco minimo obrigatorio

1. mover todos os segredos para ambiente local/segredo de deploy
2. desativar `POST /auth/register` e `POST /usuario` publico
3. limitar criacao e upload ao setor do administrador autenticado
4. trocar segredo JWT por valor forte e exclusivo por ambiente
5. remover logs de token e autenticacao detalhada

### Bloco recomendado logo depois

1. adicionar rate limit de login
2. adicionar auditoria administrativa
3. adicionar TTL no `assistant_cache`
4. desligar `show-sql` fora do ambiente local
5. substituir `ddl-auto=update` por estrategia de migracao

## Nivel de Prontidao Atual

Se fosse classificar hoje:
- **Piloto interno controlado:** viavel com cautela
- **Uso real por empresa inteira sem reforco:** nao recomendado ainda
- **Base tecnica para endurecer e chegar la:** sim, boa

## Conclusao

O sistema tem uma arquitetura que ja aponta para um produto viavel:
- autenticacao existe
- segregacao por setor existe
- assistente e PTS estao respeitando contexto por setor
- usuarios possuem login e senha individuais

O principal agora nao e mudar regra de negocio.
O principal e endurecer:
- identidade
- autorizacao administrativa
- segredos
- logs
- retencao de dados

Com esses ajustes, o sistema pode sair do modo de teste e caminhar com muito mais seguranca para um ambiente real.
