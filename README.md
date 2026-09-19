# chat-conversacion

Servicio de chat en tiempo real, comunicación 1 a 1 entre dos usuarios, por **tres
protocolos**: WebSocket, REST (solo el historial) y gRPC. Los mensajes son de texto por ahora
y se persisten en **MongoDB**.

> Estado actual: primera implementación (nace como copia del arquetipo MVC compartido, ver
> `chat-registro/` y `chat-gateway/`). Cubre el envío 1 a 1 (WebSocket y gRPC) y el historial
> de una conversación (REST y gRPC) — un mensaje mandado por cualquiera de los tres protocolos
> se reenvía en tiempo real sin importar por cuál de los otros dos esté conectado el
> destinatario. `{usuario}` es el `username` de `chat-registro` (mismo formato, validado al
> conectar); falta la autenticación real — nada comprueba todavía que quien se conecta sea el
> dueño de ese username — ver `CLAUDE.md`, `docs/contratos-api.md` §2.1 y
> `docs/contrato-grpc-conversacion.md` §1.

## Arquitectura

Paquete por feature bajo `com.arquetipo.demo`, mismo patrón que `chat-registro/`:

- `common/` — infraestructura transversal: `web/GlobalExceptionHandler` (Problem Details),
  `common/exception/`, `common/config/MongoAuditingConfig` (poblado automático de
  `enviadoEn`) y `common/grpc/` (arranca/detiene el servidor gRPC embebido, mismo patrón que
  `chat-registro` — genérico, no sabe nada del chat en sí).
- `conversacion/` — la feature: `domain/Mensaje` (documento de MongoDB), `repository/`,
  `mapper/`, `service/`:
  - `ConversacionService` — persiste el mensaje y expone el historial; no conoce WebSocket ni
    gRPC, solo notifica a `NotificadorTiempoReal` tras persistir.
  - `NotificadorTiempoReal` — registro en memoria de "quién está conectado y por dónde
    avisarle", **compartido entre WebSocket y gRPC**: es lo que hace que un mensaje mandado
    por un protocolo se reenvíe a alguien conectado por el otro.

  y dos capas de transporte, ambas delgadas (delegan todo en lo de arriba):
  - `web/` (HTTP): `ChatWebSocketHandler` en `/ws/chat/{usuario}` + `UsuarioHandshakeInterceptor`
    (valida el formato del `{usuario}` de la URL, rechaza el *handshake* con `400` si no) +
    `ConversacionController` — `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`, historial
    paginado (`page`/`size`/`sort`, ver `docs/contratos-api.md` §3). CORS habilitado ahí mismo
    (`@CrossOrigin`) via `CORS_ALLOWED_ORIGINS`.
  - `grpc/` (puerto 9091): `ConversacionGrpcController` (`Chat` bidi streaming + `Historial`
    unario) + `UsuarioMetadataInterceptor` (equivalente gRPC de `UsuarioHandshakeInterceptor`:
    valida la cabecera de metadata `usuario` del stream `Chat`). Ver
    `docs/contrato-grpc-conversacion.md`.

## Stack

| Área | Elección |
|------|----------|
| Framework | Spring Boot 4.1.1 (`spring-boot-starter-webmvc` + `spring-boot-starter-websocket`) |
| gRPC | `io.grpc` a mano (sin starter de terceros), servidor embebido — mismo patrón que `chat-registro` |
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
`spring.mongodb.uri` en `application.yml`, configurable con la variable de entorno
`MONGODB_URI`). **Ojo:** en Spring Boot 4.1 la conexión a Mongo vive bajo `spring.mongodb`,
no bajo `spring.data.mongodb` (ese prefijo cambió de significado: ahora es solo para opciones
de Spring Data como `auto-index-creation`, ya no tiene `uri`/`database`) — un `spring.data.mongodb.uri`
en `application.yml` se ignora en silencio y la app cae al default interno de Spring Boot
(`mongodb://localhost/test`), sin ningún error visible.

```bash
./gradlew bootRun
```

> Gradle necesita un JDK 17+ para ejecutarse y la toolchain compila con Java 25. Si tu
> `JAVA_HOME` apunta a un JDK antiguo, ajústalo o descomenta `org.gradle.java.home` en
> `gradle.properties`.

| Recurso | URL |
|---------|-----|
| WebSocket del chat | ws://localhost:8082/ws/chat/{usuario} |
| Historial de una conversación (paginado) | http://localhost:8082/api/v1/conversaciones/{usuarioA}/{usuarioB}?page=0&size=20 |
| gRPC (`Chat` + `Historial`) | localhost:9091 — ver `docs/contrato-grpc-conversacion.md` |
| Swagger UI | http://localhost:8082/swagger-ui.html |
| OpenAPI JSON | http://localhost:8082/v3/api-docs |
| Actuator health | http://localhost:8082/actuator/health |

Paginación del historial (REST y gRPC): `page` (0-indexada), `size` (por defecto 20, máx. 100)
y `sort` (por defecto `enviadoEn,asc`) — mismos defaults que `spring.data.web.pageable` en
`application.yml`.

Orígenes permitidos para el WebSocket: variable de entorno `WEBSOCKET_ALLOWED_ORIGINS`
(lista separada por comas; por defecto solo `http://localhost:3000`, el frontend en
desarrollo). Para el endpoint REST del historial es una variable **distinta**:
`CORS_ALLOWED_ORIGINS` (y `CORS_ALLOW_CREDENTIALS`, por defecto `false`) — mismo criterio que
`chat-registro`. Un origen fuera de la lista recibe `403 Invalid CORS request`. El gRPC no
tiene CORS (no aplica, no es un protocolo de navegador): `GRPC_SERVER_ENABLED` (por defecto
`true`) y `GRPC_SERVER_PORT` (por defecto `9091`) controlan su arranque.

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
