package com.luxray.analytics.repository;

import com.luxray.analytics.model.AnalyticsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsRepository extends JpaRepository<AnalyticsSnapshot, UUID> {
    List<AnalyticsSnapshot> findByUsuarioIdOrderByFechaDesc(UUID usuarioId);
}
