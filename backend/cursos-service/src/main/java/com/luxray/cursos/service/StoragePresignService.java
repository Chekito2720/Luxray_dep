package com.luxray.cursos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gestiona el ciclo de vida de subidas Multipart Upload en S3 (o MinIO)
 * firmando URLs prefirmadas para que el cliente móvil suba directamente
 * cada parte al almacenamiento, sin pasar el archivo por el backend.
 *
 * <p>Dos endpoints distintos, un mismo bucket:</p>
 * <ul>
 *   <li>{@code luxray.storage.endpoint} (S3_ENDPOINT): interno, solo lo usa
 *       el backend (p. ej. {@code http://s3:9000} en Docker).</li>
 *   <li>{@code luxray.storage.public-endpoint} (S3_PUBLIC_ENDPOINT): con el
 *       que se FIRMAN las URLs que abrirá el navegador. Debe resolver desde
 *       fuera de Docker ({@code http://localhost:9000} o
 *       {@code http://<IP-del-PC>:9000}).</li>
 * </ul>
 * Si {@code S3_PUBLIC_ENDPOINT} no se define se usa {@code S3_ENDPOINT}
 * (válido para AWS S3 real, que ya es público).
 */
@Service
public class StoragePresignService {

    private static final Logger log = LoggerFactory.getLogger(StoragePresignService.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final String prefix;
    private volatile boolean bucketVerificado;

    public StoragePresignService(
            @Value("${luxray.storage.endpoint:}") String endpoint,
            @Value("${luxray.storage.public-endpoint:}") String publicEndpoint,
            @Value("${luxray.storage.region:us-east-1}") String region,
            @Value("${luxray.storage.bucket:luxray-evidencias}") String bucket,
            @Value("${luxray.storage.access-key:}") String accessKey,
            @Value("${luxray.storage.secret-key:}") String secretKey,
            @Value("${luxray.storage.path-style:true}") boolean pathStyle,
            @Value("${luxray.storage.prefix:evidencias}") String prefix) {

        this.bucket = bucket;
        this.prefix = prefix;

        var creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));

        // Endpoint que usa el BACKEND para hablar con el bucket:
        // en Docker es un nombre de la red interna (p. ej. http://s3:9000),
        // que el navegador jamás podría resolver.
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException(
                    "S3_ENDPOINT es obligatorio: define el endpoint interno del bucket");
        }
        URI endpointInterno = URI.create(endpoint);

        // Endpoint con el que se FIRMAN las URLs que abrirá el cliente
        // (navegador / PWA). Debe ser una dirección alcanzable desde fuera
        // de la red de Docker: http://localhost:9000 desde el mismo PC,
        // http://<IP-de-tu-PC>:9000 desde el teléfono por WiFi.
        // Si no se configura, se usa el interno (correcto solo para AWS real).
        URI endpointPublico = (publicEndpoint == null || publicEndpoint.isBlank())
                ? endpointInterno
                : URI.create(publicEndpoint);

        // Trazas al arrancar: un endpoint público equivocado rompe las subidas
        // y no se ve el motivo. Dejar claro qué se está usando y avisar de los
        // errores típicos (una variable de entorno vieja en la consola, un
        // puerto que no es el del bucket, o un nombre solo resoluble en Docker).
        log.info("S3 endpoint interno (backend): {}", endpointInterno);
        log.info("S3 endpoint público (URLs para el navegador): {}", endpointPublico);

        String hostPublico = endpointPublico.getHost();
        if (hostPublico != null && !hostPublico.contains(".")
                && !hostPublico.equalsIgnoreCase("localhost")) {
            log.warn("El endpoint público '{}' no podrá resolverlo el navegador: "
                    + "solo existe dentro de la red de Docker. Usa http://localhost:9000 "
                    + "o http://<IP-de-tu-PC>:9000 (S3_PUBLIC_ENDPOINT).", endpointPublico);
        }

        var s3Builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(creds)
                .endpointOverride(endpointInterno)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());

        var presignerBuilder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(creds)
                .endpointOverride(endpointPublico)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());

        this.s3 = s3Builder.build();
        this.presigner = presignerBuilder.build();
    }

    public record ParteUploadDTO(int numeroParte, String url, long tamanoBytes) { }

    public record InitMultipartDTO(
            String uploadId,
            String clave,
            long tamanoParte,
            List<ParteUploadDTO> partes) { }

    /**
     * Crea el bucket si el almacenamiento se ha reiniciado con un directorio
     * vacío. Sólo se comprueba una vez por instancia; los errores se propagan
     * para que el cliente pueda reintentar la subida.
     */
    private void asegurarBucket() {
        if (bucketVerificado) return;
        synchronized (this) {
            if (bucketVerificado) return;
            try {
                s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (software.amazon.awssdk.services.s3.model.S3Exception ex) {
                if (ex.statusCode() != 404) throw ex;
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            }
            bucketVerificado = true;
        }
    }

    /** Inicia el multipart y devuelve una URL prefirmada por cada parte. */
    public InitMultipartDTO iniciarMultipart(String clave, String contentType,
                                             long tamanoBytes, int tamanoParte) {
        asegurarBucket();
        CreateMultipartUploadResponse create = s3.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(clave)
                        .contentType(contentType)
                        .build());

        long restante = tamanoBytes;
        int numeroParte = 1;
        List<ParteUploadDTO> partes = new ArrayList<>();
        while (restante > 0) {
            long tamanoEstaParte = Math.min(restante, tamanoParte);
            final int numParte = numeroParte;

            PresignedUploadPartRequest pre = presigner.presignUploadPart(
                    UploadPartPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(30))
                            .uploadPartRequest(u -> u
                                    .bucket(bucket)
                                    .key(clave)
                                    .uploadId(create.uploadId())
                                    .partNumber(numParte)
                                    .contentLength(tamanoEstaParte))
                            .build());

            partes.add(new ParteUploadDTO(numeroParte, pre.url().toString(), tamanoEstaParte));
            restante -= tamanoEstaParte;
            numeroParte++;
        }

        return new InitMultipartDTO(create.uploadId(), clave, tamanoParte, partes);
    }

    /** Confirma el multipart con los ETags que el cliente obtuvo de cada parte. */
    public void completarMultipart(String clave, String uploadId, List<ParteConEtag> partes) {
        List<CompletedPart> completadas = partes.stream()
                .map(p -> CompletedPart.builder()
                        .partNumber(p.numeroParte())
                        .eTag(p.etag())
                        .build())
                .toList();

        s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(clave)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(completadas)
                        .build())
                .build());
    }

    /**
     * Aborta un multipart (se descartan las partes ya subidas).
     *
     * <p>Tolerancia con emuladores: s3rver (el fake S3 de desarrollo) no
     * implementa {@code AbortMultipartUpload} y responde 405. Para no romper
     * el flujo del cliente (que solo quiere liberar recursos) se degrada a
     * un aviso; en AWS S3 real el abort funciona y no llega aquí el error.</p>
     */
    public void abortarMultipart(String clave, String uploadId) {
        try {
            s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(clave)
                    .uploadId(uploadId)
                    .build());
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (e.statusCode() == 405) {
                // Storage no soporta abortar multipart: se ignora.
                return;
            }
            if (e.statusCode() == 404) {
                // La subida ya fue completada o el multipart ya no existe.
                return;
            }
            throw e;
        }
    }

    /** URL prefirmada de lectura (para mostrar la evidencia). */
    public String urlLectura(String clave) {
        PresignedGetObjectRequest pre = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofHours(1))
                        .getObjectRequest(g -> g.bucket(bucket).key(clave))
                        .build());
        return pre.url().toString();
    }

    public record ParteConEtag(int numeroParte, String etag) { }

    public record ObjetoMetadata(long tamanoBytes, String contentType) { }

    /** Comprueba el objeto ya materializado después de completar el multipart. */
    public ObjetoMetadata verificarObjeto(String clave) {
        HeadObjectResponse head = s3.headObject(software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder()
                .bucket(bucket)
                .key(clave)
                .build());
        long tamano = head.contentLength() == null ? -1 : head.contentLength();
        return new ObjetoMetadata(tamano, head.contentType());
    }

    /** Lee sólo los primeros bytes para validar la firma binaria del archivo. */
    public byte[] leerPrefijo(String clave, int maxBytes) {
        int limite = Math.max(1, maxBytes);
        try (var objeto = s3.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(clave)
                .range("bytes=0-" + (limite - 1))
                .build())) {
            return objeto.readNBytes(limite);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException("No se pudo leer el contenido del archivo", ex);
        }
    }

    /** Elimina un objeto que no superó la validación de contenido. */
    public void eliminarObjeto(String clave) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(clave)
                .build());
    }

    public String nuevaClave(UUID usuarioId, UUID leccionId, String nombreArchivo) {
        String limpio = nombreArchivo.replaceAll("[^a-zA-Z0-9._-]", "_");
        // El UUID hace que cada versión sea inmutable: dos archivos con el
        // mismo nombre nunca apuntan al mismo objeto S3.
        return String.format("%s/%s/%s/%s-%s", prefix, usuarioId, leccionId,
                UUID.randomUUID(), limpio);
    }

    @PreDestroy
    void cerrar() {
        if (s3 != null) s3.close();
        if (presigner != null) presigner.close();
    }
}
