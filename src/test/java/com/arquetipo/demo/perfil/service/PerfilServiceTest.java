package com.arquetipo.demo.perfil.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.perfil.domain.Perfil;
import com.arquetipo.demo.perfil.repository.PerfilRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PerfilServiceTest {

	private PerfilRepository repository;
	private PerfilService service;

	@BeforeEach
	void iniciar() {
		repository = mock(PerfilRepository.class);
		service = new PerfilService(repository);
	}

	@Test
	void registrar_conUsernameNuevo_creaElPerfil() {
		when(repository.findByUsername("mateo")).thenReturn(Optional.empty());

		service.registrar("mateo", "<Blobatar name=\"mateo\" />");

		ArgumentCaptor<Perfil> captor = ArgumentCaptor.forClass(Perfil.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getUsername()).isEqualTo("mateo");
		assertThat(captor.getValue().getAvatar()).isEqualTo("<Blobatar name=\"mateo\" />");
	}

	@Test
	void registrar_conUsernameYaExistente_actualizaElAvatarSinCrearOtroPerfil() {
		Perfil existente = new Perfil();
		existente.setId("1");
		existente.setUsername("mateo");
		existente.setAvatar("https://viejo.example/a.png");
		when(repository.findByUsername("mateo")).thenReturn(Optional.of(existente));

		service.registrar("mateo", "https://nuevo.example/a.png");

		ArgumentCaptor<Perfil> captor = ArgumentCaptor.forClass(Perfil.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getId()).isEqualTo("1");
		assertThat(captor.getValue().getAvatar()).isEqualTo("https://nuevo.example/a.png");
	}

	@Test
	void registrar_sinAvatar_guardaAvatarNulo() {
		when(repository.findByUsername("ana")).thenReturn(Optional.empty());

		service.registrar("ana", "  ");

		ArgumentCaptor<Perfil> captor = ArgumentCaptor.forClass(Perfil.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getAvatar()).isNull();
	}

	@Test
	void registrar_sinUsername_descartaElMensajeSinPersistirNiLanzar() {
		service.registrar(null, "https://x.example/a.png");
		service.registrar("   ", "https://x.example/a.png");

		verify(repository, never()).save(any());
	}
}
