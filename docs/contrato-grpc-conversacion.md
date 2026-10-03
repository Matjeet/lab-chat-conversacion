# Contrato gRPC — Chat (`chat-conversacion`)

Referencia para que **otro servicio** consuma por gRPC el chat de `chat-conversacion`: envío
y reenvío de mensajes en tiempo real (equivalente al WebSocket), el historial paginado de una
conversación (equivalente al REST), la lista de chats de un usuario con su último mensaje
(paginada por cursor, para scroll infinito) y la creación y resolución de solicitudes de chat
(paso previo obligatorio para poder chatear con alguien), sin necesidad de leer el código de
este repositorio.

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
| Métodos (rpc) | `Chat` — bidi streaming, equivalente al WebSocket. `Historial` — unario, equivalente al REST. `ListaChats` — unario, lista de chats con el último mensaje de cada uno, paginada por cursor. `CrearSolicitud` — unario, crea una solicitud de chat. `ActualizarSolicitud` — unario, la acepta o la rechaza. |
| Reflexión de servicio | Habilitada (`io.grpc:grpc-services`) — un cliente puede descubrir el contrato sin tener el `.proto`, ver §10 |
| Autenticación | Ninguna real todavía — mismo aviso que el WebSocket (§2.1): `Chat` exige una cabecera `usuario` con **formato** válido, pero nada comprueba que quien la manda sea el dueño real de ese username. `CrearSolicitud` sí valida que `solicitante`/`solicitado` **existan** en `chat-registro`, pero tampoco que quien llama sea el dueño de `solicitante` — y `ActualizarSolicitud` (§7) directamente no comprueba que quien llama sea uno de los dos usuarios de la solicitud. **No usar con datos reales hasta que se resuelva** (ver `CLAUDE.md`). |

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
  rpc ListaChats (ListaChatsRequest) returns (ListaChatsResponse);
  rpc CrearSolicitud (CrearSolicitudRequest) returns (SolicitudResponse);
  rpc ActualizarSolicitud (ActualizarSolicitudRequest) returns (SolicitudResponse);
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

message ListaChatsRequest {
  string usuario = 1;
  string cursor = 2;
  int32 size = 3;
}

message ChatResumen {
  string otro_usuario = 1;
  MensajeEntregado ultimo_mensaje = 2;
  optional string avatar = 3;
}

message ListaChatsResponse {
  repeated ChatResumen content = 1;
  string next_cursor = 2;
  bool has_more = 3;
}

message CrearSolicitudRequest {
  string solicitante = 1;
  string solicitado = 2;
}

message SolicitudResponse {
  string id = 1;
  string solicitante = 2;
  string solicitado = 3;
  bool aceptada = 4;
  string creada_en = 5;
  bool pendiente = 6;
}

