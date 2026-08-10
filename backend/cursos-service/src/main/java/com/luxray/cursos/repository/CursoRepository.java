package com.luxray.cursos.repository;

import com.luxray.cursos.model.Curso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CursoRepository extends JpaRepository<Curso, UUID> {
    List<Curso> findByPublicadoTrue();

    @Query("""
            SELECT c FROM Curso c
            WHERE c.publicado = true
              AND (:nivel IS NULL OR c.nivel = :nivel)
              AND (:query IS NULL OR LOWER(c.titulo) LIKE %:query% OR LOWER(c.descripcion) LIKE %:query%)
            ORDER BY c.creadoEn DESC
            """)
    List<Curso> search(@Param("nivel") Curso.Nivel nivel, @Param("query") String query);
}
