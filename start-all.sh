#!/bin/bash
# Arranque de todos los servicios del backend LuxRay

echo "[start-all] Iniciando servicios LuxRay..."

# 1. Elasticsearch (si está disponible como imagen externa o servicio en Railway)
# En Railway, Elasticsearch debe ser un servicio separado (add-on) o un contenedor aparte.
# Aquí asumimos que está disponible como servicio externo o se usará Railway Postgres + Elastic add-on.
# Por simplicidad, no lo arrancamos dentro del contenedor (Railway ofrece Elasticsearch como add-on).

# 2. Bases PostgreSQL (Railway ofrece Postgres como servicio; aquí asumimos variables de entorno)
# Si Railway provee DB como servicio, estas variables se configuran en el panel.

# 3. Microservicios (arrancados en background con orden: discovery -> servicios -> gateway)
java -XX:MaxRAMPercentage=75.0 -jar discovery-service.jar > /dev/stdout 2>&1 &
sleep 15

java -XX:MaxRAMPercentage=75.0 -jar auth-service.jar > /dev/stdout 2>&1 &
sleep 5

java -XX:MaxRAMPercentage=75.0 -jar cursos-service.jar > /dev/stdout 2>&1 &
sleep 5

java -XX:MaxRAMPercentage=75.0 -jar analytics-service.jar > /dev/stdout 2>&1 &
sleep 5

java -XX:MaxRAMPercentage=75.0 -jar search-service.jar > /dev/stdout 2>&1 &
sleep 5

# Gateway (último para que encuentre servicios en Eureka)
java -XX:MaxRAMPercentage=75.0 -jar api-gateway.jar > /dev/stdout 2>&1 &

echo "[start-all] Todos los servicios iniciados. Gateway expuesto en puerto 8080."

# Mantener el contenedor vivo
wait
