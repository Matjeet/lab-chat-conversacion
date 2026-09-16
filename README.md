# chat-conversacion

Servicio de chat en tiempo real por **WebSockets**: en principio, comunicación 1 a 1 entre dos
usuarios. Los mensajes son de texto por ahora y se persisten en **MongoDB**.

> Estado actual: primera implementación (nace como copia del arquetipo MVC compartido, ver
> `chat-registro/` y `chat-gateway/`). Cubre el envío 1 a 1 por WebSocket y el historial de una
> conversación; falta decidir la identificación real del usuario (hoy es solo un `{usuario}`
> de la URL, sin validar contra ningún proveedor — ver `CLAUDE.md`).

## Arquitectura

Paquete por feature bajo `com.arquetipo.demo`, mismo patrón que `chat-registro/`:

- `common/` — infraestructura transversal: `web/GlobalExceptionHandler` (Problem Details),
  `common/exception/` y `common/config/MongoAuditingConfig` (poblado automático de
  `enviadoEn`).
- `conversacion/` — la feature: `domain/Mensaje` (documento de MongoDB), `repository/`,
  `mapper/`, `service/ConversacionService` (persiste el mensaje; no conoce sesiones de
  WebSocket) y `web/`:
  - `ChatWebSocketHandler` — punto de entrada del chat, en `/ws/chat/{usuario}`. Persiste
    cada mensaje entrante y lo reenvía al remitente y al destinatario si están conectados
    (registro de sesiones en memoria, no apto para más de una instancia todavía).
  - `UsuarioHandshakeInterceptor` — saca el `{usuario}` de la URL de conexión.
  - `ConversacionController` — `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`, historial
    para que un cliente cargue los mensajes previos al conectarse.

## Stack

| Área | Elección |
|------|----------|
| Framework | Spring Boot 4.1.1 (`spring-boot-starter-webmvc` + `spring-boot-starter-websocket`) |
| Lenguaje | Java 25 (toolchain de Gradle) |
| Build | Gradle (wrapper incluido) |
| Persistencia | Spring Data MongoDB |
| Validación | Bean Validation (`spring-boot-starter-validation`) |
| Errores | RFC 9457 *Problem Details* vía `@RestControllerAdvice` |
| Docs API | springdoc-openapi + Swagger UI |
| Observabilidad | Spring Boot Actuator |
| Utilidades | Lombok, DevTools |

## Arrancar

Requiere una instancia de MongoDB local (por defecto `mongodb://localhost:27017`, ver
`spring.data.mongodb.uri` en `application.yml`, configurable con la variable de entorno
`MONGODB_URI`).

```bash
./gradlew bootRun
```

> Gradle necesita un JDK 17+ para ejecutarse y la toolchain compila con Java 25. Si tu
> `JAVA_HOME` apunta a un JDK antiguo, ajústalo o descomenta `org.gradle.java.home` en
> `gradle.properties`.

| Recurso | URL |
|---------|-----|
| WebSocket del chat | ws://localhost:8082/ws/chat/{usuario} |
| Historial de una conversación | http://localhost:8082/api/v1/conversaciones/{usuarioA}/{usuarioB} |
| Swagger UI | http://localhost:8082/swagger-ui.html |
| OpenAPI JSON | http://localhost:8082/v3/api-docs |
| Actuator health | http://localhost:8082/actuator/health |

Orígenes permitidos para el WebSocket: variable de entorno `WEBSOCKET_ALLOWED_ORIGINS`
(lista separada por comas; por defecto solo `http://localhost:3000`, el frontend en
desarrollo).

Tests: `./gradlew test` · Empaquetar: `./gradlew bootJar` · Docker: `docker build -t chat-conversacion .`

## Contrato de errores

Todas las respuestas de error siguen RFC 9457:

```json
{
  "type": "urn:problem-type:validation-error",
  "title": "Datos invalidos",
  "status": 400,
  "detail": "El cuerpo de la peticion no supero la validacion",
  "instance": "/api/v1/...",
  "timestamp": "2026-01-01T10:00:00Z",
  "errors": [{ "field": "...", "message": "..." }]
}
```
