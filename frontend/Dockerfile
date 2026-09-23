# syntax=docker/dockerfile:1
#
# Static build served by nginx, which also reverse-proxies /api to the gateway container (see
# ../infra/docker/nginx.conf) — the browser never needs to know the gateway's own address, and the
# production environment.ts's apiBaseUrl of plain "/api" is what makes that possible.
#
# Build context is the repo root (see docker-compose.yml), so every COPY below is repo-relative.
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/. .
RUN npm run build -- --configuration production

FROM nginx:1.27-alpine
COPY --from=build /app/dist/sentinelbank-web/browser /usr/share/nginx/html
COPY infra/docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
