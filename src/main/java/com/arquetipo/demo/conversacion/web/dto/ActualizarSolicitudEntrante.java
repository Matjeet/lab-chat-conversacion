package com.arquetipo.demo.conversacion.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de la actualizacion de una solicitud de chat. Mismo formato de username (el de
 * chat-registro) que {@link SolicitudEntrante}. Los dos usuarios van en cualquier orden -- se
 * busca la solicitud pendiente entre ambos, no importa quien fue el solicitante original.
 */
public record ActualizarSolicitudEntrante(

		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$")
		String usuarioA,

		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$")
		String usuarioB,

		boolean aceptada
) {
}
