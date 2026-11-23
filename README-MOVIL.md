# 📱 LuxRay Móvil — PWA + Subida Multimedia

La app móvil de LuxRay es una **PWA** (Progressive Web App): la misma web,
instalable desde el navegador del teléfono con icono propio y pantalla completa.
**Sin APK, sin tienda de apps.**

En el móvil aparece una función exclusiva: subir **fotos como evidencia
de práctica** para validar el aprendizaje, mediante **Multipart Upload con
URLs prefirmadas**.

---

## 🏗️ Qué se implementó

| Pieza | Dónde | Qué hace |
|---|---|---|
| **PWA** | `frontend-web/` (`@angular/pwa`) | Manifest, service worker e iconos → instalable desde el navegador móvil |
| **Componente evidencias** | `src/app/components/upload-evidencia/` | Solo visible en móviles: cámara y galería, barra de progreso |
| **Servicio subidas** | `src/app/services/evidencias.service.ts` | Cliente del flujo multipart con URLs prefirmadas |
| **API backend** | `backend/cursos-service/.../EvidenciasController.java` | Emite URLs prefirmadas, une partes, registra evidencias |
| **Almacenamiento** | `StoragePresignService.java` | S3-compatible (AWS S3 / MinIO / s3rver local) |
| **Dos endpoints** | `S3_ENDPOINT` (interno) + `S3_PUBLIC_ENDPOINT` (el que firma las URL que abre el navegador) | Sin esta separación las fotos no se ven |
| **BD** | `V11__evidencias_practica.sql` | Tabla `evidencias_practica` (Flyway la aplica sola al arrancar) |

> El soporte con Capacitor (`frontend-web/android/`, `capacitor.config.ts`)
> queda instalado como **opción futura** si algún día se requiere app nativa,
> pero **no es necesario**: el flujo móvil completo funciona como PWA.

### Cómo funciona la subida (Multipart Upload con URL prefirmada)

```
 MÓVIL (PWA)              BACKEND (cursos-service)         S3 / MinIO
    │  1. POST /cursos/lecciones/{id}/uploads  │                │
    │ ─────────────────────────────────────────▶                │
    │      ← uploadId + URL prefirmada por cada parte            │
    │  2. PUT parte 1 ────────────────────────────────────────▶ │
    │  3. PUT parte 2 ────────────────────────────────────────▶ │
    │     (subida DIRECTA al bucket, sin pasar por el backend)   │
    │  4. POST .../uploads/completar (con los ETags)             │
    │ ─────────────────────────────────────────▶ ── CompleteMultipartUpload ──▶ │
    │  5. La evidencia queda registrada en PostgreSQL            │
```

El archivo nunca pasa por tu backend → no consume recursos del servidor;
las URLs prefirmadas expiran en 30 minutos.

### Detección de móvil

El panel de evidencias se muestra automáticamente en cualquier móvil:
- Dentro de una app nativa (Capacitor) usa el plugin de cámara.
- En el navegador (PWA) usa `<input type="file" capture="environment">`,
  que abre la cámara/galería del teléfono — sin permisos especiales.
- En escritorio (PC) el panel no aparece.

---

## ⚙️ Configuración del almacenamiento (backend)

Variables de entorno de `cursos-service` (ver `application.yml`, sección `luxray.storage`):

| Variable | Descripción | Default |
|---|---|---|
| `S3_ENDPOINT` | Endpoint **interno**: solo lo usa el backend (en Docker: `http://s3:9000`) | vacío |
| `S3_PUBLIC_ENDPOINT` | Endpoint con el que se **firman** las URLs que abrirá el navegador. Apunta al proxy del frontend en el puerto **4200**: `http://localhost:4200` (PC) o `http://<IP-del-PC>:4200` (teléfono por WiFi). Nginx proxifica `/luxray-evidencias` → s3:9000 conservando path y Host → firmas SigV4 válidas. Si se omite se usa `S3_ENDPOINT` | = `S3_ENDPOINT` |
| `S3_REGION` | Región | `us-east-1` |
| `S3_BUCKET` | Bucket destino | `luxray-evidencias` |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | Credenciales | vacío |
| `S3_PATH_STYLE` | `true` para MinIO/s3rver | `true` |

