# Contrato gRPC — Chat (`chat-conversacion`)

Referencia para que **otro servicio** consuma por gRPC el chat de `chat-conversacion`: envío
y reenvío de mensajes en tiempo real (equivalente al WebSocket) y el historial paginado de una
conversación (equivalente al REST), sin necesidad de leer el código de este repositorio.

Este es un **tercer protocolo**, no un reemplazo: `chat-conversacion` sigue exponiendo el
WebSocket (`/ws/chat/{usuario}`) y el REST del historial
(`GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`) — ver
[`contratos-api.md`](contratos-api.md). Los tres comparten la misma persistencia en MongoDB y
la misma entrega en tiempo real: **un mensaje mandado por cualquiera de los tres protocolos se
reenvía, si el destinatario está conectado, sin importar por cuál de los otros dos esté
conectado él**. Si tu cliente ya habla gRPC (p. ej. otro backend, o `chat-gateway` si algún día
expone REST hacia este servicio), usa este contrato; si es un navegador, probablemente
quieras el WebSocket + REST.

La fuente de verdad ejecutable es el propio `.proto`:
[`src/main/proto/conversacion.proto`](../src/main/proto/conversacion.proto).

---

## 1. Cómo conectarse

| Aspecto | Valor |
|---|---|
| Protocolo | gRPC (HTTP/2), **texto plano, sin TLS** (`-plaintext` en `grpcurl`, canal `usePlaintext()` en el cliente) |
| Host:puerto (local) | `localhost:9091` — puerto TCP propio del servidor gRPC (independiente de `server.port`, el HTTP del WebSocket/REST, `8082`) |
| Variable de entorno del servidor | `GRPC_SERVER_PORT` (por defecto `9091`); `GRPC_SERVER_ENABLED=false` apaga el servidor por completo |
| Paquete proto | `com.arquetipo.demo.conversacion.grpc` |
| Servicio | `ConversacionGrpcService` |
| Métodos (rpc) | `Chat` — bidi streaming, equivalente al WebSocket. `Historial` — unario, equivalente al REST. |
| Reflexión de servicio | Habilitada (`io.grpc:grpc-services`) — un cliente puede descubrir el contrato sin tener el `.proto`, ver §6 |
| Autenticación | Ninguna real todavía — mismo aviso que el WebSocket (§2.1): `Chat` exige una cabecera `usuario` con **formato** válido, pero nada comprueba que quien la manda sea el dueño real de ese username. **No usar con datos reales hasta que se resuelva** (ver `CLAUDE.md`). |

> El puerto real por entorno lo define infraestructura. Pregunta al equipo de infraestructura
> la dirección de tu entorno si no es `localhost:9091`.

---

## 2. El `.proto`

```proto
syntax = "proto3";

package com.arquetipo.demo.conversacion.grpc;

option java_multiple_files = true;
option java_package = "com.arquetipo.demo.conversacion.grpc";
option java_outer_classname = "ConversacionProto";

service ConversacionGrpcService {
  rpc Chat (stream MensajeSaliente) returns (stream MensajeEntregado);
  rpc Historial (HistorialRequest) returns (HistorialResponse);
}

message MensajeSaliente {
  string destinatario = 1;
  string contenido = 2;
}

message MensajeEntregado {
  string id = 1;
  string remitente = 2;
  string destinatario = 3;
  string contenido = 4;
  string enviado_en = 5;
}

message HistorialRequest {
  string usuario_a = 1;
  string usuario_b = 2;
  int32 page = 3;
  int32 size = 4;
  string sort = 5;
}

message HistorialResponse {
  repeated MensajeEntregado content = 1;
  int32 page = 2;
  int32 size = 3;
  int64 total_elements = 4;
  int32 total_pages = 5;
  bool first = 6;
  bool last = 7;
  bool empty = 8;
}
```

---

## 3. `rpc Chat` — envío y reenvío en tiempo real

Bidi streaming: el cliente abre el stream **una vez** por sesión, igual que una conexión
WebSocket, y lo mantiene abierto mientras quiera seguir recibiendo mensajes.

### 3.1 Identificación — cabecera de metadata `usuario`

El remitente **no viaja en cada mensaje**: se manda **una sola vez**, como cabecera de gRPC
metadata al abrir el stream (`usuario`, valor de texto). Es el mismo `username` de
`chat-registro` que exige el WebSocket (§2.1 de `contratos-api.md`): 3–50 caracteres, solo
`A–Z a–z 0–9 . _ -`.

Si la cabecera falta o no cumple el formato, el servidor cierra el stream inmediatamente con
`INVALID_ARGUMENT` — no llega a abrirse.

```java
Metadata cabeceras = new Metadata();
cabeceras.put(Metadata.Key.of("usuario", Metadata.ASCII_STRING_MARSHALLER), "mateo");
ConversacionGrpcServiceGrpc.ConversacionGrpcServiceStub stub =
        ConversacionGrpcServiceGrpc.newStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabeceras));
```

