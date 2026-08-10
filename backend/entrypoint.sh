#!/bin/sh
# entrypoint.sh - Selecciona qué microservicio levantar
# Uso: docker run -e SERVICE=auth-service ...

set -e

SERVICE=${SERVICE:-api-gateway}

case "$SERVICE" in
  auth-service)
    echo "🚀 Iniciando Auth Service en puerto 8081"
    exec java -XX:MaxRAMPercentage=75.0 -jar auth-service.jar
    ;;
  cursos-service)
    echo "🚀 Iniciando Cursos Service en puerto 8082"
    exec java -XX:MaxRAMPercentage=75.0 -jar cursos-service.jar
    ;;
  analytics-service)
    echo "🚀 Iniciando Analytics Service en puerto 8083"
    exec java -XX:MaxRAMPercentage=75.0 -jar analytics-service.jar
    ;;
  search-service)
    echo "🚀 Iniciando Search Service en puerto 8084"
    exec java -XX:MaxRAMPercentage=75.0 -jar search-service.jar
    ;;
  api-gateway)
    echo "🚀 Iniciando API Gateway en puerto 8080"
    exec java -XX:MaxRAMPercentage=75.0 -jar api-gateway.jar
    ;;
  discovery-service)
    echo "🚀 Iniciando Discovery Service (Eureka) en puerto 8761"
    exec java -XX:MaxRAMPercentage=75.0 -jar discovery-service.jar
    ;;
  *)
    echo "❌ SERVICE desconocido: $SERVICE"
    echo "Valores válidos: auth-service, cursos-service, analytics-service, search-service, api-gateway, discovery-service"
    exit 1
    ;;
esac