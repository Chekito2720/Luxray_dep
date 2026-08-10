package com.luxray.cursos.service;

import com.luxray.cursos.dto.BusquedaResponse;

import java.util.List;

/**
 * Glosario de conceptos del giro de la aplicación (cursos de electricidad).
 * Fuente de verdad estática e in-memory: mantiene el buscador interno
 * acotado al dominio y permite resolver consultas conceptuales como
 * "ohm", "cortocircuito", "tierra", etc., sin salir del sitio.
 */
public final class GlosarioElectrico {

    private GlosarioElectrico() {}

    public static final List<BusquedaResponse.ConceptoHit> CONCEPTOS = List.of(
        concepto("Corriente eléctrica",
                "Flujo de electrones que circula por un conductor. Se mide en amperios (A).",
                List.of("intensidad", "amperaje", "flujo de carga"),
                List.of("fundamentos", "ley de ohm")),
        concepto("Voltaje",
                "Diferencia de potencial eléctrico entre dos puntos. Se mide en voltios (V).",
                List.of("tensión", "diferencia de potencial", "ddp"),
                List.of("fundamentos", "circuitos")),
        concepto("Resistencia eléctrica",
                "Oposición que ofrece un material al paso de la corriente. Se mide en ohms (Ω).",
                List.of("impedancia", "ohm"),
                List.of("ley de ohm", "conductores")),
        concepto("Ley de Ohm",
                "V = I · R. Relaciona voltaje (V), corriente (I) y resistencia (R) en un circuito.",
                List.of("V=IR"),
                List.of("fundamentos", "cálculo")),
        concepto("Corriente alterna (CA)",
                "Corriente cuya polaridad cambia periódicamente. Frecuencia típica en México: 60 Hz.",
                List.of("AC", "CA", "alternating current"),
                List.of("fundamentos", "instalaciones")),
        concepto("Corriente continua (CC)",
                "Corriente cuyo sentido de flujo es constante. Común en electrónica y baterías.",
                List.of("DC", "CC", "direct current"),
                List.of("fundamentos", "electrónica")),
        concepto("Cortocircuito",
                "Unión de dos puntos con baja impedancia que provoca una corriente elevada deFault. Protegido por interruptores termomagnéticos/fusibles.",
                List.of("short circuit", "falla", "puente"),
                List.of("protecciones", "seguridad")),
        concepto("Puesta a tierra",
                "Conexión intencional de partes metálicas al electrodo de tierra para proteger personas y equipos ante fallas.",
                List.of("tierra", "grounding", "JAG", "PAT"),
                List.of("protecciones", "instalaciones", "seguridad")),
        concepto("Interruptor termomagnético",
                "Dispositivo de protección que abre el circuito ante sobrecarga (térmico) o cortocircuito (magnético).",
                List.of("pastilla", "breaker", "termomagnética"),
                List.of("protecciones", "tableros")),
        concepto("Diferencial / GFCI",
                "Dispositivo que detecta fugas de corriente hacia tierra y desconecta para prevenir electrocución.",
                List.of("GFCI", "ID", "interruptor diferencial", "fuga"),
                List.of("protecciones", "seguridad")),
        concepto("Potencia eléctrica",
                "Energía por unidad de tiempo. P = V · I. Se mide en watts (W).",
                List.of("watts", "kW", "VA"),
                List.of("cálculo", "facturación")),
        concepto("Factor de potencia",
                "Cociente entre potencia activa y aparente. Ideal cercano a 1. Penalizado por CFE si < 0.95.",
                List.of("cos φ", "cosfi", "fp"),
                List.of("industrial", "facturación")),
        concepto("Caída de tensión",
                "Reducción de voltaje a lo largo de un conductor. NOM-001 limita máx. 3% en ramales.",
                List.of("voltage drop", "VD"),
                List.of("instalaciones", "conductores")),
        concepto("Dimensionamiento de conductores",
                "Selección del calibre AWG según corriente, longitud y caída de tensión admisible.",
                List.of("AWG", "calibre", "sección", "capacidad de corriente"),
                List.of("instalaciones", "conductores")),
        concepto("Tablero de distribución",
                "Caja donde se concentran los circuitos derivados de una instalación, con protecciones individuales.",
                List.of("panel", "tablero", "centro de carga"),
                List.of("instalaciones", "tableros")),
        concepto("NOM-001-SEDE-2012",
                "Norma oficial mexicana para instalaciones eléctricas. Equivalente local del NEC (NFPA 70).",
                List.of("NOM-001", "nom", "norma"),
                List.of("normatividad", "instalaciones")),
        concepto("NFPA 70E",
                "Estándar de seguridad eléctrica en el lugar de trabajo (EEUU): EPP, lockout/tagout, análisis de riesgo de arco.",
                List.of("70E", "arco eléctrico", "LOTO"),
                List.of("seguridad", "industrial")),
        concepto("EPP eléctrico",
                "Equipo de protección personal: guantes dieléctricos, gafas, vestimenta ignífuga, calzado dieléctrico.",
                List.of("PPE", "protección personal"),
                List.of("seguridad")),
        concepto("Lockout / Tagout (LOTO)",
                "Procedimiento para aislar y bloquear fuentes de energía antes de trabajar en equipos eléctricos.",
                List.of("bloqueo", "etiquetado"),
                List.of("seguridad", "industrial")),
        concepto("Arco eléctrico",
                "Descarga luminosa por ionización del aire entre dos conductores con tensión. Riesgo de quemaduras graves.",
                List.of("flash de arco", "arc flash"),
                List.of("seguridad", "protecciones")),
        concepto("Eficiencia energética",
                "Uso óptimo de la energía eléctrica para reducir consumo sin afectar el servicio.",
                List.of("ahorro", "ISO 50001"),
                List.of("industrial", "solar")),
        concepto("Energía solar fotovoltaica",
                "Generación de electricidad a partir de radiación solar mediante paneles fotovoltaicos.",
                List.of("FV", "fotovoltaica", "paneles", "solar"),
                List.of("solar", "renovable")),
        concepto("Automatización con PLC",
                "Control industrial mediante controladores lógicos programables (Siemens, Rockwell, etc.).",
                List.of("PLC", "controlador lógico", "automática"),
                List.of("industrial", "automatización"))
    );

    private static BusquedaResponse.ConceptoHit concepto(String termino, String definicion,
                                                          List<String> sinonimos, List<String> temas) {
        return new BusquedaResponse.ConceptoHit(
                termino, definicion, sinonimos, temas, ""
        );
    }
}
