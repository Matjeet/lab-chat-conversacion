package com.arquetipo.demo.perfil.amqp;

import com.arquetipo.demo.perfil.amqp.dto.UsuarioRegistradoEntrante;
import com.arquetipo.demo.perfil.service.PerfilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada de las altas de usuario que publica chat-registro (exchange, cola y binding
 * declarados en {@code com.arquetipo.demo.common.config.RabbitMqConfig}). Delega todo en
 * {@link PerfilService}.
 */
@Slf4j
@Component
public class PerfilListener {

	private final PerfilService service;

	public PerfilListener(PerfilService service) {
		this.service = service;
	}

	@RabbitListener(queues = "${app.amqp.perfil-queue:chat-conversacion.perfil}")
	public void recibir(UsuarioRegistradoEntrante mensaje) {
		log.debug(">> recibir(username='{}')", mensaje.username());
		service.registrar(mensaje.username(), mensaje.avatar());
		log.debug("<< recibir()");
	}
}
