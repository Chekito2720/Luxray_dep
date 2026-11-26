package com.luxray.cursos.repository;

import com.luxray.cursos.model.CursoProfesor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CursoProfesorRepository extends JpaRepository<CursoProfesor, UUID> {

    List<CursoProfesor> findByCursoId(UUID cursoId);

    List<CursoProfesor> findByProfesorId(UUID profesorId);

    boolean existsByCursoIdAndProfesorId(UUID cursoId, UUID profesorId);

    long count();

    /**
     * Indica si el modo de asignaciones ya fue habilitado alguna vez. El
     * marcador es persistente para que borrar la última asignación no
     * devuelva acceso global a los profesores.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM configuracion_asignaciones_docentes c
                WHERE c.id = 1
                  AND c.asignaciones_configuradas = TRUE
            )
            """, nativeQuery = true)
    boolean existenAsignacionesConfiguradas();

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE configuracion_asignaciones_docentes
            SET asignaciones_configuradas = TRUE,
                actualizado_en = NOW()
            WHERE id = 1
            """, nativeQuery = true)
    int marcarAsignacionesConfiguradas();
}
