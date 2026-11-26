# Documentación técnica: Subida multimedia con URL preasignada y sistema de cámara

> Módulo móvil de **LuxRay** — Evidencias de práctica para validación de aprendizaje
> Autor: [Tu nombre] · Proyecto: [Nombre de la materia] · Fecha: [fecha]

---

## 1. Introducción

El módulo móvil de LuxRay incorpora una función exclusiva para teléfonos:
**la captura de fotografías como evidencia de práctica**, con las que el
alumno valida el correcto aprendizaje de cada lección. Las imágenes se
suben al almacenamiento mediante **subidas por partes (Multipart Upload)
firmadas con URLs preasignadas**, de modo que el archivo viaja **directamente
del teléfono al bucket de S3, sin pasar por el backend**.

---

## 2. Subida con URL preasignada (Presigned URL)

### 2.1 ¿Qué es una URL preasignada?

Una **URL preasignada** es un enlace temporal generado por el servidor que
incluye, cifrados en la propia URL, las credenciales de autorización firmadas
(Signature V4 de AWS). Quien posee ese enlace puede realizar **exactamente la
operación para la que fue firmada** (por ejemplo, `PUT` sobre un objeto
específico) durante un tiempo limitado — en este proyecto, **30 minutos** —,
sin necesidad de poseer las llaves de acceso del almacenamiento.

En otras palabras: el backend **presta** al móvil, de forma controlada y
efímera, el permiso de escribir en el bucket; las llaves reales nunca salen
del servidor.

### 2.2 ¿Por qué Multipart Upload?

| Característica | Subida simple | Multipart Upload |
|---|---|---|
| Tamaño máximo práctico | limitado por memoria del cliente | decenas de GB |
| Tolerancia a fallos | si falla, se repite TODO | solo se repite la parte fallida |
| Progreso visible | todo o nada | progreso real por partes |
| Consumo de memoria | todo el archivo en RAM | bloques de 5 MB |

Cada evidencia se divide en **partes de 5 MiB** (mínimo que exige la
especificación de S3, salvo la última parte). Una foto de 8 MB se sube en
2 partes; un video de 120 MB, en 24. El sistema calcula dinámicamente el
tamaño de parte para no superar el máximo de 10 000 partes por subida.

### 2.3 Arquitectura del flujo

```
 TELÉFONO (PWA)            CURSOS-SERVICE (8082)              S3 / BUCKET
     │                            │                              │
     │ 1. POST /cursos/lecciones/{id}/uploads                    │
     │    {nombreArchivo, contentType, tamanoBytes}               │
     │ ───────────────────────────▶                              │
     │                            │ CreateMultipartUpload        │
     │                            │ ─────────────────────────────▶
     │                            │                              │
     │    ◀── uploadId + URL preasignada POR CADA PARTE ──       │
     │                            │                              │
     │ 2. PUT parte 1 → http://bucket/...?partNumber=1&X-Amz-Signature=… ──▶
     │ 3. PUT parte 2 → http://bucket/...?partNumber=2&X-Amz-Signature=… ──▶
     │        (subida DIRECTA al bucket: el archivo no pasa por el backend) │
     │                            │                              │
     │ 4. POST .../uploads/completar {uploadId, ETags[]}          │
     │ ───────────────────────────▶ CompleteMultipartUpload ─────▶ │
     │                            │                              │
     │ 5. La evidencia se registra en PostgreSQL                  │
     │    ◀── {estado: COMPLETADA} ──                             │
```

### 2.4 Pasos detallados

1. **Inicio** — `POST /cursos/lecciones/{id}/uploads`
   El móvil envía los metadatos del archivo (nombre, tipo MIME, tamaño en
   bytes). El backend valida que el usuario esté autenticado (JWT), que la
   lección exista, que el tipo de archivo sea permitido y que no exceda
   500 MB. En respuesta entrega el `uploadId` y **una URL preasignada
   distinta por cada parte**, firmadas para expirar en 30 minutos.

2. **Subida por partes** — `PUT` a cada URL preasignada
   El móvil recorta el archivo en bloques (`Blob.slice`) y hace `PUT` de
   cada uno **directamente al bucket**. De cada respuesta captura el header
   `ETag` (huella MD5 de la parte), que actúa como comprobante de recibo.
   El backend no interviene en absoluto en este paso: el archivo jamás
   transita por sus puertos.

3. **Confirmación** — `POST .../uploads/completar`
   El móvil envía el `uploadId` y la lista de pares `número-de-parte → ETag`.
   El backend invoca `CompleteMultipartUpload` en S3, que une las partes en
   el objeto final, y **registra la evidencia en la tabla
   `evidencias_practica`** de PostgreSQL (usuario, lección, archivo, tamaño,
   clave del objeto y estado).

