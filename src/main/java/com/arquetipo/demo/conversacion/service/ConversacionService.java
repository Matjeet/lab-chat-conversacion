package com.arquetipo.demo.conversacion.service;

import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.conversacion.domain.Mensaje;
import com.arquetipo.demo.conversacion.mapper.MensajeMapper;
import com.arquetipo.demo.conversacion.repository.MensajeRepository;
import com.arquetipo.demo.conversacion.web.dto.ChatResumen;
import com.arquetipo.demo.conversacion.web.dto.CursorPage;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Orquesta el envio de mensajes de texto entre dos usuarios: persiste cada mensaje en
 * MongoDB, avisa a {@link NotificadorTiempoReal} (quien reenvia a las sesiones conectadas,
 * sea por WebSocket o por gRPC) y expone el historial de una conversacion.
 */
@Slf4j
@Service
public class ConversacionService {

	private final MensajeRepository repository;
	private final MensajeMapper mapper;
	private final NotificadorTiempoReal notificador;

	public ConversacionService(MensajeRepository repository, MensajeMapper mapper,
			NotificadorTiempoReal notificador) {
		this.repository = repository;
		this.mapper = mapper;
		this.notificador = notificador;
	}

	public MensajeResponse enviar(String remitente, MensajeEntrante entrante) {
		log.debug(">> enviar(remitente='{}', destinatario='{}')", remitente, entrante.destinatario());
		Mensaje mensaje = new Mensaje();
		mensaje.setRemitente(remitente);
		mensaje.setDestinatario(entrante.destinatario());
		mensaje.setContenido(entrante.contenido());

		Mensaje guardado = repository.save(mensaje);
		log.debug("Mensaje guardado id={} remitente='{}' destinatario='{}'",
				guardado.getId(), remitente, entrante.destinatario());

		MensajeResponse respuesta = mapper.toResponse(guardado);
		notificador.notificar(remitente, respuesta);
		notificador.notificar(entrante.destinatario(), respuesta);
		log.debug("<< enviar() -> OK, id={}", respuesta.id());
		return respuesta;
	}

	public PageResponse<MensajeResponse> historial(String usuarioA, String usuarioB, Pageable pageable) {
		log.debug(">> historial(usuarioA='{}', usuarioB='{}')", usuarioA, usuarioB);
		Page<MensajeResponse> pagina = repository.findConversacion(usuarioA, usuarioB, pageable)
				.map(mapper::toResponse);
		PageResponse<MensajeResponse> respuesta = PageResponse.from(pagina);
		log.debug("<< historial() -> OK, totalElements={}", respuesta.totalElements());
		return respuesta;
	}

	/**
	 * Lista de chats de {@code usuario}, mas reciente primero, paginada por cursor.
	 *
	 * @param cursor el {@code next_cursor} de una pagina anterior, o vacio/{@code null} para la
	 *     primera pagina
	 * @throws ValidationException si {@code cursor} no esta vacio y no es un cursor valido
	 */
	public CursorPage<ChatResumen> listaChats(String usuario, String cursor, int limite) {
		log.debug(">> listaChats(usuario='{}', conCursor={}, limite={})",
				usuario, cursor != null && !cursor.isBlank(), limite);

		ChatCursor cursorDecodificado = decodificarCursor(cursor);

		List<Mensaje> chats = repository.listaChats(
				usuario,
				cursorDecodificado == null ? null : cursorDecodificado.enviadoEn(),
				cursorDecodificado == null ? null : cursorDecodificado.id(),
				limite + 1);

		boolean hayMas = chats.size() > limite;
		List<Mensaje> pagina = hayMas ? chats.subList(0, limite) : chats;

		String siguienteCursor = "";
		if (hayMas) {
			Mensaje ultimoDeLaPagina = pagina.get(pagina.size() - 1);
			siguienteCursor = new ChatCursor(ultimoDeLaPagina.getEnviadoEn(),
					new ObjectId(ultimoDeLaPagina.getId())).codificar();
		}

		List<ChatResumen> contenido = pagina.stream()
				.map(mensaje -> new ChatResumen(otroUsuario(usuario, mensaje), mapper.toResponse(mensaje)))
				.toList();

		CursorPage<ChatResumen> respuesta = new CursorPage<>(contenido, siguienteCursor, hayMas);
		log.debug("<< listaChats() -> OK, chats={}, hasMore={}", contenido.size(), hayMas);
		return respuesta;
	}

	private static String otroUsuario(String usuario, Mensaje mensaje) {
		return mensaje.getRemitente().equals(usuario) ? mensaje.getDestinatario() : mensaje.getRemitente();
	}

	private static ChatCursor decodificarCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			return ChatCursor.decodificar(cursor);
		} catch (IllegalArgumentException ex) {
			throw new ValidationException("El cursor de paginacion no es valido", ex);
		}
	}
}
