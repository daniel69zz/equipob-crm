import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export type Nivel = 'BRONCE' | 'PLATA' | 'ORO';

export interface UmbralNivel {
  nivel: Nivel;
  puntosMinimos: number | null;
  puntosMaximos: number | null;
}

/** Configuración de niveles vigente o de una versión del historial (SCRUM-49). */
export interface ConfiguracionNiveles {
  version: number;
  periodoMeses: number;
  umbrales: UmbralNivel[];
  motivo: string | null;
  modificadoPor: string;
  vigenteDesde: string;
}

export interface SolicitudNiveles {
  periodoMeses: number | null;
  niveles: UmbralNivel[];
  motivo: string;
  version: number;
}

export interface ResultadoNiveles {
  configuracion: ConfiguracionNiveles;
  clientesReclasificados: number;
}

@Injectable({ providedIn: 'root' })
export class NivelesService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/fidelizacion/niveles';

  vigente() { return this.http.get<ConfiguracionNiveles>(this.url); }
  guardar(solicitud: SolicitudNiveles) { return this.http.put<ResultadoNiveles>(this.url, solicitud); }
  historial() { return this.http.get<ConfiguracionNiveles[]>(`${this.url}/historial`); }
  recalcular() { return this.http.post<{ clientesReclasificados: number }>(`${this.url}/recalcular`, {}); }
}

export const NIVELES: Nivel[] = ['BRONCE', 'PLATA', 'ORO'];

export const NOMBRES_NIVEL: Record<string, string> = { BRONCE: 'Bronce', PLATA: 'Plata', ORO: 'Oro' };

export function rangoTexto(u: UmbralNivel): string {
  return u.puntosMaximos === null ? `${u.puntosMinimos ?? '…'} pts o más` : `${u.puntosMinimos ?? '…'} – ${u.puntosMaximos} pts`;
}

/**
 * Misma regla que el servicio (docs/fidelizacion/niveles-fidelizacion.md), para avisar antes de enviar:
 * los rangos van en orden bronce, plata y oro, sin superponerse ni dejar huecos; solo el oro queda sin máximo.
 */
export function conflictosUmbrales(umbrales: UmbralNivel[]): Partial<Record<Nivel, string>> {
  const errores: Partial<Record<Nivel, string>> = {};
  for (const u of umbrales) {
    if (u.puntosMinimos === null || u.puntosMinimos < 0) errores[u.nivel] = 'Indique un mínimo de 0 o más.';
    else if (u.nivel !== 'ORO' && u.puntosMaximos === null) errores[u.nivel] = 'Indique el máximo: solo Oro queda sin máximo.';
    else if (u.puntosMaximos !== null && u.puntosMaximos < u.puntosMinimos) errores[u.nivel] = 'El máximo debe ser mayor o igual que el mínimo.';
  }
  if (Object.keys(errores).length) return errores;
  for (let i = 1; i < umbrales.length; i++) {
    const anterior = umbrales[i - 1], actual = umbrales[i];
    const siguiente = (anterior.puntosMaximos ?? 0) + 1;
    if ((actual.puntosMinimos ?? 0) < siguiente) {
      errores[actual.nivel] = `Se superpone con ${NOMBRES_NIVEL[anterior.nivel]} (${rangoTexto(anterior)}). El mínimo debe ser ${siguiente}.`;
    } else if ((actual.puntosMinimos ?? 0) > siguiente) {
      errores[actual.nivel] = `Deja sin nivel a quienes tengan ${siguiente}–${(actual.puntosMinimos ?? 0) - 1} pts. El mínimo debe ser ${siguiente}.`;
    }
  }
  return errores;
}
