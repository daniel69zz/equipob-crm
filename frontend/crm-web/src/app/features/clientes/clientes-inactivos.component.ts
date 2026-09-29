import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ClienteInactivo, ClientesInactivosService, PaginaClientesInactivos } from './clientes-inactivos.service';

const TAMANIO_PAGINA = 20;

const NOMBRES_DE_ORIGEN: Record<ClienteInactivo['origen'], string> = {
  VENTAS: 'Ventas',
  MARKETPLACE: 'Marketplace',
};

/**
 * Clientes sin compras vigentes desde hace más del umbral definido (SCRUM-31, RF-23), del que
 * lleva más tiempo sin comprar al que lleva menos.
 */
@Component({
  selector: 'app-clientes-inactivos',
  standalone: true,
  imports: [DatePipe],
  template: `
    <h1>Clientes inactivos</h1>
    @if (datos(); as d) {
      <p class="subtitulo">Sin compras vigentes desde hace más de {{ d.umbralDias }} días.</p>
    }

    @if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    }

    @if (datos(); as d) {
      <div class="tarjeta">
        <table>
          <thead>
            <tr>
              <th>Canal</th>
              <th>Cliente</th>
              <th>Última compra</th>
              <th>Días sin comprar</th>
            </tr>
          </thead>
          <tbody>
            @for (cliente of d.content; track cliente.origen + cliente.idClienteOrigen) {
              <tr>
                <td><span class="etiqueta" [class]="'origen-' + cliente.origen">{{ nombreOrigen(cliente.origen) }}</span></td>
                <td class="id">{{ cliente.idClienteOrigen }}</td>
                <td class="fecha">{{ cliente.ultimaCompra | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td>{{ cliente.diasTranscurridos }}</td>
              </tr>
            } @empty {
              <tr><td colspan="4">No hay clientes inactivos por ahora.</td></tr>
            }
          </tbody>
        </table>

        @if (d.total > 0) {
          <div class="paginacion">
            <span>{{ desdeRegistro() }}–{{ hastaRegistro() }} de {{ d.total }}</span>
            <button type="button" class="secundario" [disabled]="d.pagina === 0 || cargando()" (click)="buscar(d.pagina - 1)">
              Anterior
            </button>
            <button type="button" class="secundario" [disabled]="esUltimaPagina() || cargando()" (click)="buscar(d.pagina + 1)">
              Siguiente
            </button>
          </div>
        }
      </div>
    }
  `,
  styles: `
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    .id { font-family: monospace; }
    .fecha { white-space: nowrap; }
    .etiqueta { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; background: #eef2f7; }
    .origen-VENTAS { color: #1f3864; background: #dae8fc; }
    .origen-MARKETPLACE { color: #6b3fa0; background: #efe6fb; }
    .paginacion { display: flex; align-items: center; justify-content: flex-end; gap: 0.75rem; margin-top: 1rem; }
  `,
})
export class ClientesInactivosComponent implements OnInit {
  private readonly servicio = inject(ClientesInactivosService);

  readonly datos = signal<PaginaClientesInactivos | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  readonly desdeRegistro = computed(() => (this.datos()?.pagina ?? 0) * TAMANIO_PAGINA + 1);
  readonly hastaRegistro = computed(
    () => (this.datos()?.pagina ?? 0) * TAMANIO_PAGINA + (this.datos()?.content.length ?? 0),
  );
  readonly esUltimaPagina = computed(() => this.hastaRegistro() >= (this.datos()?.total ?? 0));

  ngOnInit(): void {
    this.buscar(0);
  }

  buscar(pagina: number): void {
    this.cargando.set(true);
    this.error.set(null);
    this.servicio.buscar(pagina, TAMANIO_PAGINA).subscribe({
      next: (resultado) => {
        this.datos.set(resultado);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(e.error?.mensaje ?? 'No se pudo consultar los clientes inactivos.');
      },
    });
  }

  nombreOrigen(origen: ClienteInactivo['origen']): string {
    return NOMBRES_DE_ORIGEN[origen] ?? origen;
  }
}
