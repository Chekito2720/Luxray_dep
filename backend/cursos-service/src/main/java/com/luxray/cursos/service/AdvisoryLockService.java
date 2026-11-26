package com.luxray.cursos.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Bloqueos de transacción de PostgreSQL para serializar operaciones que deben
 * comprobar y escribir un mismo recurso (entregas, progreso, asignaciones y
 * listas de cotejo). El bloqueo se libera automáticamente al terminar la
 * transacción que lo adquirió.
 */
@Service
public class AdvisoryLockService {

    private static final int DOMINIO_ENTREGA = 101;
    private static final int DOMINIO_ASIGNACION = 102;
    private static final int DOMINIO_COTEJO = 103;

    private final EntityManager entityManager;

    public AdvisoryLockService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void bloquearEntrega(UUID usuarioId, UUID leccionId) {
        bloquear(DOMINIO_ENTREGA, usuarioId, leccionId);
    }

    public void bloquearAsignaciones(UUID cursoId) {
        bloquear(DOMINIO_ASIGNACION, cursoId);
    }

    public void bloquearCotejo(UUID leccionId) {
        bloquear(DOMINIO_COTEJO, leccionId);
    }

    private void bloquear(int dominio, UUID... ids) {
        if (entityManager == null) return;
        int clave = dominio;
        for (UUID id : ids) {
            if (id != null) clave = 31 * clave + id.hashCode();
        }
        Query query = entityManager.createNativeQuery(
                "SELECT pg_advisory_xact_lock(:dominio, :clave)");
        query.setParameter("dominio", dominio);
        query.setParameter("clave", clave);
        query.getSingleResult();
    }
}
