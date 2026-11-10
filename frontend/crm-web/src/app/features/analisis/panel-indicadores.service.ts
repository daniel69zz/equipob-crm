import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface GrupoSegmento {
  /** null: clientes sin segmento asignado. */
  valor: string | null;
  clientes: number;
  porcentaje: number;
}

export interface PanelIndicadores {
  generadoEn: string;
  clientes: number;
  sinDatos: boolean;
  activos: { disponible: boolean; activos: number | null; porcentaje: number | null; umbralDias: number | null };
  segmentos: GrupoSegmento[];
  puntos: { disponible: boolean; puntos: number | null; clientesConPuntos: number | null };
}

/** Panel con los principales indicadores de clientes (SCRUM-59, GET /api/segmentacion/reportes/panel). */
@Injectable({ providedIn: 'root' })
export class PanelIndicadoresService {
  private readonly http = inject(HttpClient);

  consultar(): Observable<PanelIndicadores> {
    return this.http.get<PanelIndicadores>('/api/segmentacion/reportes/panel');
  }
}
