package com.arquetipo.demo.conversacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Prueba el registro de suscriptores en aislamiento, sin WebSocket ni gRPC de por medio: es la
 * pieza que hace posible que un mensaje mandado por un transporte llegue a alguien conectado
 * por otro.
 */
class NotificadorTiempoRealTest {

	private static final MensajeResponse MENSAJE =
			new MensajeResponse("1", "mateo", "ana", "hola", Instant.parse("2026-09-18T20:00:00Z"));

	@Test
	void notificar_conSuscriptor_leLlegaElMensaje() {
		NotificadorTiempoReal notificador = new NotificadorTiempoReal();
		List<MensajeResponse> recibidos = new ArrayList<>();

		notificador.suscribir("ana", recibidos::add);
		notificador.notificar("ana", MENSAJE);

		assertThat(recibidos).containsExactly(MENSAJE);
	}

	@Test
	void notificar_sinSuscriptor_noHaceNada() {
		NotificadorTiempoReal notificador = new NotificadorTiempoReal();

		notificador.notificar("nadie-conectado", MENSAJE);
		// No lanza excepcion ni hay nada que verificar: simplemente no hay a quien entregarle.
	}

	@Test
	void notificar_conVariosSuscriptoresDelMismoUsuario_leLlegaATodos() {
		NotificadorTiempoReal notificador = new NotificadorTiempoReal();
		List<MensajeResponse> recibidosPorWebSocket = new ArrayList<>();
		List<MensajeResponse> recibidosPorGrpc = new ArrayList<>();

		notificador.suscribir("ana", recibidosPorWebSocket::add);
		notificador.suscribir("ana", recibidosPorGrpc::add);
		notificador.notificar("ana", MENSAJE);

		assertThat(recibidosPorWebSocket).containsExactly(MENSAJE);
		assertThat(recibidosPorGrpc).containsExactly(MENSAJE);
	}

	@Test
	void desuscribir_yaNoLeLlegaElMensaje() {
		NotificadorTiempoReal notificador = new NotificadorTiempoReal();
		List<MensajeResponse> recibidos = new ArrayList<>();

		java.util.function.Consumer<MensajeResponse> receptor = recibidos::add;
		notificador.suscribir("ana", receptor);
		notificador.desuscribir("ana", receptor);
		notificador.notificar("ana", MENSAJE);

		assertThat(recibidos).isEmpty();
	}
}
