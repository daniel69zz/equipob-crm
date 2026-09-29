import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClientesService, IdentificadorOrigen, PerfilCliente } from './clientes.service';
import { Compra, ComprasService, PaginaCompras } from './compras.service';

const TAMANIO_PAGINA = 20;

const NOMBRES_DE_ORIGEN: Record<Compra['origen'], string> = {
  VENTAS: 'Ventas',
  MARKETPLACE: 'Marketplace',
};

const NOMBRES_DE_ESTADO: Record<Compra['estado'], string> = {
  CONFIRMADA: 'Confirmada',
  DEVOLUCION_PARCIAL: 'Devolución parcial',
  ANULADA: 'Anulada',
};

/**
 * Historial de compras de un cliente, de la más reciente a la más antigua (SCRUM-16). Combina
 * los identificadores por canal del perfil (GET /api/perfil/clientes/{id}) con la consulta de
 * servicio-comportamiento, tal como lo describe docs/compra/historial-compras.md.
 */
@Component({
  selector: 'app-historial-compras',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink],
  template: `
    <p><a routerLink="/clientes">← Clientes</a></p>
    <h1>Historial de compras</h1>
    @if (perfil(); as p) {
      <p class="subtitulo">
        {{ p.nombres }} {{ p.apellidos }} · {{ p.tipoDocumento }} {{ p.numeroDocumento }} · cliente {{ p.id }}
      </p>
    }

    @if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    }

    @if (historial(); as datos) {
      @for (compra of datos.content; track $index) {
        <article class="tarjeta compra">
          <header>
            <span class="fecha">{{ compra.fecha | date: 'dd/MM/yyyy HH:mm' }}</span>
            <span class="referencia">{{ compra.referencia }}</span>
            <span class="etiqueta" [class]="'origen-' + compra.origen">{{ nombreOrigen(compra.origen) }}</span>
            <span class="etiqueta" [class]="'estado-' + compra.estado">{{ nombreEstado(compra.estado) }}</span>
            <strong class="monto">{{ compra.montoTotal | number: '1.2-2' }}</strong>
          </header>
          <table>
            <thead>
              <tr>
                <th>Categoría</th>
                <th>Cantidad</th>
                <th>Monto</th>
              </tr>
            </thead>
            <tbody>
              @for (item of compra.items; track item.categoria) {
                <tr>
                  <td>{{ item.categoria }}</td>
                  <td>{{ item.cantidad }}</td>
                  <td>{{ item.monto | number: '1.2-2' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </article>
      } @empty {
        <p class="tarjeta">Este cliente no tiene compras registradas.</p>
      }

      @if (datos.total > 0) {
        <div class="paginacion">
          <span>{{ desdeRegistro() }}–{{ hastaRegistro() }} de {{ datos.total }}</span>
          <button type="button" class="secundario" [disabled]="datos.pagina === 0 || cargando()" (click)="buscar(datos.pagina - 1)">
            Más recientes
          </button>
          <button type="button" class="secundario" [disabled]="esUltimaPagina() || cargando()" (click)="buscar(datos.pagina + 1)">
            Más antiguos
          </button>
        </div>
      }
    }
  `,
  styles: `
    .subtitulo { color: var(--color-texto-suave); margin-top: 0; }
    .compra { margin-bottom: 1rem; padding: 1rem 1.25rem; }
    .compra header { display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem; }
    .fecha { font-family: monospace; }
    .referencia { color: var(--color-texto-suave); font-family: monospace; }
    .monto { margin-left: auto; }
    .etiqueta { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; background: #eef2f7; }
    .origen-VENTAS { color: #1f3864; background: #dae8fc; }
    .origen-MARKETPLACE { color: #6b3fa0; background: #efe6fb; }
    .estado-CONFIRMADA { color: #0b6e4f; background: #e3f6ee; }
    .estado-DEVOLUCION_PARCIAL { color: #9a6700; background: #fff4e0; }
    .estado-ANULADA { color: var(--color-error); background: #fdeceb; }
    .paginacion { display: flex; align-items: center; justify-content: flex-end; gap: 0.75rem; }
  `,
})
export class HistorialComprasComponent implements OnInit {
  private readonly clientes = inject(ClientesService);
  private readonly compras = inject(ComprasService);

  /** Identificador del cliente, tomado de la ruta /clientes/:id/compras. */
  readonly id = input.required<string>();

  readonly perfil = signal<PerfilCliente | null>(null);
  readonly historial = signal<PaginaCompras | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  readonly desdeRegistro = computed(() => (this.historial()?.pagina ?? 0) * TAMANIO_PAGINA + 1);
  readonly hastaRegistro = computed(
    () => (this.historial()?.pagina ?? 0) * TAMANIO_PAGINA + (this.historial()?.content.length ?? 0),
  );
  readonly esUltimaPagina = computed(() => this.hastaRegistro() >= (this.historial()?.total ?? 0));

  private identificadores: IdentificadorOrigen[] = [];

  ngOnInit(): void {
    this.clientes.obtener(this.idCliente()).subscribe({
      next: (perfil) => {
        this.perfil.set(perfil);
        // El servicio de comportamiento solo conoce los identificadores de Marketplace y Ventas.
        this.identificadores = perfil.identificadoresOrigen.filter((id) => id.origen !== 'CRM');
        this.buscar(0);
      },
      error: (e: HttpErrorResponse) => this.mostrarError(e),
    });
  }

  buscar(pagina: number): void {
    this.cargando.set(true);
    this.error.set(null);
    this.compras.historial(this.idCliente(), this.identificadores, pagina, TAMANIO_PAGINA).subscribe({
      next: (resultado) => {
        this.historial.set(resultado);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.mostrarError(e);
      },
    });
  }

  nombreOrigen(origen: Compra['origen']): string {
    return NOMBRES_DE_ORIGEN[origen] ?? origen;
  }

  nombreEstado(estado: Compra['estado']): string {
    return NOMBRES_DE_ESTADO[estado] ?? estado;
  }

  private idCliente(): number {
    return Number(this.id());
  }

  private mostrarError(error: HttpErrorResponse): void {
    this.error.set(error.error?.mensaje ?? 'No se pudo consultar el historial de compras.');
  }
}
