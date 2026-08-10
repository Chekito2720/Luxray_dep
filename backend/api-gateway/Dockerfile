FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline > /dev/null || true
COPY common-lib common-lib
COPY discovery-service discovery-service
COPY auth-service auth-service
COPY cursos-service cursos-service
COPY analytics-service analytics-service
COPY search-service search-service
COPY api-gateway api-gateway
RUN mvn -B -ntp -pl api-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt
COPY --from=builder /app/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/opt/app.jar"]
