package com.arquetipo.demo.perfil.service;

import com.arquetipo.demo.perfil.domain.Perfil;
import com.arquetipo.demo.perfil.repository.PerfilRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Registra en la coleccion {@code perfil} lo que chat-registro publica al terminar cada alta.
 * Idempotente por {@code username}: RabbitMQ entrega al menos una vez, asi que un mismo alta
 * puede llegar repetido -- en ese caso se actualiza el avatar del perfil ya existente en vez de
 * crear un segundo documento.
 */
@Slf4j
@Service
public class PerfilService {

	private final PerfilRepository repository;

	public PerfilService(PerfilRepository repository) {
		this.repository = repository;
	}

	/**
	 * Crea el perfil de {@code username}, o actualiza su avatar si ya existia. Un mensaje sin
	 * {@code username} se descarta (se loguea y no se persiste, sin lanzar nada): reintentarlo no
	 * lo arreglaria, asi que no tiene sentido que RabbitMQ lo reencole.
	 */
	public void registrar(String username, String avatar) {
		log.debug(">> registrar(username='{}')", username);
		if (username == null || username.isBlank()) {
			log.warn("Mensaje de alta de usuario sin username, se descarta");
			log.debug("<< registrar() -> descartado");
			return;
		}

		Perfil perfil = repository.findByUsername(username).orElseGet(Perfil::new);
		boolean nuevo = perfil.getId() == null;
		perfil.setUsername(username);
		perfil.setAvatar(avatar == null || avatar.isBlank() ? null : avatar);
		repository.save(perfil);
		log.debug("<< registrar() -> {}", nuevo ? "perfil creado" : "perfil actualizado");
	}
}
