package com.arquetipo.demo.conversacion.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registra el endpoint de WebSocket del chat en {@code /ws/chat/{usuario}}.
 *
 * <p>Los origenes permitidos vienen de la variable de entorno {@code WEBSOCKET_ALLOWED_ORIGINS}
 * (lista separada por comas, mismo criterio que {@code CORS_ALLOWED_ORIGINS} en
 * {@code chat-registro}); por defecto solo el frontend en desarrollo.
 */
@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {

	private final ChatWebSocketHandler handler;
	private final String[] allowedOrigins;

	public ChatWebSocketConfig(ChatWebSocketHandler handler,
			@Value("${app.websocket.allowed-origins:http://localhost:3000}") String allowedOrigins) {
		this.handler = handler;
		this.allowedOrigins = allowedOrigins.split(",");
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler, "/ws/chat/{usuario}")
				.addInterceptors(new UsuarioHandshakeInterceptor())
				.setAllowedOrigins(allowedOrigins);
	}
}
