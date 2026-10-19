import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface RetencionPeriodo {
  periodo: number;
  mes: string;
  clientes: number;
  porcentaje: number;
}

export interface CohorteRetencion {
  cohorte: string;
  clientes: number;
  periodos: RetencionPeriodo[];
}

export interface MatrizRetencion {
  granularidad: 'MENSUAL';
  zonaHoraria: string;
  desde: string;
  hasta: string;
  mesActual: string;
  cohortes: CohorteRetencion[];
}

/**
 * Retención por cohortes de primera compra (SCRUM-36, GET /api/comportamiento/cohortes):
 * cohortes mensuales y el porcentaje de clientes que vuelve a comprar en cada mes posterior
 * (docs/compra/retencion-cohortes.md).
 */
@Injectable({ providedIn: 'root' })
export class RetencionCohortesService {
  private readonly http = inject(HttpClient);

  matriz(desde: string | null, hasta: string | null): Observable<MatrizRetencion> {
    let params = new HttpParams();
    if (desde) {
      params = params.set('desde', desde);
    }
    if (hasta) {
      params = params.set('hasta', hasta);
    }
    return this.http.get<MatrizRetencion>('/api/comportamiento/cohortes', { params });
  }
}
