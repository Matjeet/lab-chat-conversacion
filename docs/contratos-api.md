# Contratos de API — chat-conversacion

Referencia de lo que expone el microservicio **chat-conversacion**: un canal de
**WebSocket** para mandar/recibir mensajes de texto en tiempo real (1 a 1) y un endpoint
**REST** para leer el historial de una conversación. Pensada para que un cliente (frontend
web, app móvil, otro servicio) los consuma sin leer el código.

> También existe un tercer protocolo, **gRPC** (`Chat` + `Historial`, mismo dominio y misma
> persistencia) pensado para consumo entre servicios — ver
> [`contrato-grpc-conversacion.md`](contrato-grpc-conversacion.md). Si tu cliente es un
> navegador, este documento (WebSocket + REST) es el que necesitas.

- Swagger UI (solo documenta el endpoint REST, no el WebSocket ni el gRPC): `http://<host>:8082/swagger-ui.html`
- OpenAPI JSON: `http://<host>:8082/v3/api-docs`

---

## 1. Convenciones generales

| Aspecto | Valor |
|---|---|
| Prefijo de versión (REST) | `/api/v1` (un cambio incompatible sube a `/api/v2`) |
| Formato de cuerpo | JSON (`application/json`) en peticiones y respuestas correctas |
| Formato de errores (REST) | `application/problem+json` (RFC 9457) |
| Codificación | UTF-8 |
| Fechas y horas | ISO-8601 en UTC, con precisión de microsegundos — ej. `2026-09-15T20:53:47.441193Z` |
| Autenticación | **Ninguna todavía.** Ver la advertencia de §2.1 — es el punto más importante de este documento. |
| Orígenes permitidos (WebSocket) | `WEBSOCKET_ALLOWED_ORIGINS` (lista separada por comas). Por defecto solo `http://localhost:3000`. Un origen fuera de la lista hace fallar el *handshake*. |
| CORS (endpoint REST) | Habilitado en `ConversacionController` (`@CrossOrigin`) para `/api/v1/conversaciones/**`. Orígenes vía `CORS_ALLOWED_ORIGINS` (lista separada por comas; por defecto solo `http://localhost:3000`, mismo criterio que `chat-registro`), credenciales vía `CORS_ALLOW_CREDENTIALS` (por defecto `false`). Un origen fuera de la lista recibe `403 Invalid CORS request`. Es una configuración **independiente** de `WEBSOCKET_ALLOWED_ORIGINS`: no cubre el WebSocket. |

### Entornos

| Entorno | Base URL | WebSocket |
|---|---|---|
| Local | `http://localhost:8082` | `ws://localhost:8082` |
| Otros | definidos por infraestructura (el servicio escucha en el puerto `8082`) | `ws://` o `wss://` según haya TLS delante |

---

## 2. WebSocket — chat en tiempo real

### 2.1 Conexión — `GET /ws/chat/{usuario}` (upgrade a WebSocket)

```
ws://localhost:8082/ws/chat/mateo
```

`{usuario}` identifica la sesión: todo mensaje que se mande por esa conexión se guarda con
`remitente = {usuario}`, y todo mensaje dirigido a `{usuario}` se reenvía por esa conexión si
está abierta.

**`{usuario}` es el `username` de `chat-registro`** (decisión explícita: no es un identificador
propio de este servicio). Mismo formato que exige `chat-registro/docs/contratos-api.md` §3.1:

| Regla | Valor |
|---|---|
| Longitud | 3–50 caracteres |
| Caracteres permitidos | `A–Z a–z 0–9 . _ -` |

Un `{usuario}` que no cumpla el formato hace que el servidor rechace el *handshake* (la
conexión ni se abre).

> ⚠️ **El formato se valida; la identidad no.** Que `{usuario}` tenga la forma de un username
> válido no significa que quien se conectó sea realmente el dueño de esa cuenta — no hay
> token, sesión de Firebase ni nada equivalente todavía comprobando eso (ver `CLAUDE.md`,
> pendiente explícito). **No usar este servicio con datos reales hasta que esto se resuelva.**

