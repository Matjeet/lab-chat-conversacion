package com.arquetipo.demo.conversacion.grpc;

import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.service.NotificadorTiempoReal;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada gRPC del chat: {@code Chat} es el equivalente al WebSocket
 * ({@code /ws/chat/{usuario}}, bidi streaming) y {@code Historial} el equivalente al REST del
 * historial ({@code GET /api/v1/conversaciones/{usuarioA}/{usuarioB}}, unario). Ninguno de los
 * dos reimplementa logica: delegan en {@link ConversacionService}, la misma que usan el
 * WebSocket y el REST.
 *
 * <p>El remitente de {@code Chat} lo deja {@link UsuarioMetadataInterceptor} en
 * {@link UsuarioMetadataInterceptor#USUARIO} antes de llegar aqui (la llamada ya se rechazo si
 * faltaba o tenia formato invalido) — no hace falta volver a comprobarlo.
 */
@Slf4j
@Component
public class ConversacionGrpcController extends ConversacionGrpcServiceGrpc.ConversacionGrpcServiceImplBase {

	private static final String DETALLE_ERROR_INTERNO = "Ocurrio un error inesperado. Contacte con soporte.";

	private final ConversacionService service;
	private final NotificadorTiempoReal notificador;
	private final ConversacionGrpcMapper mapper;
	private final Validator validator;

	public ConversacionGrpcController(ConversacionService service, NotificadorTiempoReal notificador,
			ConversacionGrpcMapper mapper, Validator validator) {
		this.service = service;
		this.notificador = notificador;
		this.mapper = mapper;
		this.validator = validator;
	}

	@Override
	public StreamObserver<MensajeSaliente> chat(StreamObserver<MensajeEntregado> responseObserver) {
		String remitente = UsuarioMetadataInterceptor.USUARIO.get();
		Consumer<MensajeResponse> receptor = mensaje -> responseObserver.onNext(mapper.aMensajeEntregado(mensaje));
		notificador.suscribir(remitente, receptor);
		log.info("Stream de chat gRPC abierto. usuario='{}'", remitente);

		return new StreamObserver<>() {

			@Override
			public void onNext(MensajeSaliente saliente) {
				MensajeEntrante entrante = mapper.aMensajeEntrante(saliente);
				Set<ConstraintViolation<MensajeEntrante>> violaciones = validator.validate(entrante);
				if (!violaciones.isEmpty()) {
					log.warn("Mensaje entrante invalido por gRPC, se descarta. remitente='{}' violaciones={}",
							remitente, violaciones.size());
					return;
				}
				try {
					service.enviar(remitente, entrante);
				} catch (Exception ex) {
					log.error("Excepcion no controlada al procesar un mensaje del stream de chat gRPC. "
							+ "remitente='{}'", remitente, ex);
				}
			}

			@Override
			public void onError(Throwable t) {
				log.warn("Stream de chat gRPC cerrado con error. usuario='{}'", remitente, t);
				notificador.desuscribir(remitente, receptor);
			}

			@Override
			public void onCompleted() {
				notificador.desuscribir(remitente, receptor);
				responseObserver.onCompleted();
				log.info("Stream de chat gRPC cerrado. usuario='{}'", remitente);
			}
		};
	}

	@Override
	public void historial(HistorialRequest request, StreamObserver<HistorialResponse> responseObserver) {
		try {
			PageResponse<MensajeResponse> pagina = service.historial(
					request.getUsuarioA(), request.getUsuarioB(), mapper.aPageable(request));
			responseObserver.onNext(mapper.aHistorialResponse(pagina));
			responseObserver.onCompleted();
		} catch (Exception ex) {
			log.error("Excepcion no controlada en el endpoint gRPC de historial", ex);
			responseObserver.onError(
					Status.INTERNAL.withDescription(DETALLE_ERROR_INTERNO).asRuntimeException());
		}
	}
}
