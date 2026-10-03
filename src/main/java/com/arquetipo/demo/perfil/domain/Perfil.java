package com.arquetipo.demo.perfil.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Perfil publico de un usuario (username + avatar), copia local de lo que chat-registro
 * publica en RabbitMQ al terminar cada alta (ver {@code PerfilListener}). La fuente de verdad
 * del usuario sigue siendo chat-registro; esto solo lo deja a mano de este servicio.
 */
@Getter
@Setter
@Document(collection = "perfil")
public class Perfil {

	@Id
	private String id;

	@Indexed(unique = true)
	private String username;

	/** Enlace http(s) o etiqueta {@code <Blobatar .../>}, tal cual lo publica chat-registro; {@code null} si no eligio uno. */
	private String avatar;

	@CreatedDate
	private Instant creadoEn;
}
