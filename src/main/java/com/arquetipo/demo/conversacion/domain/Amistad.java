package com.arquetipo.demo.conversacion.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Amistad entre dos usuarios, registrada cuando se acepta una solicitud de chat (ver
 * {@code SolicitudChatService#actualizar}). Sin direccion -- a diferencia de
 * {@link SolicitudChat}, aqui no hay "quien pidio que a quien", solo los dos usuarios y cuando
 * se hizo el registro.
 */
@Getter
@Setter
@Document(collection = "amigos")
public class Amistad {

	@Id
	private String id;

	@Indexed
	private String usuarioA;

	@Indexed
	private String usuarioB;

	@CreatedDate
	private Instant creadaEn;
}
