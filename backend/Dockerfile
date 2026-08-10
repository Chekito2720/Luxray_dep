# Dockerfile optimizado para Railway (multi-stage)
# Construye todos los microservicios Spring Boot en una sola imagen

# ═══════════════════════════════════════════
# STAGE 1: Build con Maven + JDK 21
# ═══════════════════════════════════════════
FROM maven:3.9.8-eclipse-temurin-21 AS builder

WORKDIR /app

# Copia solo pom.xml y descarga dependencias (cache layer)
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY auth-service/pom.xml auth-service/
COPY cursos-service/pom.xml cursos-service/
COPY analytics-service/pom.xml analytics-service/
COPY api-gateway/pom.xml api-gateway/
COPY discovery-service/pom.xml discovery-service/
COPY search-service/pom.xml search-service/

RUN mvn dependency:go-offline -B -q

# Copia fuentes y compila todo (saltea tests)
COPY . .
RUN mvn clean package -DskipTests -B -q

# ═══════════════════════════════════════════
# STAGE 2: Runtime JRE 21 ligero (distroless-style)
# ═══════════════════════════════════════════
FROM eclipse-temurin:21-jre-alpine

# Usuario no-root para seguridad
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copia JARs compilados
COPY --from=builder /app/auth-service/target/*.jar auth-service.jar
COPY --from=builder /app/cursos-service/target/*.jar cursos-service.jar
COPY --from=builder /app/analytics-service/target/*.jar analytics-service.jar
COPY --from=builder /app/search-service/target/*.jar search-service.jar
COPY --from=builder /app/api-gateway/target/*.jar api-gateway.jar
COPY --from=builder /app/discovery-service/target/*.jar discovery-service.jar

# Script de entrada que levanta el servicio según variable de entorno
COPY --chown=appuser:appgroup entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

USER appuser

EXPOSE 8080 8081 8082 8083 8084 8761

ENTRYPOINT ["/entrypoint.sh"]