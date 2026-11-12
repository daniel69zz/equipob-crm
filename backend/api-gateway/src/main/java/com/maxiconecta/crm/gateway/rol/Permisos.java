package com.maxiconecta.crm.gateway.rol;

/**
 * Códigos de permiso del CRM. Deben coincidir con la tabla seguridad.permiso
 * y con docs/seguridad/matriz-permisos.md.
 */
public final class Permisos {

    public static final String CLIENTE_CONSULTAR = "CLIENTE_CONSULTAR";
    public static final String CLIENTE_EDITAR = "CLIENTE_EDITAR";
    public static final String FICHA_INTEGRAL_CONSULTAR = "FICHA_INTEGRAL_CONSULTAR";
    public static final String INDICADORES_CONSULTAR = "INDICADORES_CONSULTAR";
    public static final String EVOLUCION_CONSUMO_CONSULTAR = "EVOLUCION_CONSUMO_CONSULTAR";
    public static final String SUGERENCIAS_CONSULTAR = "SUGERENCIAS_CONSULTAR";
    public static final String EVENTOS_REPROCESAR = "EVENTOS_REPROCESAR";
    public static final String SEGMENTOS_CONSULTAR = "SEGMENTOS_CONSULTAR";
    public static final String SEGMENTACION_CONFIGURAR = "SEGMENTACION_CONFIGURAR";
    /**
     * SCRUM-462: reportes de distribucion de clientes (EPIC-10, SCRUM-57). Es propio de los perfiles de
     * analisis comercial, mas restrictivo que SEGMENTOS_CONSULTAR: no lo tiene el Agente de Atencion.
     */
    public static final String REPORTES_CONSULTAR = "REPORTES_CONSULTAR";
    /**
     * SCRUM-482: exportar los reportes de clientes a CSV y PDF (SCRUM-60). Se exige junto con
     * REPORTES_CONSULTAR, para poder quitar la exportacion a un rol sin quitarle la consulta.
     */
    public static final String REPORTES_EXPORTAR = "REPORTES_EXPORTAR";
    public static final String REPORTES_PROGRAMAR = "REPORTES_PROGRAMAR";
    public static final String PUNTOS_CONSULTAR = "PUNTOS_CONSULTAR";
    /**
     * SCRUM-545: permiso de un cliente de servicio (RIO-CRM-04), no de un rol interno. Se asigna en la
     * configuración del cliente de servicio y no está en la tabla seguridad.permiso.
     */
    public static final String PUNTOS_CONSULTAR_VENTAS = "PUNTOS_CONSULTAR_VENTAS";
    public static final String FIDELIZACION_CONFIGURAR = "FIDELIZACION_CONFIGURAR";
    public static final String INTERACCIONES_CONSULTAR = "INTERACCIONES_CONSULTAR";
    public static final String INTERACCIONES_REGISTRAR = "INTERACCIONES_REGISTRAR";
    public static final String USUARIOS_ADMINISTRAR = "USUARIOS_ADMINISTRAR";
    public static final String AUDITORIA_CONSULTAR = "AUDITORIA_CONSULTAR";

    private Permisos() {
    }
}
