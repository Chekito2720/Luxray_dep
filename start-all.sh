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

# Discovery primero (necesario para Eureka)
java -XX:MaxRAMPercentage=12 -jar discovery-service.jar &
sleep 8

# Servicios en paralelo (no sequenciales)
java -XX:MaxRAMPercentage=12 -jar auth-service.jar &
java -XX:MaxRAMPercentage=12 -jar cursos-service.jar &
java -XX:MaxRAMPercentage=12 -jar analytics-service.jar &
java -XX:MaxRAMPercentage=12 -jar search-service.jar &
sleep 5

# Gateway ultimo
java -XX:MaxRAMPercentage=12 -jar api-gateway.jar &

echo "[start-all] Todos los servicios iniciados. Gateway expuesto en puerto 8080."

# Mantener el contenedor vivo
wait
