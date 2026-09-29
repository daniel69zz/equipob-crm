import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Compra } from './compras.service';
import { PuntosFidelizacion } from './puntos-fidelizacion.component';

export interface DireccionFicha {
  idDireccion: string;
  tipo: 'ENTREGA' | 'FACTURACION' | 'OTRA';
  calle: string;
  numero: string | null;
  zona: string | null;
  ciudad: string;
  referencia: string | null;
  principal: boolean;
}

export interface DatosPersonalesFicha {
  nombres: string | null;
  apellidos: string | null;
  tipoDocumento: string | null;
  numeroDocumento: string | null;
  email: string | null;
  telefono: string | null;
  direcciones: DireccionFicha[];
}

/** Segmento del cliente (GET /api/perfil/clientes/{id}/ficha-integral, bloque `segmento`). */
export interface SegmentoFicha {
  segmento: string | null;
  asignadoEn: string | null;
  sinDatos: boolean;
}

export interface FichaIntegral {
  datosPersonales: DatosPersonalesFicha;
  historialCompras: Compra[];
  segmento: SegmentoFicha;
  puntos: PuntosFidelizacion;
}

/**
 * Ficha integral del cliente (SCRUM-10, SCRUM-150): datos personales, contacto, direcciones,
 * historial de compras, segmento y puntos en una sola consulta
 * (GET /api/perfil/clientes/{id}/ficha-integral, restringido a Administrador de CRM y Agente de
 * Atención al Cliente por SCRUM-153).
 */
@Injectable({ providedIn: 'root' })
export class FichaIntegralService {
  private readonly http = inject(HttpClient);

  obtener(idCliente: number): Observable<FichaIntegral> {
    return this.http.get<FichaIntegral>(`/api/perfil/clientes/${idCliente}/ficha-integral`);
  }
}
