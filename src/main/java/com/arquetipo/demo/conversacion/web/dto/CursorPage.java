package com.arquetipo.demo.conversacion.web.dto;

import java.util.List;

/**
 * Envoltorio de paginacion por cursor (a diferencia de {@link PageResponse}, que es por
 * pagina/offset) — pensado para listas ordenadas por algo que cambia con el tiempo (aqui, la
 * fecha del ultimo mensaje de cada chat), donde un offset se desincroniza si llegan elementos
 * nuevos entre una pagina y la siguiente.
 *
 * @param nextCursor cursor opaco para pedir la siguiente pagina; vacio si {@code hasMore} es
 *     false
 */
public record CursorPage<T>(
		List<T> content,
		String nextCursor,
		boolean hasMore
) {
}
