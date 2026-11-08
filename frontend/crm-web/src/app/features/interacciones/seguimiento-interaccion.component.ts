import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import {
  CambioSeguimiento,
  ESTADOS_INTERACCION,
  EstadoInteraccion,
  Interaccion,
  InteraccionesService,
  LARGO_MAXIMO_COMENTARIO,
  LARGO_MINIMO_COMENTARIO_REAPERTURA,
  TIPOS_INTERACCION,
  TipoInteraccion,
} from './interacciones.service';

/**
 * Seguimiento de una interacción (SCRUM-55, SCRUM-450): cambia su estado (abierta, en proceso o cerrada) y su tipo.
 * Una interacción cerrada solo la reabre el Administrador de CRM, con un comentario, y mientras siga cerrada no se
 * reclasifica (docs/interacciones/seguimiento-interacciones.md).
 */
@Component({
  selector: 'app-seguimiento-interaccion',
  standalone: true,
  imports: [FormsModule],
  template: `
    <form class="seguimiento" (ngSubmit)="guardar()" novalidate [attr.aria-label]="'Seguimiento de la interacción n.º ' + interaccion().id">
      @if (cerradaSinPermiso()) {
        <p class="aviso">Interacción cerrada. Solo el Administrador de CRM puede reabrirla.</p>
      } @else {
        <div class="campos">
          <div class="campo">
            <label [for]="id('estado')">Estado</label>
            <select [id]="id('estado')" name="estado" [(ngModel)]="estado">
              @for (opcion of estados; track opcion.valor) { <option [value]="opcion.valor">{{ opcion.nombre }}</option> }
            </select>
          </div>
          <div class="campo">
            <label [for]="id('tipo')">Tipo</label>
            <select [id]="id('tipo')" name="tipo" [(ngModel)]="tipo" [disabled]="sigueCerrada()"
              [attr.aria-describedby]="sigueCerrada() ? id('ayuda-tipo') : null">
              @for (opcion of tipos; track opcion.valor) { <option [value]="opcion.valor">{{ opcion.nombre }}</option> }
            </select>
          </div>
          <div class="campo comentario">
            <label [for]="id('comentario')">
              Comentario
              <span class="opcional">{{ reabre() ? '(obligatorio: por qué se reabre)' : '(opcional)' }}</span>
            </label>
            <input [id]="id('comentario')" name="comentario" [maxlength]="largoMaximo" [(ngModel)]="comentario"
              [placeholder]="reabre() ? 'Por qué se reabre el caso' : 'Qué se hizo o cómo se resolvió'"
              [attr.aria-invalid]="!!errorComentario()" />
          </div>
          <button type="submit" class="secundario" [disabled]="guardando() || !hayCambios()">
            {{ guardando() ? 'Guardando…' : (reabre() ? 'Reabrir' : 'Actualizar') }}
          </button>
        </div>
        @if (sigueCerrada()) { <small [id]="id('ayuda-tipo')" class="ayuda">Para cambiar el tipo, reabra la interacción.</small> }
        @if (errorComentario()) { <p class="error-campo" role="alert">{{ errorComentario() }}</p> }
        @if (error()) {
          <p class="error-campo" role="alert">{{ error() }}
            @if (desactualizada()) { <button type="button" class="enlace" (click)="recargar.emit()">Recargar</button> }
          </p>
        }
      }
    </form>
  `,
  styles: `
    .seguimiento { margin-top: .75rem; padding-top: .75rem; border-top: 1px dashed var(--color-borde); }
    .campos { display: flex; gap: .75rem; flex-wrap: wrap; align-items: flex-end; }
    .campo { display: flex; flex-direction: column; gap: .25rem; }
    .comentario { flex: 1 1 16rem; }
    label { font-size: .8rem; font-weight: 600; }
    .opcional, .ayuda, .aviso { color: var(--color-texto-suave); font-weight: 400; font-size: .8rem; }
    .aviso { margin: 0; }
    .error-campo { color: var(--color-error, #b42318); font-size: .82rem; margin: .35rem 0 0; }
    input[aria-invalid='true'] { border-color: var(--color-error, #b42318); }
    .enlace { background: none; border: 0; padding: 0 0 0 .4rem; color: var(--color-primario); text-decoration: underline; cursor: pointer; font-size: inherit; }
  `,
})
export class SeguimientoInteraccionComponent implements OnInit {
  private static siguiente = 0;
  private readonly servicio = inject(InteraccionesService);
  private readonly auth = inject(AuthService);