### 3.2 `MensajeSaliente` — lo que manda el cliente

| Campo | Tipo proto | Reglas |
|---|---|---|
| `destinatario` | `string` | Username de chat-registro del receptor: 3–50 caracteres, `A–Z a–z 0–9 . _ -`. |
| `contenido` | `string` | No vacío, máx. 2000 caracteres. |

> Igual que el WebSocket: un mensaje que no cumpla estas reglas se **descarta en silencio** —
> no hay respuesta de error por el stream, solo un `WARN` en el log del servidor. Valida en el
> cliente antes de mandar.

### 3.3 `MensajeEntregado` — lo que llega por el stream

| Campo | Tipo proto | Descripción |
|---|---|---|
| `id` | `string` | `ObjectId` de MongoDB en texto. |
| `remitente` | `string` | Quien lo mandó. |
| `destinatario` | `string` | Quien lo recibe. |
| `contenido` | `string` | Texto del mensaje. |
| `enviado_en` | `string` | ISO-8601 UTC (ej. `"2026-09-18T20:53:47.441193Z"`). Como `string`, no `google.protobuf.Timestamp`, para no forzar esa dependencia en el cliente. |

Reglas de entrega (idénticas al WebSocket, §2.3 de `contratos-api.md`):

- El mensaje se persiste primero; el reenvío ocurre después.
- Llega **al remitente y al destinatario**, cada uno por su propia conexión — el remitente
  recibe su propio mensaje de vuelta como confirmación (mismo `MensajeEntregado`, no hay un
  *ack* separado).
- **Cruza protocolos**: si el destinatario está conectado por WebSocket, le llega igual; si el
  remitente está en gRPC y el destinatario en WebSocket (o viceversa), la entrega funciona
  igual — ver §5.
- Si el destinatario no tiene ninguna conexión abierta (por ningún protocolo), no recibe nada
  en tiempo real, pero el mensaje queda persistido — disponible por `Historial` (§4) o por el
  REST equivalente.
- El registro de streams conectados es en memoria, por instancia del servicio — misma
  limitación que el WebSocket para escalar horizontalmente.

### 3.4 Ejemplo (Node.js, `@grpc/grpc-js` + `@grpc/proto-loader`)

```js
const packageDef = protoLoader.loadSync("conversacion.proto", { defaults: true });
const proto = grpc.loadPackageDefinition(packageDef).com.arquetipo.demo.conversacion.grpc;
const client = new proto.ConversacionGrpcService("localhost:9091", grpc.credentials.createInsecure());

const metadata = new grpc.Metadata();
metadata.add("usuario", "mateo");
const stream = client.chat(metadata);

stream.on("data", (mensaje) => console.log("Recibido:", mensaje));
stream.write({ destinatario: "ana", contenido: "Hola!" });
```

---

## 4. `rpc Historial` — historial paginado de una conversación

Unario, sin streaming. Equivalente a
`GET /api/v1/conversaciones/{usuarioA}/{usuarioB}?page&size&sort` (§3 de `contratos-api.md`).
No exige la cabecera `usuario`: los dos participantes van en la propia petición, igual que en
el REST.

### `HistorialRequest`

| Campo | Tipo proto | Obligatorio | Reglas |
|---|---|---|---|
| `usuario_a` | `string` | sí | Un participante de la conversación. El orden con `usuario_b` no importa. |
| `usuario_b` | `string` | sí | El otro participante. |
| `page` | `int32` | no | 0-indexada. Un valor negativo se trata como `0`. Por defecto `0`. |
| `size` | `int32` | no | `0` (o ausente) usa el default del servidor (`20`); se recorta a `100` si se pide más. |
| `sort` | `string` | no | Formato `"campo,direccion"` (ej. `"enviadoEn,desc"`). Vacío = `"enviadoEn,asc"`, el mismo default del REST. Campos válidos: `id`, `remitente`, `destinatario`, `contenido`, `enviadoEn`. |

> A diferencia de `Chat`, aquí `usuario_a`/`usuario_b` **no se validan en formato** — mismo
> comportamiento que el REST: un valor que no exista simplemente no encuentra mensajes.

### `HistorialResponse`

Mismo envoltorio de paginación que el REST (`PageResponse<T>`):

| Campo | Tipo proto | Descripción |
|---|---|---|
| `content` | `repeated MensajeEntregado` | Los mensajes de esta página, en el orden pedido. |
| `page` | `int32` | Página actual. |
| `size` | `int32` | Tamaño de página aplicado. |
| `total_elements` | `int64` | Total de mensajes en la conversación, sin paginar. |
| `total_pages` | `int32` | Total de páginas con ese `size`. |
| `first` / `last` | `bool` | Si es la primera/última página. |
| `empty` | `bool` | Si `content` está vacío. |

