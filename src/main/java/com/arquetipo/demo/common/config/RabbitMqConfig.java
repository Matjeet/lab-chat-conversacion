package com.arquetipo.demo.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ de chat-conversacion, en las dos direcciones:
 *
 * <ul>
 *   <li><b>Publica</b> en el exchange {@code chat.notificaciones} (el que declara este
 *       servicio, topic) -- unico punto donde chat-conversacion avisa de eventos a otros
 *       interesados (hoy chat-notificaciones) sin saber quien los lee. Topic exchange para
 *       poder distinguir el tipo por routing key (ver
 *       {@code com.arquetipo.demo.conversacion.service.NotificadorAmqp}) sin cambiar el
 *       exchange cuando se sume un tipo nuevo.
 *   <li><b>Consume</b> del exchange {@code chat.conversacion} (el que declara chat-registro,
 *       donde publica cada alta de usuario): este servicio declara aqui tambien ese exchange
 *       -- con las mismas propiedades que chat-registro (topic, durable), asi que RabbitMQ no
 *       lo vuelve a crear si ya existe -- para poder declarar su propia cola durable y su
 *       binding aunque chat-registro todavia no haya arrancado. Ver
 *       {@code com.arquetipo.demo.perfil.amqp.PerfilListener}.
 * </ul>
 *
 * <p>El {@link ConnectionFactory} lo autoconfigura {@code spring-boot-starter-amqp} a partir
 * de {@code spring.rabbitmq.*} (ver {@code application.yml} / variables {@code RABBITMQ_HOST},
 * {@code RABBITMQ_PORT}, {@code RABBITMQ_USERNAME}, {@code RABBITMQ_PASSWORD}) y conecta de
 * forma perezosa -- el arranque de la app no depende de que RabbitMQ ya este levantado. La
 * cola y el binding los declara {@code RabbitAdmin} (autoconfigurado) en cuanto hay conexion.
 */
@Configuration
public class RabbitMqConfig {

	@Value("${app.amqp.notificaciones-exchange:chat.notificaciones}")
	private String nombreExchange;

	@Value("${app.amqp.perfil-exchange:chat.conversacion}")
	private String nombreExchangePerfil;

	@Value("${app.amqp.perfil-queue:chat-conversacion.perfil}")
	private String nombreQueuePerfil;

	@Value("${app.amqp.perfil-routing-key:registro.#}")
	private String routingKeyPerfil;

	@Bean
	public TopicExchange notificacionesExchange() {
		return new TopicExchange(nombreExchange, true, false);
	}

	@Bean
	public TopicExchange perfilExchange() {
		return new TopicExchange(nombreExchangePerfil, true, false);
	}

	@Bean
	public Queue perfilQueue() {
		return QueueBuilder.durable(nombreQueuePerfil).build();
	}

	@Bean
	public Binding perfilBinding(@Qualifier("perfilQueue") Queue perfilQueue,
			@Qualifier("perfilExchange") TopicExchange perfilExchange) {
		return BindingBuilder.bind(perfilQueue).to(perfilExchange).with(routingKeyPerfil);
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
