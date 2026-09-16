package com.arquetipo.demo.conversacion.web;

import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historial de una conversacion 1 a 1. El envio de mensajes nuevos va por
 * {@link ChatWebSocketHandler} (WebSocket, {@code /ws/chat/{usuario}}), no por aqui: este
 * endpoint REST solo sirve para que un cliente cargue los mensajes previos al conectarse.
 */
@Tag(name = "Conversaciones", description = "Historial de mensajes de una conversacion 1 a 1")
@RestController
@RequestMapping("/api/v1/conversaciones")
public class ConversacionController {

	private final ConversacionService service;

	public ConversacionController(ConversacionService service) {
		this.service = service;
	}

	@Operation(summary = "Historial de una conversacion entre dos usuarios, ordenado por fecha de envio")
	@GetMapping("/{usuarioA}/{usuarioB}")
	public List<MensajeResponse> historial(@PathVariable String usuarioA, @PathVariable String usuarioB) {
		return service.historial(usuarioA, usuarioB);
	}
}
