package com.luxray.cursos.repository;

import com.luxray.cursos.model.ProgresoLeccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgresoLeccionRepository extends JpaRepository<ProgresoLeccion, UUID> {
    List<ProgresoLeccion> findByUsuarioId(UUID usuarioId);
    Optional<ProgresoLeccion> findByUsuarioIdAndLeccionId(UUID usuarioId, UUID leccionId);
}
