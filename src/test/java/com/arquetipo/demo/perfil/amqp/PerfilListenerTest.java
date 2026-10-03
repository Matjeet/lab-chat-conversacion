package com.arquetipo.demo.perfil.amqp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.arquetipo.demo.perfil.amqp.dto.UsuarioRegistradoEntrante;
import com.arquetipo.demo.perfil.service.PerfilService;
import org.junit.jupiter.api.Test;

class PerfilListenerTest {

	@Test
	void recibir_delegaEnElServicioConUsernameYAvatar() {
		PerfilService service = mock(PerfilService.class);
		PerfilListener listener = new PerfilListener(service);

		listener.recibir(new UsuarioRegistradoEntrante("mateo", "https://x.example/a.png"));

		verify(service).registrar("mateo", "https://x.example/a.png");
	}
}
