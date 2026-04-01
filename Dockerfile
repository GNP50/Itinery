# ==============================================================================
# Dockerfile – Optimized Multi-stage Build for Travel Itinerary Platform
#
# Key Optimizations:
# - BuildKit cache mounts for Maven local repository (persistent across builds)
# - Separate dependency download from compilation (better layer caching)
# - Optimized layer ordering (least to most frequently changed)
# - Spring Boot layered JAR extraction for minimal runtime layers
#
# Build with BuildKit (required for cache mounts):
#   DOCKER_BUILDKIT=1 docker build -t com.travel/app-monolith:1.0.0-SNAPSHOT .
#
# Build specific module:
#   DOCKER_BUILDKIT=1 docker build --build-arg BUILD_MODULE=config-manager -t com.travel/config-manager:dev .
#
# Run:
#   docker run -p 8080:8080 com.travel/app-monolith:1.0.0-SNAPSHOT
# ==============================================================================

# ── Stage 1: Maven Dependency Cache ────────────────────────────────────────────
# This stage downloads dependencies and can be heavily cached
FROM eclipse-temurin:21-jdk-jammy AS dependencies

LABEL stage="dependencies"

# Install Maven
ARG MAVEN_VERSION=3.9.7
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        -o /tmp/maven.tar.gz \
    && tar -xzf /tmp/maven.tar.gz -C /opt \
    && rm /tmp/maven.tar.gz

ENV MAVEN_HOME=/opt/apache-maven-${MAVEN_VERSION}
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

WORKDIR /workspace

# Copy only POM files for dependency resolution
# This layer will be cached unless POM files change
COPY pom.xml ./pom.xml

# Shared starters
COPY config-client-starter/pom.xml              ./config-client-starter/pom.xml
COPY event-messaging-starter/pom.xml            ./event-messaging-starter/pom.xml

# Domain APIs
COPY itinerary-api/pom.xml                      ./itinerary-api/pom.xml
COPY queue-api/pom.xml                          ./queue-api/pom.xml
COPY geo-api/pom.xml                            ./geo-api/pom.xml
COPY ai-api/pom.xml                             ./ai-api/pom.xml
COPY user-api/pom.xml                           ./user-api/pom.xml
COPY saga-dto/pom.xml                           ./saga-dto/pom.xml
COPY saga-api/pom.xml                           ./saga-api/pom.xml

# Domain implementations (legacy)
COPY itinerary-impl/pom.xml                     ./itinerary-impl/pom.xml
COPY geo-impl/pom.xml                           ./geo-impl/pom.xml
COPY ai-impl/pom.xml                            ./ai-impl/pom.xml
COPY user-impl/pom.xml                          ./user-impl/pom.xml
COPY user-impl-rest/pom.xml                     ./user-impl-rest/pom.xml

# Queue domain modules
COPY queue-impl-redis/pom.xml                   ./queue-impl-redis/pom.xml

# Itinerary domain modules
COPY itinerary-impl-core/pom.xml                ./itinerary-impl-core/pom.xml
COPY itinerary-impl-jpa/pom.xml                 ./itinerary-impl-jpa/pom.xml
COPY itinerary-impl-rest/pom.xml                ./itinerary-impl-rest/pom.xml
COPY itinerary-impl-redis/pom.xml               ./itinerary-impl-redis/pom.xml
COPY itinerary-impl-kafka/pom.xml               ./itinerary-impl-kafka/pom.xml
COPY itinerary-impl-storage/pom.xml             ./itinerary-impl-storage/pom.xml

# AI domain modules
COPY ai-impl-grpc-server/pom.xml                ./ai-impl-grpc-server/pom.xml
COPY ai-impl-kafka/pom.xml                      ./ai-impl-kafka/pom.xml
COPY ai-impl-ollama/pom.xml                     ./ai-impl-ollama/pom.xml

# User domain modules
COPY user-impl-core/pom.xml                     ./user-impl-core/pom.xml
COPY user-impl-jpa/pom.xml                      ./user-impl-jpa/pom.xml
COPY user-impl-grpc-server/pom.xml              ./user-impl-grpc-server/pom.xml

# Geo domain modules
COPY geo-impl-kafka/pom.xml                     ./geo-impl-kafka/pom.xml

