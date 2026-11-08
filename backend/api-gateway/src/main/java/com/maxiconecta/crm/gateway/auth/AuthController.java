package com.maxiconecta.crm.gateway.auth;

import com.maxiconecta.crm.gateway.auth.AuthDtos.LoginRequest;
import com.maxiconecta.crm.gateway.auth.AuthDtos.LoginResponse;
import com.maxiconecta.crm.gateway.auth.AuthDtos.SesionResponse;
import com.maxiconecta.crm.gateway.auth.AuthDtos.TokenServicioRequest;
import com.maxiconecta.crm.gateway.auth.AuthDtos.TokenServicioResponse;
import com.maxiconecta.crm.gateway.seguridad.ClientesServicio;
import com.maxiconecta.crm.gateway.seguridad.JwtService;
import com.maxiconecta.crm.gateway.seguridad.JwtService.TokenEmitido;
import com.maxiconecta.crm.gateway.seguridad.SesionToken;
import com.maxiconecta.crm.gateway.usuario.Usuario;
import com.maxiconecta.crm.gateway.usuario.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioService usuarioService;
    private final JwtService jwtService;
    private final ClientesServicio clientesServicio;

    public AuthController(UsuarioService usuarioService, JwtService jwtService, ClientesServicio clientesServicio) {
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
        this.clientesServicio = clientesServicio;
    }

    @PostMapping("/login")
    public LoginResponse iniciarSesion(@Valid @RequestBody LoginRequest solicitud) {
        Usuario usuario = usuarioService.autenticar(solicitud.usuario(), solicitud.password())
                .orElseThrow(CredencialesInvalidasException::new);
        String rol = usuario.getRol().getCodigo();
        List<String> permisos = usuario.getRol().codigosDePermisos();
        TokenEmitido token = jwtService.emitir(usuario.getNombreUsuario(), usuario.getNombreCompleto(), rol, permisos);
        return new LoginResponse(token.token(), "Bearer", token.expiraEn(), usuario.getNombreUsuario(),
                usuario.getNombreCompleto(), rol, permisos);
    }

    /**
     * SCRUM-545: token de servicio de un sistema del ERP (RIO-CRM-04). Un cliente o un secreto incorrecto
     * responde 401 con el mismo mensaje, para no revelar qué clientes existen.
     */
    @PostMapping("/servicio/token")
    public TokenServicioResponse tokenDeServicio(@Valid @RequestBody TokenServicioRequest solicitud) {
        SesionToken cliente = clientesServicio.autenticar(solicitud.cliente(), solicitud.secreto())
                .orElseThrow(() -> new CredencialesInvalidasException("Cliente de servicio o secreto incorrectos"));
        TokenEmitido token = jwtService.emitirServicio(cliente);
        return new TokenServicioResponse(token.token(), "Bearer", token.expiraEn(), cliente.usuario(), cliente.permisos());
    }

    @GetMapping("/yo")
    public SesionResponse sesionActual(Authentication autenticacion) {
        SesionToken sesion = (SesionToken) autenticacion.getDetails();
        return new SesionResponse(sesion.usuario(), sesion.nombreCompleto(), sesion.rol(), sesion.permisos());
    }
}
