# Stage 1: install production dependencies
FROM node:22-alpine AS base
WORKDIR /app

COPY dashboard/package.json dashboard/package-lock.json ./
RUN npm ci --omit=dev

# Stage 2: build with dev dependencies
FROM base AS builder

RUN npm ci
COPY dashboard/ ./
RUN npm run build

# Stage 3: minimal runtime with standalone output
FROM base AS runner

COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public

EXPOSE 3000

CMD ["node", "server.js"]
