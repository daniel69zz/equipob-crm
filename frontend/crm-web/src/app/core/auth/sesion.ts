/** Sesión devuelta por POST /api/auth/login. */
export interface Sesion {
  token: string;
  tipo: string;
  expiraEn: string;
  usuario: string;
  nombreCompleto: string;
  rol: string;
  permisos: string[];
}

/** Códigos de permiso. Deben coincidir con docs/seguridad/matriz-permisos.md. */
export const Permisos = {
  CLIENTE_CONSULTAR: 'CLIENTE_CONSULTAR',
  CLIENTE_EDITAR: 'CLIENTE_EDITAR',
  INDICADORES_CONSULTAR: 'INDICADORES_CONSULTAR',
  EVENTOS_REPROCESAR: 'EVENTOS_REPROCESAR',
  SEGMENTOS_CONSULTAR: 'SEGMENTOS_CONSULTAR',
  SEGMENTACION_CONFIGURAR: 'SEGMENTACION_CONFIGURAR',
  PUNTOS_CONSULTAR: 'PUNTOS_CONSULTAR',
  FIDELIZACION_CONFIGURAR: 'FIDELIZACION_CONFIGURAR',
  INTERACCIONES_CONSULTAR: 'INTERACCIONES_CONSULTAR',
  INTERACCIONES_REGISTRAR: 'INTERACCIONES_REGISTRAR',
  USUARIOS_ADMINISTRAR: 'USUARIOS_ADMINISTRAR',
  AUDITORIA_CONSULTAR: 'AUDITORIA_CONSULTAR',
} as const;

/** Nombre visible de cada rol. */
export const NombresDeRol: Record<string, string> = {
  ADMINISTRADOR_CRM: 'Administrador de CRM',
  AGENTE_ATENCION: 'Agente de Atención al Cliente',
  GERENTE_COMERCIAL: 'Gerente Comercial',
};
