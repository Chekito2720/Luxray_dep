package com.luxray.cursos.repository;

import com.luxray.cursos.model.MensajeForo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MensajeForoRepository extends JpaRepository<MensajeForo, UUID> {

    /** Últimos 100 mensajes; el servicio los reordena del más antiguo al más reciente. */
    List<MensajeForo> findTop100ByCursoIdOrderByCreadoEnDesc(UUID cursoId);
}
