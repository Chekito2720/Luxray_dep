package com.luxray.cursos.repository;

import com.luxray.cursos.model.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, UUID> {
    List<Inscripcion> findByUsuarioId(UUID usuarioId);
    List<Inscripcion> findByCursoId(UUID cursoId);
    Optional<Inscripcion> findByUsuarioIdAndCursoId(UUID usuarioId, UUID cursoId);
    long countByCursoId(UUID cursoId);
}
