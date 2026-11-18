# Pruebas de caja roja para **LuxRay**
## Validación con usuarios beta + validación de protocolos y del manejo de errores críticos

**Proyecto:** LuxRay — plataforma educativa de cursos de electricidad
**Componentes sujetos a prueba:** aplicación móvil **LuxRay Wear OS** (Flutter, `com.luxray.waer` v1.0.0+1) · sitio web / PWA **Angular 21** (`frontend-web`) · backend de **6 microservicios Spring Boot 3.4.1** (`backend/`) · base de datos **PostgreSQL 16**
**Contexto:** continúa la auditoría técnica de las Prácticas 11 (White Box) y 12 (Optimización, estrés y resiliencia), que aportaron 159 pruebas automatizadas, una curva de saturación de la API y 18 defectos documentados
**Fecha:** 12 de noviembre de 2026

---

## 0. Resumen ejecutivo

Las **pruebas de caja roja** son un enfoque **híbrido** que agrupa tres ejes complementarios: (a) la **validación con usuarios beta** (UAT), (b) la **validación de los protocolos** de comunicación del sistema y (c) la **validación del manejo de errores críticos**. La metáfora es operativa: desde fuera de la «caja» del sistema se introducen entradas hostiles, condiciones degradadas y uso real, y se observan señales de alarma —pantallas engañosas, acciones duplicadas, recursos huérfanos, congelaciones y pérdida silenciosa de información— en lugar de quedarse en los únicamente resultados funcionales.

LuxRay es un caso especialmente adecuado: el wearable trabaja con **conectividad intermitente por diseño** (el propio código lo documenta: «los relojes pierden conectividad cada pocos minutos»), el sitio web incorpora **subidas multipart** con URLs prefirmadas, y el backend reparte la lógica entre seis servicios con autenticación JWT y restricciones de unicidad en base de datos. La caja blanca ya garantiza que el código ejecuta lo que el autor cree; la caja roja debe responder a la pregunta complementaria: **¿qué hace el sistema cuando el mundo se equivoca?**

Este documento propone (1) el marco conceptual aplicado a LuxRay, (2) un plan completo de prueba con usuarios beta en cuatro fases, con instrumentación de telemetría propia y criterios de aceptación medibles, y (3) **cinco errores concretos que pueden aparecer**, con su evidencia, causa raíz, solución propuesta y prueba de regresión. De los cinco, dos se consideran críticos.

---

## 1. Marco conceptual: qué es una prueba de caja roja

### 1.1 La «caja roja» como metáfora operativa

En la práctica de pruebas se distinguen tres niveles de conocimiento sobre el sistema objeto de prueba (ISO/IEC/IEEE 29119-1, ISTQB CTFL v4.0):

| Nivel | Conocimiento | Pregunta que responde |
|---|---|---|
| **Caja blanca** | Se conoce el código y la estructura interna | ¿Se ejecutan todas las rutas de mi código? |
| **Caja negra** | No se conoce la estructura interna | ¿El sistema cumple el contrato declarado? |
| **Caja gris** | Conocimiento parcial: se observan salidas y, a veces, el estado interno | ¿Qué ruta interna produce este fallo? |
| **Caja roja** | Se desconoce la estructura, pero se busca deliberadamente el **comportamiento de fallo** | ¿Qué ocurre cuando el sistema recibe entradas hostiles, condiciones degradadas o uso real no previsto? |

La «caja roja» se articula en torno a tres ejes:

1. **Validación con el usuario real (UAT).** El usuario beta no valida requisitos abstractos: valida *su* tarea. Su veredicto ES la prueba de aceptación de la práctica, y es la única fuente que revela si el software aporta valor en el contexto de uso real.
2. **Validación del protocolo.** El sistema habla HTTP/JSON con varios servicios. Un protocolo malformado, una respuesta con forma inesperada, una cabecera ausente o un cierre de conexión a mitad de mensaje son fallos que ninguna prueba de camino feliz detecta, pero que el usuario beta sufre de forma periódica.
3. **Validación del manejo de errores críticos.** Aquí el criterio no es «funciona», sino «**falla con honestidad**»: el sistema no debe congelarse, no debe mentir sobre el resultado, no debe duplicar efectos y no debe dejar recursos huérfanos.

### 1.2 Señales de alarma («rojas») que hay que buscar

Una prueba de caja roja no busca un resultado, busca **alarmas**. Las categorías de señal que este plan vigila son:

