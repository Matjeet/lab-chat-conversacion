package com.arquetipo.demo.conversacion.web.dto;

/**
 * Informacion adicional de una notificacion de tipo {@code "solicitud"} (ver
 * {@code NotificacionAmqp#meta()}), en su propio objeto anidado para poder sumarle mas campos
 * en el futuro sin cambiar la forma de {@code NotificacionAmqp}.
 */
public record MetaSolicitud(boolean aceptada) {
}
