# --- Build stage ---
FROM node:22-alpine AS builder
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install

COPY frontend/ .

# Build-time env vars so Next.js bakes correct service URLs into routes-manifest.json
ARG NEXT_PUBLIC_API_URL=http://api:4000
ARG NEXT_PUBLIC_AUTH_URL=http://auth:4001
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_AUTH_URL=$NEXT_PUBLIC_AUTH_URL

RUN npm run build

# --- Runtime stage ---
FROM node:22-alpine
WORKDIR /app

COPY --from=builder /app/.next ./.next
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/public ./public
COPY --from=builder /app/next.config.ts ./next.config.ts

EXPOSE 3000
ENV PORT=3000
CMD ["npm", "start"]
