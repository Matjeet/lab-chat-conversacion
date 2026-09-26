package com.arquetipo.demo.conversacion.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de la creacion de una solicitud de chat. Mismo formato de username (el de
 * chat-registro) que {@link MensajeEntrante#destinatario()}.
 */
public record SolicitudEntrante(

		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$")
		String solicitante,

		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$")
		String solicitado
) {
}
