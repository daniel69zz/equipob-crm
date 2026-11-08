import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

/** Regla de acumulación de puntos vigente o de una versión del historial (SCRUM-46). */
export interface ReglaAcumulacion {
  version: number;
  bolivianosPorPunto: number;
  montoMinimo: number;
  motivo: string | null;
  modificadoPor: string;
  vigenteDesde: string;
}

export interface CambioReglaAcumulacion extends ReglaAcumulacion {
  anteriorBolivianosPorPunto: number | null;
  anteriorMontoMinimo: number | null;
}

export interface SolicitudReglaAcumulacion {
  bolivianosPorPunto: number | null;
  montoMinimo: number | null;
  motivo: string;
  version: number;
}

@Injectable({ providedIn: 'root' })
export class ReglasAcumulacionService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/fidelizacion/reglas/acumulacion';

  vigente() { return this.http.get<ReglaAcumulacion>(this.url); }
  guardar(solicitud: SolicitudReglaAcumulacion) { return this.http.put<ReglaAcumulacion>(this.url, solicitud); }
  historial() { return this.http.get<CambioReglaAcumulacion[]>(`${this.url}/historial`); }
}

/**
 * Misma lógica que el servicio (docs/fidelizacion/reglas-acumulacion.md): menos del monto mínimo no da
 * puntos; desde el mínimo, un punto por cada tramo completo de la tasa. Solo para la vista previa.
 */
export function puntosPorCompra(valor: number, bolivianosPorPunto: number, montoMinimo: number): number {
  if (!(valor > 0) || !(bolivianosPorPunto > 0) || valor < montoMinimo) return 0;
  // En centavos, para evitar errores de redondeo de los decimales.
  return Math.floor(Math.round(valor * 100) / Math.round(bolivianosPorPunto * 100));
}
