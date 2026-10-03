package com.arquetipo.demo.conversacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.conversacion.domain.Mensaje;
import com.arquetipo.demo.conversacion.mapper.MensajeMapper;
import com.arquetipo.demo.conversacion.repository.MensajeRepository;
import com.arquetipo.demo.conversacion.web.dto.ChatResumen;
import com.arquetipo.demo.conversacion.web.dto.CursorPage;
import com.arquetipo.demo.perfil.service.PerfilService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Prueba la parte de {@link ConversacionService#listaChats} que no cubre el test del
 * controlador gRPC (que mockea este servicio entero): como se arma cada {@link ChatResumen}
 * con el avatar de la otra persona.
 */
class ConversacionServiceTest {

	private MensajeRepository repository;
	private PerfilService perfilService;
	private ConversacionService service;

	@BeforeEach
	void iniciar() {
		repository = mock(MensajeRepository.class);
		perfilService = mock(PerfilService.class);
		service = new ConversacionService(
				repository, new MensajeMapper(), new NotificadorTiempoReal(), perfilService);
	}

	private static Mensaje mensaje(String remitente, String destinatario) {
		Mensaje mensaje = new Mensaje();
		mensaje.setId(new ObjectId().toHexString());
		mensaje.setRemitente(remitente);
		mensaje.setDestinatario(destinatario);
		mensaje.setContenido("hola");
		mensaje.setEnviadoEn(Instant.parse("2026-10-03T12:00:00Z"));
		return mensaje;
	}

	@Test
	void listaChats_incluyeElAvatarDeLaOtraPersonaDeCadaChat() {
		when(repository.listaChats(org.mockito.ArgumentMatchers.eq("mateo"), isNull(), isNull(), anyInt()))
				.thenReturn(List.of(mensaje("mateo", "ana"), mensaje("beto", "mateo"), mensaje("mateo", "carla")));
		when(perfilService.avataresPorUsername(anyCollection()))
				.thenReturn(Map.of("ana", "https://cdn.example/ana.png", "beto", "<Blobatar name=\"beto\" />"));

		CursorPage<ChatResumen> pagina = service.listaChats("mateo", "", 20);

		assertThat(pagina.content()).extracting(ChatResumen::otroUsuario).containsExactly("ana", "beto", "carla");
		assertThat(pagina.content()).extracting(ChatResumen::avatar)
				.containsExactly("https://cdn.example/ana.png", "<Blobatar name=\"beto\" />", null);
	}

	@Test
	void listaChats_pideLosAvataresEnUnaSolaConsultaConLosUsuariosSinRepetir() {
		when(repository.listaChats(org.mockito.ArgumentMatchers.eq("mateo"), isNull(), isNull(), anyInt()))
				.thenReturn(List.of(mensaje("mateo", "ana"), mensaje("mateo", "beto")));
		when(perfilService.avataresPorUsername(anyCollection())).thenReturn(Map.of());

		service.listaChats("mateo", "", 20);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<java.util.Collection<String>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
		verify(perfilService).avataresPorUsername(captor.capture());
		assertThat(captor.getValue()).containsExactly("ana", "beto");
	}
}
