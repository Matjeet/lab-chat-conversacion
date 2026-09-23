package com.arquetipo.demo.conversacion.grpc;

import com.arquetipo.demo.conversacion.web.dto.CursorPage;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Traduce entre los mensajes de {@code conversacion.proto} y los DTO del dominio, para que la
 * validacion y el flujo ({@link com.arquetipo.demo.conversacion.service.ConversacionService})
 * tengan una unica fuente de verdad, igual que {@code RegistroGrpcMapper} en chat-registro.
 */
@Component
public class ConversacionGrpcMapper {

	static final int TAMANO_PAGINA_DEFECTO = 20;
	static final int TAMANO_PAGINA_MAXIMO = 100;
	private static final String CAMPO_ORDEN_DEFECTO = "enviadoEn";

	MensajeEntrante aMensajeEntrante(MensajeSaliente saliente) {
		return new MensajeEntrante(saliente.getDestinatario(), saliente.getContenido());
	}

	MensajeEntregado aMensajeEntregado(MensajeResponse response) {
		return MensajeEntregado.newBuilder()
				.setId(response.id())
				.setRemitente(response.remitente())
				.setDestinatario(response.destinatario())
				.setContenido(response.contenido())
				.setEnviadoEn(response.enviadoEn().toString())
				.build();
	}

	Pageable aPageable(HistorialRequest request) {
		int page = Math.max(request.getPage(), 0);
		return PageRequest.of(page, tamanoPagina(request.getSize()), aSort(request.getSort()));
	}

	private static int tamanoPagina(int size) {
		return size <= 0 ? TAMANO_PAGINA_DEFECTO : Math.min(size, TAMANO_PAGINA_MAXIMO);
	}

	private Sort aSort(String valor) {
		if (valor == null || valor.isBlank()) {
			return Sort.by(Sort.Direction.ASC, CAMPO_ORDEN_DEFECTO);
		}
		String[] partes = valor.split(",", 2);
		String campo = partes[0].trim();
		Sort.Direction direccion = partes.length > 1 && "desc".equalsIgnoreCase(partes[1].trim())
				? Sort.Direction.DESC
				: Sort.Direction.ASC;
		return Sort.by(direccion, campo.isEmpty() ? CAMPO_ORDEN_DEFECTO : campo);
	}

	HistorialResponse aHistorialResponse(PageResponse<MensajeResponse> pagina) {
		HistorialResponse.Builder builder = HistorialResponse.newBuilder()
				.setPage(pagina.page())
				.setSize(pagina.size())
				.setTotalElements(pagina.totalElements())
				.setTotalPages(pagina.totalPages())
				.setFirst(pagina.first())
				.setLast(pagina.last())
				.setEmpty(pagina.empty());
		pagina.content().forEach(mensaje -> builder.addContent(aMensajeEntregado(mensaje)));
		return builder.build();
	}

	int aTamanoLista(ListaChatsRequest request) {
		return tamanoPagina(request.getSize());
	}

	// El parametro es el DTO (com.arquetipo.demo.conversacion.web.dto.ChatResumen), calificado
	// del todo porque su nombre simple choca con el mensaje proto ChatResumen, en este mismo
	// paquete (conversacion.grpc) por ser el generado del .proto.
	ListaChatsResponse aListaChatsResponse(CursorPage<com.arquetipo.demo.conversacion.web.dto.ChatResumen> pagina) {
		ListaChatsResponse.Builder builder = ListaChatsResponse.newBuilder()
				.setNextCursor(pagina.nextCursor())
				.setHasMore(pagina.hasMore());
		pagina.content().forEach(chatResumen -> builder.addContent(ChatResumen.newBuilder()
				.setOtroUsuario(chatResumen.otroUsuario())
				.setUltimoMensaje(aMensajeEntregado(chatResumen.ultimoMensaje()))
				.build()));
		return builder.build();
	}
}
