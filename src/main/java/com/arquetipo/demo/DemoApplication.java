package com.arquetipo.demo;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicacion.
 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(
		title = "chat-conversacion",
		version = "v1",
		description = "Chat en tiempo real por WebSockets entre dos usuarios, con mensajes de texto persistidos en MongoDB"))
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
