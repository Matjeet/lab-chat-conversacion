package com.arquetipo.demo.conversacion.repository;

import com.arquetipo.demo.conversacion.domain.Mensaje;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.stereotype.Repository;

/**
 * Implementacion de {@link MensajeRepositoryCustom}. Los stages se arman como {@link Document}
 * (no JSON con placeholders tipo {@code @Query}) para que {@code usuario} nunca se interpole
 * como texto dentro de una consulta: no se valida el formato de {@code usuario} en este
 * endpoint (ver {@code ListaChatsRequest.usuario} en el .proto), asi que no es un dato de
 * confianza para construir una query a mano.
 */
@Slf4j
@Repository
class MensajeRepositoryImpl implements MensajeRepositoryCustom {

	private static final String COLECCION = "mensajes";

	private final MongoTemplate mongoTemplate;

	MensajeRepositoryImpl(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public List<Mensaje> listaChats(String usuario, Instant cursorEnviadoEn, ObjectId cursorId, int limite) {
		log.debug(">> listaChats(usuario='{}', conCursor={}, limite={})", usuario, cursorEnviadoEn != null, limite);

		List<AggregationOperation> stages = new ArrayList<>();

		// 1) Solo mensajes donde el usuario participa, de un lado o del otro.
		stages.add(Aggregation.stage(new Document("$match", new Document("$or", List.of(
				new Document("remitente", usuario),
				new Document("destinatario", usuario))))));

		// 2) Calcula quien es "la otra persona" de cada mensaje, desde el punto de vista de usuario.
		stages.add(Aggregation.stage(new Document("$addFields", new Document("otroUsuario",
				new Document("$cond", new Document("if", new Document("$eq", List.of("$remitente", usuario)))
						.append("then", "$destinatario")
						.append("else", "$remitente"))))));

		// 3) Ordena por fecha antes de agrupar: $first del grupo se queda con el mas reciente.
		stages.add(Aggregation.stage(new Document("$sort", new Document("enviadoEn", -1))));

		// 4) Un grupo por otroUsuario: el documento completo mas reciente de esa conversacion.
		stages.add(Aggregation.stage(new Document("$group", new Document("_id", "$otroUsuario")
				.append("ultimoMensaje", new Document("$first", "$$ROOT")))));

		// 5) Orden final de los chats: ultimo mensaje mas reciente primero, id como desempate.
		stages.add(Aggregation.stage(new Document("$sort", new Document("ultimoMensaje.enviadoEn", -1)
				.append("ultimoMensaje._id", -1))));

		// 6) Cursor: solo los chats "despues" del punto donde se quedo la pagina anterior.
		if (cursorEnviadoEn != null) {
			stages.add(Aggregation.stage(new Document("$match", new Document("$or", List.of(
					new Document("ultimoMensaje.enviadoEn", new Document("$lt", cursorEnviadoEn)),
					new Document("$and", List.of(
							new Document("ultimoMensaje.enviadoEn", cursorEnviadoEn),
							new Document("ultimoMensaje._id", new Document("$lt", cursorId)))))))));
		}

		stages.add(Aggregation.stage(new Document("$limit", limite)));

		// 7) Vuelve a la forma plana de Mensaje (el _id que queda es el del mensaje, no el del grupo).
		stages.add(Aggregation.stage(new Document("$replaceRoot", new Document("newRoot", "$ultimoMensaje"))));

		TypedAggregation<Mensaje> aggregation = Aggregation.newAggregation(Mensaje.class, stages);
		AggregationResults<Mensaje> resultados = mongoTemplate.aggregate(aggregation, COLECCION, Mensaje.class);

		List<Mensaje> chats = resultados.getMappedResults();
		log.debug("<< listaChats() -> OK, chats={}", chats.size());
		return chats;
	}
}
