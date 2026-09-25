package com.arquetipo.demo.conversacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.conversacion.domain.SolicitudChat;
import com.arquetipo.demo.conversacion.mapper.SolicitudChatMapper;
import com.arquetipo.demo.conversacion.repository.SolicitudChatRepository;
import com.arquetipo.demo.conversacion.web.dto.SolicitudChatResponse;
import com.arquetipo.demo.registro.grpc.RegistroGrpcClient;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prueba las reglas de negocio de {@link SolicitudChatService} de forma aislada, con
 * {@link SolicitudChatRepository} y {@link RegistroGrpcClient} mockeados (mismo patron que
 * {@code ConversacionGrpcControllerTest} para el resto del gRPC).
 */
class SolicitudChatServiceTest {

	private SolicitudChatRepository repository;
	private RegistroGrpcClient registroClient;
	private NotificadorAmqp notificadorAmqp;
	private SolicitudChatService service;

	@BeforeEach
	void iniciar() {
		repository = mock(SolicitudChatRepository.class);
		registroClient = mock(RegistroGrpcClient.class);
		notificadorAmqp = mock(NotificadorAmqp.class);
		service = new SolicitudChatService(repository, new SolicitudChatMapper(), registroClient, notificadorAmqp);
	}

	@Test
	void crear_conUsuariosValidos_persisteConPendienteTrueYNotifica() {
		when(registroClient.existeUsername("mateo")).thenReturn(true);
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(repository.findPendienteEntreUsuarios("mateo", "ana")).thenReturn(Optional.empty());
		when(repository.save(any(SolicitudChat.class))).thenAnswer(invocacion -> {
			SolicitudChat solicitud = invocacion.getArgument(0);
			solicitud.setId("1");
			solicitud.setCreadaEn(Instant.parse("2026-09-23T20:00:00Z"));
			return solicitud;
		});

		SolicitudChatResponse respuesta = service.crear("mateo", "ana");

		assertThat(respuesta.id()).isEqualTo("1");
		assertThat(respuesta.solicitante()).isEqualTo("mateo");
		assertThat(respuesta.solicitado()).isEqualTo("ana");
		assertThat(respuesta.aceptada()).isFalse();
		assertThat(respuesta.pendiente()).isTrue();
		verify(notificadorAmqp).notificarSolicitud("mateo", "ana");
	}

	@Test
	void crear_haciaUnoMismo_lanzaValidationExceptionSinConsultarNada() {
		assertThatThrownBy(() -> service.crear("mateo", "mateo")).isInstanceOf(ValidationException.class);

		verify(registroClient, never()).existeUsername(any());
		verify(repository, never()).save(any());
	}

	@Test
	void crear_conSolicitanteInexistente_lanzaResourceNotFoundException() {
		when(registroClient.existeUsername("fantasma")).thenReturn(false);

		assertThatThrownBy(() -> service.crear("fantasma", "ana")).isInstanceOf(ResourceNotFoundException.class);

		verify(registroClient, never()).existeUsername(eq("ana"));
		verify(repository, never()).save(any());
	}

	@Test
	void crear_conSolicitadoInexistente_lanzaResourceNotFoundException() {
		when(registroClient.existeUsername("mateo")).thenReturn(true);
		when(registroClient.existeUsername("fantasma")).thenReturn(false);

		assertThatThrownBy(() -> service.crear("mateo", "fantasma")).isInstanceOf(ResourceNotFoundException.class);

		verify(repository, never()).save(any());
	}

	@Test
	void crear_conSolicitudPendienteYaExistenteEntreAmbos_lanzaDuplicateResourceExceptionSinPersistirNiNotificar() {
		when(registroClient.existeUsername("mateo")).thenReturn(true);
		when(registroClient.existeUsername("ana")).thenReturn(true);
		when(repository.findPendienteEntreUsuarios("mateo", "ana")).thenReturn(Optional.of(new SolicitudChat()));

		assertThatThrownBy(() -> service.crear("mateo", "ana")).isInstanceOf(DuplicateResourceException.class);

		verify(repository, never()).save(any());
		verify(notificadorAmqp, never()).notificarSolicitud(any(), any());
	}
}
