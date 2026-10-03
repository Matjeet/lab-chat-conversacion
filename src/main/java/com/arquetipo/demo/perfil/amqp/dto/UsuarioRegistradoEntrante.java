package com.arquetipo.demo.perfil.amqp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mensaje tal como lo publica chat-registro al terminar un alta ({@code UsuarioRegistradoAmqp}
 * en ese servicio, exchange {@code chat.conversacion}, routing key {@code registro.#}). Mismos
 * nombres de campo para que Jackson deserialice por nombre -- si chat-registro los cambia, hay
 * que actualizar este record a mano (sin referencia compartida entre repos, ver CLAUDE.md).
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)}: si chat-registro suma un campo nuevo
 * al mensaje, se ignora en vez de que Jackson rechace el mensaje entero.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsuarioRegistradoEntrante(String username, String avatar) {
}
