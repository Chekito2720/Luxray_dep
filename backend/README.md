# LuxRay — Backend Microservices

Monorepo multi-módulo Maven con **5 servicios Spring Boot + Spring Cloud Netflix Eureka + API Gateway**.

## 🧩 Servicios

| Servicio              | Puerto | Endpoint                        | Rol                          |
|-----------------------| ------ | ------------------------------- | ---------------------------- |
| discovery-service     | 8761   | /eureka/                        | Service Registry (Eureka)   |
| api-gateway           | 8080   | /api/{auth,cursos,analytics}/** | Enrutamiento + Tracing       |
| auth-service          | 8081   | /auth/login, /auth/registro     | JWT + Usuarios + Bcrypt      |
| cursos-service        | 8082   | /cursos, /inscripciones         | Catálogo + Inscripción       |
| analytics-service     | 8083   | /analytics/dashboard            | KPIs del dashboard           |

Y **3 bases PostgreSQL** independientes (Database-per-Service):

- `auth_db`      (auth-service)
- `cursos_db`    (cursos-service)
- `analytics_db` (analytics-service)

## 🚀 Arranque con Docker

```bash
docker compose up --build
```

- Eureka UI: http://localhost:8761
- Gateway:   http://localhost:8080
- Swagger por servicio a través del gateway:
  - http://localhost:8080/auth/swagger-ui.html (si el gateway lo expone)
  - http://localhost:8081/swagger-ui.html (directo, auth-service)
  - http://localhost:8082/swagger-ui.html (cursos-service)
  - http://localhost:8083/swagger-ui.html (analytics-service)

> Aplica `token` header en auth-service: `Authorization: Bearer <jwt>`

## 👤 Usuario demo

```
email:    demo@luxray.com
password: Demo1234!
```

Disponible únicamente en desarrollo con `DISABLE_SEED_ACCOUNTS=false`; Docker Compose ya lo configura así. En producción, la migración V5 deja inactivas las cuentas conocidas `demo@luxray.com` y `admin@luxray.com` de forma predeterminada.

## 🔐 Seguridad

- **JWT HS256** firmado y validado por `JwtTokenProvider` (common-lib).
- `JwtAuthenticationFilter` extrae `Bearer <token>` y propaga `subject` en `SecurityContextHolder`.
- Config por servicio define qué rutas son públicas (`/auth/login`) y cuáles autenticadas.
- **401** lo decide cada filtro del servicio; el `authInterceptor` del frontend limpia sesión local.

## 🌐 Rutas que el frontend consume (`environment.ts`)

Con el gateway todo el frontend apunta a `http://localhost:8080/api/...`:

```ts
services: {
  api:       'http://localhost:8080/api',
  auth:      'http://localhost:8080/api/auth',          // gateway /api/auth
  cursos:    'http://localhost:8080/api/cursos',
  analytics: 'http://localhost:8080/api/analytics'
}
```

## 📚 Estructura

```
backend/
├── pom.xml                          # Maven multi-module aggregator
├── common-lib/                      # JWT, seguridad, errores, ApiResponse
├── discovery-service/               # Eureka Server
├── api-gateway/                     # Spring Cloud Gateway
├── auth-service/                    # Usuarios + login + registro
├── cursos-service/                  # CRUD cursos, lecciones, progreso
└── analytics-service/               # KPIs del dashboard
```

## 🔄 Migraciones Flyway

Cada servicio arranca con `spring-boot:run` ejecutando las migrations situées en
`src/main/resources/db/migration/V*.sql` en orden cronológico.

## ☁️ Despliegue en Render

`render.yaml` define la topología recomendada para la demostración:

- `luxray-gateway`, `luxray-auth`, `luxray-cursos` y `luxray-analytics`: web services gratuitos construidos desde `backend/Dockerfile`; `SERVICE` selecciona el JAR y `PORT` el puerto de escucha.
- `luxray-db`: una instancia PostgreSQL gratuita compartida. Cada microservicio usa un nombre distinto de tabla de historial Flyway (`SPRING_FLYWAY_TABLE`) para no mezclar migraciones.
- `luxray-evidencias`: MinIO S3-compatible en plan gratuito, sin disco persistente. Las evidencias sobreviven al reinicio del servicio pero no a un redespliegue; es suficiente para la demostración.
- El buscador se sirve desde `cursos-service` (`/api/search` → `/search`), por lo que no se despliega Elasticsearch en la demo.

No se usa Eureka en Render: el gateway recibe `AUTH_SERVICE_URL`, `CURSOS_SERVICE_URL` y `ANALYTICS_SERVICE_URL`, y `EUREKA_CLIENT_ENABLED=false`.

Variables externas necesarias:

- Base de datos: `DATABASE_URL` (Render la inyecta desde Postgres; `entrypoint.sh` la convierte a `SPRING_DATASOURCE_URL`).
- Seguridad/CORS: `JWT_SECRET` compartido y `FRONTEND_URL=https://luxray-front-74c6.vercel.app`.
- Evidencias: `S3_ENDPOINT`, `S3_PUBLIC_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_PATH_STYLE` y `S3_PREFIX`.
- Permisos docentes: `LUXRAY_CURSOS_FALLBACKPROFESORGLOBAL=false` impide que un profesor sin asignaciones vea todos los cursos en producción.
- Cuentas demo: `DISABLE_SEED_ACCOUNTS=false` mantiene activos los usuarios semilla; en un entorno público deben cambiarse las contraseñas inmediatamente.

`SEARCH_REINDEX_ENABLED=false` es el valor predeterminado; `/api/search/reindex` solo inserta datos demo y debe permanecer deshabilitado en producción.

No pongas credenciales de producción en los YAML ni en Vercel. La URL pública del API sí se configura en el frontend como `API_URL`, por ejemplo `https://luxray-gateway-q3l3.onrender.com/api`.

## ❓ Problemas comunes

- **Puerto ocupado**: ajusta `ports` en `docker-compose.yml`.
- **JWT inválido entre servicios**: el secret de `luxray.security.jwt.secret`
  debe ser el mismo en `auth-service`, `cursos-service` y `analytics-service`.
- **Eureka no encuentra servicios**: revisa que `eureka.client.service-url.defaultZone`
  apunte a `http://discovery-service:8761/eureka/` (en Docker) o
  `http://localhost:8761/eureka/` (en local).
