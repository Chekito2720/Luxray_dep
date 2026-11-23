# ── Emulador S3 (s3rver) ───────────────────────────────────────────
# Almacenamiento compatible S3 para las evidencias de práctica,
# sin instalar nada: reemplaza a AWS S3 / MinIO en desarrollo.
#
# El bucket "luxray-evidencias" se crea automáticamente al arrancar,
# con su política CORS (necesaria para que el navegador pueda subir
# las partes por PUT y leer el header ETag de respuesta).
#
# Credenciales: s3rver solo acepta su cuenta por defecto
#   accessKeyId / secretAccessKey = S3RVER / S3RVER
# (ver https://github.com/jamhall/s3rver). El backend usa esas mismas
# credenciales en S3_ACCESS_KEY / S3_SECRET_KEY.
#
# --no-vhost-buckets es OBLIGATORIO: sin él, s3rver intenta leer el bucket
# del propio hostname de la petición. Como dentro de Docker el servicio
# se llama "s3" (nunca un bucket válido), todas las llamadas del backend
# fallaban con "NoSuchBucket: s3". Con esta opción todo se resuelve
# siempre por ruta (path-style), que es lo que ya usamos.

FROM node:20-alpine
RUN npm install -g s3rver

COPY cors.xml /etc/s3rver/cors.xml

EXPOSE 9000
VOLUME /s3

# -a 0.0.0.0 : aceptar conexiones desde fuera del contenedor, para que el
#              navegador (localhost / IP del PC) pueda abrir las URL firmadas.
# --no-vhost-buckets : resolver bucket siempre por la ruta, no por el host.
# --configure-bucket : crea el bucket y le aplica cors.xml al arrancar.
CMD ["sh", "-c", "s3rver -p 9000 -a 0.0.0.0 -d /s3 --no-vhost-buckets --configure-bucket luxray-evidencias /etc/s3rver/cors.xml"]
