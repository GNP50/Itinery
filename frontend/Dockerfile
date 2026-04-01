# ==============================================================================
# Frontend Dockerfile – Optimized Multi-stage Build for React/Vite Application
#
# Key Optimizations:
# - BuildKit cache mounts for yarn cache (persistent across builds)
# - Separate dependency installation from build (better layer caching)
# - Minimal production image with nginx
#
# Build with BuildKit (recommended):
#   DOCKER_BUILDKIT=1 docker build -t com.travel/frontend:dev .
# ==============================================================================

FROM node:20-alpine AS base
RUN corepack enable

# ── Dependencies stage ─────────────────────────────────────────────────────────
# Install dependencies with cache mount for faster rebuilds
FROM base AS deps
WORKDIR /app

# Copy package files only (cached layer unless these change)
COPY package.json yarn.lock .yarnrc.yml* ./

# Install dependencies with BuildKit cache mount
# This dramatically speeds up rebuilds by caching yarn cache
RUN --mount=type=cache,target=/root/.yarn \
    yarn install --immutable

# ── Build stage ────────────────────────────────────────────────────────────────
FROM base AS builder
WORKDIR /app

# Copy dependencies from deps stage
COPY --from=deps /app/node_modules ./node_modules

# Copy source code
COPY . .

# Build the application
RUN yarn build

# ── Production stage ───────────────────────────────────────────────────────────
# Minimal nginx image for serving static files
FROM nginx:alpine AS production

# Copy built assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx configuration if it exists
COPY --from=builder /app/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
