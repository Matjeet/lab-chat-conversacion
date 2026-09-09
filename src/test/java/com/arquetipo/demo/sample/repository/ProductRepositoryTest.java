package com.arquetipo.demo.sample.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.arquetipo.demo.sample.domain.Product;
import com.arquetipo.demo.sample.config.JpaAuditingConfig;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Slice de persistencia. Importa {@link JpaAuditingConfig} para que se rellenen los campos
 * de auditoria de {@code BaseEntity}.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class ProductRepositoryTest {

	@Autowired
	private ProductRepository repository;

	@Test
	void guardaYRellenaAuditoria() {
		Product product = new Product();
		product.setName("Cable HDMI");
		product.setPrice(new BigDecimal("9.99"));
		product.setSku("SKU-TEST");

		Product saved = repository.saveAndFlush(product);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(repository.existsBySku("SKU-TEST")).isTrue();
	}
}
