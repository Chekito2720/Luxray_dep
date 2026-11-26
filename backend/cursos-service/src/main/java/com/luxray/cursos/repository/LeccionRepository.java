package com.luxray.cursos.repository;

import com.luxray.cursos.model.Leccion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeccionRepository extends JpaRepository<Leccion, UUID> {
    List<Leccion> findByCursoIdOrderByOrdenAsc(UUID cursoId);

    /** Bloquea la lección para serializar el reemplazo de su lista de cotejo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Leccion l WHERE l.id = :id")
    Optional<Leccion> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT l FROM Leccion l
            WHERE :query IS NULL
               OR LOWER(l.titulo) LIKE %:query%
              OR LOWER(l.descripcion) LIKE %:query%
              OR LOWER(l.seccionTitulo) LIKE %:query%
            ORDER BY l.cursoId, l.orden
            """)
    List<Leccion> search(@Param("query") String query);
}
