package com.arquetipo.demo.perfil.amqp.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * {@code PerfilListener} recibe este record a traves del {@code Jackson2JsonMessageConverter} de
 * {@code RabbitMqConfig} (ObjectMapper sin configuracion propia) -- esta prueba usa el mismo tipo
 * de deserializacion, con la forma exacta del mensaje que publica chat-registro.
 */
class UsuarioRegistradoEntranteTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void deserializa_elMensajeDeChatRegistro() throws Exception {
		String json = "{\"username\":\"mateo\",\"avatar\":\"<Blobatar name=\\\"mateo\\\" />\"}";

		UsuarioRegistradoEntrante mensaje = objectMapper.readValue(json, UsuarioRegistradoEntrante.class);

		assertThat(mensaje.username()).isEqualTo("mateo");
		assertThat(mensaje.avatar()).isEqualTo("<Blobatar name=\"mateo\" />");
	}

	@Test
	void deserializa_conAvatarNulo() throws Exception {
		UsuarioRegistradoEntrante mensaje =
				objectMapper.readValue("{\"username\":\"ana\",\"avatar\":null}", UsuarioRegistradoEntrante.class);

		assertThat(mensaje.username()).isEqualTo("ana");
		assertThat(mensaje.avatar()).isNull();
	}

	@Test
	void deserializa_conCampoDesconocido_loIgnoraSinFallar() throws Exception {
		UsuarioRegistradoEntrante mensaje = objectMapper.readValue(
				"{\"username\":\"ana\",\"avatar\":null,\"campoFuturo\":\"x\"}", UsuarioRegistradoEntrante.class);

		assertThat(mensaje.username()).isEqualTo("ana");
	}
}
