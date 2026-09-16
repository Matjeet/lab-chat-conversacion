package com.arquetipo.demo.conversacion.repository;

import com.arquetipo.demo.conversacion.domain.Mensaje;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repositorio de {@link Mensaje}. Solo las consultas propias del dominio de conversacion.
 */
public interface MensajeRepository extends MongoRepository<Mensaje, String> {

	@Query("{ $or: [ { remitente: ?0, destinatario: ?1 }, { remitente: ?1, destinatario: ?0 } ] }")
	List<Mensaje> findConversacion(String usuarioA, String usuarioB, Sort sort);
}
