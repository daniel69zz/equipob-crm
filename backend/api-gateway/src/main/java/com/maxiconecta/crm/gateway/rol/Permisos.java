package com.maxiconecta.crm.gateway.rol;

/**
 * Códigos de permiso del CRM. Deben coincidir con la tabla seguridad.permiso
 * y con docs/seguridad/matriz-permisos.md.
 */
public final class Permisos {

    public static final String CLIENTE_CONSULTAR = "CLIENTE_CONSULTAR";
    public static final String CLIENTE_EDITAR = "CLIENTE_EDITAR";
    public static final String INDICADORES_CONSULTAR = "INDICADORES_CONSULTAR";
    public static final String EVENTOS_REPROCESAR = "EVENTOS_REPROCESAR";
    public static final String SEGMENTOS_CONSULTAR = "SEGMENTOS_CONSULTAR";
    public static final String SEGMENTACION_CONFIGURAR = "SEGMENTACION_CONFIGURAR";
    public static final String PUNTOS_CONSULTAR = "PUNTOS_CONSULTAR";
    public static final String FIDELIZACION_CONFIGURAR = "FIDELIZACION_CONFIGURAR";
    public static final String INTERACCIONES_CONSULTAR = "INTERACCIONES_CONSULTAR";
    public static final String INTERACCIONES_REGISTRAR = "INTERACCIONES_REGISTRAR";
    public static final String USUARIOS_ADMINISTRAR = "USUARIOS_ADMINISTRAR";
    public static final String AUDITORIA_CONSULTAR = "AUDITORIA_CONSULTAR";

    private Permisos() {
    }
}
