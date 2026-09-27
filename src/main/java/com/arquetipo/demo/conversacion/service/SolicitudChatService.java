package com.arquetipo.demo.conversacion.service;

import com.arquetipo.demo.common.exception.DuplicateResourceException;
import com.arquetipo.demo.common.exception.ResourceNotFoundException;
import com.arquetipo.demo.common.exception.ValidationException;
import com.arquetipo.demo.conversacion.domain.Amistad;
import com.arquetipo.demo.conversacion.domain.SolicitudChat;
import com.arquetipo.demo.conversacion.mapper.SolicitudChatMapper;
import com.arquetipo.demo.conversacion.repository.AmistadRepository;
import com.arquetipo.demo.conversacion.repository.SolicitudChatRepository;
import com.arquetipo.demo.conversacion.web.dto.SolicitudChatResponse;
import com.arquetipo.demo.registro.grpc.RegistroGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquesta la creacion y la resolucion de una solicitud de chat.
 *
 * <p>{@link #crear}: valida que {@code solicitante} y {@code solicitado} existan en
 * chat-registro y que no sean el mismo usuario, comprueba que no exista ya una solicitud
 * **pendiente** entre ambos (en cualquier sentido), persiste y avisa por AMQP (ver
 * {@link NotificadorAmqp}). Si ya existe una solicitud pendiente, no se persiste nada nuevo ni
 * se notifica por AMQP -- solo se informa del estado actual (ver
 * {@link DuplicateResourceException}).
 *
 * <p>{@link #actualizar}: resuelve la solicitud pendiente entre dos usuarios (aceptada o
 * rechazada); si se acepta, registra la amistad entre ambos ({@link Amistad}) y, en cualquier
 * caso, avisa por AMQP con el estado ya actualizado.
 */
@Slf4j
@Service
public class SolicitudChatService {

	private final SolicitudChatRepository repository;
	private final AmistadRepository amistadRepository;
	private final SolicitudChatMapper mapper;
	private final RegistroGrpcClient registroClient;
	private final NotificadorAmqp notificadorAmqp;

	public SolicitudChatService(SolicitudChatRepository repository, AmistadRepository amistadRepository,
			SolicitudChatMapper mapper, RegistroGrpcClient registroClient, NotificadorAmqp notificadorAmqp) {
		this.repository = repository;
		this.amistadRepository = amistadRepository;
		this.mapper = mapper;
		this.registroClient = registroClient;
		this.notificadorAmqp = notificadorAmqp;
	}

	/**
	 * @throws ValidationException si {@code solicitante} y {@code solicitado} son el mismo usuario
	 * @throws ResourceNotFoundException si alguno de los dos usernames no existe en chat-registro
	 * @throws DuplicateResourceException si ya existe una solicitud **pendiente** entre ambos, en
	 *     cualquier sentido -- en ese caso no se persiste nada nuevo ni se notifica por AMQP
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
		if (repository.findPendienteEntreUsuarios(solicitante, solicitado).isPresent()) {
			log.debug("<< crear() -> ya hay una solicitud pendiente, no se persiste ni se notifica");
			throw new DuplicateResourceException(
					"Ya existe una solicitud de chat pendiente entre '%s' y '%s'".formatted(solicitante, solicitado));
		}

		SolicitudChat solicitud = new SolicitudChat();
		solicitud.setSolicitante(solicitante);
		solicitud.setSolicitado(solicitado);
		solicitud.setAceptada(false);
		solicitud.setPendiente(true);
		SolicitudChat guardada = repository.save(solicitud);
		log.debug("Solicitud de chat guardada id={} solicitante='{}' solicitado='{}'",
				guardada.getId(), solicitante, solicitado);

		notificadorAmqp.notificarSolicitud(solicitante, solicitado, guardada.isAceptada(), guardada.isPendiente());

		SolicitudChatResponse respuesta = mapper.toResponse(guardada);
		log.debug("<< crear() -> OK, id={}", respuesta.id());
		return respuesta;
	}

	/**
	 * @throws ValidationException si {@code usuarioA} y {@code usuarioB} son el mismo usuario
	 * @throws ResourceNotFoundException si no existe una solicitud pendiente entre ambos, en
	 *     cualquier sentido
	 */
	public SolicitudChatResponse actualizar(String usuarioA, String usuarioB, boolean aceptada) {
		log.debug(">> actualizar(usuarioA='{}', usuarioB='{}', aceptada={})", usuarioA, usuarioB, aceptada);

		if (usuarioA.equalsIgnoreCase(usuarioB)) {
			throw new ValidationException("No se puede actualizar una solicitud de chat hacia uno mismo");
		}

		SolicitudChat solicitud = repository.findPendienteEntreUsuarios(usuarioA, usuarioB)
				.orElseThrow(() -> new ResourceNotFoundException(
						"No existe una solicitud de chat pendiente entre '%s' y '%s'".formatted(usuarioA, usuarioB)));

		solicitud.setPendiente(false);
		solicitud.setAceptada(aceptada);
		SolicitudChat actualizada = repository.save(solicitud);
		log.debug("Solicitud de chat actualizada id={} solicitante='{}' solicitado='{}' aceptada={}",
				actualizada.getId(), actualizada.getSolicitante(), actualizada.getSolicitado(), aceptada);

		if (aceptada) {
			Amistad amistad = new Amistad();
			amistad.setUsuarioA(actualizada.getSolicitante());
			amistad.setUsuarioB(actualizada.getSolicitado());
			amistadRepository.save(amistad);
			log.debug("Amistad registrada entre '{}' y '{}'",
					actualizada.getSolicitante(), actualizada.getSolicitado());
		}

		notificadorAmqp.notificarActualizacionSolicitud(
				actualizada.getSolicitante(), actualizada.getSolicitado(), aceptada);

		SolicitudChatResponse respuesta = mapper.toResponse(actualizada);
		log.debug("<< actualizar() -> OK, id={}", respuesta.id());
		return respuesta;
	}
}
