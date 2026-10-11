import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { IdentificadorOrigen } from './clientes.service';

/**
 * Valor que consumió un cliente en un periodo (docs/compra/consumo-por-periodo.md, SCRUM-39). Un
 * periodo sin compras vigentes trae total y promedio en 0, `sinDatos` verdadero y un `mensaje`.
 */
export interface ValorPorPeriodo {
  desde: string;
  hasta: string;
  zonaHoraria: string;
  total: number;
  promedio: number;
  compras: number;
  sinDatos: boolean;
  mensaje: string | null;
}

/** Valor total y promedio por compra de un cliente en un rango de fechas (SCRUM-585). */
@Injectable({ providedIn: 'root' })
export class ConsumoPeriodoService {
  private readonly http = inject(HttpClient);

  consultar(
    idCliente: number,
    identificadores: IdentificadorOrigen[],
    desde: string,
    hasta: string,
  ): Observable<ValorPorPeriodo> {
    const params = identificadores.reduce(
      (acumulado, id) => acumulado.append('identificador', id.idCliente),
      new HttpParams().set('desde', desde).set('hasta', hasta),
    );
    return this.http.get<ValorPorPeriodo>(`/api/comportamiento/clientes/${idCliente}/consumo-periodo`, { params });
  }
}
