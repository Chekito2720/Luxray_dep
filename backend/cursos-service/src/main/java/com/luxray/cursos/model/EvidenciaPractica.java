package com.luxray.cursos.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "evidencias_practica",
       indexes = {
           @Index(name = "idx_evid_usuario", columnList = "usuario_id"),
           @Index(name = "idx_evid_leccion", columnList = "leccion_id")
       })
public class EvidenciaPractica {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "leccion_id", nullable = false)
    private UUID leccionId;

    /** Entrega versionada a la que pertenece este archivo. */
    @Column(name = "entrega_id", nullable = false)
    private UUID entregaId;

    /** FOTO, PDF o VIDEO. */
    @Column(name = "tipo_archivo", nullable = false, length = 20)
    private String tipoArchivo = "FOTO";

    /** Número de versión de la entrega dentro de la actividad. */
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "nombre_archivo", nullable = false)
    private String nombreArchivo;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @Column(name = "clave", nullable = false)
    private String clave;

    @Column(name = "estado", nullable = false)
    private String estado = "COMPLETADA";

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    /* ── Revisión docente ── */

    /** Comentario del profesor sobre la práctica (visible para el alumno). */
    @Column(name = "retroalimentacion", columnDefinition = "TEXT")
    private String retroalimentacion;

    /** Usuario (PROFESOR/ADMIN) que revisó la evidencia. */
    @Column(name = "revisada_por")
    private UUID revisadaPor;

    /** Momento de la revisión. */
    @Column(name = "revisada_en")
    private LocalDateTime revisadaEn;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public UUID getLeccionId() { return leccionId; }
    public void setLeccionId(UUID leccionId) { this.leccionId = leccionId; }
    public UUID getEntregaId() { return entregaId; }
    public void setEntregaId(UUID entregaId) { this.entregaId = entregaId; }
    public String getTipoArchivo() { return tipoArchivo; }
    public void setTipoArchivo(String tipoArchivo) { this.tipoArchivo = tipoArchivo; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public String getRetroalimentacion() { return retroalimentacion; }
    public void setRetroalimentacion(String retroalimentacion) { this.retroalimentacion = retroalimentacion; }
    public UUID getRevisadaPor() { return revisadaPor; }
    public void setRevisadaPor(UUID revisadaPor) { this.revisadaPor = revisadaPor; }
    public LocalDateTime getRevisadaEn() { return revisadaEn; }
    public void setRevisadaEn(LocalDateTime revisadaEn) { this.revisadaEn = revisadaEn; }
}
