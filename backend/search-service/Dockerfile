# Dockerfile para search-service
FROM maven:3.9.8-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY auth-service/pom.xml auth-service/
COPY cursos-service/pom.xml cursos-service/
COPY analytics-service/pom.xml analytics-service/
COPY api-gateway/pom.xml api-gateway/
COPY discovery-service/pom.xml discovery-service/
COPY search-service/pom.xml search-service/

RUN mvn dependency:go-offline -B -q

COPY . .
RUN mvn clean package -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /app/search-service/target/*.jar search-service.jar

RUN chown appuser:appgroup search-service.jar

USER appuser

EXPOSE 8084

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "search-service.jar"]