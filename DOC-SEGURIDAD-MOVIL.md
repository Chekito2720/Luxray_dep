# Documentación técnica: Medidas de seguridad básicas en la aplicación móvil

> Módulo móvil de **LuxRay** (PWA + Wear OS) — Seguridad: validación de entradas,
> manejo seguro de credenciales y prevención de exposición de información sensible
> Autor: [Tu nombre] · Proyecto: [Nombre de la materia] · Fecha: [fecha]

---

## 1. Introducción

El módulo móvil de LuxRay maneja datos sensibles de los alumnos: credenciales de
acceso (login), tokens de sesión (JWT) y evidencias fotográficas de práctica. Este
documento describe las **medidas básicas de seguridad implementadas y comprobadas**
en las tres capas de la solución:

| Capa | Tecnología | Rol en la app móvil |
|---|---|---|
| **Cliente móvil (PWA)** | Angular (`frontend-web/`) | Login, subida de evidencias con cámara |
| **Wearable** | Flutter (`luxraywaer/`) | Buscador Wear OS |
| **Backend** | Java/Spring (`backend/`) | Auth, evidencias, API Gateway |

Cada medida se aplicó según el principio de **defensa en profundidad**: la misma
regla se valida en el cliente (feedback inmediato al usuario) y se **re-valida**
en el servidor (que nunca confía en el cliente).

```
 MEDIDA                    CLIENTE MÓVIL                BACKEND
 ────────────────────────  ────────────────────────────  ─────────────────────────────
 Validación de entradas →  lista blanca MIME + tamaño,   path traversal, caracteres de
                           longitud de contraseña         control, MIME, tamaño, partes
 Manejo de credenciales →  solo email en "recordarme",    BCrypt + hash cebo anti-timing,
                           togle mostrar/ocultar          secreto JWT auditado
 No exposición de datos →  mensajes sanitizados,          500 genérico, detalle solo en
                           sin logs en consola            log del servidor
```

---

## 2. Validación de entradas

### 2.1 ¿Qué se valida y dónde?

| Entrada | Cliente (PWA / Flutter) | Servidor |
|---|---|---|
| Contraseña (login/registro) | Requerida, mín. 8 caracteres, medidor de fuerza | `@NotBlank @Size(min=8,max=64)` (Bean Validation) |
| Correo | Formato `usuario@dominio.tld` | `@NotBlank @Email` |
| Archivo de evidencia (tipo) | Lista blanca MIME: JPEG, PNG, WebP, MP4, WebM, MOV | Misma lista blanca (`esTipoPermitido`) |
| Archivo de evidencia (tamaño) | ≤ 500 MB antes de iniciar la subida | ≤ 500 MB y > 0 bytes |
| Nombre de archivo | Se regenera: prefijo + timestamp + nombre recortado | Rechaza `..`, `/`, `\`, caracteres de control, > 200 chars |
| Partes del multipart | Recuento generado por el propio cliente (slice) | Lista de 1 a 10 000 partes obligatoria |
| Query de búsqueda (Wear OS) | Se eliminan caracteres de control, máx. 80 chars (`maxLength` en el TextField) | El buscador recibe solo queries ya saneadas |

### 2.2 El hueco corregido

Antes de esta mejora, el panel de subida móvil **aceptaba cualquier archivo** y,
si el navegador no reportaba su tipo, lo etiquetaba por defecto como `image/jpeg`:

```ts
// ANTES (inseguro): un .exe sin MIME llegaba al backend como "image/jpeg"
contentType: contentType || 'image/jpeg'
```

```ts
// AHORA: rechazo explícito contra la misma lista blanca del backend
if (!this.TIPOS_PERMITIDOS.has(contentType)) {
  this.fallar('Tipo de archivo no permitido. Usa JPG, PNG, WebP, MP4, WebM o MOV.');
  return;
}
```

Además, la validación se aplica en `subirMultipart()` —no solo en el selector de
archivos—, por lo que **también cubre las rutas de cámara nativa (Capacitor) y
captura web (getUserMedia)**.

### 2.3 Validación de nombres de archivo en el servidor (path traversal)

El backend trata el nombre de archivo como entrada hostil:

```java
private void validarNombreArchivo(String nombre) {
    String s = nombre.trim();
    if (s.isEmpty()) throw new BusinessException("nombreArchivo es requerido");
    if (s.length() > 200) throw new BusinessException("nombreArchivo demasiado largo");
    if (s.contains("..") || s.contains("/") || s.contains("\\")
            || s.matches(".*[\\x00-\\x1f].*")) {
        throw new BusinessException("nombreArchivo contiene caracteres no permitidos");
    }
}
```

Esto bloquea ataques clásicos de **path traversal** (`../../etc/passwd`), inyección
de caracteres de control y nombres kilométricos. La validación se repite en el
paso de **confirmación** (`completar`), porque el cliente podría declarar allí
metadatos distintos de los validados al **iniciar** la subida.

### 2.4 Saneamiento de la query en el wearable

```dart
static String sanitizar(String q) {
  final limpio = q.replaceAll(RegExp(r'[\x00-\x1f\x7f]'), '').trim();
  return limpio.length > kMaxQuery ? limpio.substring(0, kMaxQuery) : limpio;
}
```

Una query inválida (vacía, de 1 carácter o de solo caracteres de control)
**jamás genera una petición HTTP**: se comprueba con un `MockClient` que cuenta
las llamadas y exige `expect(llamadas, 0)`.

---

## 3. Manejo seguro de credenciales

### 3.1 Almacenamiento: solo hashes BCrypt

La contraseña **nunca** se guarda ni viaja en claro:

```
 Registro:  password ──▶ BCryptPasswordEncoder.encode()  ──▶ usuarios.password_hash
 Login:     password ──▶ BCryptPasswordEncoder.matches()  ──▶ ✓ / ✗
 Respuesta: token JWT + datos públicos del usuario (sin password ni hash)
