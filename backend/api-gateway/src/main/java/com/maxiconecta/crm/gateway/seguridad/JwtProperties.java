package com.maxiconecta.crm.gateway.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.seguridad.jwt")
public record JwtProperties(String secreto, long expiracionMinutos) {
}
