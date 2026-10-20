import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SiTienePermisoDirective } from '../../core/auth/si-tiene-permiso.directive';
import { Permisos } from '../../core/auth/sesion';
import { PaginacionComponent } from '../../core/ui/paginacion.component';
import {
  ClienteDelSegmento,
  ClientesSegmentoService,
  Direccion,
  FiltroClientesSegmento,
  OrdenClientes,
  PaginaClientesSegmento,
  SegmentoDisponible,
} from './clientes-segmento.service';

const TAMANIO_PAGINA = 20;

const NOMBRES_DE_NIVEL: Record<string, string> = {
  BAJO: 'Bajo',
  MEDIO: 'Medio',
  ALTO: 'Alto',
};

const NOMBRES_DE_INDICADOR: Record<string, string> = {
  FRECUENCIA: 'Frecuencia',
  VALOR: 'Valor',
  CATEGORIA: 'Categoría',
};

function filtroVacio(): FiltroClientesSegmento {
  return { segmento: '', nivelConsumo: '', ultimaCompraDesde: '', ultimaCompraHasta: '' };
}

/**
 * Clientes de un segmento o nivel de consumo para dirigir acciones comerciales (SCRUM-28, RF-20):
 * Gerente Comercial y Administrador de CRM (SEGMENTOS_CONSULTAR). El grupo elegido se conserva al
 * ordenar, filtrar o cambiar de página (docs/segmentacion/clientes-por-segmento.md).
 */
