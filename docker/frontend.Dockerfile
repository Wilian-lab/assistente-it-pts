FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_ASSISTANT_PROVIDER=backend

ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_ASSISTANT_PROVIDER=$VITE_ASSISTANT_PROVIDER

RUN npm run build

FROM nginx:1.27-alpine
RUN printf 'server {\n  listen 80;\n  server_name _;\n\n  root /usr/share/nginx/html;\n  index index.html;\n\n  location / {\n    try_files $uri $uri/ /index.html;\n  }\n\n  location /health {\n    access_log off;\n    return 200 \"ok\";\n    add_header Content-Type text/plain;\n  }\n}\n' > /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
