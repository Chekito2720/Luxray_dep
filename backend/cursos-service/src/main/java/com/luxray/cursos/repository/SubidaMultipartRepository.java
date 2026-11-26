package com.luxray.cursos.repository;

import com.luxray.cursos.model.SubidaMultipart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SubidaMultipartRepository extends JpaRepository<SubidaMultipart, String> {

    List<SubidaMultipart> findByExpiraEnBefore(LocalDateTime limite);

    long deleteByExpiraEnBefore(LocalDateTime limite);

    long deleteByUsuarioIdAndLeccionId(UUID usuarioId, UUID leccionId);
}
