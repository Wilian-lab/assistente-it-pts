# Backend Assistente IT

Backend Spring Boot do sistema de controle de ITs e PTS, com foco em treinamento operacional, governanca por setor e consulta assistida de documentos.

## Visao geral

Este backend atende o frontend do sistema e centraliza:

- autenticacao e autorizacao por perfil
- controle de setores e segregacao de acesso
- cadastro de usuarios por `SUPER_ADMIN` e `ADMIN`
- controle de ITs, PDFs e sincronizacao de documentos
- controle de treinamento por usuario
- recuperacao de senha por link e por codigo administrativo
- consulta assistida de ITs com contexto por setor
- importacao e consulta de arquivos PTS

## Perfis de acesso

- `SUPER_ADMIN`: administra setores, admins e usuarios de todos os setores
- `ADMIN`: administra usuarios e treinamentos dentro dos setores permitidos
- `USER`: consulta ITs, treinamentos e assistente dentro do setor ativo

## Regras principais

- todo acesso a IT, cache e indexacao do assistente respeita o `setorAtivo`
- o assistente consulta apenas o contexto da IT selecionada no setor do usuario
- treinamentos, notificacoes e status operacionais ficam vinculados ao usuario
- recuperacao de senha pode acontecer por token de e-mail ou por codigo gerado pelo admin

## Arquitetura resumida

- `usuario/`: autenticacao, perfis, setores, perfil do usuario e recuperacao de acesso
- `it/`: CRUD de ITs, upload de PDF, sincronizacao e leitura protegida de arquivos
- `assistant/`: indexacao, cache, busca estruturada e resposta guiada por IT
- `pts/`: importacao e consulta de arquivos PTS por setor
- `Security/`: JWT, filtros e regras de autorizacao
- `DTO/`: contratos da API

## Integracao com o frontend

O frontend consome esta API para:

- autenticar usuarios e obter token JWT
- listar ITs do setor ativo
- consultar documentos protegidos
- cadastrar usuarios e atualizar treinamentos
- abrir o assistente contextual da IT selecionada
- atualizar perfil, foto e senha do usuario logado

## Endpoints principais

### Autenticacao e recuperacao

- `POST /auth/login`
- `GET /auth/setores`
- `POST /auth/forgot-password`
- `POST /auth/reset-password`
- `POST /auth/reset-password/recovery-code`

### Perfil do usuario autenticado

- `PUT /usuario/me/profile`
- `PUT /usuario/me/password`
- `POST /usuario/me/avatar`
- `GET /usuario/me/avatar`
- `DELETE /usuario/me/avatar`

### Administracao de usuarios

- `GET /api/admin/users`
- `POST /api/admin/users`
- `DELETE /api/admin/users/{id}`
- `PUT /api/admin/users/{id}/training`
- `PUT /api/admin/users/{id}/recovery-code`

### Administracao de setores

- `GET /api/admin/setores`
- `POST /api/admin/setores`

### ITs

- `POST /it`
- `GET /it`
- `GET /it/{id}`
- `GET /it/{id}/file`
- `PUT /it/{id}`
- `DELETE /it/{id}`
- `POST /it/upload/pdf`
- `POST /it/upload/pts`
- `POST /it/sync`

### Assistente

- `POST /assistant/ask`
- `GET /assistant/context`
- `GET /assistant/options`
- `POST /assistant/reindex`

### PTS

- `GET /api/pts/products`
- `GET /api/pts/items`
- `GET /api/pts/data`
- `GET /api/pts/files`
- `DELETE /api/pts/files/current`

## Organizacao por setor

O sistema foi preparado para operar com multiplos setores sem misturar contexto:

- ITs e usuarios usam setor ativo
- o cache do assistente e os blocos indexados foram separados por setor
- existem views por setor para leitura operacional no banco

Isso permite manter uma base unica e sustentavel, sem perder clareza para auditoria e manutencao.

## Seguranca

Boas praticas adotadas no projeto:

- segredo nunca deve ir para o cliente
- configuracoes sensiveis devem ficar fora do repositorio
- autenticacao via JWT
- controle de acesso por perfil e setor
- reset de senha com token ou codigo administrativo
- arquivos protegidos por autenticacao

## Configuracao local

O projeto usa `application.properties` como base e suporta configuracao local fora do versionamento.

Arquivos recomendados:

- `src/main/resources/application.properties`: base versionada com placeholders seguros
- `src/main/resources/application-local.properties`: configuracao local real, fora do Git
- `src/main/resources/application-local.example.properties`: modelo de referencia para novos ambientes

Exemplos de configuracao sensivel:

- banco de dados
- segredo JWT
- credenciais SMTP
- credenciais de administrador bootstrap
- chave da API Gemini

Nao versione segredos reais no repositorio.

## Git e deploy

Arquivos que devem permanecer fora do versionamento:

- `application-local.properties`
- `.env.properties`
- logs locais
- notas de revisao e rascunhos operacionais

Arquivos que devem ir para o GitHub:

- codigo-fonte
- `application.properties` base
- `README.md`
- `.gitignore`
- `.dockerignore`
- modelo `application-local.example.properties`

## Execucao local

### Requisitos

- Java 17
- Maven Wrapper
- PostgreSQL

### Subir o backend

```bash
mvnw.cmd spring-boot:run
```

### Rodar testes

```bash
mvnw.cmd test
```

## Docker Compose

O projeto ja possui uma base de containers para teste local e preparo de deploy:

- `docker/backend.Dockerfile`
- `docker/frontend.Dockerfile`
- `docker/nginx.conf`
- `docker-compose.yml`
- `.env.compose.example`

### Como testar localmente

1. Copie `.env.compose.example` para `.env`
2. Ajuste as credenciais e chaves reais
3. Garanta que o frontend continue na pasta irma `../Fron-React`
4. Rode:

```bash
docker compose up --build
```

### O que sobe

- `postgres`: banco PostgreSQL
- `backend`: API Spring Boot
- `frontend`: build estatico React servido por Nginx

### Observacao importante

O `docker-compose.yml` atual considera a estrutura local usada hoje:

- backend em `backend-assistente`
- frontend em `../Fron-React`

Para Oracle Cloud ou outro servidor, o ideal e manter os dois projetos no mesmo workspace do servidor ou adaptar o compose para usar imagens publicadas.

## Estado atual do produto

Hoje o sistema entrega:

- controle administrativo por setor
- gestao de usuarios e admins
- treinamento por IT
- consulta assistida de documentos
- PTS por setor
- perfil do usuario com foto e senha

O proximo passo natural e a preparacao final para deploy, com endurecimento de seguranca, limpeza de configuracao sensivel e validacao do ambiente produtivo.