@Component({
  selector: 'app-clientes-segmento',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe, SiTienePermisoDirective, PaginacionComponent],
  template: `
    <h1>Clientes por segmento</h1>
    <p class="subtitulo">Elija un segmento o un nivel de consumo para ver a sus clientes y dirigirles una acción comercial.</p>

    <form class="tarjeta filtros" (ngSubmit)="consultar()">
      <div>
        <label for="segmento">Segmento</label>
        <select id="segmento" name="segmento" [(ngModel)]="filtro.segmento">
          <option value="">Todos los segmentos</option>
          @for (segmento of segmentos(); track segmento.segmento) {
            <option [value]="segmento.segmento">{{ etiquetaSegmento(segmento) }}</option>
          }
        </select>
      </div>
      <div>
        <label for="nivelConsumo">Nivel de consumo</label>
        <select id="nivelConsumo" name="nivelConsumo" [(ngModel)]="filtro.nivelConsumo">
          <option value="">Todos los niveles</option>
          <option value="ALTO">Alto</option>
          <option value="MEDIO">Medio</option>
          <option value="BAJO">Bajo</option>
        </select>
      </div>
      <div>
        <label for="ultimaCompraDesde">Última compra desde</label>
        <input id="ultimaCompraDesde" name="ultimaCompraDesde" type="date" [(ngModel)]="filtro.ultimaCompraDesde" />
      </div>
      <div>
        <label for="ultimaCompraHasta">Última compra hasta</label>
        <input id="ultimaCompraHasta" name="ultimaCompraHasta" type="date" [(ngModel)]="filtro.ultimaCompraHasta" />
      </div>
      <div class="acciones">
        <button type="submit" [disabled]="cargando()">Consultar</button>
        <button type="button" class="secundario" [disabled]="cargando()" (click)="limpiar()">Limpiar</button>
      </div>
    </form>

    @if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    }

    @if (datos(); as d) {
      @if (!d.nombresDisponibles) {
        <p class="aviso" role="status">No se pudieron cargar los nombres de los clientes; se muestran sus números de cliente.</p>
      }
      <div class="tarjeta">
        <p class="grupo">
          <strong>{{ descripcionGrupo() }}</strong>
          <span class="etiqueta">{{ d.total }} {{ d.total === 1 ? 'cliente' : 'clientes' }}</span>
        </p>
        <table>
          <thead>
            <tr>
              <th>Cliente</th>
              <th [attr.aria-sort]="ariaSort('segmento')">
                <button type="button" class="orden" (click)="ordenarPor('segmento')">Segmento {{ flecha('segmento') }}</button>
              </th>
              <th [attr.aria-sort]="ariaSort('nivelConsumo')">
                <button type="button" class="orden" (click)="ordenarPor('nivelConsumo')">Nivel de consumo {{ flecha('nivelConsumo') }}</button>
              </th>
              <th class="numero" [attr.aria-sort]="ariaSort('valor')">
                <button type="button" class="orden" (click)="ordenarPor('valor')">Valor acumulado {{ flecha('valor') }}</button>
              </th>
              <th [attr.aria-sort]="ariaSort('ultimaCompra')">
                <button type="button" class="orden" (click)="ordenarPor('ultimaCompra')">Última compra {{ flecha('ultimaCompra') }}</button>
              </th>
              <th>Ver</th>
            </tr>
          </thead>
          <tbody>
            @for (cliente of d.content; track cliente.clienteId) {
              <tr>
                <td>
                  <span class="cliente">
                    <strong>{{ nombre(cliente) }}</strong>
                    <small>Cliente {{ cliente.clienteId }}</small>
                  </span>
                </td>
                <td>
                  @if (cliente.segmento) {
                    {{ cliente.segmento }}
                    @if (cliente.indicador) {
                      <small class="indicador">{{ nombreIndicador(cliente.indicador) }}</small>
                    }
                  } @else {
                    <span class="sin-dato">Sin segmento</span>
                  }
                </td>
                <td>
                  @if (cliente.nivelConsumo) {
                    <span class="etiqueta" [class]="'nivel-' + cliente.nivelConsumo">{{ nombreNivel(cliente.nivelConsumo) }}</span>
                  } @else {
                    <span class="sin-dato">—</span>
                  }
                </td>
                <td class="numero">
                  @if (cliente.valorAcumulado !== null) {
                    {{ cliente.valorAcumulado | number: '1.2-2' }}
                  } @else {
                    <span class="sin-dato">—</span>
                  }
                </td>
                <td class="fecha">
                  @if (cliente.ultimaCompra) {
                    {{ cliente.ultimaCompra | date: 'dd/MM/yyyy' }}
                  } @else {
                    <span class="sin-dato">—</span>
                  }
                </td>
                <td class="vistas">
                  <a *appSiTienePermiso="permisos.FICHA_INTEGRAL_CONSULTAR" [routerLink]="['/clientes', cliente.clienteId, 'ficha-integral']">Ficha</a>
                  <a *appSiTienePermiso="permisos.INDICADORES_CONSULTAR" [routerLink]="['/clientes', cliente.clienteId, 'compras']">Compras</a>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="vacio">{{ mensajeVacio() }}</td></tr>
            }
          </tbody>
        </table>

        <app-paginacion [pagina]="d.pagina" [tamanio]="d.tamanio" [total]="d.total" [cargando]="cargando()" (cambiar)="buscar($event)" />
      </div>
    } @else if (!error()) {
      <p class="tarjeta indicacion">Elija un segmento o un nivel de consumo y pulse «Consultar».</p>
    }
  `,
  styles: `
    .filtros {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 1rem; align-items: end; margin-bottom: 1rem;
    }
    .acciones { display: flex; gap: 0.5rem; }
    .grupo { display: flex; align-items: center; gap: 0.6rem; margin: 0 0 1rem; }
    .orden {
      padding: 0; border: none; background: none; color: inherit; font: inherit;
      text-transform: inherit; letter-spacing: inherit;
    }
    .orden:hover:not(:disabled) { background: none; color: var(--color-primario); }
    .numero { text-align: right; }
    .cliente { display: flex; flex-direction: column; }
    .cliente strong { font-weight: 600; }
    .cliente small, .indicador { color: var(--color-texto-suave); font-size: 0.78rem; }
    .indicador { display: block; }
    .sin-dato { color: var(--color-texto-suave); }
    .nivel-ALTO { color: var(--color-exito); background: var(--color-exito-claro); }
    .nivel-MEDIO { color: var(--color-aviso); background: var(--color-aviso-claro); }
    .vistas { display: flex; gap: 0.9rem; flex-wrap: wrap; }
    .vacio { color: var(--color-texto-suave); text-align: center; padding: 1.5rem; }
    .indicacion { color: var(--color-texto-suave); }
    .aviso {
      padding: 0.7rem 0.9rem; border-radius: var(--radio-chico); border: 1px solid #f3d9a4;
      color: var(--color-aviso); background: var(--color-aviso-claro); font-size: 0.9rem;
    }
  `,
})
export class ClientesSegmentoComponent implements OnInit {
  private readonly servicio = inject(ClientesSegmentoService);
  readonly permisos = Permisos;

  /** Lo que el usuario está editando; se aplica al pulsar «Consultar». */
  filtro = filtroVacio();

