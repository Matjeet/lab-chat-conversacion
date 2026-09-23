package com.arquetipo.demo.conversacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class ChatCursorTest {

	@Test
	void codificarYDecodificar_devuelveElMismoCursor() {
		Instant enviadoEn = Instant.parse("2026-09-20T15:30:00.123456Z");
		ObjectId id = new ObjectId();
		ChatCursor original = new ChatCursor(enviadoEn, id);

		ChatCursor decodificado = ChatCursor.decodificar(original.codificar());

		assertThat(decodificado).isEqualTo(original);
	}

	@Test
	void decodificar_conBase64Invalido_lanzaIllegalArgumentException() {
		assertThatThrownBy(() -> ChatCursor.decodificar("no es base64 valido!!"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void decodificar_conFormatoInvalidoTrasElBase64_lanzaIllegalArgumentException() {
		String cursorSinSeparador = java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString("sin-separador".getBytes());

		assertThatThrownBy(() -> ChatCursor.decodificar(cursorSinSeparador))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void decodificar_conIdInvalido_lanzaIllegalArgumentException() {
		String cursorConIdInvalido = java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString("2026-09-20T15:30:00Z|no-es-un-object-id".getBytes());

		assertThatThrownBy(() -> ChatCursor.decodificar(cursorConIdInvalido))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
