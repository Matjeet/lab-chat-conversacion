# chat-conversacion

Servicio de chat en tiempo real, comunicación 1 a 1 entre dos usuarios, por **tres
protocolos**: WebSocket, REST (solo el historial) y gRPC. Los mensajes son de texto por ahora
y se persisten en **MongoDB**. Antes de poder chatear con alguien hace falta crear una
**solicitud de chat** (`CrearSolicitud`, gRPC), que valida ambos usernames contra
`chat-registro` y avisa por **RabbitMQ**; aceptarla o rechazarla no está implementado
todavía (ver más abajo).

> Estado actual: primera implementación (nace como copia del arquetipo MVC compartido, ver
> `chat-registro/` y `chat-gateway/`). Cubre el envío 1 a 1 (WebSocket y gRPC), el historial
> de una conversación (REST y gRPC), la lista de chats paginada por cursor (gRPC) y la
> creación de solicitudes de chat (gRPC, con validación contra `chat-registro` y notificación
> por RabbitMQ) — un mensaje mandado por cualquiera de los tres protocolos
> se reenvía en tiempo real sin importar por cuál de los otros dos esté conectado el
> destinatario. `{usuario}` es el `username` de `chat-registro` (mismo formato, validado al
> conectar); falta la autenticación real — nada comprueba todavía que quien se conecta sea el
> dueño de ese username — ver `CLAUDE.md`, `docs/contratos-api.md` §2.1 y
> `docs/contrato-grpc-conversacion.md` §1.

## Arquitectura

Paquete por feature bajo `com.arquetipo.demo`, mismo patrón que `chat-registro/`:

- `common/` — infraestructura transversal: `web/GlobalExceptionHandler` (Problem Details),
  `common/exception/`, `common/config/MongoAuditingConfig` (poblado automático de
  `enviadoEn`/`creadaEn`), `common/config/RabbitMqConfig` (exchange de notificaciones AMQP) y
  `common/grpc/` (arranca/detiene el servidor gRPC embebido, mismo patrón que
  `chat-registro` — genérico, no sabe nada del chat en sí).
- `conversacion/` — la feature: `domain/Mensaje` y `domain/SolicitudChat` (documentos de
  MongoDB), `repository/` (`MensajeRepository` + `MensajeRepositoryCustom`/`Impl`, esta
  última con la agregación de Mongo de `listaChats`: agrupa por interlocutor y se queda con
  el mensaje más reciente de cada uno; y `SolicitudChatRepository`), `mapper/`, `service/`:
  - `ConversacionService` — persiste el mensaje, expone el historial y la lista de chats; no
    conoce WebSocket ni gRPC, solo notifica a `NotificadorTiempoReal` tras persistir.
  - `SolicitudChatService` — crea una solicitud de chat: valida que `solicitante` y
    `solicitado` no sean el mismo usuario, que ambos existan en `chat-registro` (vía
    `RegistroGrpcClient`) y que no exista ya una solicitud **pendiente** entre ambos; si ya
    existe una, no persiste nada nuevo ni notifica, solo informa del estado actual. Si no,
    persiste con `pendiente: true` y notifica por `NotificadorAmqp`.
  - `NotificadorTiempoReal` — registro en memoria de "quién está conectado y por dónde
    avisarle", **compartido entre WebSocket y gRPC**: es lo que hace que un mensaje mandado
    por un protocolo se reenvíe a alguien conectado por el otro.
  - `NotificadorAmqp` — publica en el exchange de RabbitMQ (`RabbitMqConfig`); un fallo al
    publicar se registra pero no revierte la solicitud ya persistida (aviso best-effort).
  - `ChatCursor` — codifica/decodifica el cursor opaco de paginación de `listaChats`.

  y dos capas de transporte, ambas delgadas (delegan todo en lo de arriba):
  - `web/` (HTTP): `ChatWebSocketHandler` en `/ws/chat/{usuario}` + `UsuarioHandshakeInterceptor`
    (valida el formato del `{usuario}` de la URL, rechaza el *handshake* con `400` si no) +
    `ConversacionController` — `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`, historial
    paginado (`page`/`size`/`sort`, ver `docs/contratos-api.md` §3). CORS habilitado ahí mismo
    (`@CrossOrigin`) via `CORS_ALLOWED_ORIGINS`.
  - `grpc/` (puerto 9091): `ConversacionGrpcController` (`Chat` bidi streaming, `Historial`
    unario, `ListaChats` unario — lista de chats con el último mensaje de cada uno, paginada
    por **cursor** para scroll infinito —, y `CrearSolicitud` unario — crea una solicitud de
    chat, valida contra `chat-registro` y notifica por RabbitMQ; ninguno de los dos tiene
    equivalente todavía en REST/WebSocket) + `UsuarioMetadataInterceptor` (equivalente gRPC de
    `UsuarioHandshakeInterceptor`: valida la cabecera de metadata `usuario` del stream `Chat`,
    no aplica al resto de rpc). Ver `docs/contrato-grpc-conversacion.md`.
