package com.maxiconecta.crm.gateway.comun;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** También cubre el proxy funcional de Gateway MVC, al que no llega RestControllerAdvice. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ServicioNoDisponibleFilter extends OncePerRequestFilter {
    private final ObjectMapper json;
    public ServicioNoDisponibleFilter(ObjectMapper json) { this.json = json; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try { chain.doFilter(request, response); }
        catch (ServletException | RuntimeException ex) {
            if (!request.getRequestURI().startsWith("/api/") || response.isCommitted() || !esFalloDeRed(ex)) throw ex;
            response.resetBuffer();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            json.writeValue(response.getOutputStream(), ApiError.de(503, "Service Unavailable",
                    "El servicio solicitado no está disponible. Intente nuevamente.", request.getRequestURI()));
        }
    }

    private boolean esFalloDeRed(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof ResourceAccessException) return true;
        }
        return false;
    }
}
