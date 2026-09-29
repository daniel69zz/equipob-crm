import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type AlcanceConsentimiento =
  | 'GESTION_CLIENTE'
  | 'ANALISIS_COMPORTAMIENTO'
  | 'SEGMENTACION'
  | 'FIDELIZACION'
  | 'COMUNICACIONES_COMERCIALES';

export type CanalConsentimiento = 'MARKETPLACE' | 'VENTAS' | 'PRESENCIAL' | 'TELEFONICO' | 'CORREO';

export type EstadoConsentimiento = 'OTORGADO' | 'REVOCADO';

export type OperacionConsentimiento = 'OTORGAMIENTO' | 'ACTUALIZACION' | 'REVOCACION';

export interface Consentimiento {
  idCliente: number;
  estado: EstadoConsentimiento;
  vigente: boolean;
  canal: CanalConsentimiento;
  alcances: AlcanceConsentimiento[];
  fechaOtorgamiento: string;
  vigenciaDesde: string;
  vigenciaHasta: string | null;
  fechaRevocacion: string | null;
  motivoRevocacion: string | null;
  actualizadoEn: string;
  actualizadoPor: string;
}

export interface RegistroConsentimiento {
  id: number;
  fecha: string;
  operacion: OperacionConsentimiento;
  estado: EstadoConsentimiento;
  canal: CanalConsentimiento;
  alcances: AlcanceConsentimiento[];
  fechaOtorgamiento: string;
  vigenciaDesde: string;
  vigenciaHasta: string | null;
  fechaRevocacion: string | null;
  motivoRevocacion: string | null;
  responsable: string;
}

/** Los campos vacíos toman su valor por defecto en el servidor (docs/perfil/consentimiento-datos.md). */
export interface SolicitudConsentimiento {
  canal: CanalConsentimiento;
  alcances: AlcanceConsentimiento[];
  vigenciaDesde: string | null;
  vigenciaHasta: string | null;
}

@Injectable({ providedIn: 'root' })
export class ConsentimientoService {
  private readonly http = inject(HttpClient);

  /** Responde 404 si el cliente nunca registró un consentimiento. */
  consultar(idCliente: number): Observable<Consentimiento> {
    return this.http.get<Consentimiento>(ruta(idCliente));
  }

  registrar(idCliente: number, solicitud: SolicitudConsentimiento): Observable<Consentimiento> {
    return this.http.put<Consentimiento>(ruta(idCliente), solicitud);
  }

  revocar(idCliente: number, motivo: string): Observable<Consentimiento> {
    return this.http.post<Consentimiento>(`${ruta(idCliente)}/revocacion`, { motivo: motivo.trim() || null });
  }

  historial(idCliente: number): Observable<RegistroConsentimiento[]> {
    return this.http.get<RegistroConsentimiento[]>(`${ruta(idCliente)}/historial`);
  }
}

function ruta(idCliente: number): string {
  return `/api/perfil/clientes/${idCliente}/consentimiento`;
}
