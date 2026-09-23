package com.arquetipo.demo.common.exception;

/**
 * Se lanza cuando la peticion es valida a nivel de tipos pero su contenido no lo es de una
 * forma que Bean Validation no puede expresar declarativamente — p. ej. un cursor de
 * paginacion con formato invalido en {@code ConversacionService#listaChats}. El manejador
 * global la traduce a HTTP 400; en gRPC se traduce a {@code INVALID_ARGUMENT}.
 */
public class ValidationException extends RuntimeException {

	public ValidationException(String message) {
		super(message);
	}

	public ValidationException(String message, Throwable cause) {
		super(message, cause);
	}
}
