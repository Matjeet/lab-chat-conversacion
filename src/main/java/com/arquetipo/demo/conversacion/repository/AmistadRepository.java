package com.arquetipo.demo.conversacion.repository;

import com.arquetipo.demo.conversacion.domain.Amistad;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repositorio de {@link Amistad}.
 */
public interface AmistadRepository extends MongoRepository<Amistad, String> {
}
