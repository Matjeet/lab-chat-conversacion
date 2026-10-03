package com.arquetipo.demo.conversacion.web.dto;

/**
 * Resumen de una conversacion 1 a 1 desde el punto de vista de un usuario: quien es la otra
 * persona (con su avatar, si lo tiene) y cual fue el ultimo mensaje entre ambos (en cualquiera
 * de los dos sentidos). {@code avatar} es {@code null} si la otra persona no eligio uno o si
 * todavia no hay un perfil guardado para ella.
 */
public record ChatResumen(
		String otroUsuario,
		String avatar,
		MensajeResponse ultimoMensaje
) {
}
