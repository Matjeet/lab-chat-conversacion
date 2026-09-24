package com.arquetipo.demo.conversacion.service;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.conversacion.domain.SolicitudChat;
import com.arquetipo.demo.conversacion.mapper.SolicitudChatMapper;
import com.arquetipo.demo.conversacion.repository.SolicitudChatRepository;
import com.arquetipo.demo.conversacion.web.dto.SolicitudChatResponse;
import com.arquetipo.demo.registro.grpc.RegistroGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquesta la creacion de una solicitud de chat: valida que {@code solicitante} y
 * {@code solicitado} existan en chat-registro y que no sean el mismo usuario, comprueba que no
 * exista ya una solicitud entre ambos (en cualquier sentido), persiste y avisa por AMQP (ver
 * {@link NotificadorAmqp}). Aceptar o rechazar una solicitud no esta implementado todavia.
 */
@Slf4j
@Service
public class SolicitudChatService {

	private final SolicitudChatRepository repository;
	private final SolicitudChatMapper mapper;
	private final RegistroGrpcClient registroClient;
	private final NotificadorAmqp notificadorAmqp;

	public SolicitudChatService(SolicitudChatRepository repository, SolicitudChatMapper mapper,
			RegistroGrpcClient registroClient, NotificadorAmqp notificadorAmqp) {
		this.repository = repository;
		this.mapper = mapper;
		this.registroClient = registroClient;
		this.notificadorAmqp = notificadorAmqp;
	}

	/**
	 * @throws ValidationException si {@code solicitante} y {@code solicitado} son el mismo usuario
	 * @throws ResourceNotFoundException si alguno de los dos usernames no existe en chat-registro
	 * @throws DuplicateResourceException si ya existe una solicitud entre ambos, en cualquier sentido
	 */
	public SolicitudChatResponse crear(String solicitante, String solicitado) {
		log.debug(">> crear(solicitante='{}', solicitado='{}')", solicitante, solicitado);

		if (solicitante.equalsIgnoreCase(solicitado)) {
			throw new ValidationException("No se puede crear una solicitud de chat hacia uno mismo");
		}
		if (!registroClient.existeUsername(solicitante)) {
			throw new ResourceNotFoundException("No existe el usuario solicitante '%s'".formatted(solicitante));
		}
		if (!registroClient.existeUsername(solicitado)) {
			throw new ResourceNotFoundException("No existe el usuario solicitado '%s'".formatted(solicitado));
		}
		if (repository.findEntreUsuarios(solicitante, solicitado).isPresent()) {
			throw new DuplicateResourceException(
					"Ya existe una solicitud de chat entre '%s' y '%s'".formatted(solicitante, solicitado));
		}

		SolicitudChat solicitud = new SolicitudChat();
		solicitud.setSolicitante(solicitante);
		solicitud.setSolicitado(solicitado);
		solicitud.setAceptada(false);
		SolicitudChat guardada = repository.save(solicitud);
		log.debug("Solicitud de chat guardada id={} solicitante='{}' solicitado='{}'",
				guardada.getId(), solicitante, solicitado);

		notificadorAmqp.notificarSolicitud(solicitante, solicitado);

		SolicitudChatResponse respuesta = mapper.toResponse(guardada);
		log.debug("<< crear() -> OK, id={}", respuesta.id());
		return respuesta;
	}
}
