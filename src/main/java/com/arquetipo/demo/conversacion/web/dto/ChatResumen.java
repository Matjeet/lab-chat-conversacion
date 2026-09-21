package com.arquetipo.demo.conversacion.web.dto;

/**
 * Resumen de una conversacion 1 a 1 desde el punto de vista de un usuario: quien es la otra
 * persona y cual fue el ultimo mensaje entre ambos (en cualquiera de los dos sentidos).
 */
public record ChatResumen(
		String otroUsuario,
		MensajeResponse ultimoMensaje
) {
}
