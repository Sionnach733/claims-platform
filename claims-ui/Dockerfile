# syntax=docker/dockerfile:1.7
# claims-ui: build static assets with Node, serve them from unprivileged nginx (runs as UID 101, port 8080).

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine
# Templates are rendered with envsubst at startup, so the API upstream is configured per environment.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV CLAIMS_API_URL=http://claims-service:8080
EXPOSE 8080
# Shell form on purpose: Docker treats exit code 1 as unhealthy, and `curl -f`/wget exit with other codes.
# hadolint ignore=DL3025
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