| Señal | Definición operativa | Ejemplo en LuxRay |
|---|---|---|
| **Caída** | Proceso terminado por excepción no controlada | El PWA cae al recargar durante una subida de evidencia |
| **Cuelgue** | La interfaz deja de responder sin error visible | El wearable queda con el spinner indefinidamente |
| **Pantalla engañosa** | La UI afirma algo que el sistema no sabe | «Sin coincidencias» cuando en realidad falló la red |
| **Acción duplicada** | El mismo efecto lógico se aplica dos veces | Doble inscripción o doble evidencia por un reintento |
| **Dato corrupto o perdido** | Escritura parcial, huérfana o sobrescrita | Evidencia asociada a una lección inexistente |
| **Recurso huérfano** | Recurso creado en el servidor que nunca se libera | Carga multipart en S3 sin `completar` ni `cancelar` |
| **Fuga de información** | Se exponen datos internos o se pierde el diagnóstico | El cliente recibe un HTML de error en lugar de JSON |
| **Degradación silenciosa** | Un cambio de contrato se traduce en «no hay resultados» | La API renombra `data` y el wearable deja de encontrar nada |

### 1.3 Cuándo aporta valor un proyecto que ya tiene caja blanca

La Práctica 11 dejó el móvil en **93,33 % de cobertura de instrucciones, 87,35 % de decisiones y 100 % de condiciones** (13/13 mutantes abatidos), y la Práctica 12 añadió **35 pruebas** de resiliencia y optimización. Ese cobertura demuestra que **el código ejecuta lo que su autorroyo ima**. No demuestra tres cosas distintas:

* que el **contrato entre componentes** siga siendo el esperado cuando uno de ellos cambia o falla;
* que la **experiencia** del usuario en su contexto real (un reloj con 1 GB de RAM y 400 mAh de batería, con cobertura intermitente) sea aceptable;
* que el sistema **no mienta** cuando algo falla.

La caja roja cubre exactamente esas tres carencias. Por eso no es un enfoque redundante sino **ortogonal**: la caja blanca trabaja hacia dentro, la caja roja trabaja hacia fuera.

---

## 2. La aplicación bajo prueba: dónde está la caja

### 2.1 Superficie de la caja roja en LuxRay

| Frontera («caja») | Qué se puede alterar desde fuera | Modo de fallo esperado |
|---|---|---|
| **Usuario ↔ Wear OS** | Gestos, queries, condiciones de red y batería del reloj | Búsqueda imposible, cuelgue, agotamiento de batería |
| **Wear OS ↔ gateway** | Respuestas HTTP malformadas, cierres a mitad de mensaje, latencia | `catch` que degrada a vacío, resultado obsoleto |
| **PWA ↔ gateway** | Sesión caducada a mitad de un flujo largo | Logout inesperado, subida huérfana, reintento duplicado |
| **Gateway ↔ microservicios** | Contrato JSON entre `search-service` y `cursos-service` | Deriva silenciosa: «sin resultados» para todo el parque |
| **Microservicios ↔ PostgreSQL** | Restricciones `UNIQUE`, `NOT NULL`, `CASCADE` | Violación traducida a 500 en lugar de 409 |
| **Almacenamiento S3** | Subidas multipart interrumpidas | Cargas huérfanas, coste y ocupación de espacio |

### 2.2 Contexto heredado: qué queda abierto y debe cerrar la caja roja

La auditoría previa dejó **12 defectos abiertos** y **7 correcciones aplicadas**. Dos de ellos son de naturaleza estrictamente «caja roja» y son el objetivo prioritario de este plan:

* **DEF-08 / D12-09** — el alta de usuario devuelve **400** en lugar de **409** ante duplicado, y la violación de `UNIQUE` se traduce en **500**; el patrón *check-then-act* deja una ventana de carrera.
* **D12-10** — no existe **clave de idempotencia**: un reintento tras un *outcome desconocido* depende sólo de la restricción `UNIQUE`, lo que es insuficiente para cualquier escritura sin clave natural única.

Y uno ya corregido que la caja roja debe **verificar en uso real**, no en prueba unitaria:

* **DEF-18 / D12-05** — antes, un corte de red se mostraba como «Sin coincidencias». La versión optimizada introduce seis estados explícitos; corresponde confirmar que el usuario beta *entiende* los seis mensajes.

---

## 3. Plan de la prueba con usuarios beta (UAT)

### 3.1 Objetivos y alcance

**Objetivo general:** determinar si LuxRay aporta valor real al usuario final en su contexto de uso, y si se comporta de forma honesta y segura cuando ese contexto falla.

**Objetivos específicos**

