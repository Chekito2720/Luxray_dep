package com.luxray.cursos.service;

import com.luxray.cursos.config.ChatSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Difunde cada mensaje únicamente a las sesiones suscritas que siguen
 * teniendo acceso al curso. La revalidación ocurre en cada publicación para
 * que revocar una asignación o una inscripción retire el acceso en tiempo
 * real, sin esperar a que el cliente cierre la conexión.
 */
@Service
public class ForoBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(ForoBroadcastService.class);
    private static final int MAX_CONCURRENTES = 8;
    private static final long TIMEOUT_SEGUNDOS = 3;

    private final SimpMessagingTemplate messaging;
    private final ChatSessionRegistry sesiones;
    private final ForoService foro;
    private final ExecutorService executor = Executors.newFixedThreadPool(
            MAX_CONCURRENTES, t -> {
                Thread hilo = new Thread(t, "luxray-foro-fanout");
                hilo.setDaemon(true);
                return hilo;
            });

    public ForoBroadcastService(SimpMessagingTemplate messaging,
                                ChatSessionRegistry sesiones,
                                ForoService foro) {
        this.messaging = messaging;
        this.sesiones = sesiones;
        this.foro = foro;
    }

    public void difundir(UUID cursoId, Object mensaje) {
        long ahora = Instant.now().toEpochMilli();
        List<CompletableFuture<Void>> tareas = new ArrayList<>();
        for (ChatSessionRegistry.Participante participante : sesiones.participantes(cursoId)) {
            if (participante.expiraEn() <= ahora) continue;
            tareas.add(CompletableFuture.runAsync(() -> {
                if (!foro.puedeAcceder(participante.usuarioId(), participante.rol(), cursoId,
                        participante.token())) return;
                messaging.convertAndSendToUser(
                        participante.usuarioId().toString(),
                        "/queue/chat/" + cursoId,
                        mensaje);
            }, executor).orTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS));
        }
        for (CompletableFuture<Void> tarea : tareas) {
            try {
                tarea.join();
            } catch (RuntimeException ex) {
                // Un participante lento o con auth-service caído no debe
                // bloquear ni impedir la entrega a los demás suscritos.
                log.warn("No se pudo difundir un mensaje del foro: {}", ex.getMessage());
            }
        }
    }

    @PreDestroy
    void cerrar() {
        executor.shutdownNow();
    }
}
