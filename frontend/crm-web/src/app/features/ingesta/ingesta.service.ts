import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type EstadoEvento = 'RECIBIDO' | 'PROCESADO' | 'FALLIDO' | 'DESCARTADO' | 'EN_ESPERA';

export interface EventoRecibido {
  id: number;
  idEventoOrigen: string | null;
  tipoEvento: string | null;
  idTransaccion: string | null;
  estado: EstadoEvento;
  causa: string | null;
  recibidoEn: string;
  procesadoEn: string | null;
  reprocesado: boolean;
  enRevision: boolean;
}

export interface Bitacora {
  desde: string | null;
  hasta: string | null;
  resumen: Record<EstadoEvento, number>;
  eventos: EventoRecibido[];
  pagina: number;
  tamanio: number;
  total: number;
}

export type ResultadoReproceso = 'EN_PROCESO' | 'PROCESADO' | 'FALLIDO' | 'DESCARTADO';

export interface IntentoReproceso {
  id: number;
  idEvento: number;
  numero: number;
  intentadoEn: string;
  resultado: ResultadoReproceso;
  causa: string | null;
  usuario: string | null;
}

/** Fechas opcionales AAAA-MM-DD. Vacías, se consulta todo el historial con paginación. */
export interface FiltroBitacora {
  desde: string;
  hasta: string;
  estado: string;
  transaccion: string;
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

  enviarARevision(idEvento: number): Observable<IntentoReproceso> {
    return this.http.post<IntentoReproceso>(`/api/comportamiento/eventos/${idEvento}/revision`, {});
  }

  consultarIntentos(idEvento: number): Observable<IntentoReproceso[]> {
    return this.http.get<IntentoReproceso[]>(`/api/comportamiento/eventos/${idEvento}/intentos`);
  }
}