1. Validar que las **tareas críticas** del dominio (buscar un concepto, matricularse en un curso, subir una evidencia de práctica) se completan con sucesso por personas no developers.
2. Detectar **pantallas engañosas**: casos en los que la UI afirma un resultado que el sistema no ha podido verificar.
3. Detectar **acciones duplicadas** y **recursos huérfanos** bajo uso real con red degradada.
4. Medir el **consumo real de batería, datos y memoria** del wearable en condiciones de uso prolongadas.
5. Cerrar los defectos abiertos de tipo contractual, de idempotencia y de manejo de error crítico.

**Fuera de alcance (cubierto por otras técnicas):** cobertura de código, pruebas de rendimiento sintético (Práctica 12), verificación formal de contratos entre microservicios.

### 3.2 Participantes

| Perfil | n | Criterios de inclusión | Exclusiones |
|---|---|---|---|
| **B1 — Wear OS usuario** | 8 | Reloj con Wear OS 3+, usuario real de formación técnica en electricidad | Emulador (no reproduce radio ni batería) |
| **B2 — Estudiante móvil web** | 6 | Android/iOS con datos móviles o WiFi institucional | Conexión exclusivamente cableada y estable |
| **B3 — Instructor** | 3 | Imparte o supervisa formación en el giro eléctrico | — |
| **Total** | **17** | | |

> **Justificación del tamaño.** Por debajo de 8 usuarios por perfil los defectos de usabilidad aparecen con alta probabilidad pero la confianza en la estimación de frecuencia es baja; la cifra de 17 se justifica por la **diversidad de condiciones de red** (cada participante aporta su propio patrón real de cobertura y datos), no por análisis estadístico.

**Recompensa y consentimiento:** participación voluntaria sin retribución económica, consentimiento informado por escrito que cubre la recogida de telemetría anonimizada, y derecho de baja sin justificación. Los participantes del perfil B1 reciben el reloj en modo «beta» y devuelven el dispositivo al final.

### 3.3 Instrumentación: convertir cada fallo en evidencia objetiva

El beta test se apoya en **tres fuentes** que triangulan el veredicto:

1. **Telemetría on-device.** El wearable debe emitir, de forma opt-in y anonimizada, un evento por cada transición de estado:
   `search_started {query_len, cache_hit}` · `search_finished {state, http_status, attempts, duration_ms, bytes_down}` · `state_shown {state}` · `network_changed {type}` · `app_error {kind, stack_hash}`.
   **Requisito crítico:** el evento `search_finished` debe incluir el **hash de la forma de la respuesta recibida** (no su contenido), para poder detectar deriva de contrato sin almacenar datos de usuario.
2. **Bitácora del servidor.** Correlación por `X-Request-Id` (ya existente en el `RequestIdLoggingFilter` del gateway) entre la petición del móvil y la que llegó al servicio. Permite saber si el fallo fue «el móvil no pidió», «la API no respondió» o «la API respondió mal».
3. **Diario del participante.** Cuestionario de 8 ítems tras cada tarea (éxito, confianza 1-5, qué esperaba, qué ocurrió) y una entrevista de 20 min al final.

### 3.4 Guion de tareas y señales de alarma

| # | Tarea | Criterio de éxito (medible) | Señal de alarma «roja» |
|---|---|---|---|
| T1 | Buscar un concepto conocido («ley de ohm») desde la pantalla de inicio | Resultados visibles en ≤ 3 s; el participante identifica el resultado | Pantalla «Sin coincidencias» con la red operativa; *spinner* infinito; arranque en una pantalla distinta del inicio (DEF-04) |
| T2 | Buscar un término **inexistente** | Mensaje explícito de «sin coincidencias» | Mensaje de error de red ante una búsqueda válida; o resultado de una búsqueda anterior |
| T3 | Repetir la misma búsqueda 3 veces seguidas | Resultado idéntico y coherente | Resultados distintos entre repeticiones; consumición de datos desproporcionada |
| T4 | Buscar **sin cobertura** (modo avión) y luego recuperar | Mensaje de «sin conexión»; recuperación automática al volver la cobertura | «Sin coincidencias» (engaño); cierre forzado de la app; batería agotada |
| T5 | Matricularse en un curso desde el PWA móvil | Matrícula confirmada y visible en «Mis cursos» | Doble matriculación; error 500 al repetir; sesión expulsada a mitad |
| T6 | **Subir una evidencia de práctica** (foto) y volver a subir la misma | Evidencia visible y correctamente asociada | Doble evidencia; carga huérfana en S3; «subida correcta» cuando no se guardó |
| T7 | Dejar la app abierta 30 min y volver a usarla | Sesión viva o re-login limpio | Estado corrupto, pantalla en blanco, tokens caducados sin avisar |
| T8 | Buscar mientras la app está en segundo plano y vuelve | Resultado correspondiente a la **última** búsqueda | Resultado de una búsqueda **antigua** (carrera de respuestas desordenadas) |

