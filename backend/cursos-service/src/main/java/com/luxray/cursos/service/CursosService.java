package com.luxray.cursos.service;

import com.luxray.common.dto.ApiResponse;
import com.luxray.common.exception.BusinessException;
import com.luxray.common.exception.ResourceNotFoundException;
import com.luxray.common.jwt.JwtTokenProvider;
import com.luxray.cursos.dto.BusquedaResponse;
import com.luxray.cursos.dto.CursoRequest;
import com.luxray.cursos.dto.CursoResponse;
import com.luxray.cursos.dto.InscripcionResponse;
import com.luxray.cursos.model.Curso;
import com.luxray.cursos.model.Inscripcion;
import com.luxray.cursos.model.Leccion;
import com.luxray.cursos.model.ProgresoLeccion;
import com.luxray.cursos.repository.CursoRepository;
import com.luxray.cursos.repository.InscripcionRepository;
import com.luxray.cursos.repository.LeccionRepository;
import com.luxray.cursos.repository.ProgresoLeccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CursosService {

    private final CursoRepository cursos;
    private final LeccionRepository lecciones;
    private final InscripcionRepository inscripciones;
    private final ProgresoLeccionRepository progreso;

    private final JwtTokenProvider jwt;

    public CursosService(CursoRepository cursos,
                         LeccionRepository lecciones,
                         InscripcionRepository inscripciones,
                         ProgresoLeccionRepository progreso,
                         JwtTokenProvider jwt) {
        this.cursos = cursos;
        this.lecciones = lecciones;
        this.inscripciones = inscripciones;
        this.progreso = progreso;
        this.jwt = jwt;
    }

    /* ───── Listado / Búsqueda ───── */
    public ApiResponse<List<CursoResponse>> listar(String nivelStr, String query) {
        Curso.Nivel nivel = null;
        if (nivelStr != null && !nivelStr.isBlank() && !"Todos".equalsIgnoreCase(nivelStr)) {
            try {
                nivel = Curso.Nivel.valueOf(nivelStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Nivel de curso inválido: " + nivelStr);
            }
        }
        String q = (query != null && !query.isBlank()) ? query.toLowerCase() : null;
        List<Curso> resultado = cursos.search(nivel, q);
        List<CursoResponse> data = resultado.stream().map(CursoResponse::from).toList();
        return ApiResponse.ok(data);
    }

    /* ───── Búsqueda interna (cursos, lecciones, conceptos del giro) ───── */
    private static final int MAX_RESULTADOS_POR_TIPO = 20;

    public ApiResponse<BusquedaResponse> buscar(String query) {
        String q = (query == null) ? "" : query.toLowerCase().trim();
        if (q.isEmpty() || q.length() < 2) {
            return ApiResponse.ok(new BusquedaResponse(query == null ? "" : query, 0,
                    List.of(), List.of(), List.of(), List.of()));
        }

        // 1) Cursos publicados que coincidan por título/descripción.
        List<Curso> cursosMatch = cursos.search(null, q);
        List<BusquedaResponse.CursoHit> cursoHits = cursosMatch.stream()
                .limit(MAX_RESULTADOS_POR_TIPO)
                .map(c -> new BusquedaResponse.CursoHit(
                        c.getId().toString(),
                        c.getTitulo(),
                        truncar(c.getDescripcion(), 160),
                        capitalizar(c.getNivel().name()),
                        c.getNivel().name().toLowerCase(),
                        c.getInstructor(),
                        snippet(c.getTitulo() + " " + c.getDescripcion(), q)
                ))
                .toList();

        // 2) Lecciones cuyo título/descripción/sección coinciden.
        List<Leccion> leccionesMatch = lecciones.search(q);
        // Indexa cursos para resolver título sin N+1.
        Map<UUID, String> tituloCursoPorId = cursosMatch.stream()
                .collect(Collectors.toMap(Curso::getId, Curso::getTitulo, (a, b) -> a));
        Map<UUID, String> todosLosCursos = new HashMap<>(tituloCursoPorId);
        if (leccionesMatch.size() > tituloCursoPorId.size()) {
            cursos.findAll().forEach(c -> todosLosCursos.putIfAbsent(c.getId(), c.getTitulo()));
        }
        List<BusquedaResponse.LeccionHit> leccionHits = leccionesMatch.stream()
                .limit(MAX_RESULTADOS_POR_TIPO)
                .map(l -> new BusquedaResponse.LeccionHit(
                        l.getId().toString(),
                        l.getCursoId().toString(),
                        todosLosCursos.getOrDefault(l.getCursoId(), "Curso eliminado"),
                        l.getTitulo(),
                        truncar(l.getDescripcion(), 160),
                        l.getTipo() == null ? null : l.getTipo().name(),
                        snippet(l.getTitulo() + " " + l.getDescripcion() + " " + l.getSeccionTitulo(), q)
                ))
                .toList();

        // 3) Conceptos del glosario electricidad (término, sinónimos, temas).
        List<BusquedaResponse.ConceptoHit> conceptoHits = GlosarioElectrico.CONCEPTOS.stream()
                .filter(c -> contiene(c.termino(), c.sinonimos(), c.temas(), c.definicion(), q))
                .map(c -> new BusquedaResponse.ConceptoHit(
                        c.termino(), c.definicion(), c.sinonimos(), c.temas(),
                        snippet(c.termino() + " " + c.definicion(), q)
                ))
                .limit(MAX_RESULTADOS_POR_TIPO)
                .toList();

        // 4) FAQ eléctrico embebido (estático, propio del dominio).
        List<BusquedaResponse.FaqHit> faqHits = FaqElectrico.buscar(q).stream()
                .limit(MAX_RESULTADOS_POR_TIPO)
                .map(f -> new BusquedaResponse.FaqHit(
                        f.id(), f.pregunta(), truncar(f.respuesta(), 220),
                        f.categoria(),
                        snippet(f.pregunta() + " " + f.respuesta(), q)
                ))
                .toList();

        long total = cursoHits.size() + leccionHits.size() + conceptoHits.size() + faqHits.size();
        BusquedaResponse resp = new BusquedaResponse(query, total,
                cursoHits, leccionHits, conceptoHits, faqHits);
        return ApiResponse.ok(resp);
    }

    private static boolean contiene(String termino, List<String> sinonimos,
                                    List<String> temas, String definicion, String q) {
        if (termino != null && termino.toLowerCase().contains(q)) return true;
        if (definicion != null && definicion.toLowerCase().contains(q)) return true;
        if (sinonimos != null)
            for (String s : sinonimos) if (s != null && s.toLowerCase().contains(q)) return true;
        if (temas != null)
            for (String t : temas) if (t != null && t.toLowerCase().contains(q)) return true;
        return false;
    }

    private static String snippet(String texto, String q) {
        if (texto == null) return "";
        String lower = texto.toLowerCase();
        int idx = lower.indexOf(q);
        if (idx < 0) return "";
        int ini = Math.max(0, idx - 40);
        int fin = Math.min(texto.length(), idx + q.length() + 60);
        String pref = ini > 0 ? "…" : "";
        String suff = fin < texto.length() ? "…" : "";
        return pref + texto.substring(ini, fin) + suff;
    }

    private static String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String capitalizar(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    /* ───── Detalle ───── */
    public ApiResponse<CursoDetalleResponse> detalle(UUID id) {
        Curso c = cursos.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curso no encontrado"));
        List<Leccion> ls = lecciones.findByCursoIdOrderByOrdenAsc(id);
        List<LeccionDetalleDTO> leccionesDTO = ls.stream().map(LeccionDetalleDTO::from).toList();
        return ApiResponse.ok(new CursoDetalleResponse(CursoResponse.from(c), leccionesDTO));
    }

    /* ───── Inscripción ───── */
    @Transactional
    public ApiResponse<InscripcionResponse> inscribir(String authHeader, UUID cursoId) {
        UUID usuarioId = resolverUsuario(authHeader);
        Curso c = cursos.findById(cursoId)
                .orElseThrow(() -> new ResourceNotFoundException("Curso no encontrado"));

        Inscripcion insc = inscripciones.findByUsuarioIdAndCursoId(usuarioId, cursoId)
                .orElseGet(() -> {
                    Inscripcion i = new Inscripcion();
                    i.setUsuarioId(usuarioId);
                    i.setCursoId(cursoId);
                    return i;
                });
        if (insc.getInscritoEn() == null) insc.setInscritoEn(LocalDateTime.now());
        inscripciones.save(insc);

        int total = lecciones.findByCursoIdOrderByOrdenAsc(cursoId).size();
        if (total == 0) total = c.getLecciones();
        return ApiResponse.ok(InscripcionResponse.from(insc, c.getTitulo(), total));
    }

    /* ───── Mis cursos (progreso) ───── */
    public ApiResponse<List<InscripcionResponse>> misCursos(String authHeader) {
        UUID usuarioId = resolverUsuario(authHeader);
        List<Inscripcion> lista = inscripciones.findByUsuarioId(usuarioId);
        List<InscripcionResponse> data = lista.stream().map(i -> {
            Curso c = cursos.findById(i.getCursoId()).orElseThrow();
            int total = lecciones.findByCursoIdOrderByOrdenAsc(i.getCursoId()).size();
            return InscripcionResponse.from(i, c.getTitulo(), total);
        }).toList();
        return ApiResponse.ok(data);
    }

    /* ───── Marcar lección completada ───── */
    @Transactional
    public ApiResponse<Void> marcarLeccionCompletada(String authHeader, UUID leccionId) {
        UUID usuarioId = resolverUsuario(authHeader);
        Leccion leccion = lecciones.findById(leccionId)
                .orElseThrow(() -> new ResourceNotFoundException("Lección no encontrada"));

        ProgresoLeccion p = progreso.findByUsuarioIdAndLeccionId(usuarioId, leccionId)
                .orElseGet(() -> {
                    ProgresoLeccion np = new ProgresoLeccion();
                    np.setUsuarioId(usuarioId);
                    np.setLeccionId(leccionId);
                    return np;
                });
        p.setCompletada(true);
        p.setCompletadoEn(LocalDateTime.now());
        progreso.save(p);

        // Recalcular porcentaje de inscripción
        Inscripcion insc = inscripciones.findByUsuarioIdAndCursoId(usuarioId, leccion.getCursoId())
                .orElse(null);
        if (insc != null) {
            int hechas = progreso.findByUsuarioId(usuarioId).stream()
                    .filter(pr -> {
                        try {
                            return lecciones.findById(pr.getLeccionId())
                                    .map(l -> l.getCursoId().equals(insc.getCursoId()))
                                    .orElse(false);
                        } catch (Exception e) { return false; }
                    })
                    .toList().size();
            insc.setLeccionesCompletadas(hechas);
            int total = lecciones.findByCursoIdOrderByOrdenAsc(insc.getCursoId()).size();
            int pct = total == 0 ? 0 : (int) Math.round(hechas * 100.0 / total);
            insc.setPorcentaje(pct);
            // Determinar próxima
            lecciones.findByCursoIdOrderByOrdenAsc(insc.getCursoId()).stream()
                    .filter(l -> !progreso.findByUsuarioIdAndLeccionId(usuarioId, l.getId())
                            .map(ProgresoLeccion::getCompletada).orElse(false))
                    .findFirst()
                    .ifPresent(l -> insc.setProximaLeccion(l.getTitulo()));
            inscripciones.save(insc);
        }
        return ApiResponse.ok();
    }

    /* ───── CRUD Admin ───── */
    @Transactional
    public ApiResponse<CursoResponse> crear(CursoRequest req) {
        Curso c = req.toEntity();
        Curso saved = cursos.save(c);
        return ApiResponse.ok(CursoResponse.from(saved));
    }

    @Transactional
    public ApiResponse<CursoResponse> actualizar(UUID id, CursoRequest req) {
        Curso c = cursos.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curso no encontrado"));
        req.applyTo(c);
        return ApiResponse.ok(CursoResponse.from(cursos.save(c)));
    }

    @Transactional
    public ApiResponse<Void> eliminar(UUID id) {
        if (!cursos.existsById(id)) {
            throw new ResourceNotFoundException("Curso no encontrado");
        }
        inscripciones.findByCursoId(id).forEach(inscripciones::delete);
        progreso.findAll().stream()
                .filter(p -> true) // el filtro real se hace via lecciones del curso
                .toList();
        cursos.deleteById(id);
        return ApiResponse.ok();
    }

    @Transactional
    public ApiResponse<CursoResponse> togglePublicado(UUID id) {
        Curso c = cursos.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curso no encontrado"));
        c.setPublicado(!c.getPublicado());
        return ApiResponse.ok(CursoResponse.from(cursos.save(c)));
    }

    public ApiResponse<List<CursoResponse>> listarTodosAdmin() {
        List<Curso> todos = cursos.findAll();
        List<CursoResponse> data = todos.stream().map(CursoResponse::from).toList();
        return ApiResponse.ok(data);
    }

    /* ── Auxiliares ── */
    private UUID resolverUsuario(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException("Token de autorización requerido");
        }
        String token = authHeader.substring(7);
        try {
            var claims = jwt.parse(token);
            return UUID.fromString(claims.get("uid", String.class));
        } catch (Exception e) {
            throw new BusinessException("Token inválido o expirado");
        }
    }

    /* ── DTO interno ── */
    public record CursoDetalleResponse(CursoResponse curso, List<LeccionDetalleDTO> lecciones) { }

    public record LeccionDetalleDTO(
        UUID id,
        UUID cursoId,
        String seccionId,
        String seccionTitulo,
        String titulo,
        String duracion,
        Leccion.Tipo tipo,
        String videoUrl,
        String descripcion,
        String contenido,
        List<PreguntaDTO> preguntas,
        Integer orden
    ) {
        public static LeccionDetalleDTO from(Leccion l) {
            List<PreguntaDTO> preg = null;
            if (l.getPreguntas() != null && !l.getPreguntas().isBlank()) {
                try {
                    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    preg = mapper.readValue(l.getPreguntas(),
                        mapper.getTypeFactory().constructCollectionType(List.class, PreguntaDTO.class));
                } catch (Exception ignored) { }
            }
            return new LeccionDetalleDTO(
                l.getId(), l.getCursoId(), l.getSeccionId(), l.getSeccionTitulo(),
                l.getTitulo(), l.getDuracion(), l.getTipo(), l.getVideoUrl(),
                l.getDescripcion(), l.getContenido(), preg, l.getOrden()
            );
        }
    }

    public record PreguntaDTO(String id, String texto, List<String> opciones, String respuestaCorrecta) { }
}