  readonly interaccion = input.required<Interaccion>();
  readonly actualizada = output<Interaccion>();
  /** Pide volver a cargar la interacción cuando otra persona la cambió. */
  readonly recargar = output<void>();

  readonly estados = ESTADOS_INTERACCION;
  readonly tipos = TIPOS_INTERACCION;
  readonly largoMaximo = LARGO_MAXIMO_COMENTARIO;
  private readonly prefijo = `seguimiento-${++SeguimientoInteraccionComponent.siguiente}`;

  readonly guardando = signal(false);
  readonly error = signal('');
  readonly errorComentario = signal('');
  readonly desactualizada = signal(false);
  private readonly estadoElegido = signal<EstadoInteraccion>('ABIERTA');

  tipo: TipoInteraccion = 'CONSULTA';
  comentario = '';

  get estado(): EstadoInteraccion {
    return this.estadoElegido();
  }
  set estado(valor: EstadoInteraccion) {
    this.estadoElegido.set(valor);
    // Al volver a "Cerrada" sin reabrir, el tipo vuelve al guardado: una cerrada no se reclasifica.
    if (this.interaccion().estado === 'CERRADA' && valor === 'CERRADA') this.tipo = this.interaccion().tipo;
  }

  /** Un Agente ve el estado de una cerrada, pero no puede reabrirla (criterio de aceptación 2). */
  readonly cerradaSinPermiso = computed(() => this.interaccion().estado === 'CERRADA' && !this.auth.puedeReabrirInteracciones());
  readonly reabre = computed(() => this.interaccion().estado === 'CERRADA' && this.estadoElegido() !== 'CERRADA');
  readonly sigueCerrada = computed(() => this.interaccion().estado === 'CERRADA' && this.estadoElegido() === 'CERRADA');

  ngOnInit(): void {
    this.estadoElegido.set(this.interaccion().estado);
    this.tipo = this.interaccion().tipo;
  }

  id(campo: string): string {
    return `${this.prefijo}-${campo}`;
  }

  hayCambios(): boolean {
    return this.estado !== this.interaccion().estado || this.tipo !== this.interaccion().tipo;
  }

  guardar(): void {
    const actual = this.interaccion();
    if (this.guardando() || !this.hayCambios()) return;
    const comentario = this.comentario.trim();
    this.errorComentario.set('');
    if (this.reabre() && comentario.length < LARGO_MINIMO_COMENTARIO_REAPERTURA) {
      this.errorComentario.set(`Explique por qué se reabre, en al menos ${LARGO_MINIMO_COMENTARIO_REAPERTURA} caracteres.`);
      return;
    }
    const cambio: CambioSeguimiento = { comentario: comentario || null };
    if (this.estado !== actual.estado) cambio.estado = this.estado;
    if (this.tipo !== actual.tipo) cambio.tipo = this.tipo;
    this.guardando.set(true);
    this.error.set('');
    this.desactualizada.set(false);
    this.servicio.seguimiento(actual.clienteId, actual.id, cambio, actual.version)
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: interaccion => this.actualizada.emit(interaccion),
        error: (e: HttpErrorResponse) => this.rechazado(e),
      });
  }

  private rechazado(e: HttpErrorResponse): void {
    const campos = (e.error?.campos ?? {}) as Record<string, string>;
    if (e.status === 409 && campos['version']) {
      this.desactualizada.set(true);
      this.error.set('Otra persona actualizó esta interacción. Recárguela y repita el cambio.');
    } else if (e.status === 400 && campos['comentario']) {
      this.errorComentario.set(campos['comentario'].charAt(0).toUpperCase() + campos['comentario'].slice(1) + '.');
    } else if (e.status === 401) {
      this.error.set('La sesión ha vencido. Inicie sesión nuevamente.');
    } else if (e.status === 0) {
      this.error.set('No se pudo conectar con el servidor. Compruebe la conexión e intente de nuevo.');
    } else {
      this.error.set(e.error?.mensaje ?? 'No se pudo actualizar el seguimiento. Intente de nuevo.');
    }
  }
}