### 3.5 Matriz de condiciones de prueba

Cada tarea T1–T8 se ejecuta bajo, al menos, estas condiciones, que son las que generan el tráfico real del wearable:

| Condición | Simulación | Tareas |
|---|---|---|
| WiFi estable | Red del centro | Todas |
| **Datos móviles** | 4G con cobertura intermitente | T1, T2, T4, T5 |
| **Sin cobertura** | Modo avión | T4, T8 |
| **Cobertura degradada** (2G / alta latencia) | Limitador de red del sistema | T1, T6 |
| Batería < 20 % | Modo de ahorro | T1, T4, T6 |
| Pantalla apagada / reloj en reposo | Suspensión | T8 |

### 3.6 Métricas de aceptación

| Métrica | Umbral de aceptación | Cómo se mide |
|---|---|---|
| Tasa de éxito de las tareas T1–T6 | **≥ 90 %** de los intentos | Bitácora + diario |
| Detección del fallo de red por el usuario | **100 %** en T4 | El participante debe *nombrar* la falta de conexión, no sólo «no funcionó» |
| Acciones duplicadas | **0** | Conteo en base de datos por participant-email |
| Cargas multipart huérfanas | **0** | `SELECT` de multipart uploads sin `completar` ni `cancelar` |
| Peticiones HTTP por 10 búsquedas idénticas | **≤ 3** | Telemetría y traza del servidor |
| Consumo de datos del wearable en 1 h de uso | **≤ 8 MB** | Telemetría |
| Caixas o cierres forzados | **0** | Logcat + telemetría de `app_error` |
| Confianza declarada (1-5) en la tarea completada | **≥ 4,0** de media | Cuestionario |

### 3.7 Plan de fases

| Fase | Duración | Contenido | Salida |
|---|---|---|---|
| **F0 · Preparación** | 1 semana | Instrumentación de telemetría, alta de `X-Request-Id`, script de consentimientos, empaquetado de los APK release | Build firmado + telemetría validada en staging |
| **F1 · Prueba de banco** | 3 días | 3 técnicos ejecutan el guion T1–T8 con **las condiciones degradadas** antes de que un usuario beta las encuentre | Lista de fallos reproducibles + corrección de los bloqueantes |
| **F2 · Beta cerrado** | 2 semanas | 17 participantes, T1–T8, con soporte en remoto (sin corregir en caliente) | Telemetría + diarios + informe de fallos |
| **F3 · Análisis y corrección** | 1 semana | Triangulación de telemetría + servidor + diarios; clasificación por severidad | Registro de defectos con reproducción |
| **F4 · Beta abierto y cierre** | 2 semanas | Segunda vuelta con los defectos críticos corregidos; validación de las correcciones | Informe final de aceptación |

> **Principio de la F1.** Ejecutar el guion completo con degradaciones **antes** del beta reduce el ruido: un fallo de infraestructura discovered por un participante consume su participación y su confianza, y eseparticipant ya no puede volver a probar bien.

### 3.8 Ética, privacidad y tratamiento de datos

* La telemetría es **opt-in**, **anonimizada** (identificador aleatorio por instalación) y **limitada a las métricas necesarias**; no se registra texto de búsqueda, ni correos, ni contenido de las evidencias.
* Los diarios se transcriben y se destruyen el texto original tras la extracción de hallazgos; las grabaciones de sesión sólo se conservan con consentimiento expreso.
* Los participantes del perfil B1 **no** producen contenido académico evaluable: las tareas T5 y T6 se ejecutan en un curso de prueba.
* **Ningún dato de producción se usa como caso de prueba** sin anonimización previa.

### 3.9 Roles

| Rol | Responsabilidad |
|---|---|
| Responsable de la prueba | Coordinación, cronificación, entrega de informes |
| Analista de telemetría | Correlación de eventos y construcción de los casos de reproducción |
| Desarrollador de la app | Corrección y validación en el dispositivo |
| Soporte de primer nivel | Atención a participantes, sin acceso a la telemetría sensible |
| Supervisor de datos (perfil B3) | Validación del valor pedagógico de la tarea |

### 3.10 Criterios de salida

La beta se da por superada cuando: (a) todas las tareas alcanzan el umbral de éxito; (b) no hay acciones duplicadas ni cargas huérfanas; (c) los defectos críticos de la F3 están cerrados y verificados en dispositivo; (d) el usuario B3 valida el valor pedagógico; (e) la telemetría cubre al menos el 95 % de los eventos de las tareas ejecutadas.

---

## 4. Errores que pueden aparecer y cómo los resolvería

