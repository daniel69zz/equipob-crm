import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CompraHistorial, Interaccion, InteraccionesService } from './interacciones.service';

const LIMITE_COMPRAS = 20;

const ESTADOS_COMPRA: Record<string, string> = {
  CONFIRMADA: 'Confirmada',
  DEVOLUCION_PARCIAL: 'Devolución parcial',
  ANULADA: 'Anulada',
};

/**
 * Compra vinculada a una interacción (SCRUM-54, SCRUM-444): la muestra a quien ve la interacción, con quién la vinculó
 * y cuándo. El Agente de Atención y el Administrador, con acceso al historial de compras, buscan una compra del
 * cliente y la vinculan, la cambian o la quitan mientras la interacción no esté cerrada
 * (docs/interacciones/vinculacion-compras.md).
 */
@Component({
  selector: 'app-compra-interaccion',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule],
  template: `
    <section class="compra" [attr.aria-label]="'Compra vinculada a la interacción n.º ' + interaccion().id">
      @if (interaccion().compra; as compra) {
        <p class="vinculada">
          <span class="etiqueta">Compra {{ compra.referencia }}</span>
          @if (compra.fecha) { <span>{{ compra.fecha | date: 'dd/MM/yyyy' }}</span> }
          @if (compra.montoTotal !== null) { <strong>Bs {{ compra.montoTotal | number: '1.2-2' }}</strong> }
          @if (compra.estado) { <span [class]="'estado-' + compra.estado">{{ nombreEstado(compra.estado) }}</span> }
          <small>Vinculada por {{ compra.vinculadaPor }} el {{ compra.vinculadaEn | date: 'dd/MM/yyyy HH:mm' }}</small>
        </p>
      }
      @if (puedeCambiar() && !abierto()) {
        <div class="acciones">
          <button type="button" class="secundario" (click)="abrir()">
            {{ interaccion().compra ? 'Cambiar compra' : 'Vincular compra' }}
          </button>
          @if (interaccion().compra) {
            <button type="button" class="secundario" [disabled]="guardando()" (click)="quitar()">Quitar compra</button>
          }
        </div>
      }
      @if (abierto()) {
        <form class="buscador" (ngSubmit)="buscar()" novalidate role="search"
          [attr.aria-label]="'Buscar una compra del cliente para la interacción n.º ' + interaccion().id">
          <label [for]="id('texto')">Referencia de la compra</label>
          <div class="fila">
            <input [id]="id('texto')" name="texto" type="search" maxlength="64" [(ngModel)]="texto" placeholder="Ej.: V-1001" />
            <button type="submit" class="secundario" [disabled]="buscando()">Buscar</button>
            <button type="button" class="enlace" (click)="cerrar()">Cancelar</button>
          </div>
        </form>
        @if (buscando()) {
          <p role="status">Buscando las compras del cliente…</p>
        } @else if (compras() !== null) {
          @for (aviso of avisos(); track aviso) { <p class="aviso" role="status">{{ aviso }}</p> }
          @if (compras()!.length) {
            <ul class="lista" aria-label="Compras del cliente">
              @for (c of compras(); track c.referencia) {
                <li>
                  <span class="referencia">{{ c.referencia }}</span>
                  <span>{{ c.fecha | date: 'dd/MM/yyyy' }}</span>
                  <span>Bs {{ c.montoTotal | number: '1.2-2' }}</span>
                  <span [class]="'estado-' + c.estado">{{ nombreEstado(c.estado) }}</span>
                  <button type="button" [disabled]="guardando() || c.referencia === interaccion().compra?.referencia"
                    (click)="vincular(c)" [attr.aria-label]="'Vincular la compra ' + c.referencia">
                    {{ c.referencia === interaccion().compra?.referencia ? 'Vinculada' : 'Vincular' }}
                  </button>
                </li>
              }
            </ul>
          } @else if (!avisos().length) {
            <p class="aviso">Ninguna compra del cliente coincide con la referencia.</p>
          }
        }
      }
      @if (error()) {
        <p class="error-campo" role="alert">{{ error() }}
          @if (desactualizada()) { <button type="button" class="enlace" (click)="recargar.emit()">Recargar</button> }
        </p>
      }
    </section>
  `,
  styles: `
    .compra { margin-top: .6rem; }
    .vinculada { display: flex; gap: .6rem; flex-wrap: wrap; align-items: center; margin: 0; font-size: .85rem; }
    .vinculada small { color: var(--color-texto-suave); }
    .etiqueta { font-weight: 600; color: #6941c6; background: #f4f3ff; }
    .acciones { display: flex; gap: .5rem; margin-top: .4rem; }
    .acciones button { padding: .25rem .7rem; font-size: .82rem; }
    .buscador { margin-top: .5rem; }
    .buscador label { font-size: .8rem; font-weight: 600; }
    .fila { display: flex; gap: .5rem; align-items: center; flex-wrap: wrap; margin-top: .25rem; }
    .fila input { flex: 1 1 12rem; }
    .lista { list-style: none; margin: .5rem 0 0; padding: 0; display: flex; flex-direction: column; gap: .35rem; }
    .lista li {
      display: grid; grid-template-columns: minmax(6rem, 1fr) auto auto auto auto; gap: .75rem; align-items: center;
      padding: .4rem .6rem; border: 1px solid var(--color-borde); border-radius: 6px; font-size: .85rem;
    }
    @media (max-width: 560px) { .lista li { grid-template-columns: 1fr 1fr; } }
    .lista button { padding: .2rem .6rem; font-size: .8rem; }
    .referencia { font-weight: 600; overflow-wrap: anywhere; }
    .aviso { color: var(--color-texto-suave); font-size: .85rem; margin: .4rem 0 0; }
    .estado-CONFIRMADA { color: #067647; }
    .estado-DEVOLUCION_PARCIAL { color: #b54708; }
    .estado-ANULADA { color: #b42318; }
    .error-campo { color: var(--color-error, #b42318); font-size: .82rem; margin: .35rem 0 0; }
    .enlace { background: none; border: 0; padding: 0 0 0 .4rem; color: var(--color-primario); text-decoration: underline; cursor: pointer; font-size: inherit; }
  `,
})
export class CompraInteraccionComponent {
  private static siguiente = 0;
  private readonly servicio = inject(InteraccionesService);
  private readonly auth = inject(AuthService);

