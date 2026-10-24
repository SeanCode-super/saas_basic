# ==========================================
# Stage 1: Build Frontend Assets
# ==========================================
FROM node:20-alpine AS builder

WORKDIR /app

# Cache dependencies
COPY package*.json ./
RUN npm ci || npm install

# Build static bundle
COPY . .
RUN npm run build

# ==========================================
# Stage 2: Serve via High-Performance Nginx
# ==========================================
FROM nginx:1.27-alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
