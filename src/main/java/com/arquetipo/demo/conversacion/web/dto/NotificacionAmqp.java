package com.arquetipo.demo.conversacion.web.dto;

/**
 * Mensaje publicado al exchange de notificaciones de RabbitMQ (ver
 * {@code com.arquetipo.demo.common.config.RabbitMqConfig}). {@code tipo} distingue el motivo
 * de la notificacion -- por ahora solo {@code "solicitud"} (ver
 * {@code NotificadorAmqp#TIPO_SOLICITUD}), pensado para admitir otros tipos en el futuro sin
 * cambiar el contrato del mensaje. {@code meta} lleva informacion adicional propia de ese tipo
 * -- para {@code "solicitud"}, si ya fue aceptada (ver {@link MetaSolicitud}).
 */
public record NotificacionAmqp(String solicitante, String solicitado, String tipo, MetaSolicitud meta) {
}
