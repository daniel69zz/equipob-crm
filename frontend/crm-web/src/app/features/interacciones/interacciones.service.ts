import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

/** Tipos de interacción (SCRUM-433, docs/interacciones/registro-interacciones.md). */
export type TipoInteraccion = 'CONSULTA' | 'RECLAMO' | 'NOTA';
/** Estados de seguimiento (SCRUM-451, docs/interacciones/seguimiento-interacciones.md). */
export type EstadoInteraccion = 'ABIERTA' | 'EN_PROCESO' | 'CERRADA';
export type CanalInteraccion = 'TELEFONO' | 'CORREO' | 'PRESENCIAL' | 'CHAT' | 'WHATSAPP' | 'OTRO';

export const TIPOS_INTERACCION: { valor: TipoInteraccion; nombre: string }[] = [
  { valor: 'CONSULTA', nombre: 'Consulta' },
  { valor: 'RECLAMO', nombre: 'Reclamo' },
  { valor: 'NOTA', nombre: 'Nota' },
];

export const ESTADOS_INTERACCION: { valor: EstadoInteraccion; nombre: string }[] = [
  { valor: 'ABIERTA', nombre: 'Abierta' },
  { valor: 'EN_PROCESO', nombre: 'En proceso' },
  { valor: 'CERRADA', nombre: 'Cerrada' },
];

/** Largo del comentario al reabrir una interacción cerrada (SCRUM-453). */
export const LARGO_MINIMO_COMENTARIO_REAPERTURA = 10;
export const LARGO_MAXIMO_COMENTARIO = 500;

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
  estado: EstadoInteraccion;
  /** Quién cambió el estado por última vez y cuándo; nulos mientras siga como se registró. */
  estadoActualizadoPor: string | null;
  estadoActualizadoEn: string | null;
  registradoPor: string;
  registradoRol: string | null;
  registradoEn: string;
  modificadoPor: string | null;
  modificadoEn: string | null;
  /** Sube con cada cambio; la edición la envía para detectar cambios simultáneos (409). */
  version: number;
  /** Compra del historial del cliente vinculada (SCRUM-54), o null. */
  compra: CompraVinculada | null;
}

/** Compra vinculada a una interacción, tal como estaba al vincularla (docs/interacciones/vinculacion-compras.md). */
export interface CompraVinculada {
  referencia: string;
  fecha: string | null;
  montoTotal: number | null;
  estado: string | null;
  vinculadaPor: string;
  vinculadaEn: string;
}

/** GET /api/interacciones/clientes/{clienteId}/compras (SCRUM-446). */
export interface ComprasElegibles {
  compras: CompraHistorial[];
  disponible: boolean;
  avisos: string[];
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

/** Cambio de seguimiento (SCRUM-452): lo que no se envía no cambia. */
export interface CambioSeguimiento {
  estado?: EstadoInteraccion;
  tipo?: TipoInteraccion;
  comentario?: string | null;
}

/** Filtros del historial (SCRUM-431 y SCRUM-55). Las fechas son días (aaaa-mm-dd) en la hora de Bolivia. */
export interface FiltroInteracciones {
  tipo?: TipoInteraccion | '';
  estado?: EstadoInteraccion | '';
  texto?: string;
  desde?: string;
  hasta?: string;
}

/** Compra del cliente en la línea de tiempo (servicio-comportamiento, SCRUM-441). */
export interface CompraHistorial {
  fecha: string;
  referencia: string;
  montoTotal: number;
  estado: 'CONFIRMADA' | 'DEVOLUCION_PARCIAL' | 'ANULADA' | string;
  items: { categoria: string; cantidad: number; monto: number }[];
}

/** Un evento del historial del cliente: una interacción o una compra (SCRUM-53). */
export interface EventoHistorial {
  tipo: 'INTERACCION' | 'COMPRA';
  fecha: string;
  interaccion?: Interaccion;
  compra?: CompraHistorial;
}

/** GET /api/interacciones/clientes/{clienteId}/historial (docs/interacciones/historial-cliente.md). */
export interface HistorialCliente {
  clienteId: number;
  /** Del más reciente al más antiguo. */
  eventos: EventoHistorial[];
  totalInteracciones: number;
  totalCompras: number;
  sinInteracciones: boolean;
  comprasDisponibles: boolean;
  hayMas: boolean;
  avisos: string[];
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

  /** Interacciones y compras del cliente en una línea de tiempo (SCRUM-53). */
  historialCliente(clienteId: number, limite: number) {
    return this.http.get<HistorialCliente>(`/api/interacciones/clientes/${clienteId}/historial`, {
      params: new HttpParams().set('limite', limite),
    });
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

  seguimiento(clienteId: number, id: number, cambio: CambioSeguimiento, version: number) {
    return this.http.patch<Interaccion>(`${this.ruta(clienteId)}/${id}`, { ...cambio, version });
  }

  /** Compras del cliente para vincular a una interacción, filtradas por referencia (SCRUM-446). */
  comprasElegibles(clienteId: number, texto: string, limite: number) {
    let params = new HttpParams().set('limite', limite);
    if (texto.trim()) params = params.set('texto', texto.trim());
    return this.http.get<ComprasElegibles>(`/api/interacciones/clientes/${clienteId}/compras`, { params });
  }

  /** Vincula la compra o reemplaza la vinculada (SCRUM-54). */
  vincularCompra(clienteId: number, id: number, referencia: string, version: number) {
    return this.http.put<Interaccion>(`${this.ruta(clienteId)}/${id}/compra`, { referencia, version });
  }

  desvincularCompra(clienteId: number, id: number, version: number) {
    return this.http.delete<Interaccion>(`${this.ruta(clienteId)}/${id}/compra`, {
      params: new HttpParams().set('version', version),
    });
  }
}

export function nombreTipo(tipo: string): string {
  return TIPOS_INTERACCION.find(t => t.valor === tipo)?.nombre ?? tipo;
}

export function nombreEstado(estado: string): string {
  return ESTADOS_INTERACCION.find(e => e.valor === estado)?.nombre ?? estado;
}

export function nombreCanal(canal: string | null): string {
  return canal ? CANALES_INTERACCION.find(c => c.valor === canal)?.nombre ?? canal : '';
}