### 2.2 Mandar un mensaje (cliente → servidor)

Un *frame* de texto con este JSON:

```json
{
  "destinatario": "ana",
  "contenido": "Hola!"
}
```

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `destinatario` | string | sí | Mismo formato que `{usuario}` (username de chat-registro, 3–50 caracteres, `A–Z a–z 0–9 . _ -`). Es el `{usuario}` de la otra conexión — no se comprueba que exista como cuenta real, solo el formato. |
| `contenido` | string | sí | No vacío, máx. 2000 caracteres. Solo texto — nada de adjuntos todavía. |

`remitente` **no** va en este mensaje: lo pone el servidor a partir del `{usuario}` de la
conexión (§2.1).

> ⚠️ **Si el JSON es inválido o no cumple las reglas de arriba, el servidor descarta el
> mensaje en silencio.** No se envía ningún error ni *frame* de rechazo por el socket — solo
> queda un `WARN` en el log del servidor. El cliente debe validar `destinatario`/`contenido`
> **antes** de mandar (mismas reglas de la tabla) para no depender de esto.

### 2.3 Recibir un mensaje (servidor → cliente)

Mismo JSON en ambas direcciones — confirmación de envío y recepción son el mismo *frame*:

```json
{
  "id": "66f1c2a8b4c9a12345678901",
  "remitente": "mateo",
  "destinatario": "ana",
  "contenido": "Hola!",
  "enviadoEn": "2026-09-15T20:53:47.441193Z"
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | string | Identificador generado por MongoDB (`ObjectId` en texto). |
| `remitente` | string | Quien lo mandó. |
| `destinatario` | string | Quien lo recibe. |
| `contenido` | string | Texto del mensaje, tal cual se envió. |
| `enviadoEn` | string (ISO-8601) | Instante en que el servidor lo persistió (UTC). |

Reglas de entrega:

- El mensaje se **persiste siempre** primero (ver §5), y solo después se reenvía.
- Se reenvía **al remitente y al destinatario**, cada uno por su propia conexión — el
  remitente recibe de vuelta su propio mensaje ya con `id`/`enviadoEn`; **no** hace falta
  (ni hay) un *frame* de confirmación distinto. No pintes el mensaje de forma optimista en la
  UI y otra vez al recibirlo: usa este *frame* como la única fuente de verdad.
- Si el destinatario **no** tiene una conexión abierta en este momento, simplemente no recibe
  nada — el mensaje no se pierde (queda en MongoDB, disponible por el historial de §3), pero
  no hay push ni notificación de "mensaje pendiente" todavía.
- **La entrega cruza protocolos**: si el destinatario está conectado por el `rpc Chat` de gRPC
  en vez de por WebSocket (o viceversa), le llega igual — ver
  [`contrato-grpc-conversacion.md`](contrato-grpc-conversacion.md) §3 y §5.
- El registro de conexiones abiertas es en memoria, por instancia del servicio. Con más de una
  instancia corriendo, dos usuarios conectados a instancias distintas no se ven en tiempo
  real (aunque el mensaje sí queda guardado). No hay balanceo/sticky-sessions resuelto para
  este caso todavía.

---

## 3. REST — `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`

Historial de una conversación entre dos usuarios, para que el cliente cargue los mensajes
previos al conectarse por WebSocket. El orden de `usuarioA`/`usuarioB` en la URL no importa
(se buscan mensajes en ambos sentidos).

#### Petición

| | |
|---|---|
| Método | `GET` |
| Path | `/api/v1/conversaciones/{usuarioA}/{usuarioB}` |
| Query params | `page`, `size`, `sort` — paginación estándar de Spring Data (ver tabla abajo) |
| Autenticación | Ninguna (misma advertencia de §2.1: cualquiera puede leer el historial de cualquier par de usuarios) |

`usuarioA`/`usuarioB` son también el `username` de `chat-registro` (§2.1), pero a diferencia
del WebSocket, **este endpoint todavía no valida el formato** — un valor que no exista o no
cumpla el patrón simplemente no encuentra mensajes y devuelve una página vacía (ver más abajo).

| Query param | Tipo | Por defecto | Notas |
|---|---|---|---|
| `page` | number | `0` | Primera página es `0`, no `1`. |
| `size` | number | `20` | Máximo `100`; un valor mayor se recorta a `100`. |
| `sort` | string | `enviadoEn,asc` | Formato `campo,dirección` (`asc`/`desc`). Repetible para varios campos. Los campos válidos son los de `MensajeResponse` (§2.3): `id`, `remitente`, `destinatario`, `contenido`, `enviadoEn`. |

#### Respuesta `200 OK`

`Content-Type: application/json` — una página de mensajes (ver estructura de cada mensaje en
§2.3), ordenados por `enviadoEn` ascendente por defecto (el más antiguo primero):

```json
{
  "content": [
    {
      "id": "66f1c2a8b4c9a12345678901",
      "remitente": "mateo",
      "destinatario": "ana",
      "contenido": "Hola!",
      "enviadoEn": "2026-09-15T20:53:47.441193Z"
    },
    {
      "id": "66f1c2a8b4c9a12345678902",
      "remitente": "ana",
      "destinatario": "mateo",
      "contenido": "Hola, que tal?",
      "enviadoEn": "2026-09-15T20:53:52.001045Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true,
  "empty": false
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `content` | array | Los mensajes de esta página, en el orden pedido. |
| `page` | number | Página actual (0-indexada). |
| `size` | number | Tamaño de página pedido (o el máximo, si se pidió más de 100). |
| `totalElements` | number | Total de mensajes en la conversación, sin paginar. |
| `totalPages` | number | Total de páginas con ese `size`. |
| `first` / `last` | boolean | Si esta es la primera/última página. |
| `empty` | boolean | Si `content` está vacío. |

Si no hay mensajes entre ambos usuarios, la respuesta es `200` con `content: []`,
`totalElements: 0` — **no** es un `404` (no existe el concepto de "conversación" como recurso
propio, solo mensajes).

#### Ejemplo `curl`

```bash
# primera página, tamaño por defecto (20), mas antiguo primero
curl http://localhost:8082/api/v1/conversaciones/mateo/ana

# segunda página de 50, mas reciente primero
curl "http://localhost:8082/api/v1/conversaciones/mateo/ana?page=1&size=50&sort=enviadoEn,desc"
```

---

## 4. Formato de errores del endpoint REST (RFC 9457 *Problem Details*)

Toda respuesta con código `4xx` o `5xx` de `/api/v1/**` tiene
`Content-Type: application/problem+json` y este cuerpo:

```json
{
  "type": "urn:problem-type:internal-error",
  "title": "Error interno",
  "status": 500,
  "detail": "Ocurrio un error inesperado. Contacte con soporte.",
  "instance": "/api/v1/conversaciones/mateo/ana",
  "timestamp": "2026-09-15T20:53:47.441193Z"
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string (URI) | Identificador estable de la categoría de error. Es el campo para ramificar lógica, no `title` ni `detail`. |
| `title` | string | Título legible, fijo por `type`. |
| `status` | number | Código HTTP, repetido en el cuerpo. |
| `detail` | string | Descripción legible. |
| `instance` | string | Path de la petición que falló. |
| `timestamp` | string (ISO-8601) | Momento en que se generó el error. |

### Catálogo de `type`

| `type` | HTTP | Cuándo |
|---|---|---|
| `urn:problem-type:internal-error` | 500 | Error inesperado (p. ej. MongoDB no disponible). `detail` siempre genérico; el detalle real queda en logs del servidor. |
| `urn:problem-type:validation-error` | 400 | Infraestructura genérica compartida con el resto de servicios del sistema (ver `chat-registro/docs/contratos-api.md`). Sin uso en el endpoint actual: no valida el formato de `usuarioA`/`usuarioB`. |
| `urn:problem-type:duplicate-resource` | 409 | Infraestructura genérica. Sin uso: no hay restricción de unicidad en este servicio. |
| `urn:problem-type:data-integrity` | 409 | Infraestructura genérica. Sin uso hoy. |
| `urn:problem-type:resource-not-found` | 404 | Infraestructura genérica. Sin uso: el historial vacío responde `200` con `[]`, no `404` (ver §3). |

El WebSocket (§2) **no** usa este formato de error: un mensaje inválido simplemente se
descarta (§2.2), no hay respuesta de error por el socket.

---

## 5. Notas de integración para el frontend

1. **No hay autenticación todavía.** `{usuario}` se valida en formato (es un username de
   chat-registro), pero no se comprueba que quien se conecta sea su dueño real — no trates
   este servicio como seguro para datos reales hasta que eso se resuelva (§2.1). Cuando se
   resuelva, este documento cambiará.
2. **El WebSocket no confirma ni rechaza mensajes inválidos.** Valida `destinatario` (formato
   de username: 3–50 caracteres, `A–Z a–z 0–9 . _ -`) y `contenido` (no vacío, ≤ 2000
   caracteres) en el cliente antes de mandar.
3. **El mensaje que llega por el socket es la única confirmación de envío.** No hay un *ack*
   separado del *frame* que se reenvía al propio remitente.
4. **Un destinatario desconectado no recibe nada en tiempo real**, pero el mensaje queda
   guardado — se ve al pedir el historial (§3) la próxima vez que ese usuario cargue la
   conversación.
5. **Carga el historial por REST al abrir la pantalla de chat, y luego conéctate al
   WebSocket** para los mensajes nuevos: son dos canales independientes, ninguno sustituye al
   otro.
6. **`id` es un `ObjectId` de MongoDB en texto** (24 caracteres hexadecimales), no un número
   incremental — no asumas que se puede ordenar u operar como número.
7. **El historial es paginado (§3), por defecto más antiguo primero.** Para un chat típico
   (cargar los últimos mensajes al abrir la conversación) probablemente quieras pedir
   `sort=enviadoEn,desc` y luego invertir `content` en el cliente para pintarlo cronológico —
   el servidor no expone un "traer los N más recientes" distinto de ordenar descendente.

---

## 6. Modelos (TypeScript)

```ts
// Mensaje que manda el cliente por el WebSocket
export interface MensajeEntrante {
  destinatario: string; // username de chat-registro: 3-50, /^[A-Za-z0-9._-]+$/
  contenido: string;    // no vacio, <= 2000 caracteres
}

// Mensaje persistido: llega por el WebSocket (envio y recepcion) y en el historial REST
export interface MensajeResponse {
  id: string;          // ObjectId de MongoDB en texto
  remitente: string;
  destinatario: string;
  contenido: string;
  enviadoEn: string;    // ISO-8601 UTC
}

// Respuesta paginada del historial (GET /api/v1/conversaciones/{usuarioA}/{usuarioB})
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

// Error RFC 9457 (solo en el endpoint REST, nunca por el WebSocket)
export interface ProblemDetail {
  type: string;      // "urn:problem-type:*"
  title: string;
  status: number;
  detail: string;
  instance: string;
  timestamp: string; // ISO-8601 UTC
}
```

---

## 7. Cómo funciona por dentro (para quien depure un mensaje que no llega)

`ChatWebSocketHandler` (`com.arquetipo.demo.conversacion.web`) es el punto de entrada del
WebSocket (el de gRPC es `ConversacionGrpcController`, ver
[`contrato-grpc-conversacion.md`](contrato-grpc-conversacion.md) §5 — ambos comparten el mismo
flujo de aquí en adelante):

1. Al conectar, `UsuarioHandshakeInterceptor` saca `{usuario}` de la URL y lo deja en los
   atributos de la sesión; el *handler* se suscribe a `NotificadorTiempoReal` con ese usuario
   (un registro compartido con gRPC — es lo que hace posible la entrega cruzada de §2.3).
2. Al llegar un *frame* de texto: lo parsea a `MensajeEntrante`, lo valida (Bean Validation) y,
   si es válido, llama a `ConversacionService.enviar(remitente, entrante)`.
3. `ConversacionService` crea el documento `Mensaje`, lo guarda en MongoDB
   (`MensajeRepository`, colección `mensajes` — `enviadoEn` lo pone el propio MongoDB al
   guardar, `@CreatedDate` + `MongoAuditingConfig`) y notifica a `NotificadorTiempoReal` con el
   `MensajeResponse` ya guardado, tanto para el `remitente` como para el `destinatario`.
4. `NotificadorTiempoReal` reenvía a quien esté suscrito con ese usuario — sea el `handler` de
   WebSocket (manda un *frame*) o el controlador de gRPC (manda por el stream).

El historial (§3) usa la misma colección: `MensajeRepository.findConversacion` busca por
`remitente`/`destinatario` en ambos sentidos, recibe un `Pageable` (Spring Data lo arma a
partir de `page`/`size`/`sort`, con `enviadoEn` ascendente por defecto vía
`@PageableDefault` en `ConversacionController`) y devuelve un `Page<Mensaje>`;
`ConversacionService.historial` lo mapea a `PageResponse<MensajeResponse>` — el mismo
envoltorio de paginación del arquetipo base, para no acoplar el contrato HTTP a la
serialización interna de Spring Data.

Errores de la aplicación (si los hubiera) los traduce `GlobalExceptionHandler`
(`com.arquetipo.demo.common.web`) al formato de §4 — pero solo para el endpoint REST: no hay
equivalente para el WebSocket.

---

## 8. Otros recursos del servicio

| Recurso | Path | Uso |
|---|---|---|
| Swagger UI | `/swagger-ui.html` | Exploración interactiva (solo el endpoint REST) |
| OpenAPI JSON | `/v3/api-docs` | Generación de clientes / tipos (solo el endpoint REST) |
| Health check | `/actuator/health` | Monitorización / readiness |

---

## 9. Control de versiones de este documento

| Fecha | Cambio |
|---|---|
| 2026-09-18 | Se agrega un tercer protocolo, gRPC (`ConversacionGrpcService/Chat` + `/Historial`, puerto `9091`) — ver [`contrato-grpc-conversacion.md`](contrato-grpc-conversacion.md). La entrega en tiempo real ahora cruza protocolos (§2.3, §7): `NotificadorTiempoReal` reemplaza el mapa de sesiones que tenía solo `ChatWebSocketHandler`. Tambien se corrige `UsuarioHandshakeInterceptor` para devolver `400` explícito al rechazar un `{usuario}` invalido (antes devolvía `200` sin upgrade, y un cliente real se quedaba esperando en vez de recibir un rechazo limpio). |
| 2026-09-18 (anterior) | Se habilita CORS en `ConversacionController` (`CORS_ALLOWED_ORIGINS` / `CORS_ALLOW_CREDENTIALS`, por defecto `http://localhost:3000`). Es una configuración aparte de `WEBSOCKET_ALLOWED_ORIGINS`. |
| 2026-09-17 | `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}` pasa a devolver una página (`PageResponse`) en vez del array completo — query params `page`/`size`/`sort`, por defecto `size=20`, ordenado por `enviadoEn` ascendente. |
| 2026-09-17 (anterior) | Se decide que `{usuario}`/`destinatario` es el `username` de chat-registro; se valida su formato (3–50, `A–Z a–z 0–9 . _ -`) en el *handshake* del WebSocket y en `MensajeEntrante.destinatario`. La identidad real sigue sin verificarse (pendiente). |
| 2026-09-15 | Versión inicial: WebSocket `/ws/chat/{usuario}` y `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`. |
