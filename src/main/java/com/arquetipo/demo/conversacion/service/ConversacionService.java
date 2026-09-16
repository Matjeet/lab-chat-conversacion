package com.arquetipo.demo.conversacion.service;

import com.arquetipo.demo.conversacion.domain.Mensaje;
import com.arquetipo.demo.conversacion.mapper.MensajeMapper;
import com.arquetipo.demo.conversacion.repository.MensajeRepository;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Orquesta el envio de mensajes de texto entre dos usuarios: persiste cada mensaje en
 * MongoDB y expone el historial de una conversacion.
 *
 * <p>La entrega en tiempo real (a que sesiones de WebSocket abiertas hay que reenviar el
 * mensaje) no es responsabilidad de este servicio: la resuelve quien lo invoca
 * ({@code ChatWebSocketHandler}), que es quien conoce las sesiones conectadas.
 */
@Slf4j
@Service
public class ConversacionService {

	private final MensajeRepository repository;
	private final MensajeMapper mapper;

	public ConversacionService(MensajeRepository repository, MensajeMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	public MensajeResponse enviar(String remitente, MensajeEntrante entrante) {
		Mensaje mensaje = new Mensaje();
		mensaje.setRemitente(remitente);
		mensaje.setDestinatario(entrante.destinatario());
		mensaje.setContenido(entrante.contenido());

		Mensaje guardado = repository.save(mensaje);
		log.debug("Mensaje guardado id={} remitente='{}' destinatario='{}'",
				guardado.getId(), remitente, entrante.destinatario());
		return mapper.toResponse(guardado);
	}

	public List<MensajeResponse> historial(String usuarioA, String usuarioB) {
		return repository.findConversacion(usuarioA, usuarioB, Sort.by(Sort.Direction.ASC, "enviadoEn"))
				.stream()
				.map(mapper::toResponse)
				.toList();
	}
}
