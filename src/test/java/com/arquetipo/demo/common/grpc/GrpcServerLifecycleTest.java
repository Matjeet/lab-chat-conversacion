package com.arquetipo.demo.common.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Arranca el servidor gRPC real (Netty), no el in-process de
 * {@code ConversacionGrpcControllerTest}. Existe porque un desajuste de versiones entre
 * dependencias de gRPC solo revienta al construir el {@code Server} real
 * ({@code NettyServerBuilder.build()}) — un canal in-process nunca pasa por ahi y no lo
 * habria detectado. Mismo test que en chat-registro.
 */
class GrpcServerLifecycleTest {

	@Test
	void start_levantaElServidorNettyReal_yLuegoSeDetieneLimpio() {
		GrpcServerProperties properties = new GrpcServerProperties();
		properties.setPort(0); // puerto efimero: no colisiona si varias suites corren en paralelo
		GrpcServerLifecycle lifecycle = new GrpcServerLifecycle(properties, List.of(), List.of());

		lifecycle.start();
		try {
			assertThat(lifecycle.isRunning()).isTrue();
		} finally {
			lifecycle.stop();
		}
		assertThat(lifecycle.isRunning()).isFalse();
	}
}
