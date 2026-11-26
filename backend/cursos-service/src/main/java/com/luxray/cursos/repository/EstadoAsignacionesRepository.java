package com.luxray.cursos.repository;

import com.luxray.cursos.model.EstadoAsignaciones;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EstadoAsignacionesRepository extends JpaRepository<EstadoAsignaciones, Integer> {

    /** TRUE cuando un administrador ya guardó una configuración de asignaciones. */
    @Query(value = """
            SELECT COALESCE(
                (SELECT inicializado FROM asignaciones_estado WHERE id = 1),
                FALSE)
            """, nativeQuery = true)
    boolean estaInicializado();

    @Modifying
    @Query(value = "UPDATE asignaciones_estado SET inicializado = TRUE WHERE id = 1",
           nativeQuery = true)
    void marcarInicializado();
}
