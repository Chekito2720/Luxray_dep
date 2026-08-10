#!/bin/bash
# Arranque de todos los servicios del backend LuxRay

echo "[start-all] Iniciando servicios LuxRay..."

echo "=== ENV DEBUG ==="
echo "PGHOST=$PGHOST"
echo "PGPORT=$PGPORT"
echo "PGUSER=$PGUSER"
echo "PGPASSWORD=${PGPASSWORD:0:4}****"
echo "EUREKA_URL=$EUREKA_URL"
echo "ELASTICSEARCH_URL=$ELASTICSEARCH_URL"
echo "JWT_SECRET=${JWT_SECRET:0:4}****"
echo "==================="

# URLs de BD por servicio (Spring Boot estándar)
export SPRING_DATASOURCE_URL_AUTH="jdbc:postgresql://${PGHOST}:${PGPORT}/auth_db"
export SPRING_DATASOURCE_URL_CURSOS="jdbc:postgresql://${PGHOST}:${PGPORT}/cursos_db"
export SPRING_DATASOURCE_URL_ANALYTICS="jdbc:postgresql://${PGHOST}:${PGPORT}/analytics_db"
export SPRING_DATASOURCE_USERNAME="${PGUSER}"
export SPRING_DATASOURCE_PASSWORD="${PGPASSWORD}"

# Discovery primero (necesario para Eureka)
java -XX:MaxRAMPercentage=12 -jar discovery-service.jar &
sleep 8

# Servicios en paralelo con sus URLs de BD respectivas
SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL_AUTH}" \
SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}" \
SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}" \
java -XX:MaxRAMPercentage=12 -jar auth-service.jar &

SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL_CURSOS}" \
SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}" \
SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}" \
java -XX:MaxRAMPercentage=12 -jar cursos-service.jar &

SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL_ANALYTICS}" \
SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}" \
SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}" \
java -XX:MaxRAMPercentage=12 -jar analytics-service.jar &

java -XX:MaxRAMPercentage=12 -jar search-service.jar &
sleep 5

# Gateway ultimo
java -XX:MaxRAMPercentage=12 -jar api-gateway.jar &

echo "[start-all] Todos los servicios iniciados. Gateway expuesto en puerto 8080."

# Mantener el contenedor vivo
wait