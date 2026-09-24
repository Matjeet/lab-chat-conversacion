package com.arquetipo.demo.conversacion.repository;

import com.arquetipo.demo.conversacion.domain.SolicitudChat;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repositorio de {@link SolicitudChat}.
 */
public interface SolicitudChatRepository extends MongoRepository<SolicitudChat, String> {

	/**
	 * Busca una solicitud existente entre dos usuarios, sin importar quien fue el solicitante
	 * original -- usado para no permitir una segunda solicitud mientras exista una (ver
	 * {@code SolicitudChatService#crear}).
	 */
	@Query("{ $or: [ { solicitante: ?0, solicitado: ?1 }, { solicitante: ?1, solicitado: ?0 } ] }")
	Optional<SolicitudChat> findEntreUsuarios(String usuarioA, String usuarioB);
}
