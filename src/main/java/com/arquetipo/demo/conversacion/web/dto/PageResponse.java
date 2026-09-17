package com.arquetipo.demo.conversacion.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Envoltorio de paginacion estable para las respuestas de la API. Se expone en lugar de
 * {@link org.springframework.data.domain.Page} para no acoplar el contrato HTTP a la
 * serializacion interna de Spring Data.
 *
 * @param <T> tipo del elemento ya mapeado a DTO de respuesta
 */
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last,
		boolean empty
) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast(),
				page.isEmpty());
	}
}