```

### 3.2 Anti-enumeración de usuarios (mensaje y timing)

Un login fallido responde **exactamente lo mismo** tanto si el correo no existe
como si la contraseña es incorrecta: *"Credenciales inválidas"*. Pero además,
un atacante podía distinguir ambos casos **midiendo el tiempo de respuesta**:
si el correo no existía, el servidor no ejecutaba BCrypt y contestaba en
milisegundos. La corrección ejecuta una comparación BCrypt contra un *hash cebo*
(dummy) aunque el usuario no exista, igualando el tiempo:

```java
Usuario u = usuarios.findByEmailAndActivoTrue(req.email().toLowerCase()).orElse(null);
if (u == null) {
    // Igualar el tiempo de respuesta: comparación BCrypt contra un hash cebo
    passwordEncoder.matches(req.password(), HASH_CEBO);
    throw new BusinessException("Credenciales inválidas");
}
if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
    throw new BusinessException("Credenciales inválidas");
}
```

### 3.3 Secretos de firma (JWT)

- El secreto JWT vive en la variable de entorno `JWT_SECRET`; **no hay secretos
  en el código**.
- `JwtTokenProvider` **audita al arrancar**: si el secreto es uno de los valores
  de desarrollo conocidos en el repositorio, emite un `WARN` explícito en el log:

  ```
  ADVERTENCIA DE SEGURIDAD: el secreto JWT es el valor de desarrollo por defecto.
  Define la variable JWT_SECRET con un secreto aleatorio (>= 32 bytes) en producción.
  ```

### 3.4 En el dispositivo móvil

- **"Recordarme" guarda solo el correo** — la contraseña nunca se persiste en
  `localStorage`; el token se elimina en `logout()` y al recibir un 401.
- Las URLs de subida al bucket son **prefirmadas y efímeras (30 min)**: las
  llaves S3 viven solo en el backend y el `PUT` de cada parte se hace con
  `XMLHttpRequest` crudo, **sin el header `Authorization`**, de modo que el JWT
  del usuario nunca viaja hacia el almacenamiento.
- Los campos de contraseña usan `type="password"` con botón mostrar/ocultar.

---

## 4. Prevención de exposición de información sensible

### 4.1 El problema: el 500 filtraba internals

El manejador global de errores devolvía al cliente el mensaje crudo de la
excepción. Un fallo de conexión a la base de datos exponía, por ejemplo:

```json
// ANTES — respuesta al cliente (vulnerabilidad de divulgación de información)
{
  "status": 500,
  "message": "Connection refused: jdbc:postgresql://admin:s3creto@prod-db:5432/luxray"
}
```

Cadena de conexión, host, usuario y hasta pistas de credenciales viajaban al
teléfono (y quedarían en la consola del navegador).

### 4.2 La corrección: detalle al log del servidor, genérico al cliente

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiError> handleAll(Exception ex, HttpServletRequest req) {
    // Detalle completo SOLO en el log del servidor (con stacktrace para depuración)
    log.error("Error no controlado en {}: {}", req.getRequestURI(), ex.getMessage(), ex);
    // Al cliente: mensaje genérico, sin exponer internals
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
            MSG_ERROR_INTERNO, req);
}
```

```json
// DESPUÉS — lo que llega al cliente
{
  "status": 500,
  "message": "Error interno del servidor. Intenta más tarde."
}
```

El **trace-id** para depurar (`X-Request-Id`) sigue disponible, pero el detalle
técnicamente queda únicamente en el log del servidor. Los errores de **negocio**
(“El correo ya está registrado”) sí se propagan, porque están redactados para
el usuario.

### 4.3 Entradas malformadas y mensajes en el móvil

| Caso | Comportamiento ahora |
|---|---|
| JSON malformado al backend | `400` con mensaje genérico “La solicitud no es válida.” (antes: 500 con el error del parser) |
| Error técnico durante la subida | `mensajeSeguro()` en la PWA: solo mensajes de negocio del backend o del propio cliente; nunca volcados de red/excepciones |
| Fallo de red o respuesta corrupta en el wearable | Degradación silenciosa a “sin resultados”; nunca se propaga la excepción ni la query del usuario |
| Consola del navegador | `main.ts` sin volcados de objetos de error |

