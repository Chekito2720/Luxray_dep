package com.luxray.cursos.service;

import com.luxray.cursos.dto.BusquedaResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * FAQ interno del dominio electricidad, espejo del FAQ público del frontend
 * pero consultable desde el buscador interno. SÓLO contiene preguntas del
 * giro de la aplicación (cursos de electricidad).
 */
public final class FaqElectrico {

    private FaqElectrico() {}

    public record Faq(String id, String pregunta, String respuesta, String categoria) {}

    public static final List<Faq> PREGUNTAS = List.of(
        new Faq("g1", "¿Qué es LuxRay y qué nos diferencia?",
                "Plataforma de capacitación especializada en electricidad residencial, comercial e industrial. Instructores certificados, contenido apegado a NOM-001-SEDE-2012 y NFPA 70E, enfoque práctico 60/40 y soporte en < 24h.",
                "general"),
        new Faq("g2", "¿Los cursos son 100% en línea o hay parte presencial?",
                "Cursos 100% en línea auto-gestionados con acceso de por vida. Talleres presenciales opcionales en CDMX y Monterrey para prácticas de tableros y medición.",
                "general"),
        new Faq("g3", "¿Necesito conocimientos previos para empezar?",
                "No. El curso Fundamentos de Electricidad (Básico) parte de cero: voltaje, corriente, resistencia, ley de Ohm, circuitos simples, seguridad básica. Existen evaluaciones de ubicación para saltar a Intermedio/Avanzado.",
                "general"),
        new Faq("c1", "¿Cuánto tiempo tengo para completar un curso?",
                "Acceso de por vida. Sin caducidad. Promedio finalización 4–8 semanas dedicando 5–8 h/semana.",
                "cursos"),
        new Faq("c2", "¿Incluyen material descargable (PDF, guías, plantillas)?",
                "Cada módulo incluye guía en PDF, hoja de fórmulas, checklists de seguridad, plantillas de cálculo (caída de tensión, dimensionamiento, protección) y archivos DWG/DXF cuando aplica.",
                "cursos"),
        new Faq("c3", "¿Hay evaluaciones? ¿Qué pasa si repruebo?",
                "Cada módulo cierra con quiz (mínimo 70% para avanzar) y un examen integrador final. Reintentos ilimitados sin penalización.",
                "cursos"),
        new Faq("c4", "¿Los cursos tienen validez oficial ante SEP o STPS?",
                "Constancias de valor curricular privado, cumplen DC-3 STPS. Aceptadas por colegios, constructoras y áreas de mantenimiento.",
                "cursos"),
        new Faq("p1", "¿Qué métodos de pago aceptan?",
                "Tarjeta, SPEI, OXXO Pay, PayPal y Kueski Pay a plazos. Facturación CFDI 4.0 con RFC.",
                "pagos"),
        new Faq("p2", "¿Hay becas o descuentos para estudiantes o empresas?",
                "20% estudiantes con credencial, 15% grupos ≥ 5, beca Electricista del Futuro 100% en Fundamentos, plan empresarial con panel de gestión.",
                "pagos"),
        new Faq("p3", "¿Política de reembolso?",
                "Garantía de 7 días o 20% de avance (lo que ocurra primero). 100% al método de pago original, sin preguntas.",
                "pagos"),
        new Faq("ce1", "¿Cómo y cuándo recibo mi constancia?",
                "Al aprobar el examen final (≥ 70%) se genera PDF automático con código QR de verificación, firma digital del instructor y envío por correo.",
                "certificados"),
        new Faq("ce2", "¿La constancia tiene validez internacional?",
                "Documento privado de capacitación. En México es ampliamente aceptado. Para uso extranjero se recomienda apostillar y acompañar del temario (proveemos gratis).",
                "certificados"),
        new Faq("ce3", "¿Puedo compartir mi constancia en LinkedIn?",
                "Sí. Botón Agregar a LinkedIn que pre-rellena institución, curso, fecha y credencial ID. Badge PNG incluido.",
                "certificados"),
        new Faq("t1", "¿Qué requisitos técnicos necesito?",
                "Navegador actualizado (Chrome, Edge, Firefox, Safari) e internet ≥ 3 Mbps. Responsive en móvil/tablet. App iOS/Android en beta.",
                "tecnico"),
        new Faq("t2", "¿Los videos se pueden descargar para ver offline?",
                "Por derechos de autor los videos no son descargables, aunque la app móvil beta permite caché temporal hasta 30 días.",
                "tecnico"),
        new Faq("t3", "¿Qué hago si tengo problemas de acceso o reproducción?",
                "Borrar caché/cookies, probar incógnito, desactivar bloqueadores de scripts. Si persiste, abrir ticket en Soporte o soporte@luxray.com con captura, navegador y hora. Respuesta en < 4h hábiles.",
                "tecnico")
    );

    public static List<Faq> buscar(String q) {
        String lower = q == null ? "" : q.toLowerCase();
        if (lower.isBlank()) return List.of();
        return PREGUNTAS.stream()
                .filter(f -> f.pregunta().toLowerCase().contains(lower)
                        || f.respuesta().toLowerCase().contains(lower)
                        || f.categoria().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }
}
