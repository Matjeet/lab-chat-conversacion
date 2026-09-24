package com.arquetipo.demo.conversacion.service;

import com.arquetipo.demo.conversacion.web.dto.NotificacionAmqp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

	private final RabbitTemplate rabbitTemplate;
	private final TopicExchange exchange;

	public NotificadorAmqp(RabbitTemplate rabbitTemplate, TopicExchange notificacionesExchange) {
		this.rabbitTemplate = rabbitTemplate;
		this.exchange = notificacionesExchange;
	}

	public void notificarSolicitud(String solicitante, String solicitado) {
		log.debug(">> notificarSolicitud(solicitante='{}', solicitado='{}')", solicitante, solicitado);
		NotificacionAmqp notificacion = new NotificacionAmqp(solicitante, solicitado, TIPO_SOLICITUD);
		try {
			rabbitTemplate.convertAndSend(exchange.getName(), ROUTING_KEY_SOLICITUD, notificacion);
			log.debug("<< notificarSolicitud() -> OK");
		} catch (AmqpException ex) {
			log.error("No se pudo publicar la notificacion de solicitud en RabbitMQ. "
					+ "solicitante='{}' solicitado='{}'", solicitante, solicitado, ex);
		}
	}
}
