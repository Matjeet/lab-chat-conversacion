package com.arquetipo.demo.sample.repository;

import com.arquetipo.demo.sample.domain.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio del recurso de ejemplo. Extiende {@link JpaRepository} y anade solo las consultas
 * propias del dominio.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

	boolean existsBySku(String sku);

	Optional<Product> findBySku(String sku);
}
