FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline > /dev/null || true
COPY common-lib common-lib
COPY api-gateway api-gateway
COPY auth-service auth-service
COPY cursos-service cursos-service
COPY analytics-service analytics-service
COPY search-service search-service
COPY discovery-service discovery-service
RUN mvn -B -ntp -pl discovery-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt
COPY --from=builder /app/discovery-service/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java","-jar","/opt/app.jar"]