4. **Cancelación** (opcional) — `POST .../uploads/cancelar`
   Si la subida se interrumpe, el móvil aborta el multipart para que S3
   descarte las partes huérfanas y no se acumule basura ni costo.

### 2.5 Ventajas del diseño

- **El backend no procesa archivos**: consume 0 bytes de ancho de banda por
  subida y no arriesga quedarse sin memoria con archivos grandes.
- **Seguridad por diseño**: las llaves del bucket viven solo en el backend;
  en el teléfono circula únicamente un permiso firmado que expira solo.
- **Reanudable y observable**: el usuario ve el progreso real
  ("Subiendo 2/3 partes… 67%").
- **Escalable**: cambiar de s3rver (emulador de desarrollo) a AWS S3, MinIO
  o cualquier S3-compatible solo requiere cambiar variables de entorno
  (`S3_ENDPOINT`, `S3_BUCKET`, credenciales); ni una línea de código.

### 2.6 Seguridad del flujo

| Mecanismo | Descripción |
|---|---|
| JWT obligatorio | Todos los endpoints exigen token; el usuario se resuelve desde el claim `uid` |
| URLs efímeras | Firmadas (SigV4) con expiración de 30 minutos |
| Alcance mínimo | Cada URL solo permite escribir UNA parte de UN objeto concreto |
| Tipos permitidos | Solo `image/jpeg`, `image/png`, `image/webp` (y video, reservado) |
| Límite de tamaño | Máximo 500 MB por evidencia |
| Trazabilidad | Cada evidencia queda registrada con usuario, lección, fecha y clave S3 |

### 2.7 Endpoints implementados (cursos-service)

| Método | Ruta | Función |
|---|---|---|
| `POST` | `/cursos/lecciones/{id}/uploads` | Inicia multipart, devuelve URLs preasignadas por parte |
| `POST` | `/cursos/lecciones/{id}/uploads/completar` | Une las partes y registra la evidencia |
| `POST` | `/cursos/lecciones/{id}/uploads/cancelar` | Aborta la subida y libera partes |
| `GET`  | `/cursos/lecciones/{id}/uploads` | Lista las evidencias del usuario con URL de lectura firmada (1 h) |

---

## 3. Sistema de cámara (interacción con el hardware del móvil)

### 3.1 Concepto

En una aplicación web el navegador funciona como una **capa de abstracción
sobre el hardware** del teléfono. El módulo de evidencias aprovecha esa capa
para **acceder directamente al sensor de cámara** del dispositivo, lo que
constituye la interacción de más bajo nivel con el hardware que ofrece una
aplicación sin firmar nativamente: el software de la app controla el lente
(encendido, selección trasera/delantera, resolución) y captura fotogramas en
tiempo real desde el stream de video del dispositivo.

Esta interacción con el hardware móvil — cámara, permisos y flujo de video —
es el componente que en el proyecto denominamos **firmware de captura**:
la capa encargada de negociar con los controladores del dispositivo el
acceso al sensor, gestionar el ciclo de vida del stream y entregar al resto
del sistema un fotograma listo para procesarse.

### 3.2 Tecnologías de acceso a la cámara (en cascada)

El sistema implementa **tres mecanismos de captura** y elige automáticamente
el mejor disponible en el dispositivo:

```
                 ┌────────────────────────────┐
                 │ ¿Corre dentro de la app    │
                 │ nativa (Capacitor)?        │
                 └───────┬───────────────┬────┘
                       SÍ               NO
                        │               │
              Plugin nativo        ¿Contexto seguro?
              @capacitor/camera    (localhost o HTTPS)
                        │           │         │
                        │         SÍ          NO
                        │           │         └──▶ <input capture="environment">
                        │           │               (app de cámara del sistema)
                        │           ▼
                        │     getUserMedia()
                        │     ── cámara DENTRO de la app ──
                        ▼
```

1. **`getUserMedia` (Media Capture and Streams API)** — *mecanismo principal*.
   Solicita al navegador el stream de video de la **cámara trasera**
   (`facingMode: environment`) a resolución 1920×1080. El usuario autoriza el
   acceso con el diálogo de permisos del sistema y el video se renderiza en
   vivo dentro de la propia interfaz. Requiere **contexto seguro**
   (HTTPS o `localhost`), garantía criptográfica de que el stream del sensor
   solo puede consumirlo una app confiable.

2. **`<input type="file" accept="image/*" capture="environment">`** —
   *respaldo universal*. Delega en la aplicación de cámara del sistema
   operativo. Funciona en cualquier contexto, incluido HTTP simple.

