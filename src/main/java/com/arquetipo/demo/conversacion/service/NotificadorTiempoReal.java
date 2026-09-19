package com.arquetipo.demo.conversacion.service;

import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Registro de "quien esta conectado ahora mismo y por donde avisarle" para el chat en tiempo
 * real, compartido entre los tres protocolos de transporte (WebSocket, gRPC). Cada transporte
 * se suscribe con un receptor propio (que sabe mandar el mensaje por su canal concreto: un
 * {@code WebSocketSession} o un {@code StreamObserver} de gRPC) y {@link ConversacionService}
 * notifica aqui tras persistir un mensaje — asi un mensaje mandado por WebSocket llega en
 * tiempo real a un destinatario conectado por gRPC, y viceversa.
 *
 * <p>En memoria, por instancia del servicio: mismas limitaciones que tenia el registro de
 * sesiones cuando vivia solo en {@code ChatWebSocketHandler} (no apto para mas de una
 * instancia del servicio todavia).
 */
@Slf4j
@Component
public class NotificadorTiempoReal {

	private final Map<String, Set<Consumer<MensajeResponse>>> suscriptores = new ConcurrentHashMap<>();

	public void suscribir(String usuario, Consumer<MensajeResponse> receptor) {
		suscriptores.computeIfAbsent(usuario, u -> ConcurrentHashMap.newKeySet()).add(receptor);
	}

	public void desuscribir(String usuario, Consumer<MensajeResponse> receptor) {
		suscriptores.computeIfPresent(usuario, (u, receptores) -> {
			receptores.remove(receptor);
			return receptores.isEmpty() ? null : receptores;
		});
	}

	public void notificar(String usuario, MensajeResponse mensaje) {
		Set<Consumer<MensajeResponse>> receptores = suscriptores.get(usuario);
		if (receptores == null) {
			return;
		}
		for (Consumer<MensajeResponse> receptor : receptores) {
			try {
				receptor.accept(mensaje);
			} catch (Exception ex) {
				log.warn("No se pudo entregar el mensaje en tiempo real a '{}', se ignora este receptor",
						usuario, ex);
			}
		}
	}
}
