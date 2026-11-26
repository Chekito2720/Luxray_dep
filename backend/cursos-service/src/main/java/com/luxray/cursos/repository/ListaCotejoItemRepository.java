package com.luxray.cursos.repository;

import com.luxray.cursos.model.ListaCotejoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListaCotejoItemRepository extends JpaRepository<ListaCotejoItem, UUID> {

    List<ListaCotejoItem> findByLeccionIdOrderByOrdenAsc(UUID leccionId);

    long countByLeccionId(UUID leccionId);

    void deleteByLeccionId(UUID leccionId);
}
