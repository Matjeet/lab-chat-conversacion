package com.arquetipo.demo.conversacion.web;

import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.service.NotificadorTiempoReal;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Punto de entrada del chat por WebSocket. Cada sesion se identifica por el {@code usuario}
 * de la URL de conexion ({@link UsuarioHandshakeInterceptor} lo deja en los atributos de la
 * sesion); un mensaje entrante se persiste via {@link ConversacionService}, que avisa a
 * {@link NotificadorTiempoReal} — es ese notificador, no este handler, quien decide a quien
 * reenviar el mensaje (puede ser una sesion WebSocket como esta, o un stream de gRPC).
 *
 * <p>Este handler solo se suscribe/desuscribe al conectar/desconectar y traduce el mensaje ya
 * persistido a un frame de texto. El registro de suscriptores es en memoria, por instancia del
 * servicio: si el servicio corre en mas de una instancia, dos usuarios conectados a instancias
 * distintas no se veran los mensajes en tiempo real (les llegaria igual al pedir el historial,
 * porque ya quedan persistidos). Pendiente para cuando haga falta escalar horizontalmente.
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

	private static final String ATRIBUTO_RECEPTOR = "receptorTiempoReal";

	private final ConversacionService service;
	private final NotificadorTiempoReal notificador;
	private final ObjectMapper objectMapper;
	private final Validator validator;

	public ChatWebSocketHandler(ConversacionService service, NotificadorTiempoReal notificador,
			ObjectMapper objectMapper, Validator validator) {
		this.service = service;
		this.notificador = notificador;
		this.objectMapper = objectMapper;
		this.validator = validator;
	}

	// Suscribe la sesion recien conectada al notificador, para que le reenvien mensajes despues.
	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		String usuario = usuarioDe(session);
		Consumer<MensajeResponse> receptor = mensaje -> enviarPorSocket(session, mensaje);
		session.getAttributes().put(ATRIBUTO_RECEPTOR, receptor);
		notificador.suscribir(usuario, receptor);
		log.info("Sesion de chat abierta. usuario='{}'", usuario);
	}

	// Da de baja la suscripcion al desconectarse, para no intentar reenviarle a un socket cerrado.
	@Override
	@SuppressWarnings("unchecked")
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		String usuario = usuarioDe(session);
		Object receptor = session.getAttributes().get(ATRIBUTO_RECEPTOR);
		if (receptor != null) {
			notificador.desuscribir(usuario, (Consumer<MensajeResponse>) receptor);
		}
		log.info("Sesion de chat cerrada. usuario='{}' status={}", usuario, status);
	}

	// Parsea, valida y persiste un mensaje entrante; la entrega la resuelve NotificadorTiempoReal.
	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		String remitente = usuarioDe(session);

		MensajeEntrante entrante;
		try {
			entrante = objectMapper.readValue(message.getPayload(), MensajeEntrante.class);
		} catch (JacksonException ex) {
			log.warn("Mensaje entrante no es JSON valido, se descarta. remitente='{}'", remitente, ex);
			return;
		}

		Set<ConstraintViolation<MensajeEntrante>> violaciones = validator.validate(entrante);
		if (!violaciones.isEmpty()) {
			log.warn("Mensaje entrante invalido, se descarta. remitente='{}' violaciones={}",
					remitente, violaciones.size());
			return;
		}

		service.enviar(remitente, entrante);
	}

	// Traduce un mensaje ya persistido a un frame de texto, solo si el socket sigue abierto.
	private void enviarPorSocket(WebSocketSession session, MensajeResponse mensaje) {
		if (!session.isOpen()) {
			return;
		}
		try {
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(mensaje)));
		} catch (Exception ex) {
			log.warn("No se pudo reenviar el mensaje por WebSocket. usuario='{}'", usuarioDe(session), ex);
		}
	}

	// Saca el usuario de los atributos de la sesion (UsuarioHandshakeInterceptor lo dejo ahi).
	private String usuarioDe(WebSocketSession session) {
		Object usuario = session.getAttributes().get(UsuarioHandshakeInterceptor.ATRIBUTO_USUARIO);
		return usuario == null ? session.getId() : usuario.toString();
	}
}