> Se documentan **cinco** errores (dos de ellos críticos). Todos son **concretos, reproducibles y específicos de LuxRay**, y se apoyan en hallazgos ya medidos en las auditorías previas o en el diseño actual del código.

### E-01 · *Crítico* · Deriva de contrato: el wearable responde «sin coincidencias» cuando la API cambia de forma

**Qué ocurre.** El backend renombra o reestructura el envelope de respuesta (por ejemplo, `data` pasa de objeto a array, o `data.total` desaparece). El wearable **no falla**: parsea lo que puede, obtiene un `BusquedaResponse.empty()` y muestra **«Sin coincidencias dentro del giro eléctrico»**. El usuario cree que el contenido no existe; en realidad la búsqueda funciona y lo que se rompió es el contrato.

**Por qué es especialmente grave en LuxRay.** El buscador tiene **dos formas de respuesta distintas** según el endpoint: `search-service` envuelve cada hit en `{id, score, source, highlight}` mientras `cursos-service` devuelve hits planos. El móvil implementa **dos parsers distintos** (`BusquedaResponse.fromSearchApiResponse` y `fromApiResponse`) para el mismo dominio, y el riesgo de deriva está duplicado. A esto se suma que **el wearable no emite telemetría**: no hay forma de detectar el fallo desde el servidor, porque desde el servidor todo es «un 200 con 0 resultados».

**Cómo lo detecta la caja roja.** Inyección de un contrato alternativo en el entorno de pruebas (respuesta con `data` como array, con `data` ausente, con campos renombrados) y observación de la pantalla; además, la métrica «tasa de 0 resultados por término de catálogo» del panel de analítica, que debe correlacionarse con el número de búsquedas del wearable.

**Solución que aplicaría.**

1. **Validación de forma en el cliente**: añadir al parser un chequeo explícito de la forma esperada y clasificar la discrepancia como un **estado de error de contrato** (`errorContrato`), no como «vacío». El mensaje al usuario sería «El servicio devolvió una respuesta inesperada», que es honesto y permite diagnosticar.
2. **Versión del contrato**: incluir `contractVersion` en la respuesta y validarlo en el cliente; ante una versión desconocida, la app puede degradar a la ruta legacy (que ya implementa y que consulta la base de datos completa) en lugar de mostrar un vacío.
3. **Telemetría de contrato**: enviar el `hash de la forma` de cada respuesta (no su contenido) para que una deriva se detecte en el servidor aunque el cliente no pueda clasificarla.
4. **Canario sintético**: una petición periódica desde la app contra un endpoint de contrato conocido cuyo resultado esperado es fijo; si el canario no coincide, la app muestra un aviso de «servicio desactualizado» en lugar de un vacío.
5. **Prueba de regresión**: fijar en el contrato un test que parsee un golden file de cada versión de contrato y falle ante un cambio no declarado.

### E-02 · *Crítico* · «Outcome desconocido» en la escritura: reintento duplicado y recurso huérfano

**Qué ocurre.** La subida de evidencia de práctica es un flujo de **tres pasos** en el PWA: `POST /uploads` (crea `uploadId` y las URLs prefirmadas) → `PUT` de cada parte directamente a S3 → `POST /uploads/completar` (ETags). Si la conexión se cae **después** de que S3 haya aceptado las partes pero **antes** de recibir la respuesta del `completar`, el cliente no sabe si la evidencia quedó registrada. El participante reintenta: vuelve a llamar a `/uploads`, obtiene un `uploadId` nuevo y sube las partes otra vez. Resultado: una carga multipart **huérfana** en S3 (nunca completada ni cancelada) y, si el `completar` sí llegó a aplicarse en el primer intento, **evidencias duplicadas** en la base de datos.

**Por qué es realista aquí.** Es el modo de fallo más frecuente del wearable y del móvil con red degradada, y el diseño multipart es exactamente el que introduce la ambigüedad. La Práctica 12 ya midió el caso equivalente en el registro de usuarios: tres intentos producían `[200, 400, 400]` y **una sola fila** (la restricción `UNIQUE` salvó la duplicidad). En las subidas de evidencia **no existe tal red de seguridad**: la tabla `evidencias_practica` no tiene una clave única natural, de modo que el mismo problema se materializaría en duplicados reales.

**Cómo lo detecta la caja roja.** Interrumpir la conexión en puntos controlados del flujo (después de `/uploads`, después de cada `PUT`, durante `/completar`) y verificar el estado final en la base de datos y en el bucket: número de evidencias por intento, número de multipart uploads sin cerrar, y coste de almacenamiento.

**Solución que aplicaría.**