message ActualizarSolicitudRequest {
  string usuario_a = 1;
  string usuario_b = 2;
  bool aceptada = 3;
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
  igual — ver §8.
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

## 5. `rpc ListaChats` — lista de chats con el último mensaje, paginada por cursor

Unario, sin streaming. **No tiene equivalente en REST ni en WebSocket todavía** — es el primer
endpoint que solo existe por gRPC. Pensado para la pantalla de "conversaciones" de un cliente
(lista de con quién ha hablado un usuario, con scroll infinito): un resumen por cada persona
con la que `usuario` tiene al menos un mensaje (en cualquiera de los dos sentidos), con el
último mensaje de esa conversación, ordenados por la fecha de ese último mensaje —
**más reciente primero**. No exige la cabecera `usuario` de `Chat` (§3.1): igual que
`Historial`, el usuario va en la propia petición.

### 5.1 Por qué cursor y no página/offset

`Historial` (§4) pagina por página/offset porque una conversación concreta es, en la práctica,
de solo lectura hacia atrás en el tiempo mientras se pagina (los mensajes viejos no cambian de
posición). La lista de chats es distinta: **el orden cambia con cada mensaje nuevo** — un chat
que estaba en la página 3 puede saltar a la página 1 si le llega un mensaje mientras el cliente
está paginando, y un offset por número de página se desincroniza (repite o salta chats). Un
cursor evita esto: en vez de "dame la página N", pide "dame los que siguen después de este
punto exacto" — estable aunque cambie el orden de lo que ya se pidió.

### `ListaChatsRequest`

| Campo | Tipo proto | Obligatorio | Reglas |
|---|---|---|---|
| `usuario` | `string` | sí | El usuario cuya lista de chats se pide. No se valida en formato (igual que `usuario_a`/`usuario_b` en `Historial`): un valor que no exista simplemente no tiene chats. |
| `cursor` | `string` | no | El `next_cursor` de una respuesta anterior. Vacío (o ausente) = primera página. Un valor que no venga de un `next_cursor` real de este servicio devuelve `INVALID_ARGUMENT` — ver §9. |
| `size` | `int32` | no | `0` (o ausente) usa el default del servidor (`20`); se recorta a `100` si se pide más — mismos límites que `Historial`. |

> El cursor es **opaco a propósito**: no lo parsees ni lo construyas a mano en el cliente,
> guárdalo tal cual llegó y mándalo de vuelta sin modificar para pedir la siguiente página.

### `ListaChatsResponse`

| Campo | Tipo proto | Descripción |
|---|---|---|
| `content` | `repeated ChatResumen` | Los chats de esta página, ordenados por `ultimo_mensaje.enviado_en` descendente. |
| `next_cursor` | `string` | Cursor para pedir la siguiente página. **Vacío si `has_more` es `false`** — no lo mandes de vuelta en ese caso, no hay garantía de que siga siendo válido. |
| `has_more` | `bool` | Si hay más chats después de esta página. |

### `ChatResumen`

| Campo | Tipo proto | Descripción |
|---|---|---|
| `otro_usuario` | `string` | La otra persona de la conversación (nunca `ListaChatsRequest.usuario`). |
| `ultimo_mensaje` | `MensajeEntregado` | El mensaje más reciente entre ambos, en cualquiera de los dos sentidos (§3.3 para la forma de `MensajeEntregado`). |
| `avatar` | `optional string` | Avatar de `otro_usuario` (nunca el de quien pregunta), tal cual lo guardó `chat-registro`: un enlace `http(s)` o una etiqueta `<Blobatar .../>`. Se lee de la colección `perfil` (que llena el consumidor de RabbitMQ del `README.md`), en **una sola consulta para toda la página**. **Ausente (`has_avatar = false`) si esa persona no eligió uno, o si todavía no hay un perfil guardado para su username** — p. ej. un usuario dado de alta antes de que existiera esa integración. La búsqueda distingue mayúsculas: si el chat se guardó con un username escrito distinto al registrado (`ANA` frente a `ana`), no encuentra el perfil y el avatar queda ausente. |

Si `usuario` no tiene ningún mensaje con nadie, la respuesta es `content: []`, `has_more:
false` — no es un error.

### 5.2 Ejemplo (Node.js) — recorrer todas las páginas

```js
let cursor = "";
do {
  const pagina = await new Promise((resolve, reject) =>
    client.listaChats({ usuario: "mateo", cursor, size: 20 },
        (err, resp) => err ? reject(err) : resolve(resp)));

  for (const chat of pagina.content) {
    console.log(chat.otroUsuario, "->", chat.ultimoMensaje.contenido);
  }
  cursor = pagina.hasMore ? pagina.nextCursor : null;
} while (cursor);
```

### Ejemplo `grpcurl`

```bash
grpcurl -plaintext -d '{"usuario": "mateo", "size": 20}' \
  localhost:9091 com.arquetipo.demo.conversacion.grpc.ConversacionGrpcService/ListaChats
```

---

## 6. `rpc CrearSolicitud` — crea una solicitud de chat

Unario, sin streaming. **No tiene equivalente en REST ni en WebSocket todavía**. Antes de que
dos usuarios puedan chatear hace falta una solicitud entre ellos — este rpc la crea. No exige
la cabecera `usuario` de `Chat` (§3.1): igual que `Historial`/`ListaChats`, los dos usuarios
van en la propia petición.

A diferencia de `Historial`/`ListaChats`, aquí `solicitante`/`solicitado` **sí se validan**:

1. **Formato** (Bean Validation, igual que `destinatario` en `MensajeSaliente`, §3.2): 3–50
   caracteres, solo `A–Z a–z 0–9 . _ -`. Si no cumple, `INVALID_ARGUMENT`.
2. **No pueden ser el mismo usuario** (sin distinguir mayúsculas). Si lo son,
   `INVALID_ARGUMENT`.
3. **Ambos deben existir en `chat-registro`** — `chat-conversacion` llama por gRPC al rpc
   `ExisteUsername` de `chat-registro` (ver `CLAUDE.md`, variables `REGISTRO_GRPC_HOST`/
   `REGISTRO_GRPC_PORT`). Si alguno no existe, `NOT_FOUND`. Si `chat-registro` no responde,
   `UNAVAILABLE`.
4. **No puede existir ya una solicitud *pendiente* entre ambos**, en cualquier sentido (da
   igual quién sea `solicitante` y quién `solicitado` en la solicitud existente). Si ya existe,
   `ALREADY_EXISTS` — y, a propósito, ni se persiste una solicitud nueva ni se publica nada en
   RabbitMQ: la única respuesta es ese error, con un mensaje que dice que ya hay una pendiente.
   Una solicitud ya **resuelta** (`pendiente: false` — aceptada o rechazada, aunque hoy no hay
   forma de llegar a ese estado, ver más abajo) no cuenta para este chequeo y no bloquea una
   solicitud nueva.

Si las cuatro pasan, se persiste con `aceptada: false` y `pendiente: true` (aceptar o rechazar
una solicitud no está implementado todavía, así que toda solicitud creada queda pendiente para
siempre por ahora) y se publica una notificación en RabbitMQ (§6.3) — un fallo al publicar
**no** hace fallar la petición: la solicitud ya quedó creada, la notificación es un aviso
best-effort.

### `CrearSolicitudRequest`

| Campo | Tipo proto | Obligatorio | Reglas |
|---|---|---|---|
| `solicitante` | `string` | sí | Username (chat-registro) de quien inicia la solicitud. |
| `solicitado` | `string` | sí | Username (chat-registro) de quien la recibe. Distinto de `solicitante`. |

### `SolicitudResponse`

| Campo | Tipo proto | Descripción |
|---|---|---|
| `id` | `string` | `ObjectId` de MongoDB en texto. |
| `solicitante` | `string` | Quien la inició. |
| `solicitado` | `string` | Quien la recibió. |
| `aceptada` | `bool` | Nace siempre en `false` — no hay rpc todavía para aceptarla/rechazarla. |
| `pendiente` | `bool` | Nace siempre en `true` y hoy se queda así para siempre (no hay rpc para resolverla). Mientras es `true`, bloquea una solicitud nueva entre el mismo par de usuarios — ver la validación 4 más arriba. |
| `creada_en` | `string` | ISO-8601 UTC, mismo formato que `enviado_en` en `MensajeEntregado` (§3.3). |

### 6.1 Ejemplo `grpcurl`

```bash
grpcurl -plaintext -d '{
  "solicitante": "mateo",
  "solicitado": "ana"
}' localhost:9091 com.arquetipo.demo.conversacion.grpc.ConversacionGrpcService/CrearSolicitud
```

### 6.2 Ejemplo (Node.js)

```js
client.crearSolicitud({ solicitante: "mateo", solicitado: "ana" }, (err, resp) => {
  if (err) {
    console.error(err.code, err.details); // p. ej. ALREADY_EXISTS si ya existia
    return;
  }
  console.log("Solicitud creada:", resp.id);
});
```

### 6.3 Notificación por RabbitMQ

Tras persistir, se publica un mensaje JSON en el exchange **topic** `chat.notificaciones`
(configurable con `RABBITMQ_NOTIFICACIONES_EXCHANGE`), routing key `notificacion.solicitud`:

```json
{
  "solicitante": "mateo",
  "solicitado": "ana",
  "tipo": "solicitud",
  "meta": { "aceptada": false, "pendiente": true }
}
```

`tipo` distingue el motivo de la notificación — hoy solo existe `"solicitud"`, pensado para
admitir otros tipos en el futuro sin cambiar el contrato del mensaje ni el exchange. `meta` es
un objeto con información adicional propia de `tipo` — para `"solicitud"`, `aceptada` y
`pendiente` son el mismo par de campos que `SolicitudResponse` (§6): con los dos, un
consumidor distingue los tres estados posibles — **pendiente** (`pendiente: true, aceptada:
false`, el único que existe al crearla), **aceptada** (`pendiente: false, aceptada: true`) y
**rechazada** (`pendiente: false, aceptada: false`) — algo que un solo booleano no podría
expresar. `ActualizarSolicitud` (§7) publica esta misma forma de mensaje, ya resuelta, en una
routing key distinta. Un consumidor que no conozca `meta` (o un campo nuevo dentro de él) debe
ignorarlo sin fallar, no tratarlo como un error — así lo hace
`chat-notificaciones`, el único consumidor hoy (ver su propio `CLAUDE.md`). Un cliente
interesado debe declarar su propia cola y enlazarla al
exchange con la routing key que le interese: `notificacion.solicitud` (o `notificacion.#` para
cualquier tipo de creación futuro) para esto, `actualizacion.solicitud` (o `actualizacion.#`)
para lo de §7.2 — dos prefijos separados a propósito, para poder suscribirse a uno sin recibir
también el otro. `chat-conversacion` no declara ninguna cola, solo el exchange.

---

## 7. `rpc ActualizarSolicitud` — acepta o rechaza una solicitud de chat

Unario, sin streaming. **No tiene equivalente en REST ni en WebSocket todavía**. Resuelve una
solicitud creada con `CrearSolicitud` (§6): la busca por los dos usuarios (no por `id` — el
cliente no tiene por qué conocerlo) y fija si fue aceptada o rechazada.

1. **Busca la solicitud pendiente entre `usuario_a` y `usuario_b`**, en cualquier orden y sin
   importar quién fue el `solicitante` original — mismo criterio de búsqueda que el chequeo de
   duplicados de `CrearSolicitud` (§6, validación 4). Si no hay ninguna, `NOT_FOUND`. Una
   solicitud ya resuelta (`pendiente: false`) tampoco cuenta — no se puede volver a actualizar.
2. **`usuario_a` y `usuario_b` no pueden ser el mismo usuario** (sin distinguir mayúsculas). Si
   lo son, `INVALID_ARGUMENT` — ni siquiera se llega a buscar la solicitud.
3. A diferencia de `CrearSolicitud`, **no vuelve a validar contra `chat-registro`** que los
   usernames existan: ya se validaron al crear la solicitud, y encontrarla pendiente entre
   ambos ya implica que en su momento existían.

Si la solicitud existe, se le fija `pendiente: false` y `aceptada` al valor de la petición. Si
`aceptada` es `true`, además se registra la amistad entre ambos (colección `amigos`, ver
§7.1). En cualquier caso (aceptada o rechazada) se publica una notificación en RabbitMQ con el
estado ya resuelto (§7.2) — igual que en `CrearSolicitud`, un fallo al publicar no hace fallar
la petición.

### `ActualizarSolicitudRequest`

| Campo | Tipo proto | Obligatorio | Reglas |
|---|---|---|---|
| `usuario_a` | `string` | sí | Uno de los dos usuarios de la solicitud, en cualquier orden. |
| `usuario_b` | `string` | sí | El otro usuario. Distinto de `usuario_a`. |
| `aceptada` | `bool` | sí (default `false` si se omite, como cualquier `bool` de proto3 — mándalo explícito) | `true` = aceptada, `false` = rechazada. |

La respuesta es el mismo `SolicitudResponse` de `CrearSolicitud` (§6), ya con `pendiente:
false` y `aceptada` igual al valor pedido. `solicitante`/`solicitado` en la respuesta
conservan el orden **original** de cuando se creó la solicitud (no necesariamente el mismo
orden en que llegaron `usuario_a`/`usuario_b` en esta petición).

### 7.1 Colección `amigos`

Si `aceptada` es `true`, se inserta un documento en la colección `amigos` de MongoDB con
`usuario_a`, `usuario_b` (los mismos dos usuarios, sin dirección) y `creada_en` (instante del
registro, UTC). No hay ningún rpc todavía para leer esta colección ni para deshacer una
amistad; tampoco hay una comprobación de duplicados aquí — si dos usuarios llegaran a tener más
de una solicitud aceptada entre sí (posible solo si `chat-registro`/el cliente permitiera
crear una solicitud nueva tras una ya resuelta, ver §6 validación 4), se insertaría un
documento por cada aceptación.

### 7.2 Notificación por RabbitMQ

Mismo exchange y misma forma de mensaje que `CrearSolicitud` (§6.3), pero con un **prefijo de
routing key distinto**, `actualizacion.solicitud` — no `notificacion.solicitud.algo`, a
propósito: si empezara por `notificacion.` haría match también con el comodín
`notificacion.#` que usa `chat-notificaciones` para las solicitudes nuevas (§6.3), y el mensaje
le llegaría (y se procesaría) por las dos colas a la vez. `chat-notificaciones` enlaza una
segunda cola aparte al comodín `actualizacion.#` para esto (ver su propio `CLAUDE.md`).

```json
{
  "solicitante": "mateo",
  "solicitado": "ana",
  "tipo": "solicitud",
  "meta": { "aceptada": true, "pendiente": false }
}
```

`meta.pendiente` siempre viaja en `false` aquí — una solicitud recién resuelta no puede seguir
pendiente.

### Ejemplo `grpcurl`

```bash
grpcurl -plaintext -d '{
  "usuarioA": "mateo",
  "usuarioB": "ana",
  "aceptada": true
}' localhost:9091 com.arquetipo.demo.conversacion.grpc.ConversacionGrpcService/ActualizarSolicitud
```

---

## 8. Cómo funciona por dentro

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
pasar el resto de rpc sin exigir la cabecera.

`ListaChats` no usa `MensajeRepository.findConversacion` (el de `Historial`): tiene su propia
consulta, `MensajeRepositoryCustom.listaChats` (implementada a mano sobre `MongoTemplate` en
`MensajeRepositoryImpl`, porque agrupar por "la otra persona de la conversación" y quedarse
con el más reciente de cada grupo no encaja en un `@Query` de una sola línea). La agregación:
filtra los mensajes donde participa `usuario`, calcula quién es la otra persona de cada uno,
ordena por fecha, agrupa por esa otra persona quedándose con el más reciente
(`$group` + `$first`), y aplica el cursor como un filtro adicional después de ordenar los
grupos. `ChatCursor` (`com.arquetipo.demo.conversacion.service`) es quien codifica/decodifica
el cursor opaco de §5 — un cursor con formato inválido hace que `decodificar` lance, que el
servicio traduce a `ValidationException`, que el controlador traduce a `INVALID_ARGUMENT`
(ver §9).

`CrearSolicitud` y `ActualizarSolicitud` delegan en `SolicitudChatService`
(`com.arquetipo.demo.conversacion.service`), la misma clase para ambos. `crear(...)` aplica las
cuatro validaciones de §6 en orden y, si todas pasan, persiste con `SolicitudChatRepository`
(`findPendienteEntreUsuarios` es el `@Query` que busca una solicitud **pendiente** existente
entre dos usuarios en cualquier sentido — filtra por `pendiente: true`, así que una solicitud
ya resuelta no la encuentra) y notifica con `NotificadorAmqp` (`RabbitTemplate.convertAndSend`,
exchange declarado en `RabbitMqConfig`, `common/config`). La existencia de usernames la
resuelve `RegistroGrpcClient` (`com.arquetipo.demo.registro.grpc`) — cliente gRPC de
`chat-registro`, con su propia copia local y mínima del `.proto` de ese servicio (solo
`ExisteUsername`, ver `src/main/proto/registro.proto`), mismo patrón que usa `chat-gateway`
para hablar con `chat-registro`.

`actualizar(...)` (§7) reutiliza el mismo `findPendienteEntreUsuarios` para encontrar la
solicitud a resolver -- por eso una solicitud ya resuelta, o inexistente, se ve igual (no hay
forma de distinguir "nunca existió" de "ya se resolvió" desde este rpc, ambos dan
`NOT_FOUND`). Si `aceptada` es `true`, guarda un `Amistad` con `AmistadRepository` (§7.1); en
cualquier caso, notifica con `NotificadorAmqp#notificarActualizacionSolicitud` (§7.2). No pasa
por `RegistroGrpcClient` -- no vuelve a validar que los usernames existan.

---

## 9. Errores

gRPC no tiene *Problem Details*: los errores llegan como `StatusRuntimeException` con un
`Status.Code` y una `description` de texto libre.

| Situación | Método | Código gRPC | `description` |
|---|---|---|---|
| Cabecera `usuario` ausente o con formato inválido | `Chat` | `INVALID_ARGUMENT` | `"Cabecera de metadata 'usuario' ausente o con formato invalido (username de chat-registro: 3-50 caracteres, solo A-Z a-z 0-9 . _ -)"` |
| `destinatario`/`contenido` inválidos en un `MensajeSaliente` | `Chat` | *(ninguno)* | El mensaje se descarta en silencio (§3.2) — no hay error por el stream, el stream sigue abierto. |
| `cursor` con formato inválido (no viene de un `next_cursor` real) | `ListaChats` | `INVALID_ARGUMENT` | `"El cursor de paginacion no es valido"` |
| `solicitante`/`solicitado` con formato inválido (Bean Validation) | `CrearSolicitud` | `INVALID_ARGUMENT` | `"El cuerpo de la peticion no supero la validacion -> <campo>: <mensaje>"` |
| `solicitante` y `solicitado` son el mismo usuario | `CrearSolicitud` | `INVALID_ARGUMENT` | `"No se puede crear una solicitud de chat hacia uno mismo"` |
| `solicitante`/`solicitado` no existe en `chat-registro` | `CrearSolicitud` | `NOT_FOUND` | `"No existe el usuario solicitante/solicitado '<username>'"` |
| Ya existe una solicitud **pendiente** entre `solicitante` y `solicitado`, en cualquier sentido | `CrearSolicitud` | `ALREADY_EXISTS` | `"Ya existe una solicitud de chat pendiente entre '<a>' y '<b>'"` — no se persiste nada nuevo ni se publica nada en RabbitMQ |
| `chat-registro` no responde al validar `ExisteUsername` | `CrearSolicitud` | `UNAVAILABLE` | `"El servicio 'chat-registro' no esta disponible"` |
| `usuario_a`/`usuario_b` con formato inválido (Bean Validation) | `ActualizarSolicitud` | `INVALID_ARGUMENT` | `"El cuerpo de la peticion no supero la validacion -> <campo>: <mensaje>"` |
| `usuario_a` y `usuario_b` son el mismo usuario | `ActualizarSolicitud` | `INVALID_ARGUMENT` | `"No se puede actualizar una solicitud de chat hacia uno mismo"` |
| No existe una solicitud **pendiente** entre `usuario_a` y `usuario_b`, en cualquier sentido (nunca existió, o ya se resolvió) | `ActualizarSolicitud` | `NOT_FOUND` | `"No existe una solicitud de chat pendiente entre '<a>' y '<b>'"` |
| Cualquier fallo inesperado (MongoDB no disponible, bug interno) | `Historial`, `ListaChats`, `CrearSolicitud`, `ActualizarSolicitud` | `INTERNAL` | Mensaje genérico fijo: `"Ocurrio un error inesperado. Contacte con soporte."` — el detalle real queda en el log del servidor. |
| Cualquier fallo inesperado al procesar un `MensajeSaliente` | `Chat` | *(ninguno)* | Se registra en el log del servidor; el stream sigue abierto, ese mensaje concreto simplemente no se persiste ni se reenvía. |

Nota sobre RabbitMQ: un fallo al publicar la notificación de `CrearSolicitud` (§6.3) o de
`ActualizarSolicitud` (§7.2) **no** genera ningún código de error — la petición ya devolvió
éxito porque la solicitud ya se persistió/actualizó correctamente. El fallo solo se registra
en el log del servidor (`NotificadorAmqp`).

Notas:

- **Un fallo de conexión** (servidor caído, puerto equivocado) llega como `UNAVAILABLE`, no
  está en la tabla porque no lo genera este servicio — es infraestructura de gRPC.
- **`Chat` nunca cierra el stream por un mensaje individual inválido o por un fallo al
  procesarlo** — a propósito, para que un mensaje suelto con problemas no tire la sesión
  completa. Sí se cierra (`onError`/`onCompleted`) si el propio cliente lo cierra o si la
  conexión de red se cae.

---

## 10. Generar el stub del cliente

Si tu proyecto usa Gradle con el plugin `com.google.protobuf` (igual que este repo), copia
`conversacion.proto` a tu `src/main/proto/` y añade las dependencias `io.grpc:grpc-stub` +
`io.grpc:grpc-protobuf` — el plugin genera `ConversacionGrpcServiceGrpc` y todos los mensajes
(`MensajeSaliente`/`MensajeEntregado`/`HistorialRequest`/`HistorialResponse`/
`ListaChatsRequest`/`ListaChatsResponse`/`ChatResumen`/`CrearSolicitudRequest`/
`SolicitudResponse`/`ActualizarSolicitudRequest`) automáticamente.
Para otros lenguajes (Go, Python, Node...), el mismo `.proto` es válido tal cual con el
`protoc`/plugin de cada uno.

Si no quieres mantener una copia del `.proto`, el servidor tiene la **reflexión gRPC**
habilitada: herramientas como `grpcurl` o
[Postman](https://learning.postman.com/docs/sending-requests/grpc/grpc-request-interface/)
pueden listar servicios y construir la petición sin el archivo, apuntando solo a
`localhost:9091` (o el host:puerto de tu entorno).

---

## 11. Control de versiones de este documento

| Fecha | Cambio |
|---|---|
| 2026-10-03 | `ChatResumen` (respuesta de `ListaChats`) suma `avatar` (`optional string`): el avatar de `otro_usuario`, leído de la colección `perfil`. |
| 2026-09-27 | Se agrega `ConversacionGrpcService/ActualizarSolicitud`: acepta o rechaza una solicitud de chat (buscándola por los dos usuarios), registra la amistad en la colección `amigos` si se acepta, y notifica por RabbitMQ (routing key `actualizacion.solicitud` — prefijo `actualizacion.`, no `notificacion.`, para no hacer match con el comodín de las solicitudes nuevas) con el `meta` ya resuelto. |
| 2026-09-26 | El mensaje AMQP de §6.3 suma el campo `meta` (objeto con información adicional propia de `tipo` — para `"solicitud"`, `aceptada` y `pendiente`, el mismo par de campos que `SolicitudResponse`, para poder distinguir pendiente/aceptada/rechazada). |
| 2026-09-24 | `SolicitudResponse` suma el campo `pendiente`. Nueva regla de negocio: `CrearSolicitud` solo bloquea (`ALREADY_EXISTS`) si ya existe una solicitud **pendiente** entre los dos usuarios — antes bloqueaba cualquier solicitud previa, sin distinguir su estado; en ese caso no se persiste nada nuevo ni se publica nada en RabbitMQ. |
| 2026-09-23 | Se agrega `ConversacionGrpcService/CrearSolicitud`: crea una solicitud de chat (paso previo obligatorio para poder chatear), valida `solicitante`/`solicitado` contra `chat-registro` por gRPC (`RegistroGrpcClient`) y notifica por RabbitMQ (exchange `chat.notificaciones`). Aceptar/rechazar la solicitud no está implementado todavía. |
| 2026-09-20 | Se agrega `ConversacionGrpcService/ListaChats`: lista de chats de un usuario con el último mensaje de cada uno, paginada por cursor (pensada para scroll infinito). Primer endpoint sin equivalente en REST/WebSocket. |
| 2026-09-18 | Versión inicial: `ConversacionGrpcService/Chat` (bidi streaming, espejo del WebSocket) y `ConversacionGrpcService/Historial` (unario, espejo del REST paginado). Se documenta la entrega cruzada entre WebSocket y gRPC via `NotificadorTiempoReal`. |
