package com.arquetipo.demo.conversacion.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Solicitud de chat ya persistida.
 */
@Schema(name = "SolicitudChatResponse", description = "Solicitud de chat entre dos usuarios")
public record SolicitudChatResponse(

		@Schema(description = "Identificador generado", example = "66f1c2a8b4c9a12345678902")
		String id,

		@Schema(description = "Usuario que inicia la solicitud", example = "mateo")
		String solicitante,

		@Schema(description = "Usuario que la recibe", example = "ana")
		String solicitado,

		@Schema(description = "Si la solicitud ya fue aceptada", example = "false")
		boolean aceptada,

		@Schema(description = "Instante de creacion (UTC)", example = "2026-09-23T20:53:47.441193Z")
		Instant creadaEn
) {
}
