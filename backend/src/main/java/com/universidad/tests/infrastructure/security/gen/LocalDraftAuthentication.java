package com.universidad.tests.infrastructure.security.gen;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Autenticación local opcional. Sustituir por la identidad del equipo GEN antes de integrar. */
@Component
@Profile("gen02-draft")
public class LocalDraftAuthentication extends OncePerRequestFilter {
    private final String usuario;
    private final String password;

    public LocalDraftAuthentication(
            @Value("${GEN02_LOCAL_USER:}") String usuario,
            @Value("${GEN02_LOCAL_PASSWORD:}") String password) {
        this.usuario = usuario;
        this.password = password;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/evaluaciones")
                || request.getUserPrincipal() != null
                || usuario.isBlank()
                || password.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Basic ")) {
            try {
                byte[] provided = Base64.getDecoder().decode(header.substring(6));
                byte[] expected = (usuario + ":" + password).getBytes(StandardCharsets.UTF_8);
                if (MessageDigest.isEqual(provided, expected)) {
                    chain.doFilter(
                            new HttpServletRequestWrapper(request) {
                                @Override
                                public Principal getUserPrincipal() {
                                    return () -> usuario;
                                }

                                @Override
                                public String getRemoteUser() {
                                    return usuario;
                                }
                            },
                            response);
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // Credencial no válida: la petición sigue sin identidad y el caso de uso devuelve
                // 401.
            }
        }
        chain.doFilter(request, response);
    }
}
