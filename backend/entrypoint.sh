#!/bin/sh
# entrypoint.sh - Selecciona qué microservicio levantar
# Uso: docker run -e SERVICE=auth-service ...

set -e

SERVICE=${SERVICE:-api-gateway}

# Render entrega DATABASE_URL como postgresql://usuario:password@host:puerto/bd.
# Los servicios Spring usan SPRING_DATASOURCE_URL; convertimos ese valor sin
# imprimir credenciales en los logs.
if [ -n "${DATABASE_URL:-}" ]; then
  sin_esquema="${DATABASE_URL#*://}"
  host_bd="${sin_esquema##*@}"
  credenciales="${sin_esquema%@*}"
  host_puerto="${host_bd%%/*}"
  base_datos="${host_bd#*/}"
  # Render puede omitir el puerto en connectionString; en ese caso URL es
  # postgresql://usuario:password@host/bd y el puerto Postgres es 5432.
  case "$host_puerto" in
    *:*)
      host="${host_puerto%:*}"
      puerto="${host_puerto##*:}"
      ;;
    *)
      host="$host_puerto"
      puerto=5432
      ;;
  esac
  usuario="${credenciales%%:*}"
  password="${credenciales#*:}"
  export PGHOST="$host"
  export PGPORT="$puerto"
  export PGUSER="$usuario"
  export PGPASSWORD="$password"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${host}:${puerto}/${base_datos}"
fi

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