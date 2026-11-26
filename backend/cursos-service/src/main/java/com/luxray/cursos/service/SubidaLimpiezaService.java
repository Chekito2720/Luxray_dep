package com.luxray.cursos.service;

import com.luxray.cursos.model.SubidaMultipart;
import com.luxray.cursos.repository.SubidaMultipartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elimina de forma periódica las sesiones multipart caducadas y los objetos
 * que pudieron quedar sin una entrega asociada. Si el almacenamiento no
 * responde, la sesión se conserva para reintentar en la siguiente ejecución.
 */
@Service
public class SubidaLimpiezaService {

    private static final Logger log = LoggerFactory.getLogger(SubidaLimpiezaService.class);

    private final SubidaMultipartRepository subidas;
    private final StoragePresignService storage;

    public SubidaLimpiezaService(SubidaMultipartRepository subidas,
                                 StoragePresignService storage) {
        this.subidas = subidas;
        this.storage = storage;
    }

    @Scheduled(
            fixedDelayString = "${luxray.storage.limpieza-intervalo-ms:900000}",
            initialDelayString = "${luxray.storage.limpieza-inicio-ms:60000}")
    @Transactional
    public void limpiarSesionesCaducadas() {
        List<SubidaMultipart> caducadas = subidas.findByExpiraEnBefore(LocalDateTime.now());
        for (SubidaMultipart sesion : caducadas) {
            if (!limpiarAlmacenamiento(sesion)) continue;
            subidas.delete(sesion);
            log.info("Sesión multipart caducada eliminada: {}", sesion.getUploadId());
        }
    }

    private boolean limpiarAlmacenamiento(SubidaMultipart sesion) {
        boolean limpio = true;
        try {
            storage.abortarMultipart(sesion.getClave(), sesion.getUploadId());
        } catch (RuntimeException ex) {
            limpio = false;
            log.warn("No se pudo abortar la subida multipart {}: {}",
                    sesion.getUploadId(), ex.getMessage());
        }
        try {
            storage.eliminarObjeto(sesion.getClave());
        } catch (RuntimeException ex) {
            limpio = false;
            log.warn("No se pudo eliminar el objeto huérfano {}: {}",
                    sesion.getClave(), ex.getMessage());
        }
        return limpio;
    }
}