- `registro/grpc/` — `RegistroGrpcClient`: cliente gRPC de `chat-registro` (copia local y
  mínima de su `.proto`, solo `ExisteUsername`), usado por `SolicitudChatService` para
  validar usernames. Mismo patrón que el cliente equivalente en `chat-gateway`.

## Stack

| Área | Elección |
|------|----------|
| Framework | Spring Boot 4.1.1 (`spring-boot-starter-webmvc` + `spring-boot-starter-websocket`) |
| gRPC | `io.grpc` a mano (sin starter de terceros), servidor embebido — mismo patrón que `chat-registro` |
| Lenguaje | Java 25 (toolchain de Gradle) |
| Build | Gradle (wrapper incluido) |
| Persistencia | Spring Data MongoDB |
| Mensajería | RabbitMQ (`spring-boot-starter-amqp`) — notificaciones de eventos (solicitudes de chat) |
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

También requiere **RabbitMQ** (por defecto `localhost:5672`, ver `spring.rabbitmq.*` /
variables `RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`) — la
conexión es perezosa (no bloquea el arranque), pero `CrearSolicitud` falla en tiempo de
petición si no hay broker. Para levantar uno local con Podman:

```bash
podman run -d --name rabbitmq-dev --hostname rabbitmq-dev \
  -p 5672:5672 -p 15672:15672 rabbitmq:4-management
```

Panel de administración en http://localhost:15672 (usuario/clave por defecto: `guest`/`guest`).

Y, para que `CrearSolicitud` pueda validar los usernames, una instancia de `chat-registro`
alcanzable por gRPC (por defecto `localhost:9090`, ver `servicios.registro.*` / variables
`REGISTRO_GRPC_HOST`/`REGISTRO_GRPC_PORT`) — ver `chat-registro/README.md`.

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
| gRPC (`Chat` + `Historial` + `ListaChats` + `CrearSolicitud`) | localhost:9091 — ver `docs/contrato-grpc-conversacion.md` |
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

Tests: `./gradlew test` · Empaquetar: `./gradlew bootJar`

## Imagen de contenedor

`Dockerfile` es multi-stage (build con `eclipse-temurin:25-jdk` + Gradle, runtime con
`eclipse-temurin:25-jre`, corre como usuario no root) — funciona igual con Docker o con
[Podman](https://podman.io/). Expone `8082` (HTTP: WebSocket + REST) y `9091` (gRPC).

```bash
podman build -t chat-conversacion .
```

Para correrla necesita llegar a una MongoDB, un RabbitMQ y (para `CrearSolicitud`) a
`chat-registro` por gRPC — si esos corren en tu máquina (no en otro contenedor), `localhost`
**dentro** del contenedor no es tu máquina: con Podman Machine (Windows/Mac) usa el host
especial `host.containers.internal` (con Docker Desktop es `host.docker.internal`):

```bash
podman run -d --name chat-conversacion \
  -p 8082:8082 -p 9091:9091 \
  -e MONGODB_URI="mongodb://host.containers.internal:27017/chat_conversacion" \
  -e RABBITMQ_HOST="host.containers.internal" \
  -e REGISTRO_GRPC_HOST="host.containers.internal" \
  chat-conversacion
```

Probado en caliente con Podman: build completo (incluida la generación de los stubs de gRPC
via el plugin `com.google.protobuf`, que sí necesita red durante el build para bajar
`protoc`/`protoc-gen-grpc-java`) + contenedor arrancando, conectando a la Mongo del host, y
respondiendo tanto el REST (`/api/v1/conversaciones/...`) como el gRPC (`Historial`) con los
mismos datos que ve la app corriendo fuera del contenedor.

Para levantar el sistema completo (chat-conversacion + chat-registro + RabbitMQ + Mongo +
MySQL + chat-gateway) de una vez, ver `docker-compose.yml` en la raíz de `Proyectos/Chat/`.

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
