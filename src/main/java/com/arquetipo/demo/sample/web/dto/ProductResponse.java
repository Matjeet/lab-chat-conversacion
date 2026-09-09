package com.arquetipo.demo.sample.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de salida de un {@code Product}.
 */
public record ProductResponse(
		Long id,
		String name,
		String description,
		BigDecimal price,
		String sku,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) {
}
