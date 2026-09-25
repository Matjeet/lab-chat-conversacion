package com.arquetipo.demo.conversacion.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Solicitud de chat de un usuario hacia otro: paso previo obligatorio para poder chatear (ver
 * ConversacionGrpcController#crearSolicitud). Aceptarla o rechazarla no esta implementado
 * todavia -- {@code aceptada} nace siempre en {@code false} y {@code pendiente} siempre en
 * {@code true}. Mientras exista una solicitud pendiente entre dos usuarios, no se puede crear
 * otra entre ellos (ver {@code SolicitudChatService#crear}).
 */
@Getter
@Setter
@Document(collection = "solicitudes_chat")
public class SolicitudChat {

	@Id
	private String id;

	@Indexed
	private String solicitante;

	@Indexed
	private String solicitado;

	private boolean aceptada;

	private boolean pendiente;

	@CreatedDate
	private Instant creadaEn;
}
