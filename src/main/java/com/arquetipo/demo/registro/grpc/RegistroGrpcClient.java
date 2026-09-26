package com.arquetipo.demo.registro.grpc;

import com.arquetipo.demo.common.exception.ServiceUnavailableException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Unico punto donde chat-conversacion habla con {@code chat-registro}: por ahora solo para
 * comprobar que un username existe ({@code SolicitudChatService}, antes de crear una solicitud
 * de chat). Mismo patron que {@code RegistroGrpcClient} en chat-gateway, recortado a lo que
 * este servicio necesita -- ver {@code src/main/proto/registro.proto}, la copia local y minima
 * del contrato de chat-registro.
 */
@Slf4j
@Component
public class RegistroGrpcClient {

	private static final String NOMBRE_SERVICIO = "chat-registro";

	private final RegistroGrpcServiceGrpc.RegistroGrpcServiceBlockingStub stub;

	public RegistroGrpcClient(RegistroGrpcServiceGrpc.RegistroGrpcServiceBlockingStub stub) {
		this.stub = stub;
	}

	/**
	 * Si un {@code username} ya esta en uso (sin distinguir mayusculas). Consulta publica de
	 * disponibilidad: no requiere que el {@code username} pertenezca a quien pregunta.
	 */
	public boolean existeUsername(String username) {
		log.debug(">> existeUsername(username='{}')", username);
		ExisteUsernameRequest peticion = ExisteUsernameRequest.newBuilder()
				.setUsername(username == null ? "" : username)
				.build();

		try {
			ExisteUsernameResponse respuesta = stub.existeUsername(peticion);
			log.debug("<< existeUsername() -> OK, existe={}", respuesta.getExiste());
			return respuesta.getExiste();
		} catch (StatusRuntimeException ex) {
			if (ex.getStatus().getCode() == Status.Code.UNAVAILABLE) {
				log.error("No se pudo contactar con {} por gRPC", NOMBRE_SERVICIO, ex);
				throw new ServiceUnavailableException(NOMBRE_SERVICIO);
			}
			log.error("Fallo inesperado llamando a {} por gRPC (codigo={})",
					NOMBRE_SERVICIO, ex.getStatus().getCode(), ex);
			throw new RuntimeException("Ocurrio un error inesperado al comunicarse con " + NOMBRE_SERVICIO, ex);
		}
	}
}