### Ejemplo `grpcurl`

```bash
grpcurl -plaintext -d '{
  "usuarioA": "mateo",
  "usuarioB": "ana",
  "page": 0,
  "size": 20
}' localhost:9091 com.arquetipo.demo.conversacion.grpc.ConversacionGrpcService/Historial
```

---

## 5. Cómo funciona por dentro

`ConversacionGrpcController` (`com.arquetipo.demo.conversacion.grpc`) no reimplementa ninguna
lógica: traduce los mensajes proto a los mismos DTO que usan el WebSocket y el REST
(`MensajeEntrante`/`MensajeResponse`) y delega en `ConversacionService` — la misma clase que
persiste en MongoDB para los tres protocolos.

La entrega cruzada (§3.3) la resuelve `NotificadorTiempoReal`
(`com.arquetipo.demo.conversacion.service`): un registro en memoria de "quién está conectado y
por dónde avisarle", compartido por `ChatWebSocketHandler` y `ConversacionGrpcController`.
Cada transporte se suscribe con su propio receptor al conectar (uno sabe mandar por un
`WebSocketSession`, el otro por un `StreamObserver` de gRPC) y se da de baja al desconectar;
`ConversacionService.enviar(...)` notifica ahí después de persistir, sin saber ni preguntar
por qué transporte está conectado cada uno.

`UsuarioMetadataInterceptor` (`com.arquetipo.demo.conversacion.grpc`) es el equivalente gRPC
de `UsuarioHandshakeInterceptor` (el que identifica al WebSocket): se registra a nivel de
servidor (`GrpcServerLifecycle`, `common/grpc`) y solo actúa sobre el método `Chat` — deja
pasar `Historial` sin exigir la cabecera.

---

## 6. Errores

gRPC no tiene *Problem Details*: los errores llegan como `StatusRuntimeException` con un
`Status.Code` y una `description` de texto libre.

| Situación | Método | Código gRPC | `description` |
|---|---|---|---|
| Cabecera `usuario` ausente o con formato inválido | `Chat` | `INVALID_ARGUMENT` | `"Cabecera de metadata 'usuario' ausente o con formato invalido (username de chat-registro: 3-50 caracteres, solo A-Z a-z 0-9 . _ -)"` |
| `destinatario`/`contenido` inválidos en un `MensajeSaliente` | `Chat` | *(ninguno)* | El mensaje se descarta en silencio (§3.2) — no hay error por el stream, el stream sigue abierto. |
| Cualquier fallo inesperado (MongoDB no disponible, bug interno) | `Historial` | `INTERNAL` | Mensaje genérico fijo: `"Ocurrio un error inesperado. Contacte con soporte."` — el detalle real queda en el log del servidor. |
| Cualquier fallo inesperado al procesar un `MensajeSaliente` | `Chat` | *(ninguno)* | Se registra en el log del servidor; el stream sigue abierto, ese mensaje concreto simplemente no se persiste ni se reenvía. |

Notas:

- **Un fallo de conexión** (servidor caído, puerto equivocado) llega como `UNAVAILABLE`, no
  está en la tabla porque no lo genera este servicio — es infraestructura de gRPC.
- **`Chat` nunca cierra el stream por un mensaje individual inválido o por un fallo al
  procesarlo** — a propósito, para que un mensaje suelto con problemas no tire la sesión
  completa. Sí se cierra (`onError`/`onCompleted`) si el propio cliente lo cierra o si la
  conexión de red se cae.

---

## 7. Generar el stub del cliente

Si tu proyecto usa Gradle con el plugin `com.google.protobuf` (igual que este repo), copia
`conversacion.proto` a tu `src/main/proto/` y añade las dependencias `io.grpc:grpc-stub` +
`io.grpc:grpc-protobuf` — el plugin genera `ConversacionGrpcServiceGrpc`,
`MensajeSaliente`/`MensajeEntregado`/`HistorialRequest`/`HistorialResponse` automáticamente.
Para otros lenguajes (Go, Python, Node...), el mismo `.proto` es válido tal cual con el
`protoc`/plugin de cada uno.

Si no quieres mantener una copia del `.proto`, el servidor tiene la **reflexión gRPC**
habilitada: herramientas como `grpcurl` o
[Postman](https://learning.postman.com/docs/sending-requests/grpc/grpc-request-interface/)
pueden listar servicios y construir la petición sin el archivo, apuntando solo a
`localhost:9091` (o el host:puerto de tu entorno).

---

## 8. Control de versiones de este documento

| Fecha | Cambio |
|---|---|
| 2026-09-18 | Versión inicial: `ConversacionGrpcService/Chat` (bidi streaming, espejo del WebSocket) y `ConversacionGrpcService/Historial` (unario, espejo del REST paginado). Se documenta la entrega cruzada entre WebSocket y gRPC via `NotificadorTiempoReal`. |