  readonly interaccion = input.required<Interaccion>();
  readonly actualizada = output<Interaccion>();
  /** Pide volver a cargar la interacción cuando otra persona la cambió. */
  readonly recargar = output<void>();

  private readonly prefijo = `compra-${++CompraInteraccionComponent.siguiente}`;
  readonly abierto = signal(false);
  readonly buscando = signal(false);
  readonly guardando = signal(false);
  readonly compras = signal<CompraHistorial[] | null>(null);
  readonly avisos = signal<string[]>([]);
  readonly error = signal('');
  readonly desactualizada = signal(false);
  texto = '';

  /** Una cerrada no se vincula hasta que el Administrador la reabra (SCRUM-447). */
  readonly puedeCambiar = computed(() => this.interaccion().estado !== 'CERRADA' && this.auth.puedeVincularCompras());

  id(campo: string): string {
    return `${this.prefijo}-${campo}`;
  }

  nombreEstado(estado: string): string {
    return ESTADOS_COMPRA[estado] ?? estado;
  }

  abrir(): void {
    this.abierto.set(true);
    this.limpiarError();
    this.buscar();
  }

  cerrar(): void {
    this.abierto.set(false);
    this.compras.set(null);
    this.texto = '';
  }

  buscar(): void {
    this.buscando.set(true);
    this.limpiarError();
    this.servicio.comprasElegibles(this.interaccion().clienteId, this.texto, LIMITE_COMPRAS)
      .pipe(finalize(() => this.buscando.set(false)))
      .subscribe({
        next: respuesta => {
          this.compras.set(respuesta.compras);
          this.avisos.set(respuesta.avisos);
        },
        error: (e: HttpErrorResponse) => this.rechazado(e, 'No se pudieron consultar las compras del cliente.'),
      });
  }

  vincular(compra: CompraHistorial): void {
    const actual = this.interaccion();
    this.guardando.set(true);
    this.limpiarError();
    this.servicio.vincularCompra(actual.clienteId, actual.id, compra.referencia, actual.version)
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: interaccion => {
          this.cerrar();
          this.actualizada.emit(interaccion);
        },
        error: (e: HttpErrorResponse) => this.rechazado(e, 'No se pudo vincular la compra.'),
      });
  }

  quitar(): void {
    const actual = this.interaccion();
    this.guardando.set(true);
    this.limpiarError();
    this.servicio.desvincularCompra(actual.clienteId, actual.id, actual.version)
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: interaccion => this.actualizada.emit(interaccion),
        error: (e: HttpErrorResponse) => this.rechazado(e, 'No se pudo quitar la compra.'),
      });
  }

  private limpiarError(): void {
    this.error.set('');
    this.desactualizada.set(false);
  }

  private rechazado(e: HttpErrorResponse, porDefecto: string): void {
    const campos = (e.error?.campos ?? {}) as Record<string, string>;
    if (e.status === 409 && campos['version']) {
      this.desactualizada.set(true);
      this.error.set('Otra persona actualizó esta interacción. Recárguela y repita el cambio.');
    } else if (e.status === 401) {
      this.error.set('La sesión ha vencido. Inicie sesión nuevamente.');
    } else if (e.status === 0) {
      this.error.set('No se pudo conectar con el servidor. Compruebe la conexión e intente de nuevo.');
    } else {
      this.error.set(e.error?.mensaje ?? porDefecto);
    }
  }
}
