# El repositorio oficial minio/minio ya no se publica en Docker Hub.
# Esta imagen comunitaria distribuye el último binario Community Edition.
FROM alpine/minio:RELEASE.2025-10-15T17-29-55Z

# La imagen corre como usuario minio (100:101); Render monta el trabajo en
# /data, que pertenece a root. Le damos propiedad antes de volver al usuario.
USER root
RUN mkdir -p /data && chown -R minio:minio /data
USER minio

EXPOSE 9000 9001