3. **Plugin `@capacitor/camera`** — ruta nativa si la PWA se empaqueta algún
   día como aplicación instalable (Capacitor), con acceso a la cámara vía
   puente nativo (Android Camera API).

La cascada garantiza que **el botón "Tomar foto" siempre termina en una
cámara real**, independientemente del navegador, del modo de conexión o del
sistema operativo del teléfono.

### 3.3 Ciclo de captura (con `getUserMedia`)

1. **Solicitud de stream** — `getUserMedia({video: {facingMode: {ideal:
   'environment'}}, audio: false})`. El SO muestra el diálogo de permisos;
   el navegador solo entrega el stream si el usuario lo autoriza (y en
   Android el indicador de cámara en uso se enciende a nivel de sistema).
2. **Vista previa en vivo** — el stream se conecta a un elemento `<video>`
   embebido en el panel de evidencias: el alumno encuadra su práctica y
   verifica la toma antes de capturar.
3. **Captura del fotograma** — al pulsar el botón, el fotograma activo se
   dibuja en un `<canvas>` (a la resolución nativa del sensor) y se extrae
   como **JPEG con calidad 90%** mediante `canvas.toBlob()`.
4. **Liberación del hardware** — el stream se detiene explícitamente
   (`getTracks().stop()`), apagando el sensor y el indicador de cámara del
   sistema. Este paso es crítico: un stream sin liberar mantiene el sensor
   ocupado y consume batería.
5. **Entrega al sistema de subida** — el JPEG resultante entra al flujo de
   la sección 2 (multipart + URL preasignada) como cualquier otro archivo.

### 3.4 Permisos y privacidad

- El acceso a la cámara **jamás es automático**: el sistema operativo lo
  concede por aplicación y por usuario, con diálogo explícito y revocable
  desde ajustes.
- La captura es **100% local**: el fotograma se procesa en memoria del
  dispositivo; nada sale del teléfono hasta que el usuario confirma la
  subida.
- El stream se cierra inmediatamente tras cada captura; el panel de cámara
  no persiste estados entre usos.

---

## 4. Integración de ambos sistemas: el recorrido completo

```
 [Sensor de cámara]  →  getUserMedia (stream de video)
        ↓
 [Vista previa en pantalla]  →  captura con canvas → JPEG en memoria
        ↓
 [1] POST /uploads  ──────────▶  backend firma y devuelve URLs
        ↓
 [2] PUT por partes  ─────────▶  S3 directo (5 MiB por parte, ETag por recibo)
        ↓
 [3] POST /completar  ───────▶  S3 une partes → PostgreSQL registra evidencia
        ↓
 [✓] "Evidencia registrada" — validación de la práctica del alumno
```

**Resultado**: el alumno toma una fotografía de su práctica con la cámara
real de su teléfono y la evidencia queda registrada de forma verificable,
sin que el backend haya transportado un solo byte del archivo, con permisos
de hardware gestionados por el sistema operativo y con URLs que expiran por
sí solas.

---

## 5. Evidencias de prueba

Durante las pruebas de integración se verificó el flujo completo con una
prueba automatizada de 12 MB (3 partes):

```
✓ Token JWT obtenido
✓ Inicio de multipart → 3 URLs preasignadas emitidas (~5 MB c/u)
✓ PUT parte 1/3 → ETag "b8fc857a…"  (subida directa al bucket)
✓ PUT parte 2/3 → ETag "ba8c3fac…"
✓ PUT parte 3/3 → ETag "bf52955b…"
✓ Completa multipart → evidencia COMPLETADA en PostgreSQL
✓ Lectura por URL firmada → objeto íntegro: 12 MB, contenido verificado byte a byte
```

Así mismo, la suite de endpoints del API reportó **16/16 pruebas exitosas**
(auth, cursos, inscripción, progreso, evidencias, búsqueda y analítica).

---

## 6. Archivos principales del módulo

| Archivo | Rol |
|---|---|
| `frontend-web/src/app/components/upload-evidencia/` | Panel móvil: cámara, galería, progreso, listado |
| `frontend-web/src/app/services/evidencias.service.ts` | Cliente del flujo multipart (iniciar / subirParte / completar / cancelar) |
| `backend/cursos-service/.../EvidenciasController.java` | Endpoints REST de evidencias |
| `backend/cursos-service/.../EvidenciasService.java` | Reglas de negocio, validaciones y registro en BD |
| `backend/cursos-service/.../StoragePresignService.java` | Firmado de URLs multipart (AWS SDK v2) |
| `backend/cursos-service/.../db/migration/V11__evidencias_practica.sql` | Esquema de la tabla `evidencias_practica` |
