import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

/** Tipos de interacción (SCRUM-433, docs/interacciones/registro-interacciones.md). */
export type TipoInteraccion = 'CONSULTA' | 'RECLAMO' | 'NOTA';
export type CanalInteraccion = 'TELEFONO' | 'CORREO' | 'PRESENCIAL' | 'CHAT' | 'WHATSAPP' | 'OTRO';

export const TIPOS_INTERACCION: { valor: TipoInteraccion; nombre: string }[] = [
  { valor: 'CONSULTA', nombre: 'Consulta' },
  { valor: 'RECLAMO', nombre: 'Reclamo' },
  { valor: 'NOTA', nombre: 'Nota' },
];

export const CANALES_INTERACCION: { valor: CanalInteraccion; nombre: string }[] = [
  { valor: 'TELEFONO', nombre: 'Teléfono' },
  { valor: 'CORREO', nombre: 'Correo' },
  { valor: 'PRESENCIAL', nombre: 'Presencial' },
  { valor: 'CHAT', nombre: 'Chat' },
  { valor: 'WHATSAPP', nombre: 'WhatsApp' },
  { valor: 'OTRO', nombre: 'Otro' },
];

export const LARGO_MAXIMO_DESCRIPCION = 4000;
export const LARGO_MAXIMO_ASUNTO = 120;
export const LARGO_MAXIMO_REFERENCIA = 64;

/** Una consulta, un reclamo o una nota del historial del cliente. */
export interface Interaccion {
  id: number;
  clienteId: number;
  tipo: TipoInteraccion;
  canal: CanalInteraccion | null;
  asunto: string | null;
  descripcion: string;
  referenciaCompra: string | null;
  registradoPor: string;
  registradoRol: string | null;
  registradoEn: string;
  modificadoPor: string | null;
  modificadoEn: string | null;
  /** Sube con cada cambio; la edición la envía para detectar cambios simultáneos (409). */
  version: number;
}

export interface PaginaInteracciones {
  interacciones: Interaccion[];
  total: number;
  pagina: number;
  tamanio: number;
}

/** Campos que se registran o se editan. El responsable y la fecha los pone el servidor. */
export interface DatosInteraccion {
  tipo: TipoInteraccion;
  canal: CanalInteraccion | null;
  asunto: string | null;
  descripcion: string;
  referenciaCompra: string | null;
}

/** Filtros del historial (SCRUM-431). Las fechas son días (aaaa-mm-dd) en la hora de Bolivia. */
export interface FiltroInteracciones {
  tipo?: TipoInteraccion | '';
  texto?: string;
  desde?: string;
  hasta?: string;
}

/**
 * Rutas bajo el cliente (docs/seguridad/convencion-rutas-clientes.md): el Gateway audita cada consulta y cada cambio
 * con el cliente afectado.
 */
@Injectable({ providedIn: 'root' })
export class InteraccionesService {
  private readonly http = inject(HttpClient);

  private ruta(clienteId: number): string {
    return `/api/interacciones/clientes/${clienteId}/interacciones`;
  }

  historial(clienteId: number, filtro: FiltroInteracciones, pagina: number, tamanio: number) {
    let params = new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
    for (const [clave, valor] of Object.entries(filtro)) {
      const texto = typeof valor === 'string' ? valor.trim() : '';
      if (texto) params = params.set(clave, texto);
    }
    return this.http.get<PaginaInteracciones>(this.ruta(clienteId), { params });
  }

  detalle(clienteId: number, id: number) {
    return this.http.get<Interaccion>(`${this.ruta(clienteId)}/${id}`);
  }

  registrar(clienteId: number, datos: DatosInteraccion) {
    return this.http.post<Interaccion>(this.ruta(clienteId), datos);
  }

  modificar(clienteId: number, id: number, datos: DatosInteraccion, version: number) {
    return this.http.put<Interaccion>(`${this.ruta(clienteId)}/${id}`, { ...datos, version });
  }
}

export function nombreTipo(tipo: string): string {
  return TIPOS_INTERACCION.find(t => t.valor === tipo)?.nombre ?? tipo;
}

export function nombreCanal(canal: string | null): string {
  return canal ? CANALES_INTERACCION.find(c => c.valor === canal)?.nombre ?? canal : '';
}
