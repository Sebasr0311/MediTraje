# ==============================================================================
# MediTriaje 2.0 — Multi-Stage Production Dockerfile (Backend)
# Java 21 + Spring Boot 3 + Oracle ATP Thin Driver + Flyway
# ==============================================================================

# --- Etapa 1: Build y empaquetado del artefacto ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copiar el modelo relacional y migraciones primero (requeridas por pom.xml en classpath)
COPY database/ database/

# Copiar configuración de dependencias de Maven
COPY backend/pom.xml backend/pom.xml

# Pre-descarga de dependencias offline para optimizar caché de capas Docker
WORKDIR /build/backend
RUN mvn dependency:go-offline -B || true

# Copiar código fuente del backend
COPY backend/src src

# Empaquetar JAR ejecutable excluyendo pruebas unitarias para agilizar el build en cloud
RUN mvn clean package -DskipTests -B

# --- Etapa 2: Imagen de ejecución ligera y endurecida ---
FROM eclipse-temurin:21-jre-alpine AS runner

LABEL maintainer="MediTriaje Team"
LABEL description="MediTriaje 2.0 API Backend Service"

# Crear usuario y grupo sin privilegios de root (seguridad en contenedor)
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

# Crear directorio para Wallet mTLS de Oracle en caso de utilizarse
RUN mkdir -p /etc/secrets/wallet && \
    chown -R appuser:appgroup /etc/secrets/wallet && \
    chmod -R 700 /etc/secrets/wallet

WORKDIR /app

# Copiar artefacto compilado y script de preparacion de wallet
COPY --from=builder /build/backend/target/meditriaje-api-*.jar app.jar
COPY scripts/prepare-wallet.sh /app/prepare-wallet.sh
RUN chmod +x /app/prepare-wallet.sh && \
    chown appuser:appgroup app.jar /app/prepare-wallet.sh

# Configuración de zona horaria de Colombia y utilidades del sistema
ENV TZ=America/Bogota
RUN apk add --no-cache tzdata unzip openssl && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# Variables por defecto
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

USER appuser

EXPOSE 8080

# Parámetros JVM optimizados para contenedores (cgroups v1/v2, memoria ergonómica y entropía segura)
ENTRYPOINT ["sh", "-c", "/app/prepare-wallet.sh && exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Duser.timezone=America/Bogota -jar app.jar"]
