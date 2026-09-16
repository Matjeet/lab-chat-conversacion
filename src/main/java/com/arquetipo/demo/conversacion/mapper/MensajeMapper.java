package com.arquetipo.demo.conversacion.mapper;

import com.arquetipo.demo.conversacion.domain.Mensaje;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import org.springframework.stereotype.Component;

/**
 * Mapeo manual entre {@link Mensaje} y su DTO de salida.
 */
@Component
public class MensajeMapper {

	public MensajeResponse toResponse(Mensaje mensaje) {
		return new MensajeResponse(
				mensaje.getId(),
				mensaje.getRemitente(),
				mensaje.getDestinatario(),
				mensaje.getContenido(),
				mensaje.getEnviadoEn());
	}
}
