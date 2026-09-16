package com.arquetipo.demo.conversacion.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload que envia el cliente por el WebSocket para mandar un mensaje de texto. El
 * remitente no viaja en el mensaje: lo determina la sesion (ver {@code ChatWebSocketHandler}).
 */
public record MensajeEntrante(

		@NotBlank
		String destinatario,

		@NotBlank
		@Size(max = 2000)
		String contenido
) {
}