### Emulador local (sin instalar nada)

```powershell
npm install -g s3rver
mkdir $env:TEMP\s3rver\luxray-evidencias
s3rver -p 9000 -a 0.0.0.0 -d $env:TEMP\s3rver --no-vhost-buckets
```

Y arranca cursos-service con:

```powershell
$env:S3_ENDPOINT="http://localhost:9000"
$env:S3_PUBLIC_ENDPOINT="http://localhost:4200"
$env:S3_ACCESS_KEY="S3RVER"; $env:S3_SECRET_KEY="S3RVER"
$env:S3_BUCKET="luxray-evidencias"
```

> **Nota S3 CORS**: ya está resuelto en este proyecto. `docker/s3rver/cors.xml`
> se aplica al bucket al arrancar el contenedor (ver `docker/s3rver/Dockerfile`),
> lo que permite los `PUT` desde el navegador y expone el header `ETag`.
>
> **Nota credenciales**: `s3rver` solo acepta su cuenta por defecto
> `S3RVER` / `S3RVER`. Con otras credenciales responde
> `403 InvalidAccessKeyId`.
>
> **Nota `--no-vhost-buckets`**: `s3rver` intenta sacar el bucket del *hostname*
> de la petición. Como en Docker el servicio se llama `s3` (que no es un bucket),
> todas las llamadas del backend fallaban con `NoSuchBucket: s3`. Esa opción
> fuerza el estilo por ruta (path-style), que es el que usa el backend.

### ⚠️ URLs prefirmadas vs caché del service worker (PWA)

Las URLs de lectura firmadas **caducan en 1 hora**. La PWA tiene un service
worker con caché para funcionar sin conexión; si ese caché guarda durante más
tiempo las respuestas del API (que incluyen las URLs firmadas), el usuario vería
imágenes rotas con errores como:

* `403 AccessDenied — Request has expired`
* `504 Gateway Timeout` (si la respuesta cacheada apunta al host interno `s3`)

Por eso en `frontend-web/ngsw-config.json` el `dataGroup` del API usa
`strategy: "freshness"` (primero red, caché solo si no hay conexión) y
`maxAge: "30m"`. Si cambias ese valor, **nunca lo pongas por encima de 1 hora**.

Tras cambiar `ngsw-config.json` hay que reconstruir el frontend y, en el
navegador/teléfono, forzar la actualización del service worker
(DevTools → Application → Service Workers → *Unregister*, o "Borrar datos del
sitio"). En el teléfono: cerrar la PWA, o reinstalarla.

---

## 📲 Cómo probar en el teléfono (PWA)

### 1. Docker (misma WiFi / red de la universidad) — recomendado

En este modo la API y el bucket S3 se sirven a través del **mismo Nginx
del frontend** (puertos `/api` → gateway y `/luxray-evidencias` → s3),
por lo que **no hace falta abrir otros puertos ni configurar CORS extra**:
el teléfono solo necesita llegar al puerto 4200.

```powershell
docker compose up -d --build
```

En el teléfono abre: **`http://10.13.58.24:4200`** (la IP de tu PC;
consúltala con `ipconfig`). Si cambias de red, reinicia el servicio con
`set S3_PUBLIC_ENDPOINT=http://<TU_IP>:4200` y
`docker compose up -d cursos-service`.

> En este modo funciona TODO: login, cursos y subida de evidencias
> (el navegador abre la cámara con el atributo `capture`). Lo único que no
> registra en HTTP inseguro es el service worker (caché offline).

### 2. Instalación como PWA real (requiere HTTPS)
Despliega el build de producción (Railway/Vercel, como ya tienes) — con HTTPS
el navegador mostrará **"Añadir a pantalla de inicio"** y la app queda con
icono propio, pantalla completa y caché offline.

```powershell
cd frontend-web
npm run build:prod
```

### 3. Probar la subida de evidencias
1. Inicia sesión desde el móvil.
2. Entra a un curso → selecciona cualquier lección.
3. Verás el panel "📱 Evidencia de práctica": Tomar foto / Galería.
4. La subida muestra progreso por partes; al final queda registrada y listada.
5. API también en Swagger: `http://localhost:8082/swagger-ui.html`
   (sección "Evidencias de práctica").
