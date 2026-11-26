# Imagen pública de MinIO para el almacenamiento S3 de la demostración.
# Render construye este Dockerfile en lugar de resolver la imagen por nombre.
# El repositorio oficial minio/minio ya no se publica en Docker Hub.
# Esta imagen comunitaria distribuye el último binario Community Edition.
FROM alpine/minio:RELEASE.2025-10-15T17-29-55Z

EXPOSE 9000 9001
