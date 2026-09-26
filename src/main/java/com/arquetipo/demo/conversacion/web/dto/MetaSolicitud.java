package com.arquetipo.demo.conversacion.web.dto;

/**
 * Informacion adicional de una notificacion de tipo {@code "solicitud"} (ver
 * {@code NotificacionAmqp#meta()}), en su propio objeto anidado para poder sumarle mas campos
 * en el futuro sin cambiar la forma de {@code NotificacionAmqp}. {@code aceptada} y
 * {@code pendiente} son independientes (mismo par de campos que {@code SolicitudChat}): con
 * ambos, un consumidor distingue los tres estados posibles -- pendiente
 * ({@code pendiente=true, aceptada=false}), aceptada ({@code pendiente=false, aceptada=true})
 * y rechazada ({@code pendiente=false, aceptada=false}) -- aunque hoy solo existe el primero,
 * porque aceptar/rechazar no esta implementado todavia.
 */
public record MetaSolicitud(boolean aceptada, boolean pendiente) {
}
