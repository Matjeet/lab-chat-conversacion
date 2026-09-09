package com.arquetipo.demo.sample.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arquetipo.demo.sample.service.ProductService;
import com.arquetipo.demo.sample.exception.ResourceNotFoundException;
import com.arquetipo.demo.sample.web.dto.PageResponse;
import com.arquetipo.demo.sample.web.dto.ProductResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductService productService;

	private static ProductResponse sample() {
		return new ProductResponse(1L, "Teclado", "desc", new BigDecimal("79.90"),
				"SKU-001", true, Instant.now(), Instant.now());
	}

	@Test
	void findAll_devuelvePaginaMapeada() throws Exception {
		when(productService.findAll(any()))
				.thenReturn(new PageResponse<>(List.of(sample()), 0, 20, 1, 1, true, true, false));

		mockMvc.perform(get("/api/v1/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].sku").value("SKU-001"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void findById_inexistente_devuelve404ProblemDetail() throws Exception {
		when(productService.findById(eq(99L)))
				.thenThrow(new ResourceNotFoundException("Producto", 99L));

		mockMvc.perform(get("/api/v1/products/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Recurso no encontrado"))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void create_cuerpoInvalido_devuelve400ConErrores() throws Exception {
		mockMvc.perform(post("/api/v1/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors").isArray());
	}
}
