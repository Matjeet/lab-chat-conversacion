package com.arquetipo.demo.sample.mapper;

import com.arquetipo.demo.sample.domain.Product;
import com.arquetipo.demo.sample.web.dto.ProductRequest;
import com.arquetipo.demo.sample.web.dto.ProductResponse;
import org.springframework.stereotype.Component;

/**
 * Mapeo manual entre {@code Product} y sus DTO. Patron a replicar por cada recurso nuevo
 * (si el proyecto adopta MapStruct, este componente se sustituye por el mapper generado).
 */
@Component
public class ProductMapper {

	/** Crea una entidad nueva a partir del DTO de peticion. */
	public Product toEntity(ProductRequest request) {
		Product product = new Product();
		apply(request, product);
		return product;
	}

	/** Aplica los cambios del DTO sobre una entidad ya gestionada. */
	public void updateEntity(ProductRequest request, Product entity) {
		apply(request, entity);
	}

	/** Proyecta la entidad al DTO de respuesta. */
	public ProductResponse toResponse(Product entity) {
		return new ProductResponse(
				entity.getId(),
				entity.getName(),
				entity.getDescription(),
				entity.getPrice(),
				entity.getSku(),
				entity.isActive(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private void apply(ProductRequest request, Product product) {
		product.setName(request.name());
		product.setDescription(request.description());
		product.setPrice(request.price());
		product.setSku(request.sku());
		product.setActive(request.active() == null || request.active());
	}
}
