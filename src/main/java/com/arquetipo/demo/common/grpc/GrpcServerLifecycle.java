package com.arquetipo.demo.common.grpc;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

/**
 * Arranca y detiene el servidor gRPC embebido junto con el ciclo de vida de Spring, igual que
 * el servlet container arranca el servidor HTTP — pero en un puerto TCP propio
 * ({@code grpc.server.port}), independiente de {@code server.port}.
 *
 * <p>No hay un starter de gRPC de terceros de por medio: este componente descubre todos los
 * beans {@link BindableService} del contexto (hoy solo {@code ConversacionGrpcController}, ver
 * conversacion/grpc/) y los registra en el servidor. Anadir un nuevo servicio gRPC en el
 * futuro es declarar un nuevo {@code BindableService}, no tocar esta clase.
 *
 * <p>Tambien aplica, a nivel de servidor (para todos los servicios), cualquier bean
 * {@link ServerInterceptor} del contexto (hoy {@code UsuarioMetadataInterceptor}) — un
 * interceptor decide el mismo a que metodos aplica (ver su propio Javadoc), esta clase no
 * sabe nada de esa logica.
 *
 * <p>Se desactiva por completo con {@code grpc.server.enabled=false} (los tests lo hacen) para
 * no competir por el puerto entre clases de test.
 */
@Slf4j
public class GrpcServerLifecycle implements SmartLifecycle {

	private final GrpcServerProperties properties;
	private final List<BindableService> servicios;
	private final List<ServerInterceptor> interceptores;
	private Server server;
	private volatile boolean corriendo = false;

	public GrpcServerLifecycle(GrpcServerProperties properties, List<BindableService> servicios,
			List<ServerInterceptor> interceptores) {
		this.properties = properties;
		this.servicios = servicios;
		this.interceptores = interceptores;
	}

	@Override
	public void start() {
		try {
			NettyServerBuilder builder = NettyServerBuilder.forPort(properties.getPort())
					.addService(ProtoReflectionServiceV1.newInstance());
			servicios.forEach(builder::addService);
			interceptores.forEach(builder::intercept);

			server = builder.build().start();
			corriendo = true;
			log.info("Servidor gRPC escuchando en el puerto {} con {} servicio(s): {}",
					properties.getPort(), servicios.size(),
					servicios.stream().map(s -> s.bindService().getServiceDescriptor().getName()).toList());
		} catch (IOException ex) {
			throw new IllegalStateException(
					"No se pudo iniciar el servidor gRPC en el puerto " + properties.getPort(), ex);
		}
	}

	@Override
	public void stop() {
		if (server == null) {
			return;
		}
		try {
			server.shutdown();
			if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
				log.warn("El servidor gRPC no termino en el tiempo de gracia; se fuerza el cierre");
				server.shutdownNow();
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			server.shutdownNow();
		} finally {
			corriendo = false;
		}
	}

	@Override
	public boolean isRunning() {
		return corriendo;
	}

	@Override
	public int getPhase() {
		// Arranca despues de la infraestructura por defecto (repositorios, etc.) y se detiene
		// antes de esta, para no aceptar peticiones nuevas mientras el resto del contexto ya se
		// esta cerrando.
		return Integer.MAX_VALUE - 1;
	}
}
