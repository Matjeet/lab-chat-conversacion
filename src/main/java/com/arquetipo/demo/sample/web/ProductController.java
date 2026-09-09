package com.arquetipo.demo.sample.web;

import com.arquetipo.demo.sample.service.ProductService;
import com.arquetipo.demo.sample.web.dto.PageResponse;
import com.arquetipo.demo.sample.web.dto.ProductRequest;
import com.arquetipo.demo.sample.web.dto.ProductResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST del recurso Product. Plantilla del CRUD estandar:
 * listar (paginado), obtener, crear, actualizar y borrar.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Recurso de ejemplo del arquetipo")
public class ProductController {

	private final ProductService service;

	public ProductController(ProductService service) {
		this.service = service;
	}

	@GetMapping
	public PageResponse<ProductResponse> findAll(@ParameterObject Pageable pageable) {
		return service.findAll(pageable);
	}

	@GetMapping("/{id}")
	public ProductResponse findById(@PathVariable Long id) {
		return service.findById(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductResponse create(@Valid @RequestBody ProductRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
