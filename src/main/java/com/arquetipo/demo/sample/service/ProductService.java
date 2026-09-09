package com.arquetipo.demo.sample.service;

import com.arquetipo.demo.sample.domain.Product;
import com.arquetipo.demo.sample.exception.DuplicateResourceException;
import com.arquetipo.demo.sample.exception.ResourceNotFoundException;
import com.arquetipo.demo.sample.mapper.ProductMapper;
import com.arquetipo.demo.sample.repository.ProductRepository;
import com.arquetipo.demo.sample.web.dto.PageResponse;
import com.arquetipo.demo.sample.web.dto.ProductRequest;
import com.arquetipo.demo.sample.web.dto.ProductResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Capa de servicio del recurso Product: transacciones, reglas de negocio y orquestacion
 * repositorio + mapper. Plantilla para el servicio de cualquier recurso CRUD.
 */
@Service
@Transactional
public class ProductService {

	private static final String RESOURCE = "Producto";

	private final ProductRepository repository;
	private final ProductMapper mapper;

	public ProductService(ProductRepository repository, ProductMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public PageResponse<ProductResponse> findAll(Pageable pageable) {
		return PageResponse.from(repository.findAll(pageable).map(mapper::toResponse));
	}

	@Transactional(readOnly = true)
	public ProductResponse findById(Long id) {
		return mapper.toResponse(getOrThrow(id));
	}

	public ProductResponse create(ProductRequest request) {
		if (repository.existsBySku(request.sku())) {
			throw new DuplicateResourceException(RESOURCE, "sku", request.sku());
		}
		Product saved = repository.save(mapper.toEntity(request));
		return mapper.toResponse(saved);
	}

	public ProductResponse update(Long id, ProductRequest request) {
		Product entity = getOrThrow(id);
		repository.findBySku(request.sku())
				.filter(other -> !other.getId().equals(id))
				.ifPresent(other -> {
					throw new DuplicateResourceException(RESOURCE, "sku", request.sku());
				});
		mapper.updateEntity(request, entity);
		return mapper.toResponse(repository.save(entity));
	}

	public void delete(Long id) {
		repository.delete(getOrThrow(id));
	}

	private Product getOrThrow(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
	}
}
