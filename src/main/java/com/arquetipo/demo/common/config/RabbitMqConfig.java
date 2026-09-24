package com.arquetipo.demo.common.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exchange de notificaciones de RabbitMQ y su {@link RabbitTemplate}: unico punto donde
 * chat-conversacion publica eventos para que otros interesados (a futuro, un servicio de
 * notificaciones) los consuman, sin que este servicio sepa quien los lee. Topic exchange para
 * poder distinguir el tipo de notificacion por routing key (ver
 * {@code com.arquetipo.demo.conversacion.service.NotificadorAmqp}) sin tener que cambiar el
 * exchange cuando se sume un tipo de notificacion nuevo.
 *
 * <p>El {@link ConnectionFactory} lo autoconfigura {@code spring-boot-starter-amqp} a partir
 * de {@code spring.rabbitmq.*} (ver {@code application.yml} / variables {@code RABBITMQ_HOST},
 * {@code RABBITMQ_PORT}, {@code RABBITMQ_USERNAME}, {@code RABBITMQ_PASSWORD}) y conecta de
 * forma perezosa -- el arranque de la app no depende de que RabbitMQ ya este levantado.
 */
@Configuration
public class RabbitMqConfig {

	@Value("${app.amqp.notificaciones-exchange:chat.notificaciones}")
	private String nombreExchange;

	@Bean
	public TopicExchange notificacionesExchange() {
		return new TopicExchange(nombreExchange, true, false);
	}

	@Bean
	public MessageConverter jackson2JsonMessageConverter() {
		// Sin ObjectMapper inyectado a proposito: Spring Boot 4 autoconfigura Jackson 3
		// (tools.jackson.databind.ObjectMapper) para el propio framework, pero
		// Jackson2JsonMessageConverter sigue siendo Jackson 2.x (com.fasterxml) -- son tipos
		// distintos, no hay bean de ese tipo que inyectar. El constructor sin argumentos crea
		// su propio ObjectMapper interno, suficiente para el DTO simple que se serializa aqui.
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(messageConverter);
		return template;
	}
}
