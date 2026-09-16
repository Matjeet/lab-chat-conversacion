package com.arquetipo.demo.conversacion.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Mensaje de texto de una conversacion 1 a 1. Se guarda tal cual llega: por ahora este
 * servicio no soporta editar ni borrar mensajes.
 */
@Getter
@Setter
@Document(collection = "mensajes")
public class Mensaje {

	@Id
	private String id;

	@Indexed
	private String remitente;

	@Indexed
	private String destinatario;

	private String contenido;

	@CreatedDate
	private Instant enviadoEn;
}
