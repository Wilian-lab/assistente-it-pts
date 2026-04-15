# Backend Assistente IT PTS

API Spring Boot do sistema de controle de ITs, PTS, treinamento operacional e assistente documental por setor.

Este backend foi projetado para ambientes industriais com segregacao por setor, controle de acesso por perfil e consultas guiadas por documento, sem misturar contexto entre Moagem, Refinaria, Agri-Products e demais setores cadastrados.

## O que o sistema faz

- autentica usuarios com `SUPER_ADMIN`, `ADMIN` e `USER`
- controla acesso por setor ativo
- cadastra usuarios, admins e setores
- registra treinamento por usuario e proximo vencimento
- faz upload, revisao e leitura protegida de ITs em PDF
- importa e consulta arquivos PTS
- entrega respostas do assistente com base na IT selecionada
- reutiliza respostas por cache dentro do mesmo setor e da mesma IT
- envia recuperacao de senha e codigo de recuperacao por email

## Perfis de acesso

- `SUPER_ADMIN`
  - navega entre setores sem logout
  - cria setores
  - cria admins e usuarios
  - ajusta os setores permitidos de cada admin
- `ADMIN`
  - gerencia usuarios e treinamentos dos setores aos quais foi vinculado
  - faz upload e revisao de ITs do setor ativo
- `USER`
  - consulta ITs, treinamento e assistente no setor ativo

## Arquitetura resumida

- `src/main/java/com/wlilan/backend_assistent/usuario`
  - autenticacao, perfis, setores, bootstrap admin, perfil e recuperacao de acesso
- `src/main/java/com/wlilan/backend_assistent/it`
  - CRUD de ITs, upload de PDF e leitura protegida de documentos
- `src/main/java/com/wlilan/backend_assistent/assistant`
  - indexacao, cache, busca contextual e respostas do assistente
- `src/main/java/com/wlilan/backend_assistent/pts`
  - importacao e consulta de dados PTS
- `src/main/java/com/wlilan/backend_assistent/Security`
  - JWT, filtros, rate limit e regras de acesso

## Stack tecnica

- Java 17
- Spring Boot 4
- Spring Security com JWT
- Spring Data JPA
- PostgreSQL
- Docker e Docker Compose
- Gemini API para enriquecimento das respostas do assistente

## Como clonar

### Backend

```bash
git clone https://github.com/Wilian-lab/assistente-it-pts.git
cd assistente-it-pts
```

### Frontend para subir o full stack no Compose

O `docker-compose.yml` deste repositório sobe o frontend apenas no profile `fullstack`, usando a pasta `frontend` deste mesmo repositório.

Estrutura esperada:

```text
assistente-it-pts\
  docker\
  frontend\
  src\
```

Se voce quiser subir apenas backend + banco, o repositório atual sozinho ja basta.

## Variaveis de ambiente

Este projeto usa um arquivo `.env` local para o Docker Compose e `application.properties` com placeholders seguros.

Arquivos sensiveis nao devem ir para o Git:

- `.env`
- `.env.properties`
- `src/main/resources/application-local.properties`

### Variaveis minimas para Docker

```env
POSTGRES_DB=assistant_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=uma-senha-forte

BACKEND_PORT=8081
FRONTEND_PORT=5173

SECURITY_TOKEN_SECRET=uma-chave-jwt-forte-e-longa
SECURITY_TOKEN_EXPIRES_IN_SECONDS=28800

APP_ADMIN_NAME=Administrador Sistema
APP_ADMIN_EMAIL=admin.master@empresa.com
APP_ADMIN_PASSWORD=senha-inicial-do-super-admin
APP_ADMIN_SETORES=GLOBAL
APP_ADMIN_RECOVERY_CODE=codigo-inicial-forte

APP_PASSWORD_RESET_FRONTEND_URL=http://localhost:5173/reset-password
APP_PASSWORD_RESET_MAIL_FROM=suporte.assistente@empresa.com
APP_PASSWORD_RESET_MAIL_SUBJECT=Recuperacao de senha - Assistente IT

SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=suporte.assistente@empresa.com
SPRING_MAIL_PASSWORD=senha-ou-app-password
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS_ENABLE=true
SPRING_MAIL_SMTP_STARTTLS_REQUIRED=true

ASSISTANT_GEMINI_API_KEY=sua-chave-gemini

APP_SECURITY_ALLOWED_ORIGIN_PATTERNS=http://localhost:5173,http://127.0.0.1:5173
VITE_API_BASE_URL=http://localhost:8081
VITE_ASSISTANT_PROVIDER=backend
```

