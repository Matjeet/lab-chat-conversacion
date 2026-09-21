package com.arquetipo.demo.conversacion.service;

import com.arquetipo.demo.conversacion.domain.Mensaje;
import com.arquetipo.demo.conversacion.mapper.MensajeMapper;
import com.arquetipo.demo.conversacion.repository.MensajeRepository;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import lombok.extern.slf4j.Slf4j;
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
}
