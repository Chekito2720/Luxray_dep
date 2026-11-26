# Imagen pública de MinIO para el almacenamiento S3 de la demostración.
# Render construye este Dockerfile en lugar de resolver la imagen por nombre.
FROM minio/minio:latest

EXPOSE 9000 9001
