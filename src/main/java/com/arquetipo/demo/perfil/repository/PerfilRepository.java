package com.arquetipo.demo.perfil.repository;

import com.arquetipo.demo.perfil.domain.Perfil;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repositorio de {@link Perfil}.
 */
public interface PerfilRepository extends MongoRepository<Perfil, String> {

	Optional<Perfil> findByUsername(String username);
}
