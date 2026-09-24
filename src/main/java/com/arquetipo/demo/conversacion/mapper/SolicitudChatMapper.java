package com.arquetipo.demo.conversacion.mapper;

import com.arquetipo.demo.conversacion.domain.SolicitudChat;
import com.arquetipo.demo.conversacion.web.dto.SolicitudChatResponse;
import org.springframework.stereotype.Component;

/**
 * Mapeo manual entre {@link SolicitudChat} y su DTO de salida.
 */
@Component
public class SolicitudChatMapper {

	public SolicitudChatResponse toResponse(SolicitudChat solicitud) {
		return new SolicitudChatResponse(
				solicitud.getId(),
				solicitud.getSolicitante(),
				solicitud.getSolicitado(),
				solicitud.isAceptada(),
				solicitud.getCreadaEn());
	}
}
