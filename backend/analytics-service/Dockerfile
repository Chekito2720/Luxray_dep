FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline > /dev/null || true
COPY common-lib common-lib
COPY discovery-service discovery-service
COPY api-gateway api-gateway
COPY auth-service auth-service
COPY cursos-service cursos-service
COPY analytics-service analytics-service
RUN mvn -B -ntp -pl analytics-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt
COPY --from=builder /app/analytics-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","/opt/app.jar"]
