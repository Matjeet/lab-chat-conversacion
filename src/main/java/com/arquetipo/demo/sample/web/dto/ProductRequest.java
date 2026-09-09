package com.arquetipo.demo.sample.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * DTO de entrada para crear o actualizar un {@code Product}.
 */
public record ProductRequest(

		@NotBlank
		@Size(max = 255)
		String name,

		@Size(max = 1000)
		String description,

		@NotNull
		@DecimalMin(value = "0.0", inclusive = false)
		BigDecimal price,

		@NotBlank
		@Size(max = 64)
		String sku,

		Boolean active
) {
}
