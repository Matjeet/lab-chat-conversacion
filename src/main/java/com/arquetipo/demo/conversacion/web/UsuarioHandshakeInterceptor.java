package com.arquetipo.demo.conversacion.web;

import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Extrae el {@code usuario} del path de conexion ({@code /ws/chat/{usuario}}) y lo deja en
 * los atributos de la sesion, para que {@link ChatWebSocketHandler} no tenga que reparsear la
 * URL en cada mensaje.
 *
 * <p>Identificacion minima a proposito: no valida contra ningun proveedor de identidad
 * todavia (pendiente decidir si reutiliza la sesion de Firebase de {@code chat-frontend} o
 * pasa por {@code chat-gateway}, ver {@code CLAUDE.md}).
 */
public class UsuarioHandshakeInterceptor implements HandshakeInterceptor {

	static final String ATRIBUTO_USUARIO = "usuario";

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) {
		String path = request.getURI().getPath();
		String usuario = path.substring(path.lastIndexOf('/') + 1);
		if (usuario.isBlank()) {
			return false;
		}
		attributes.put(ATRIBUTO_USUARIO, usuario);
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Exception exception) {
	}
}
