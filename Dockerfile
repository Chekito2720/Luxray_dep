FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /build
COPY backend/ ./
RUN mvn clean package -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache postgresql-client bash curl
WORKDIR /app

# Copiar todos los jars del backend
COPY --from=builder /build/search-service/target/*.jar search-service.jar
COPY --from=builder /build/auth-service/target/*.jar auth-service.jar
COPY --from=builder /build/cursos-service/target/*.jar cursos-service.jar
COPY --from=builder /build/analytics-service/target/*.jar analytics-service.jar
COPY --from=builder /build/api-gateway/target/*.jar api-gateway.jar
COPY --from=builder /build/discovery-service/target/*.jar discovery-service.jar

# Copiar docker-compose de elasticsearch y DB (para referencia, no se corren en este contenedor)
COPY docker-compose.yml .
COPY backend/docker-compose.elasticsearch.yml .

# Copiar script de arranque
COPY start-all.sh .
RUN chmod +x start-all.sh start-all.sh

# Puerto del gateway (Railway usa este para exponer)
EXPOSE 8080 8081 8082 8083 8084 8761 9200 5432 5433 5434

ENTRYPOINT ["./start-all.sh"]
