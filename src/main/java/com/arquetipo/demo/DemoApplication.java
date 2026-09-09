package com.arquetipo.demo;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicacion.
 *
 * <p>El paquete {@code com.arquetipo.demo.sample} contiene un recurso CRUD completo (Product)
 * que sirve de plantilla: copia esas clases, renombralas para tu entidad y ajusta los campos.
 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(
		title = "Servicio backend (arquetipo)",
		version = "v1",
		description = "API de ejemplo generada a partir del arquetipo MVC de Spring Boot"))
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
