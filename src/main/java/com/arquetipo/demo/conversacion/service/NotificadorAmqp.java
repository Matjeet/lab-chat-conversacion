package com.arquetipo.demo.conversacion.service;

import com.arquetipo.demo.conversacion.web.dto.MetaSolicitud;
import com.arquetipo.demo.conversacion.web.dto.NotificacionAmqp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Publica notificaciones al exchange de RabbitMQ (ver
 * {@code com.arquetipo.demo.common.config.RabbitMqConfig}) para avisar de eventos que
 * interesan fuera de este servicio, aunque el interesado no este conectado a ningun stream en
 * este momento -- primer caso: solicitudes de chat nuevas. Un fallo al publicar (broker caido,
 * etc.) se registra pero no revierte la operacion que ya se persistio: la notificacion es un
 * aviso best-effort, no la fuente de verdad (esa es MongoDB).
 */
@Slf4j
@Component
public class NotificadorAmqp {

	public static final String TIPO_SOLICITUD = "solicitud";
	private static final String ROUTING_KEY_SOLICITUD = "notificacion.solicitud";
	// Prefijo "actualizacion.", no "notificacion.", a proposito: chat-notificaciones tiene una
	// cola aparte enlazada al comodin "actualizacion.#" precisamente para no recibir esto
	// tambien por la cola de "notificacion.#" (ver su propio RabbitMqConfig).
	private static final String ROUTING_KEY_SOLICITUD_ACTUALIZADA = "actualizacion.solicitud";

	private final RabbitTemplate rabbitTemplate;
	private final TopicExchange exchange;

	public NotificadorAmqp(RabbitTemplate rabbitTemplate,
			@Qualifier("notificacionesExchange") TopicExchange notificacionesExchange) {
		this.rabbitTemplate = rabbitTemplate;
		this.exchange = notificacionesExchange;
	}

	public void notificarSolicitud(String solicitante, String solicitado, boolean aceptada, boolean pendiente) {
		log.debug(">> notificarSolicitud(solicitante='{}', solicitado='{}', aceptada={}, pendiente={})",
				solicitante, solicitado, aceptada, pendiente);
		NotificacionAmqp notificacion = new NotificacionAmqp(
				solicitante, solicitado, TIPO_SOLICITUD, new MetaSolicitud(aceptada, pendiente));
		try {
			rabbitTemplate.convertAndSend(exchange.getName(), ROUTING_KEY_SOLICITUD, notificacion);
			log.debug("<< notificarSolicitud() -> OK");
		} catch (AmqpException ex) {
			log.error("No se pudo publicar la notificacion de solicitud en RabbitMQ. "
					+ "solicitante='{}' solicitado='{}'", solicitante, solicitado, ex);
		}
	}

	/**
	 * Avisa que una solicitud ya se resolvio (ver {@code SolicitudChatService#actualizar}) --
	 * routing key con un prefijo distinto al de {@link #notificarSolicitud}
	 * ({@code actualizacion.}, no {@code notificacion.}) para que un consumidor pueda
	 * suscribirse solo a esto sin recibir tambien las solicitudes nuevas (y viceversa) -- ver
	 * {@link #ROUTING_KEY_SOLICITUD_ACTUALIZADA}. {@code pendiente} siempre viaja en
	 * {@code false}: una vez resuelta, ya no puede volver a estar pendiente.
	 */
	public void notificarActualizacionSolicitud(String solicitante, String solicitado, boolean aceptada) {
		log.debug(">> notificarActualizacionSolicitud(solicitante='{}', solicitado='{}', aceptada={})",
				solicitante, solicitado, aceptada);
		NotificacionAmqp notificacion = new NotificacionAmqp(
				solicitante, solicitado, TIPO_SOLICITUD, new MetaSolicitud(aceptada, false));
		try {
			rabbitTemplate.convertAndSend(exchange.getName(), ROUTING_KEY_SOLICITUD_ACTUALIZADA, notificacion);
			log.debug("<< notificarActualizacionSolicitud() -> OK");
		} catch (AmqpException ex) {
			log.error("No se pudo publicar la notificacion de actualizacion de solicitud en RabbitMQ. "
					+ "solicitante='{}' solicitado='{}'", solicitante, solicitado, ex);
		}
	}
}
