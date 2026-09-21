package com.arquetipo.demo.conversacion.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arquetipo.demo.conversacion.service.ConversacionService;
import com.arquetipo.demo.conversacion.service.NotificadorTiempoReal;
import com.arquetipo.demo.conversacion.web.dto.MensajeEntrante;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prueba el endpoint gRPC del chat sobre un servidor in-process (sin red real), incluido
 * {@link UsuarioMetadataInterceptor} — mismo patron que {@code RegistroGrpcControllerTest} en
 * chat-registro. {@link ConversacionService} va mockeado: la entrega en tiempo real
 * ({@link NotificadorTiempoReal}) se prueba aparte, en {@code NotificadorTiempoRealTest}.
 */
class ConversacionGrpcControllerTest {

	private ConversacionService service;
	private Server server;
	private ManagedChannel channel;
	private ConversacionGrpcServiceGrpc.ConversacionGrpcServiceBlockingStub stubBloqueante;
	private ConversacionGrpcServiceGrpc.ConversacionGrpcServiceStub stubAsincrono;

	@BeforeEach
	void iniciarServidorInProcess() throws Exception {
		String nombreServidor = "conversacion-grpc-test-" + System.nanoTime();
		service = mock(ConversacionService.class);
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
		ConversacionGrpcController controller = new ConversacionGrpcController(
				service, new NotificadorTiempoReal(), new ConversacionGrpcMapper(), validator);

		server = InProcessServerBuilder.forName(nombreServidor)
				.directExecutor()
				.addService(controller)
				.intercept(new UsuarioMetadataInterceptor())
				.build()
				.start();
		channel = InProcessChannelBuilder.forName(nombreServidor).directExecutor().build();
		stubBloqueante = ConversacionGrpcServiceGrpc.newBlockingStub(channel);
		stubAsincrono = ConversacionGrpcServiceGrpc.newStub(channel);
	}

	@AfterEach
	void detenerServidor() throws Exception {
		channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
	}

	@Test
	void historial_delegaEnElServicioYMapeaLaPagina() {
		MensajeResponse mensaje = new MensajeResponse(
				"1", "mateo", "ana", "hola", Instant.parse("2026-09-18T20:00:00Z"));
		PageResponse<MensajeResponse> pagina =
				new PageResponse<>(List.of(mensaje), 0, 20, 1, 1, true, true, false);
		when(service.historial(eq("mateo"), eq("ana"), any())).thenReturn(pagina);

		HistorialResponse respuesta = stubBloqueante.historial(HistorialRequest.newBuilder()
				.setUsuarioA("mateo")
				.setUsuarioB("ana")
				.build());

		assertThat(respuesta.getContentCount()).isEqualTo(1);
		assertThat(respuesta.getContent(0).getRemitente()).isEqualTo("mateo");
		assertThat(respuesta.getContent(0).getContenido()).isEqualTo("hola");
		assertThat(respuesta.getTotalElements()).isEqualTo(1);
		assertThat(respuesta.getFirst()).isTrue();
	}

	@Test
	void chat_sinCabeceraUsuario_rechazaLaConexionConInvalidArgument() throws InterruptedException {
		AtomicReference<Throwable> errorRecibido = new AtomicReference<>();
		CountDownLatch cerrado = new CountDownLatch(1);

		stubAsincrono.chat(new StreamObserver<MensajeEntregado>() {
			@Override
			public void onNext(MensajeEntregado value) {
			}

			@Override
			public void onError(Throwable t) {
				errorRecibido.set(t);
				cerrado.countDown();
			}

			@Override
			public void onCompleted() {
				cerrado.countDown();
			}
		});

		assertThat(cerrado.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(errorRecibido.get()).isInstanceOf(StatusRuntimeException.class);
		assertThat(((StatusRuntimeException) errorRecibido.get()).getStatus().getCode())
				.isEqualTo(Status.Code.INVALID_ARGUMENT);
	}

	@Test
	void chat_conCabeceraUsuarioValida_persisteElMensajeConEseRemitente() {
		ConversacionGrpcServiceGrpc.ConversacionGrpcServiceStub stubConUsuario = conUsuario("mateo");

		StreamObserver<MensajeSaliente> peticion = stubConUsuario.chat(observadorVacio());
		peticion.onNext(MensajeSaliente.newBuilder().setDestinatario("ana").setContenido("hola").build());
		peticion.onCompleted();

		verify(service).enviar(eq("mateo"), eq(new MensajeEntrante("ana", "hola")));
	}

	@Test
	void chat_conMensajeInvalido_seDescartaSinLlamarAlServicio() {
		ConversacionGrpcServiceGrpc.ConversacionGrpcServiceStub stubConUsuario = conUsuario("mateo");

		StreamObserver<MensajeSaliente> peticion = stubConUsuario.chat(observadorVacio());
		// destinatario vacio: invalido, igual que en el WebSocket.
		peticion.onNext(MensajeSaliente.newBuilder().setDestinatario("").setContenido("hola").build());
		peticion.onCompleted();

		verify(service, never()).enviar(any(), any());
	}

	private ConversacionGrpcServiceGrpc.ConversacionGrpcServiceStub conUsuario(String usuario) {
		Metadata cabeceras = new Metadata();
		cabeceras.put(UsuarioMetadataInterceptor.USUARIO_METADATA_KEY, usuario);
		return stubAsincrono.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabeceras));
	}

	private static StreamObserver<MensajeEntregado> observadorVacio() {
		return new StreamObserver<MensajeEntregado>() {
			@Override
			public void onNext(MensajeEntregado value) {
			}

			@Override
			public void onError(Throwable t) {
			}

			@Override
			public void onCompleted() {
			}
		};
	}
}
