package com.maxiconecta.crm.gateway.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.gateway.comun.ApiError;
import com.maxiconecta.crm.gateway.usuario.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.maxiconecta.crm.gateway.rol.Permisos.AUDITORIA_CONSULTAR;
import static com.maxiconecta.crm.gateway.rol.Permisos.CLIENTE_CONSULTAR;
import static com.maxiconecta.crm.gateway.rol.Permisos.CLIENTE_EDITAR;
import static com.maxiconecta.crm.gateway.rol.Permisos.EVENTOS_REPROCESAR;
import static com.maxiconecta.crm.gateway.rol.Permisos.FIDELIZACION_CONFIGURAR;
import static com.maxiconecta.crm.gateway.rol.Permisos.INDICADORES_CONSULTAR;
import static com.maxiconecta.crm.gateway.rol.Permisos.INTERACCIONES_CONSULTAR;
import static com.maxiconecta.crm.gateway.rol.Permisos.INTERACCIONES_REGISTRAR;
import static com.maxiconecta.crm.gateway.rol.Permisos.PUNTOS_CONSULTAR;
import static com.maxiconecta.crm.gateway.rol.Permisos.SEGMENTACION_CONFIGURAR;
import static com.maxiconecta.crm.gateway.rol.Permisos.SEGMENTOS_CONSULTAR;
import static com.maxiconecta.crm.gateway.rol.Permisos.USUARIOS_ADMINISTRAR;

/**
 * Control de acceso del CRM. Implementa la tabla "Permiso requerido por ruta"
 * de docs/seguridad/matriz-permisos.md: la primera regla que coincide decide.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, JwtService jwtService,
                                                 UsuarioService usuarioService, ObjectMapper objectMapper)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((solicitud, respuesta, ex) -> escribirError(objectMapper, solicitud,
                                respuesta, HttpServletResponse.SC_UNAUTHORIZED, "No autenticado",
                                "Se requiere iniciar sesión con un token válido"))
                        .accessDeniedHandler((solicitud, respuesta, ex) -> escribirError(objectMapper, solicitud,
                                respuesta, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado",
                                "Su rol no tiene permiso para esta operación")))
                .authorizeHttpRequests(reglas -> reglas
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()

                        .requestMatchers("/api/admin/auditoria/**").hasAuthority(AUDITORIA_CONSULTAR)
                        .requestMatchers("/api/admin/**").hasAuthority(USUARIOS_ADMINISTRAR)

                        .requestMatchers("/api/comportamiento/eventos/**").hasAuthority(EVENTOS_REPROCESAR)

                        .requestMatchers(HttpMethod.GET, "/api/perfil/**").hasAuthority(CLIENTE_CONSULTAR)
                        .requestMatchers("/api/perfil/**").hasAuthority(CLIENTE_EDITAR)

                        .requestMatchers(HttpMethod.GET, "/api/comportamiento/**").hasAuthority(INDICADORES_CONSULTAR)

                        .requestMatchers(HttpMethod.GET, "/api/segmentacion/**").hasAuthority(SEGMENTOS_CONSULTAR)
                        .requestMatchers("/api/segmentacion/**").hasAuthority(SEGMENTACION_CONFIGURAR)

                        .requestMatchers(HttpMethod.GET, "/api/fidelizacion/**").hasAuthority(PUNTOS_CONSULTAR)
                        .requestMatchers("/api/fidelizacion/**").hasAuthority(FIDELIZACION_CONFIGURAR)

                        .requestMatchers(HttpMethod.GET, "/api/interacciones/**").hasAuthority(INTERACCIONES_CONSULTAR)
                        .requestMatchers("/api/interacciones/**").hasAuthority(INTERACCIONES_REGISTRAR)

                        .anyRequest().denyAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, usuarioService),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new IdentidadHeadersFilter(), AuthorizationFilter.class);
        return http.build();
    }

    private static void escribirError(ObjectMapper objectMapper, HttpServletRequest solicitud,
                                      HttpServletResponse respuesta, int estado, String error, String mensaje)
            throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(respuesta.getOutputStream(), ApiError.de(estado, error, mensaje, solicitud.getRequestURI()));
    }
}