1. **Clave de idempotencia** (D12-10): la app genera un `Idempotency-Key` (UUID) por operación lógica de subida y la reenvía en cada reintento. El backend la persiste con `UNIQUE (clave, usuario)` y, ante una repetición, devuelve la **respuesta original** en lugar de repetir el efecto.
2. **Reconciliación explícita del «outcome desconocido»**: si el cliente no sabe si la operación se aplicó, no debe «reintentar a ciegas»; debe consultar el estado (`GET /uploads/{id}/estado`) y, si existe, adoptar el resultado.
3. **Cierre garantizado de los recursos huérfanos**: `POST /uploads/cancelar` debe ejecutarse en un `finally` del cliente, y el backend debe tener una tarea programada que revoque los multipart uploads caducados (actualmente `abortarMultipart` sólo existe como acción del cliente: si el cliente muere, la carga queda ahí).
4. **Restricción de unicidad en `evidencias_practica`**: añadir una clave idempotente única (por ejemplo `cliente_idempotencia`) para que la base de datos sea la última línea de defensa, igual que lo es `UNIQUE (email)` en usuarios.
5. **Prueba de regresión**: automatizar el escenario «corte tras cada paso» y afirmar, para cada punto de corte, que existe **exactamente una** evidencia y **cero** cargas huérfanas.

### E-03 · *Alta* · Sesión caducada a mitad de un flujo largo: cierre de sesión inesperado y subida abandonada

**Qué ocurre.** El JWT tiene una caducidad por defecto de **1 440 minutos (24 h)**. Si la sesión caduca **durante** la subida de una evidencia (un flujo que puede tardar minutos en una red lenta), el `POST /uploads/completar` devuelve 401. El interceptor del PWA detecta el 401 con token previo y ejecuta **logout + redirección al login**, con lo que el participante pierde el contexto de la tarea, la carga queda a medias en S3 y el mensaje que ve es «tu sesión ha expirado», sin relación aparente con la subida.

**Cómo lo detecta la caja roja.** Tarea T5/T6 con el token caducado por anticipación (el perfil de beta incluye un caso específico con token expirado), y medición del tiempo de recuperación: cuántos intentos y cuánto tiempo necesita el participante para volver a dejar la evidencia en su sitio.

**Solución que aplicaría.**

1. **Renovar el token de forma proactiva** antes de iniciar un flujo largo y, sobre todo, **reintentar de forma transparente** la petición que falló por 401 tras el refresco, en lugar de cerrar la sesión.
2. **Punto único de reanudación**: el backend debe poder reanudar una subida multipart ya iniciada (`uploadId` + partes ya subidas), de modo que el cliente no tenga que empezar de cero y el participante no pierda el trabajo.
3. **Mensaje específico** para el 401 en mitad de un flujo: «Tu sesión ha caducado; al iniciar sesión podrás terminar de subir la evidencia», accompanied de la reanudación automática.
4. **Medición**: añadir a la telemetría el tiempo de recuperación tras 401 y el porcentaje de flujos largos que se completan tras un refresco de token.

### E-04 · *Media* · La caché de 30 s sirve un resultado obsoleto sin aviso

**Qué ocurre.** La optimización de la Práctica 12 introduz una caché LRU con TTL de 30 s en el wearable (para evitar repetir la descarga de la misma respuesta). Si el catálogo cambia (un curso se publica o se retira) y el usuario repite la misma búsqueda dentro de esa ventana, ve **datos obsoletos** sin ninguna indicación de que lo son. El riesgo es mayor de lo que parece porque el resultado cacheado es el de la **consulta anterior**, no una copia antigua de la misma: en el wearable, la búsqueda con el mismo texto devuelve en general el mismo contenido, pero tras una modificación editorial la discrepancia es perceptible y sin explicación.

**Cómo lo detecta la caja roja.** Tarea T3 (repetir la misma búsqueda) combinada con una modificación del catálogo entre repeticiones, y pregunta explícita al participante sobre si «lo que ve ahora es lo mismo que antes».

**Solución que aplicaría.**

1. **Invalidación por evento**: si el backend publica un evento de cambio de catálogo (p. ej. vía la misma cola de eventos que usa el service discovery), el cliente puede purgar las entradas afectadas en lugar de esperar al TTL.
2. **Marca de antigüedad en la UI**: indicar «resultados de hace N s» cuando la entrada servida procede de la caché. Es una honestidad mínima y de bajo coste.
3. **TTL adaptativo**: 30 s es un valor de compromiso; con la invalidación por evento podría bajar a 10-15 s sin coste de tráfico apreciable.
4. **Prueba de regresión**: verificar que la caché se purga al cambiar el catálogo y que la marca de antigüedad aparece en la respuesta servida desde caché.

