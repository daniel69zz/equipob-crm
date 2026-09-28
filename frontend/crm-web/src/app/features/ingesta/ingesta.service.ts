import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type EstadoEvento = 'RECIBIDO' | 'PROCESADO' | 'FALLIDO' | 'DESCARTADO';

export interface EventoRecibido {
  id: number;
  idEventoOrigen: string | null;
  tipoEvento: string | null;
  origen: string | null;
  estado: EstadoEvento;
  causa: string | null;
  recibidoEn: string;
  procesadoEn: string | null;
}

export interface Bitacora {
  desde: string;
  hasta: string;
  resumen: Record<EstadoEvento, number>;
  eventos: EventoRecibido[];
  pagina: number;
  tamanio: number;
  total: number;
}

/** Filtros de la bitácora. Las fechas van en formato AAAA-MM-DD; vacías, el servidor usa los últimos 7 días. */
export interface FiltroBitacora {
  desde: string;
  hasta: string;
  estado: string;
  origen: string;
}

@Injectable({ providedIn: 'root' })
export class IngestaService {
  private readonly http = inject(HttpClient);

  buscar(filtro: FiltroBitacora, pagina: number, tamanio: number): Observable<Bitacora> {
    let params = new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
    for (const [clave, valor] of Object.entries(filtro)) {
      if (valor.trim()) {
        params = params.set(clave, valor.trim());
      }
    }
    return this.http.get<Bitacora>('/api/comportamiento/eventos', { params });
  }
}
