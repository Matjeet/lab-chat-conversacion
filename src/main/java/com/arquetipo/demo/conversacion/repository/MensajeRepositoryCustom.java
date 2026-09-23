package com.arquetipo.demo.conversacion.repository;

import com.arquetipo.demo.conversacion.domain.Mensaje;
import java.time.Instant;
import java.util.List;
import org.bson.types.ObjectId;

/**
 * Consultas de {@link Mensaje} que no encajan en un {@code @Query} de una sola linea (ver
 * {@link MensajeRepository}) — implementada a mano sobre {@code MongoTemplate} en
 * {@link MensajeRepositoryImpl}, siguiendo la convencion de Spring Data de fragmentos
 * custom (el sufijo {@code Impl} es lo que Spring Data usa para encontrarla).
 */
public interface MensajeRepositoryCustom {

	/**
	 * Un {@link Mensaje} por cada persona con la que {@code usuario} tiene al menos un mensaje
	 * (en cualquiera de los dos sentidos): el mas reciente de esa conversacion, ordenados por
	 * {@code enviadoEn} descendente (con {@code id} descendente como desempate).
	 *
	 * @param cursorEnviadoEn {@code null} para la primera pagina; si no, junto con
	 *     {@code cursorId} delimita donde sigue una pagina anterior (estrictamente "despues" de
	 *     ese punto en el orden de arriba)
	 * @param cursorId ver {@code cursorEnviadoEn}; ambos son {@code null} o ninguno
	 * @param limite cuantos resultados devolver como maximo
	 */
	List<Mensaje> listaChats(String usuario, Instant cursorEnviadoEn, ObjectId cursorId, int limite);
}
