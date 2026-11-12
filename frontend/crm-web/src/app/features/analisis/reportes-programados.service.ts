import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';

export type Frecuencia = 'DIARIA' | 'SEMANAL' | 'MENSUAL';
export type EstadoProgramacion = 'ACTIVA' | 'PAUSADA' | 'CANCELADA';
export type ResultadoEjecucion = 'COMPLETADA' | 'FALLIDA';

export interface ConfiguracionReporte {
  nombre: string;
  frecuencia: Frecuencia;
  formato: 'CSV' | 'PDF';
  dias: number;
  segmento: string | null;
  inicio: string;
  fin: string | null;
}
export interface ProgramacionReporte extends ConfiguracionReporte {
  id: string; proxima: string; estado: EstadoProgramacion;
}
export interface EjecucionReporte {
  id: string; programacion: string; reporte: string; prevista: string; ejecutada: string;
  estado: ResultadoEjecucion; causa: string | null; nombre: string | null; leida: boolean;
}

/** Ejecuciones por página del historial; coincide con el servidor. */
export const TAMANO_PAGINA = 50;
/** Bolivia (America/La_Paz) no tiene horario de verano: siempre UTC-4. */
export const DESFASE_BOLIVIA = '-04:00';
const DESFASE_MS = 4 * 3600000;

/** Un valor de input datetime-local (hora de Bolivia) como fecha ISO con su desfase. */
export function aFechaBolivia(local: string): string {
  return `${local}:00${DESFASE_BOLIVIA}`;
}

/** Una fecha ISO del servidor como valor de input datetime-local en hora de Bolivia. */
export function aEntradaBolivia(fecha: string): string {
  return new Date(Date.parse(fecha) - DESFASE_MS).toISOString().slice(0, 16);
}

/** Reportes programados del usuario (SCRUM-61) y el contador de avisos sin leer que muestra la cabecera. */
@Injectable({ providedIn: 'root' })
export class ReportesProgramadosService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/comportamiento/reportes/programaciones';
  readonly noLeidas = signal(0);

  listar() { return this.http.get<ProgramacionReporte[]>(this.url); }
  guardar(id: string | null, s: ConfiguracionReporte) {
    return id ? this.http.put(`${this.url}/${id}`, s) : this.http.post(this.url, s);
  }
  estado(id: string, estado: EstadoProgramacion) { return this.http.put(`${this.url}/${id}/estado`, { estado }); }
  historial(pagina: number) { return this.http.get<EjecucionReporte[]>(`${this.url}/ejecuciones`, { params: { pagina } }); }
  leer(id: string) { return this.http.put(`${this.url}/ejecuciones/${id}/leida`, {}); }
  archivo(id: string) { return this.http.get(`${this.url}/ejecuciones/${id}/archivo`, { observe: 'response', responseType: 'blob' }); }

  /** Vuelve a contar los avisos sin leer; si falla, conserva el último valor conocido. */
  actualizarAvisos() {
    this.http.get<{ noLeidas: number }>(`${this.url}/avisos`).pipe(catchError(() => of(null)))
      .subscribe(r => { if (r) this.noLeidas.set(r.noLeidas); });
  }
}