# Saga domain modules
COPY saga-impl-core/pom.xml                     ./saga-impl-core/pom.xml
COPY saga-impl-jpa/pom.xml                      ./saga-impl-jpa/pom.xml
COPY saga-impl-grpc-server/pom.xml              ./saga-impl-grpc-server/pom.xml

# Cross-domain gRPC clients
COPY ai-grpc-client/pom.xml                     ./ai-grpc-client/pom.xml
COPY user-grpc-client/pom.xml                   ./user-grpc-client/pom.xml
COPY geo-grpc-client/pom.xml                    ./geo-grpc-client/pom.xml
COPY saga-grpc-client/pom.xml                   ./saga-grpc-client/pom.xml

# Infrastructure / platform services
COPY config-manager/pom.xml                     ./config-manager/pom.xml
COPY event-saga-orchestrator/pom.xml            ./event-saga-orchestrator/pom.xml
COPY file-manager/pom.xml                       ./file-manager/pom.xml
COPY monitoring-service/pom.xml                 ./monitoring-service/pom.xml

# Deployable applications
COPY app-monolith/pom.xml                       ./app-monolith/pom.xml
COPY app-microservice-itinerary/pom.xml         ./app-microservice-itinerary/pom.xml
COPY app-microservice-user/pom.xml              ./app-microservice-user/pom.xml
COPY app-microservice-ai/pom.xml                ./app-microservice-ai/pom.xml

# Download all dependencies using BuildKit cache mount
# This dramatically speeds up rebuilds by caching Maven's local repository
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress \
        dependency:go-offline \
        -P skip-it \
        --fail-at-end \
    || true

# ── Stage 2: Build ─────────────────────────────────────────────────────────────
FROM dependencies AS builder

LABEL stage="builder"

# Copy proto sources (needed by code generation phases)
COPY proto/ ./proto/

# Copy all module source trees
# Shared starters
COPY config-client-starter/src              ./config-client-starter/src
COPY event-messaging-starter/src            ./event-messaging-starter/src

# Domain APIs
COPY itinerary-api/src                      ./itinerary-api/src
COPY queue-api/src                          ./queue-api/src
COPY geo-api/src                            ./geo-api/src
COPY ai-api/src                             ./ai-api/src
COPY user-api/src                           ./user-api/src
COPY saga-dto/src                           ./saga-dto/src
COPY saga-api/src                           ./saga-api/src

# Domain implementations (legacy)
COPY itinerary-impl/src                     ./itinerary-impl/src
COPY geo-impl/src                           ./geo-impl/src
COPY ai-impl/src                            ./ai-impl/src
COPY user-impl/src                          ./user-impl/src

# Queue domain modules
COPY queue-impl-redis/src                   ./queue-impl-redis/src

# Itinerary domain modules
COPY itinerary-impl-core/src                ./itinerary-impl-core/src
COPY itinerary-impl-jpa/src                 ./itinerary-impl-jpa/src
COPY itinerary-impl-rest/src                ./itinerary-impl-rest/src
COPY itinerary-impl-redis/src               ./itinerary-impl-redis/src
COPY itinerary-impl-kafka/src               ./itinerary-impl-kafka/src
COPY itinerary-impl-storage/src             ./itinerary-impl-storage/src

# AI domain modules
COPY ai-impl-grpc-server/src                ./ai-impl-grpc-server/src
COPY ai-impl-kafka/src                      ./ai-impl-kafka/src
COPY ai-impl-ollama/src                     ./ai-impl-ollama/src

# User domain modules
COPY user-impl-core/src                     ./user-impl-core/src
COPY user-impl-jpa/src                      ./user-impl-jpa/src
COPY user-impl-grpc-server/src              ./user-impl-grpc-server/src

# Geo domain modules
COPY geo-impl-kafka/src                     ./geo-impl-kafka/src

# Saga domain modules
COPY saga-impl-core/src                     ./saga-impl-core/src
COPY saga-impl-jpa/src                      ./saga-impl-jpa/src
COPY saga-impl-grpc-server/src              ./saga-impl-grpc-server/src