### E-05 · *Media* · La pantalla engaña cuando falla el acceso (y la tentación de revertir la corrección)

**Qué ocurre.** La versión optimizada distingue seis estados (`ok`, `vacio`, `sinConexion`, `errorServidor`, `accesoRechazado`, `errorPeticion`) y cada uno tiene su mensaje. Pero existe un riesgo **de proceso**: una corrección posterior que «simplifique» el manejo de errores puede reintroducir el `catch-all` que colapsaba todos los fallos en un vacío (DEF-18), y nada en las 159 pruebas automatizadas lo impediría, porque el comportamiento correcto **está** cubierto: lo que no está cubierto es el comportamiento **incorrecto** cuando alguien lo reintroduce sin querer.

**Cómo lo detecta la caja roja.** Tarea T2 y T4 con los seis estados provocados de forma artificial; el criterio no es que el mensaje exista, sino que el participante **lo distinga sin ayuda** y lo clasifique correctamente («no me deja entrar» ≠ «no hay resultados»).

**Solución que aplicaría.**

1. **Prueba de contrato de la capa de presentación**: un conjunto de aserciones sobre el texto renderizado para cada estado, para que una regresión al `catch-all` rompa la suite y no llegue a beta.
2. **Prueba de usabilidad del mensaje**: pedir al participante que describa qué ha pasado con sus palabras; si dice «no hay nada» cuando el sistema estaba caído, el mensaje ha fallado aunque el código HTTP sea correcto.
3. **Revisión de la deuda técnica con impacto en el usuario**: el `catch-all` y la ausencia de telemetría son decisiones, no accidents; dejarlas escritas en el código (comentario de la clase) reduce la probabilidad de reintroducirlas.

### 4.1 Trazabilidad con los hallazgos de las auditorías previas

| Error de caja roja | Hallazgo previo relacionado | Severidad | ¿Corregido en código? |
|---|---|---|---|
| E-01 · Deriva de contrato | Riesgo «dos parsers para el mismo dominio» (Práctica 11, §4.5) | Crítica | No |
| E-02 · Outcome desconocido en la escritura | **D12-09** y **D12-10** (Práctica 12) | Crítica | No |
| E-03 · Sesión caducada a mitad de flujo | `auth.interceptor.ts` (logout + redirección en 401) | Alta | No |
| E-04 · Caché que sirve obsoletos | Optimización **P12-01** (Práctica 12) | Media | Parcial (TTL de 30 s) |
| E-05 · Pantalla engañosa | **DEF-18** / **D12-05** (Prácticas 11 y 12) | Media | Sí (verificar en uso) |

---

## 5. Conclusiones

La caja roja es, para LuxRay, la técnica que cierra el hueco que la caja blanca no puede cerrar. Las 159 pruebas automatizadas de las Prácticas 11 y 12 acreditan que el código móvil ejecuta lo que su autor cree; no acreditan que el contrato entre el wearable, el PWA y los seis microservicios siga siendo el esperado, ni que el conjunto de cuatro medal de flujo (alta de usuario, inscripción, subida de evidencia y búsqueda) se comporte de forma útil cuando la red del reloj se degrada.

La aplicación es un caso adecuado porque su contexto de uso **es** la degradación: un wearable pierde cobertura cada pocos minutos por diseño, y el sitio web móvil sube archivos de gran tamaño a través de tres pasos con URLs prefirmadas. Los cinco errores documentados en el apartado 4 no son hipótesis de laboratorio, sino modos de fallo que la propia arquitectura hace plausibles, y dos de ellos (deriva de contrato y outcome desconocido en la escritura) tienen ya un hueco de mitigación identificado en el código.

El plan de beta propuesto es deliberadamente escalonado: una prueba de banco con degradaciones antes de exponer a los participantes, instrumentación de telemetría que convierte cada queja subjetiva en un caso reproducible, y una matriz de condiciones que cubre la variabilidad real de la red. El criterio de aceptación más importante no es la tasa de éxito por tarea —que es donde se concentra el esfuerzo habitual de UAT— sino tres indicadores de honestidad del sistema: que el usuario **distinga** «no hay resultados» de «no hay red», que **no se duplique** ninguna acción por un reintento, y que **no quede ningún recurso huérfano** en el servidor tras una interrupción.

Finalmente, la caja roja debe cerrar por escrito los defectos que la caja blanca dejó abiertos y, sobre todo, proteger los ya corregidos: si E-05 llegase a materializarse, significaría que la corrección de DEF-18 se ha degradado por el camino, y eso sólo se detecta si existe una prueba que falle cuando el sistema vuelve a mentir.

