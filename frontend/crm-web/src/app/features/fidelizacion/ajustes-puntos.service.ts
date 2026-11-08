import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

/** Un ajuste manual del saldo de puntos (SCRUM-51, docs/fidelizacion/ajuste-manual-puntos.md). */
export interface AjustePuntos {
  id: number;
  idTransaccion: string;
  clienteId: number;
  /** Variación con signo: positiva suma y negativa resta. */
  puntos: number;
  saldoAnterior: number;
  saldoResultante: number;
  motivo: string;
  referencia: string | null;
  /** Responsable del ajuste. */
  usuario: string;
  rol: string | null;
  registradoEn: string;
  /** Nivel del cliente después del ajuste (solo en la respuesta del ajuste). */
  nivel: string | null;
}

/** Ajuste que confirma el Administrador de CRM (SCRUM-429). */
export interface SolicitudAjuste {
  /** UUID nuevo para cada ajuste: un doble clic o un reintento no lo aplica dos veces. */
  idSolicitud: string;
  puntos: number;
  motivo: string;
  referencia: string | null;
  /** Saldo disponible que se revisó al confirmar; si cambió, el servicio responde 409. */
  saldoActual: number;
}

export const PUNTOS_MAXIMOS_AJUSTE = 100000;
export const LARGO_MINIMO_MOTIVO = 10;

@Injectable({ providedIn: 'root' })
export class AjustesPuntosService {
  private readonly http = inject(HttpClient);

  ajustar(clienteId: number, solicitud: SolicitudAjuste) {
    return this.http.post<AjustePuntos>(`/api/fidelizacion/clientes/${clienteId}/ajustes`, solicitud);
  }

  historial(clienteId: number) {
    return this.http.get<AjustePuntos[]>(`/api/fidelizacion/clientes/${clienteId}/ajustes`);
  }
}

/** UUID v4 para la solicitud; usa crypto.randomUUID si el navegador lo ofrece (solo en contextos seguros). */
export function nuevoIdSolicitud(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