  readonly segmentos = signal<SegmentoDisponible[]>([]);
  /** Grupo y filtros de la última consulta: ordenar o paginar no los pierde (criterio de aceptación 2). */
  readonly consultado = signal<FiltroClientesSegmento | null>(null);
  readonly orden = signal<OrdenClientes>('valor');
  readonly direccion = signal<Direccion>('desc');
  readonly datos = signal<PaginaClientesSegmento | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  readonly descripcionGrupo = computed(() => {
    const filtro = this.consultado();
    if (!filtro) {
      return '';
    }
    const partes: string[] = [];
    if (filtro.segmento) {
      partes.push(`Segmento «${filtro.segmento}»`);
    }
    if (filtro.nivelConsumo) {
      partes.push(`nivel de consumo ${this.nombreNivel(filtro.nivelConsumo).toLowerCase()}`);
    }
    const texto = partes.join(' con ');
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  });

  readonly mensajeVacio = computed(() => {
    const filtro = this.consultado();
    if (filtro?.ultimaCompraDesde || filtro?.ultimaCompraHasta) {
      return 'Ningún cliente del grupo cumple los filtros de fecha elegidos.';
    }
    if (filtro?.segmento && !filtro.nivelConsumo) {
      return `El segmento «${filtro.segmento}» no tiene clientes asignados.`;
    }
    return 'No hay clientes en este grupo.';
  });

  ngOnInit(): void {
    this.servicio.segmentos().subscribe({
      next: (segmentos) => this.segmentos.set(segmentos),
      error: (e: HttpErrorResponse) => this.error.set(this.mensajeDeError(e, 'No se pudieron cargar los segmentos.')),
    });
  }

  consultar(): void {
    if (!this.filtro.segmento && !this.filtro.nivelConsumo) {
      this.error.set('Elija un segmento o un nivel de consumo.');
      return;
    }
    this.consultado.set({ ...this.filtro });
    this.buscar(0);
  }

  limpiar(): void {
    this.filtro = filtroVacio();
    this.consultado.set(null);
    this.datos.set(null);
    this.error.set(null);
    this.orden.set('valor');
    this.direccion.set('desc');
  }

  /** La misma columna invierte el sentido; otra columna empieza de mayor a menor. Vuelve a la primera página. */
  ordenarPor(columna: OrdenClientes): void {
    if (!this.consultado()) {
      return;
    }
    if (this.orden() === columna) {
      this.direccion.set(this.direccion() === 'desc' ? 'asc' : 'desc');
    } else {
      this.orden.set(columna);
      this.direccion.set('desc');
    }
    this.buscar(0);
  }

  buscar(pagina: number): void {
    const filtro = this.consultado();
    if (!filtro) {
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    this.servicio.buscar(filtro, this.orden(), this.direccion(), pagina, TAMANIO_PAGINA).subscribe({
      next: (resultado) => {
        this.datos.set(resultado);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.datos.set(null);
        this.error.set(this.mensajeDeError(e, 'No se pudo consultar los clientes del segmento.'));
      },
    });
  }

  flecha(columna: OrdenClientes): string {
    if (this.orden() !== columna) {
      return '';
    }
    return this.direccion() === 'asc' ? '▲' : '▼';
  }

  ariaSort(columna: OrdenClientes): string | null {
    if (this.orden() !== columna) {
      return null;
    }
    return this.direccion() === 'asc' ? 'ascending' : 'descending';
  }

  etiquetaSegmento(segmento: SegmentoDisponible): string {
    const clientes = segmento.clientes === 1 ? '1 cliente' : `${segmento.clientes} clientes`;
    const inactivo = segmento.criterioActivo === false ? ' · criterio inactivo' : '';
    return `${segmento.segmento} (${clientes})${inactivo}`;
  }

  nombre(cliente: ClienteDelSegmento): string {
    const completo = [cliente.nombres, cliente.apellidos].filter((parte): parte is string => !!parte?.trim()).join(' ');
    return completo || 'Nombre no disponible';
  }

  nombreNivel(nivel: string): string {
    return NOMBRES_DE_NIVEL[nivel] ?? nivel;
  }

  nombreIndicador(indicador: string): string {
    return NOMBRES_DE_INDICADOR[indicador] ?? indicador;
  }

  private mensajeDeError(e: HttpErrorResponse, porDefecto: string): string {
    if (e.status === 403) {
      return 'Su rol no tiene permiso para consultar clientes por segmento.';
    }
    return e.error?.mensaje ?? porDefecto;
  }
}
