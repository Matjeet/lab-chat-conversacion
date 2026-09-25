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
	 * Busca una solicitud **pendiente** entre dos usuarios, sin importar quien fue el
	 * solicitante original -- usado para no permitir una segunda solicitud mientras exista una
	 * sin resolver (ver {@code SolicitudChatService#crear}). Una solicitud ya resuelta
	 * (aceptada o rechazada -- pendiente en {@code false}) no bloquea una nueva.
	 */
	@Query("{ pendiente: true, $or: [ { solicitante: ?0, solicitado: ?1 }, { solicitante: ?1, solicitado: ?0 } ] }")
	Optional<SolicitudChat> findPendienteEntreUsuarios(String usuarioA, String usuarioB);
}