### Observacao sobre email

Em producao, estes campos devem apontar para a conta oficial que envia os emails do sistema:

- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `APP_PASSWORD_RESET_MAIL_FROM`

Os destinatarios nao ficam no `.env`. Eles vem do email cadastrado de cada usuario no banco.

## Como rodar com Docker

### Subir backend + PostgreSQL

Na raiz do backend:

```bash
docker compose up --build -d
```

Servicos:

- backend: `http://localhost:8081`
- postgres Docker: `localhost:5433`

### Subir full stack com frontend

Com o frontend dentro da pasta `frontend` deste repositório:

```bash
docker compose --profile fullstack up --build -d
```

Servicos:

- frontend: `http://localhost:5173`
- backend: `http://localhost:8081`
- postgres Docker: `localhost:5433`

### Parar os containers

```bash
docker compose down
```

### Ver logs

```bash
docker compose logs -f backend
docker compose logs -f postgres
docker compose logs -f frontend
```

## Como rodar sem Docker

### Requisitos

- Java 17
- PostgreSQL
- Maven Wrapper

### Backend

```bash
mvnw.cmd spring-boot:run
```

### Testes

```bash
mvnw.cmd test
```

## Fluxo operacional do sistema

1. O `SUPER_ADMIN` entra no sistema.
2. Cria setores, se necessario.
3. Cria admins e define os setores que cada admin pode gerenciar.
4. Cada admin cria usuarios do proprio contexto.
5. Usuarios e admins consultam ITs e treinamentos dentro do setor ativo.
6. O assistente responde com base na IT selecionada e no setor ativo.

## Endpoints principais

### Autenticacao e setor ativo

- `POST /auth/login`
- `POST /auth/switch-sector`
- `GET /auth/setores`

### Perfil do usuario autenticado

- `GET /usuario/me`
- `PUT /usuario/me/profile`
- `PUT /usuario/me/password`
- `POST /usuario/me/avatar`
- `GET /usuario/me/avatar`
- `DELETE /usuario/me/avatar`

### Recuperacao de acesso

- `POST /auth/forgot-password`
- `POST /auth/reset-password`
- `POST /auth/reset-password/recovery-code`

### Administracao

- `GET /api/admin/users`
- `POST /api/admin/users`
- `DELETE /api/admin/users/{id}`
- `PUT /api/admin/users/{id}/training`
- `PUT /api/admin/users/{id}/recovery-code`
- `PUT /api/admin/users/{id}/setores`
- `GET /api/admin/setores`
- `POST /api/admin/setores`

### ITs

- `GET /it`
- `GET /it/{id}`
- `GET /it/{id}/file`
- `POST /it`
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

## Banco e organizacao por setor

O sistema usa uma base unica, mas com segregacao logica por setor:

- usuarios vinculados a multiplos setores quando necessario
- token JWT com `setorAtivo`
- ITs separadas por setor
- cache do assistente separado por setor e por IT
- views por setor para consulta operacional

Isso evita mistura de contexto e mantem a manutencao do banco sustentavel.

## Seguranca e boas praticas

- segredos ficam fora do Git
- autenticacao via JWT
- segregacao por perfil e por setor
- arquivos protegidos por autenticacao
- rate limit em login e recuperacao
- upload com validacao de extensao e assinatura
- CORS configuravel por ambiente

## O que  precisa saber

- o sistema pode rodar em container com baixo atrito
- o arquivo `.env` do servidor deve ser preenchido antes do `docker compose up`
- a conta SMTP usada para envio de email deve ser institucional
- a chave do Gemini tambem deve vir por ambiente
- para Render, Oracle ou outro ambiente, o principio e o mesmo:
  - banco
  - JWT secret
  - SMTP
  - Gemini
  - bootstrap do super admin

## Checklist rapido para deploy

1. Clonar o backend.
2. Garantir que a pasta `frontend` esteja presente no repositório, se for usar `fullstack`.
3. Criar o `.env` com as credenciais do ambiente.
4. Rodar:

```bash
docker compose --profile fullstack up --build -d
```

5. Validar:

- login
- troca de setor
- visualizacao de IT
- assistente
- criacao de usuarios
- envio de email de recuperacao

## Status atual

O projeto ja esta apto para:

- execucao em Docker
- demonstracao interna
- validacao funcional por setor
- preparacao de deploy em ambiente de teste

Os proximos passos naturais sao:

- configurar segredos no ambiente de deploy
- publicar frontend e backend
- validar o fluxo produtivo com a TI da empresa
