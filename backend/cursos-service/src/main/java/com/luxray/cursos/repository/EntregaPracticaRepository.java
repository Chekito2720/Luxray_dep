package com.luxray.cursos.repository;

import com.luxray.cursos.model.EntregaPractica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntregaPracticaRepository extends JpaRepository<EntregaPractica, UUID> {

    List<EntregaPractica> findByUsuarioIdAndLeccionIdOrderByVersionDesc(UUID usuarioId, UUID leccionId);

    Optional<EntregaPractica> findTopByUsuarioIdAndLeccionIdOrderByVersionDesc(UUID usuarioId, UUID leccionId);

    List<EntregaPractica> findByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId);

    List<EntregaPractica> findByCursoIdInOrderByCreadoEnAsc(Collection<UUID> cursoIds);

    List<EntregaPractica> findByCursoIdInAndEstadoOrderByCreadoEnAsc(Collection<UUID> cursoIds, String estado);

    long countByEstado(String estado);
}
