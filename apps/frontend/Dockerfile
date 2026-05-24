# ─── Stage 1: Dependencies ───────────────────────────────────────────────────
FROM node:22-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci

# ─── Stage 2: Development ────────────────────────────────────────────────────
FROM deps AS development
ARG APP_DEV_PORT=5177
ENV APP_DEV_PORT=${APP_DEV_PORT}
COPY . .
EXPOSE ${APP_DEV_PORT}
CMD ["sh", "-c", "npm run dev -- --host 0.0.0.0 --port \"${APP_DEV_PORT}\""]

# ─── Stage 3: Builder ────────────────────────────────────────────────────────
FROM deps AS builder
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
COPY . .
RUN npm run build

# ─── Stage 4: Production ─────────────────────────────────────────────────────
# Host port (5177) = DEPLOY_HOST_PORT in secrets; map HOST:80 in compose/deploy.
ARG NGINX_VERSION=1.27-alpine
FROM nginx:${NGINX_VERSION} AS production
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]