package com.arquetipo.demo.conversacion.web;

import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * sesion); un mensaje entrante se persiste via {@link ConversacionService} y se reenvia al
 * remitente y al destinatario si tienen una sesion abierta.
 *
 * <p>El registro de sesiones es en memoria: si el servicio corre en mas de una instancia, dos
 * usuarios conectados a instancias distintas no se veran los mensajes en tiempo real (les
 * llegaria igual al pedir el historial, porque ya quedan persistidos). Pendiente para cuando
 * haga falta escalar horizontalmente.
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

	private final ConversacionService service;
	private final ObjectMapper objectMapper;
	private final Validator validator;
	private final Map<String, WebSocketSession> sesiones = new ConcurrentHashMap<>();

	public ChatWebSocketHandler(ConversacionService service, ObjectMapper objectMapper, Validator validator) {
		this.service = service;
		this.objectMapper = objectMapper;
		this.validator = validator;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		String usuario = usuarioDe(session);
		sesiones.put(usuario, session);
		log.info("Sesion de chat abierta. usuario='{}'", usuario);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		String usuario = usuarioDe(session);
		sesiones.remove(usuario, session);
		log.info("Sesion de chat cerrada. usuario='{}' status={}", usuario, status);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
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

		MensajeResponse enviado = service.enviar(remitente, entrante);
		String payload = objectMapper.writeValueAsString(enviado);

		enviarSiConectado(remitente, payload);
		enviarSiConectado(entrante.destinatario(), payload);
	}

	private void enviarSiConectado(String usuario, String payload) throws IOException {
		WebSocketSession sesion = sesiones.get(usuario);
		if (sesion != null && sesion.isOpen()) {
			sesion.sendMessage(new TextMessage(payload));
		}
	}

	private String usuarioDe(WebSocketSession session) {
		Object usuario = session.getAttributes().get(UsuarioHandshakeInterceptor.ATRIBUTO_USUARIO);
		return usuario == null ? session.getId() : usuario.toString();
	}
}
