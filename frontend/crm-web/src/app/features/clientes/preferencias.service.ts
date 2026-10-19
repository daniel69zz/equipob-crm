import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface CategoriaPreferida {
  posicion: number;
  categoria: string;
  compras: number;
  unidades: number;
  monto: number;
}

/** Contrato de lectura de SCRUM-41/42: la consulta no reconstruye el perfil. */
export interface PerfilPreferencias {
  clienteId: number;
  construido: boolean;
  sinPreferencias: boolean;
  construidoEn: string | null;
  version: number;
  criterio: string;
  categorias: CategoriaPreferida[];
  origen: string | null;
  idEventoOrigen: string | null;
}

@Injectable({ providedIn: 'root' })
export class PreferenciasService {
  private readonly http = inject(HttpClient);

  consultar(clienteId: number) {
    return this.http.get<PerfilPreferencias>(`/api/comportamiento/clientes/${clienteId}/preferencias`);
  }
}