---

## 6. Referencias

1. **ISO/IEC/IEEE 29119-1:2018** — *Software Testing — Part 1: Concepts and Terms*. International Organization for Standardization / IEEE. Base normativa de los niveles de conocimiento (caja blanca, negra y gris) y de las técnicas de prueba de caja negra. https://standards.ieee.org/ieee/29119/
2. **ISO/IEC/IEEE 29119-4:2021** — *Software Testing — Part 4: Test Techniques*. Técnicas de caja negra (partición de equivalencias, análisis de valores límite, tablas de decisión, transiciones de estado) y **técnicas basadas en la experiencia**, que sustentan el carácter predictivo de la caja roja. https://standards.ieee.org/ieee/29119/
3. **ISTQB** — *Certified Tester Syllabus, Versión 4.0* (2019) y *Glossary of Testing Terms* (en línea, v4.x). Definiciones de *black-box testing*, *grey-box testing*, *User Acceptance Testing* y *beta testing*. https://www.istqb.org/certifications/certified-tester-syllabus · https://istqb-glossary.page/
4. **Cooper, M. (2008)** — *Exploring Software: A Project Guide to Developing and Testing*. Addison-Wesley. Marco de UAT basado en escenarios y tareas de usuario, con criterios de aceptación observables.
5. **Majors, C. (2016)** — «Flaky Tests at Google and How We Mitigate Them». *Google Testing Blog*. https://testing.googleblog.com/2016/05/flaky-tests-at-google-and-how-we.html — Referencia para el criterio metodológico de no aceptar mediciones de beta no reproducibles.
6. **Rodden, A., Hutchinson, J. y Fu, A. (2010)** — *Measuring the User Experience on a Large Scale: User-Centered Metrics for Web Applications*. Morgan Kaufmann. Marco **HEART** (Happiness, Engagement, Adoption, Retention, Task success), base de las métricas de aceptación de la beta.
7. **NASA** — *Software Testing Handbook* (DOT-HDBK-700-002). NASA Software Engineering Center. Tratamiento de la prueba de robustez y de la inyección de fallos para validar el manejo de condiciones degradadas.
8. **RFC 9110 (2022)** — *HTTP Semantics*. §9.3.1 *Robustness* (principio «Be liberal in what you accept») y catálogo de códigos de estado empleado en el análisis de protocolos. IETF. https://www.rfc-editor.org/rfc/rfc9110
9. **RFC 9112 (2022)** — *HTTP/1.1*. Encuadre de mensajes, `Content-Length` frente a `Transfer-Encoding: chunked` y cierre de conexión a mitad de mensaje: los casos de fallo de protocolo que la caja roja debe verificar en el cliente. IETF. https://www.rfc-editor.org/rfc/rfc9112
10. **OWASP** — *Web Security Testing Guide* y *API Security Top 10 (2023)*, en particular API4 (*Unrestricted Resource Consumption*), API6 (*Unrestricted Access to Sensitive Business Flows*), API8 (*Security Misconfiguration*) y API10 (*Unsafe Consumption of APIs*). https://owasp.org/www-project-web-security-testing-guide/ · https://owasp.org/API-Security/editions/2023/en/0x11-t10/
11. **Android Developers** — *Network security configuration* y *Cleartext traffic* (políticas de red por defecto del sistema operativo, relevantes para el wearable, que opera en HTTP sobre red local de laboratorio). https://developer.android.com/privacy-and-security/security-config
12. **Flutter** — *Testing apps* y *integration_test* (referencia para la instrumentación de la telemetría y la ejecución de escenarios de degradación sobre el dispositivo real). https://docs.flutter.dev/testing/overview · https://docs.flutter.dev/testing/integration-testing
13. **Grafana** — *k6 documentation* (herramienta de carga y de inyección de fallos por escenario, empleada como referencia en la Práctica 12 y útil para las fases de degradación). https://k6.io/docs/
14. **Charity Majors / Martin Fowler** — sobre la diferencia entre *fault* y *failure* en la validación de sistemas: un fallo del sistema (comportamiento incorrecto tras una condición) frente a un error del operador. Base conceptual de la matriz de «puntos de corte» de la tabla del apartado 4.2.
15. **Deutsch, P. (1984)** — «The Eight Fallacies of Distributed Computing». *ACM Queue*. Base para el análisis del «outcome desconocido» (E-02) en un sistema con varios servicios y almacenamiento externo prefirmado.

---

*Documento de plan de pruebas. Las pruebas descritas **no se han ejecutado**: este documento es el plan, la justificación metodológica, los criterios de aceptación y el registro de errores previstos, para su uso en el documento de entrega del proyecto.*
