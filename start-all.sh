#!/bin/bash
# Arranque de todos los servicios del backend LuxRay

echo "[start-all] Iniciando servicios LuxRay..."

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