# Cross-domain gRPC clients
COPY ai-grpc-client/src                     ./ai-grpc-client/src
COPY user-grpc-client/src                   ./user-grpc-client/src
COPY geo-grpc-client/src                    ./geo-grpc-client/src
COPY saga-grpc-client/src                   ./saga-grpc-client/src

# Infrastructure / platform services
COPY config-manager/src                     ./config-manager/src
COPY event-saga-orchestrator/src            ./event-saga-orchestrator/src
COPY file-manager/src                       ./file-manager/src
COPY monitoring-service/src                 ./monitoring-service/src

# Deployable applications
COPY app-monolith/src                       ./app-monolith/src
COPY app-microservice-itinerary/src         ./app-microservice-itinerary/src
COPY app-microservice-user/src              ./app-microservice-user/src
COPY app-microservice-ai/src                ./app-microservice-ai/src

# Build arguments
ARG BUILD_MODULE=app-monolith
ARG SKIP_TESTS=true

# Build with Maven cache mount for maximum speed
# Only the specified module and its dependencies are built
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress \
        clean package \
        -pl "${BUILD_MODULE}" \
        -am \
        -P skip-it \
        -Dmaven.test.skip=${SKIP_TESTS}

# Resolve the fat JAR location
RUN find /workspace/"${BUILD_MODULE}"/target \
        -name "*.jar" \
        -not -name "*-sources.jar" \
        -not -name "*-javadoc.jar" \
        | grep -v 'original' \
        | head -1 \
        > /workspace/jar-path.txt \
    && echo "Built JAR: $(cat /workspace/jar-path.txt)"

# Extract Spring Boot layered JAR for optimal runtime layer caching
RUN java -Djarmode=layertools \
        -jar "$(cat /workspace/jar-path.txt)" \
        extract \
        --destination /workspace/extracted

# ── Stage 3: Runtime ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

LABEL org.opencontainers.image.title="Travel Itinerary Platform"
LABEL org.opencontainers.image.description="Spring Boot 3 / Java 21 backend"
LABEL org.opencontainers.image.vendor="com.travel"
LABEL org.opencontainers.image.version="1.0.0-SNAPSHOT"
LABEL org.opencontainers.image.source="https://github.com/travel/itinerary-platform"

# Security hardening: run as non-root user
ARG APP_USER=appuser
ARG APP_UID=1001
ARG APP_GID=1001

RUN groupadd --gid "${APP_GID}" "${APP_USER}" \
    && useradd  --uid "${APP_UID}" \
                --gid "${APP_GID}" \
                --no-create-home \
                --shell /sbin/nologin \
                "${APP_USER}"

# Install minimal runtime dependencies
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        dumb-init \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the layered JAR content from the builder stage.
# Order from least-frequently to most-frequently changed for optimal caching.
COPY --from=builder --chown=${APP_USER}:${APP_USER} /workspace/extracted/dependencies          ./
COPY --from=builder --chown=${APP_USER}:${APP_USER} /workspace/extracted/spring-boot-loader   ./
COPY --from=builder --chown=${APP_USER}:${APP_USER} /workspace/extracted/snapshot-dependencies ./
COPY --from=builder --chown=${APP_USER}:${APP_USER} /workspace/extracted/application           ./

# Create directories for writable runtime data
RUN mkdir -p /app/logs /app/tmp \
    && chown -R "${APP_USER}:${APP_USER}" /app/logs /app/tmp

USER ${APP_USER}

# ── Environment ──────────────────────────────────────────────────────────────
ENV SERVER_PORT=8080
ENV MANAGEMENT_PORT=8081
ENV SPRING_PROFILES_ACTIVE=production
ENV TZ=UTC

# JVM tuning defaults
ENV JAVA_OPTS="\
    -XX:+UseZGC \
    -XX:+ZGenerational \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs/heap-dump.hprof \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=UTC"

# Expose ports
EXPOSE 8080 9090 9096 8081

# Health check
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=5 \
    CMD curl -sf "http://localhost:${MANAGEMENT_PORT:-8081}/actuator/health" | \
        grep -q '"status":"UP"' || exit 1

# Use dumb-init as PID 1 to correctly handle signals
ENTRYPOINT ["dumb-init", "--"]

CMD ["sh", "-c", \
     "exec java ${JAVA_OPTS} \
      org.springframework.boot.loader.launch.JarLauncher"]
