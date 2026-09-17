package com.arquetipo.demo.conversacion.repository;

import com.arquetipo.demo.conversacion.domain.Mensaje;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repositorio de {@link Mensaje}. Solo las consultas propias del dominio de conversacion.
 */
public interface MensajeRepository extends MongoRepository<Mensaje, String> {

	@Query("{ $or: [ { remitente: ?0, destinatario: ?1 }, { remitente: ?1, destinatario: ?0 } ] }")
	Page<Mensaje> findConversacion(String usuarioA, String usuarioB, Pageable pageable);
}