---

## 5. Evidencias de comprobación

Todas las medidas se verificaron de forma automatizada; se añadieron **24 pruebas
nuevas** (10 en el wearable, 14 en el backend) y se ejecutaron las suites existentes.

### 5.1 Wearable (Flutter) — `luxraywaer/test/seguridad_test.dart` (10 tests)

```
✓ Validación de entradas — sanitizar() elimina caracteres de control
✓ Validación de entradas — sanitizar() recorta la longitud máxima a 80 caracteres
✓ Validación de entradas — sanitizar() recorta espacios alrededor
✓ query de 1 carácter no toca la red (llamadas = 0)
✓ query de solo caracteres de control no toca la red (llamadas = 0)
✓ query de 500 caracteres se trunca a 80 antes de enviar
✓ error HTTP 500 del servidor no lanza ni expone detalles → degrada a vacío
✓ JSON malformado del servidor degrada a respuesta vacía
✓ fallo de red degrada a respuesta vacía sin propagar la excepción
✓ la query del usuario ("password=secreto123") nunca aparece expuesta
```

Resultado: `flutter analyze` → **0 issues** · `flutter test` → **17/17 pasados**
(10 de seguridad + 7 existentes del módulo).

### 5.2 Backend — GlobalExceptionHandlerTest (3 tests)

```
✓ errorInternoNoExponeDetallesAlCliente
    — excepción con "jdbc:postgresql://admin:s3creto@prod-db" → el cliente
      recibe "Error interno del servidor. Intenta más tarde."
    — assertFalse(message.contains("s3creto"))
✓ jsonMalformadoDevuelve400Generico
    — "JSON parse error: Unexpected char" → 400 "La solicitud no es válida."
✓ errorNegocioSiExplicaElMotivoAlUsuario
    — los mensajes de negocio (400) siguen llegando al usuario
```

### 5.3 Backend — AuthServiceTest (4 tests)

```
✓ usuarioInexistente_mismoMensajeQuePasswordIncorrecta → "Credenciales inválidas"
✓ passwordIncorrecta_mensajeIdentico_alDeUsuarioInexistente (indistinguibles)
✓ usuarioInexistente_ejecutaBcryptDeTodasFormas → verify(encoder).matches(...) [hash cebo]
✓ loginExitoso_laPasswordNuncaApareceEnLaRespuesta (ni en token, ni refresh, ni BD en claro)
```

### 5.4 Backend — EvidenciasServiceTest (7 tests)

```
✓ rechazaPathTraversalEnNombreDeArchivo  → "../../etc/passwd", "a/b.jpg", "a\b.jpg"
✓ rechazaCaracteresDeControlEnNombre     → NUL, tab
✓ rechazaTipoMimeFueraDeListaBlanca      → application/pdf, x-msdownload, image/gif, null
✓ rechazaTamanoExcesivoOCero             → 600 MB, 0, -5
✓ aceptaUnaSolicitudValida               → uploadId emitido correctamente
✓ completarRechazaListaDePartesVacia
✓ completarRevalidaTipoMime               → el cliente no puede colar un exe en la confirmación
```

### 5.5 Resumen de ejecución

| Comando | Resultado |
|---|---|
| `flutter analyze` | 0 issues |
| `flutter test` (wearable) | **17/17** ✓ |
| `mvn test` (common-lib + auth + cursos) | **14/14** ✓ (3 + 4 + 7) |
| `mvn compile` (7 módulos del reactor) | BUILD SUCCESS |
| `npm run build` (PWA Angular) | Compilación completa, sin errores |

---

## 6. Archivos principales de la medida

| Archivo | Cambio de seguridad |
|---|---|
| `frontend-web/src/app/components/upload-evidencia/upload-evidencia.ts` | Lista blanca MIME + tope 500 MB en cliente; `mensajeSeguro()` para la UI |
| `frontend-web/src/app/pages/auth/login/login.ts` | Validación de contraseña mín. 8 chars en cliente |
| `frontend-web/src/main.ts` | Sin volcados de errores técnicos en consola |
| `luxraywaer/lib/services/luxray_api.dart` | `sanitizar()`: control chars + longitud máx. 80 |
| `luxraywaer/lib/screens/home_screen.dart` | `maxLength: 80` en el campo de búsqueda |
| `luxraywaer/test/seguridad_test.dart` | 10 pruebas de seguridad del wearable |
| `backend/common-lib/.../exception/GlobalExceptionHandler.java` | 500 genérico + log del detalle solo en servidor; 400 para JSON malformado |
| `backend/common-lib/.../jwt/JwtTokenProvider.java` | Auditoría del secreto JWT por defecto |
| `backend/auth-service/.../service/AuthService.java` | Hash cebo anti-timing; mensaje único de login |
| `backend/cursos-service/.../service/EvidenciasService.java` | `validarNombreArchivo()` (path traversal); revalidación en `completar` |
| `backend/*/src/test/...` | 14 pruebas backend (handler de errores, credenciales, validaciones) |
