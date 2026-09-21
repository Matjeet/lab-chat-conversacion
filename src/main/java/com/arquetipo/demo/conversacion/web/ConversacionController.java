package com.arquetipo.demo.conversacion.web;

import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historial de una conversacion 1 a 1. El envio de mensajes nuevos va por
 * {@link ChatWebSocketHandler} (WebSocket, {@code /ws/chat/{usuario}}), no por aqui: este
 * endpoint REST solo sirve para que un cliente cargue los mensajes previos al conectarse.
 *
 * <p>Origenes permitidos por CORS via {@code CORS_ALLOWED_ORIGINS} (mismo criterio que
 * {@code chat-registro}); no aplica al WebSocket, que tiene su propia variable
 * {@code WEBSOCKET_ALLOWED_ORIGINS} (ver {@link ChatWebSocketConfig}).
 */
@Slf4j
@Tag(name = "Conversaciones", description = "Historial de mensajes de una conversacion 1 a 1")
@RestController
@RequestMapping("/api/v1/conversaciones")
@CrossOrigin(
		origins = "${app.cors.allowed-origins:http://localhost:3000}",
		allowCredentials = "${app.cors.allow-credentials:false}")
public class ConversacionController {

	private final ConversacionService service;

	public ConversacionController(ConversacionService service) {
		this.service = service;
	}

	@Operation(summary = "Historial paginado de una conversacion entre dos usuarios, "
			+ "ordenado por fecha de envio (mas antiguo primero por defecto)")
	@GetMapping("/{usuarioA}/{usuarioB}")
	public PageResponse<MensajeResponse> historial(
			@PathVariable String usuarioA,
			@PathVariable String usuarioB,
			@ParameterObject
			@PageableDefault(size = 20, sort = "enviadoEn", direction = Sort.Direction.ASC)
			Pageable pageable) {
		log.debug(">> historial(usuarioA='{}', usuarioB='{}')", usuarioA, usuarioB);
		PageResponse<MensajeResponse> respuesta = service.historial(usuarioA, usuarioB, pageable);
		log.debug("<< historial() -> OK, totalElements={}", respuesta.totalElements());
		return respuesta;
	}
}
