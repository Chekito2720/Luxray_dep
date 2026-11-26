package com.luxray.cursos.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sesión de una subida multipart iniciada por el servidor.Vincula el uploadId
 * con el alumno, la actividad y los metadatos que deben coincidir al finalizar.
 */
@Entity
@Table(name = "subidas_multipart",
       indexes = @Index(name = "idx_subidas_usuario_leccion",
                        columnList = "usuario_id,leccion_id"))
public class SubidaMultipart {

    @Id
    @Column(name = "upload_id", length = 512)
    private String uploadId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "leccion_id", nullable = false)
    private UUID leccionId;

    @Column(name = "clave", nullable = false, length = 500)
    private String clave;

    @Column(name = "nombre_archivo", nullable = false, length = 200)
    private String nombreArchivo;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    /** FOTO, PDF o VIDEO. */
    @Column(name = "tipo_archivo", nullable = false, length = 20)
    private String tipoArchivo;

    /** Número de partes que el servidor firmó al iniciar la subida. */
    @Column(name = "partes_esperadas", nullable = false)
    private Integer partesEsperadas;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public UUID getLeccionId() { return leccionId; }
    public void setLeccionId(UUID leccionId) { this.leccionId = leccionId; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }
    public String getTipoArchivo() { return tipoArchivo; }
    public void setTipoArchivo(String tipoArchivo) { this.tipoArchivo = tipoArchivo; }
    public Integer getPartesEsperadas() { return partesEsperadas; }
    public void setPartesEsperadas(Integer partesEsperadas) { this.partesEsperadas = partesEsperadas; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public LocalDateTime getExpiraEn() { return expiraEn; }
    public void setExpiraEn(LocalDateTime expiraEn) { this.expiraEn = expiraEn; }
}
