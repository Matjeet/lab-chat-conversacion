# chat-conversacion

Servicio de chat en tiempo real por **WebSockets**: en principio, comunicación 1 a 1 entre dos
usuarios. Los mensajes son de texto por ahora y se persisten en **MongoDB**.

> Estado actual: proyecto en **preparación** (nace como copia del arquetipo MVC compartido,
> ver `chat-registro/` y `chat-gateway/`). Todavía no hay implementación de WebSockets ni de
> persistencia de mensajes — eso llega en una rama posterior.

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
| Swagger UI | http://localhost:8082/swagger-ui.html |
| OpenAPI JSON | http://localhost:8082/v3/api-docs |
| Actuator health | http://localhost:8082/actuator/health |

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
