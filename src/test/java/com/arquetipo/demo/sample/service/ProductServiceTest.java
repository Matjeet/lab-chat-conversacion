package com.arquetipo.demo.sample.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.sample.domain.Product;
import com.arquetipo.demo.sample.mapper.ProductMapper;
import com.arquetipo.demo.sample.repository.ProductRepository;
import com.arquetipo.demo.sample.web.dto.ProductRequest;
import com.arquetipo.demo.sample.web.dto.ProductResponse;
import com.arquetipo.demo.sample.exception.DuplicateResourceException;
import com.arquetipo.demo.sample.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock
	private ProductRepository repository;

	private ProductService service;

	@BeforeEach
	void setUp() {
		service = new ProductService(repository, new ProductMapper());
	}

	private static ProductRequest request() {
		return new ProductRequest("Teclado", "desc", new BigDecimal("79.90"), "SKU-001", true);
	}

	@Test
	void create_persisteYMapeaRespuesta() {
		when(repository.existsBySku("SKU-001")).thenReturn(false);
		when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

		ProductResponse response = service.create(request());

		assertThat(response.sku()).isEqualTo("SKU-001");
		assertThat(response.name()).isEqualTo("Teclado");
	}

	@Test
	void create_skuDuplicado_lanzaDuplicateResource() {
		when(repository.existsBySku("SKU-001")).thenReturn(true);

		assertThatThrownBy(() -> service.create(request()))
				.isInstanceOf(DuplicateResourceException.class);
	}

	@Test
	void findById_inexistente_lanzaResourceNotFound() {
		when(repository.findById(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findById(42L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("42");
	}
}
