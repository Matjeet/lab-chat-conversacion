package com.arquetipo.demo.conversacion.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Identifica al remitente del {@code rpc Chat} (equivalente gRPC de
 * {@code UsuarioHandshakeInterceptor} para el WebSocket): lee la cabecera de metadata
 * {@code usuario} al abrir el stream y, si tiene formato de username de chat-registro
 * (3-50 caracteres, {@code A-Z a-z 0-9 . _ -}), la deja disponible en {@link #USUARIO} para
 * todo el ciclo de vida del stream. Si falta o el formato es invalido, rechaza la llamada con
 * {@code INVALID_ARGUMENT} antes de que llegue al controlador.
 *
 * <p>Solo aplica al metodo {@code Chat}: {@code Historial} no necesita identidad (los dos
 * usuarios de la conversacion van en la propia peticion, igual que en el REST).
 *
 * <p>Igual que en el WebSocket, esto valida el <b>formato</b>, no la identidad real: nada
 * comprueba todavia que quien manda la cabecera sea el dueno de ese username.
 *
 * <p>Se aplica a nivel de servidor (ver {@code GrpcServerLifecycle}, common/grpc), no
 * envolviendo el {@code BindableService} a mano: asi {@code ConversacionGrpcController} sigue
 * siendo el unico bean {@code BindableService} del contexto (igual que
 * {@code RegistroGrpcController} en chat-registro) y no hay que registrar el servicio dos
 * veces.
 */
@Component
public class UsuarioMetadataInterceptor implements ServerInterceptor {

	public static final Context.Key<String> USUARIO = Context.key("usuario");

	static final Metadata.Key<String> USUARIO_METADATA_KEY =
			Metadata.Key.of("usuario", Metadata.ASCII_STRING_MARSHALLER);

	private static final Pattern FORMATO_USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,50}$");

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
			ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

		if (!ConversacionGrpcServiceGrpc.getChatMethod().getFullMethodName()
				.equals(call.getMethodDescriptor().getFullMethodName())) {
			return next.startCall(call, headers);
		}

		String usuario = headers.get(USUARIO_METADATA_KEY);
		if (usuario == null || !FORMATO_USERNAME.matcher(usuario).matches()) {
			call.close(Status.INVALID_ARGUMENT.withDescription(
					"Cabecera de metadata 'usuario' ausente o con formato invalido "
							+ "(username de chat-registro: 3-50 caracteres, solo A-Z a-z 0-9 . _ -)"),
					headers);
			return new ServerCall.Listener<>() { };
		}

		Context context = Context.current().withValue(USUARIO, usuario);
		return Contexts.interceptCall(context, call, headers, next);
	}
}
