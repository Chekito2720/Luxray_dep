package com.luxray.cursos.repository;

import com.luxray.cursos.model.EvidenciaPractica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvidenciaPracticaRepository extends JpaRepository<EvidenciaPractica, UUID> {

    List<EvidenciaPractica> findByUsuarioIdAndLeccionIdOrderByCreadoEnDesc(UUID usuarioId, UUID leccionId);

    List<EvidenciaPractica> findByEntregaIdOrderByCreadoEnAsc(UUID entregaId);

    /* ── Revisión docente ── */

    /** Cola de revisión del profesor: un estado concreto, la más antigua primero. */
    List<EvidenciaPractica> findByEstadoOrderByCreadoEnAsc(String estado);

    /** Todas las evidencias del plantel (historial completo), la más antigua primero. */
    List<EvidenciaPractica> findAllByOrderByCreadoEnAsc();

    /** Expediente del alumno: todas sus evidencias, la más reciente primero. */
    List<EvidenciaPractica> findByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId);
}
