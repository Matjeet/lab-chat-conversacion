package com.arquetipo.demo.conversacion.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.bson.types.ObjectId;

/**
 * Cursor opaco de paginacion para {@code ConversacionService#listaChats}: identifica el
 * ultimo elemento de una pagina por la fecha de su ultimo mensaje y el id de ese mensaje
 * (desempate cuando dos conversaciones comparten exactamente el mismo instante). Se ordena
 * siempre {@code enviadoEn} descendente, con {@code id} descendente como desempate.
 *
 * <p>Codificado como Base64 de {@code "<enviadoEn ISO-8601>|<id>"} — opaco para el cliente a
 * proposito (no debe intentar parsearlo ni construirlo a mano), pero trivial de depurar
 * decodificando el Base64 a mano si hace falta.
 */
record ChatCursor(Instant enviadoEn, ObjectId id) {

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	String codificar() {
		String valor = enviadoEn + "|" + id.toHexString();
		return ENCODER.encodeToString(valor.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @throws IllegalArgumentException si {@code cursor} no es un cursor valido emitido por
	 *     {@link #codificar()} (el llamador lo traduce a un error de validacion para el cliente).
	 */
	static ChatCursor decodificar(String cursor) {
		try {
			String valor = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
			int separador = valor.indexOf('|');
			if (separador < 0) {
				throw new IllegalArgumentException("formato invalido");
			}
			Instant enviadoEn = Instant.parse(valor.substring(0, separador));
			ObjectId id = new ObjectId(valor.substring(separador + 1));
			return new ChatCursor(enviadoEn, id);
		} catch (IllegalArgumentException | java.time.format.DateTimeParseException ex) {
			throw new IllegalArgumentException("Cursor invalido", ex);
		}
	}
}
