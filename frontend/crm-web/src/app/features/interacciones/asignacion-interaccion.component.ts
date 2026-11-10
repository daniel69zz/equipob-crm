import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AgenteAtencion, Interaccion, InteraccionesService, LARGO_MAXIMO_COMENTARIO } from './interacciones.service';

/**
 * Responsable de una interacción (SCRUM-56, SCRUM-455): todos ven quién la atiende, quién lo asignó y cuándo. Solo el
 * Administrador de CRM la asigna o la reasigna a un agente activo, con un comentario opcional que le llega en la
 * notificación; una cerrada no se reasigna hasta reabrirla (docs/interacciones/asignacion-interacciones.md).
 */
@Component({
  selector: 'app-asignacion-interaccion',
  standalone: true,
  imports: [DatePipe, FormsModule],
  template: `
    <section class="asignacion" [attr.aria-label]="'Responsable de la interacción n.º ' + interaccion().id">
      <p class="actual">
        @if (interaccion().responsable) {
          <span class="etiqueta">Responsable: {{ interaccion().responsable }}</span>
          <small>Asignada por {{ interaccion().responsableAsignadoPor }} el
            {{ interaccion().responsableAsignadoEn | date: 'dd/MM/yyyy HH:mm' }}</small>
        } @else {
          <span class="sin">Sin responsable</span>
        }
        @if (puedeAsignar() && !abierto()) {
          <button type="button" class="secundario" (click)="abrir()">
            {{ interaccion().responsable ? 'Reasignar' : 'Asignar' }}
          </button>
        }
      </p>
      @if (abierto()) {
        <form class="formulario" (ngSubmit)="guardar()" novalidate
          [attr.aria-label]="'Asignar la interacción n.º ' + interaccion().id">
          <div class="campo">
            <label [for]="id('agente')">Agente</label>
            <select [id]="id('agente')" name="agente" [(ngModel)]="agente" [disabled]="cargando()" required>
              <option value="" disabled>{{ cargando() ? 'Cargando agentes…' : 'Elija un agente' }}</option>
              @for (a of agentes(); track a.usuario) {
                <option [value]="a.usuario" [disabled]="a.usuario === interaccion().responsable">
                  {{ a.nombreCompleto }} ({{ a.usuario }}){{ a.usuario === interaccion().responsable ? ' · actual' : '' }}
                </option>
              }
            </select>
          </div>
          <div class="campo comentario">
            <label [for]="id('comentario')">Comentario <span class="opcional">(opcional, le llega al agente)</span></label>
            <input [id]="id('comentario')" name="comentario" [maxlength]="largoMaximo" [(ngModel)]="comentario"
              placeholder="Por qué se le asigna o qué priorizar" />
          </div>
          <div class="botones">
            <button type="submit" [disabled]="guardando() || !agente || agente === interaccion().responsable">
              {{ guardando() ? 'Guardando…' : 'Asignar' }}
            </button>
            <button type="button" class="secundario" (click)="cerrar()">Cancelar</button>
          </div>
        </form>
        @if (!cargando() && !agentes().length && !error()) {
          <p class="aviso">No hay agentes de atención activos para asignar.</p>
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
    .asignacion { margin-top: .6rem; }
    .actual { display: flex; gap: .6rem; flex-wrap: wrap; align-items: center; margin: 0; font-size: .85rem; }
    .actual small, .sin, .opcional, .aviso { color: var(--color-texto-suave); }
    .actual button { padding: .2rem .65rem; font-size: .8rem; }
    .etiqueta { font-weight: 600; color: #026aa2; background: #f0f9ff; }
    .formulario { display: flex; gap: .75rem; flex-wrap: wrap; align-items: flex-end; margin-top: .5rem; }
    .campo { display: flex; flex-direction: column; gap: .25rem; }
    .comentario { flex: 1 1 16rem; }
    label { font-size: .8rem; font-weight: 600; }
    .opcional { font-weight: 400; }
    .botones { display: flex; gap: .5rem; }
    .aviso { font-size: .85rem; margin: .4rem 0 0; }
    .error-campo { color: var(--color-error, #b42318); font-size: .82rem; margin: .35rem 0 0; }
    .enlace { background: none; border: 0; padding: 0 0 0 .4rem; color: var(--color-primario); text-decoration: underline; cursor: pointer; font-size: inherit; }
  `,
})
export class AsignacionInteraccionComponent {
  private static siguiente = 0;
  private readonly servicio = inject(InteraccionesService);
  private readonly auth = inject(AuthService);

  readonly interaccion = input.required<Interaccion>();
  readonly actualizada = output<Interaccion>();
  /** Pide volver a cargar la interacción cuando otra persona la cambió. */
  readonly recargar = output<void>();

  readonly largoMaximo = LARGO_MAXIMO_COMENTARIO;
  private readonly prefijo = `asignacion-${++AsignacionInteraccionComponent.siguiente}`;
  readonly abierto = signal(false);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly agentes = signal<AgenteAtencion[]>([]);
  readonly error = signal('');
  readonly desactualizada = signal(false);
  agente = '';
  comentario = '';

  /** Criterio de aceptación 3: solo el Administrador asigna; una cerrada se reabre antes (SCRUM-453). */
  readonly puedeAsignar = computed(() => this.interaccion().estado !== 'CERRADA' && this.auth.puedeAsignarInteracciones());

  id(campo: string): string {
    return `${this.prefijo}-${campo}`;
  }

  abrir(): void {
    this.abierto.set(true);
    this.agente = '';
    this.comentario = '';
    this.limpiarError();
    this.cargando.set(true);
    this.servicio.agentes()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: agentes => this.agentes.set(agentes),
        error: (e: HttpErrorResponse) => this.rechazado(e, 'No se pudieron cargar los agentes.'),
      });
  }

  cerrar(): void {
    this.abierto.set(false);
    this.limpiarError();
  }

  guardar(): void {
    const actual = this.interaccion();
    if (this.guardando() || !this.agente || this.agente === actual.responsable) return;
    this.guardando.set(true);
    this.limpiarError();
    this.servicio.asignar(actual.clienteId, actual.id, this.agente, this.comentario.trim() || null, actual.version)
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: interaccion => {
          this.abierto.set(false);
          this.actualizada.emit(interaccion);
        },
        error: (e: HttpErrorResponse) => this.rechazado(e, 'No se pudo asignar la interacción.'),
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
      this.error.set('Otra persona actualizó esta interacción. Recárguela y repita la asignación.');
    } else if (e.status === 401) {
      this.error.set('La sesión ha vencido. Inicie sesión nuevamente.');
    } else if (e.status === 0) {
      this.error.set('No se pudo conectar con el servidor. Compruebe la conexión e intente de nuevo.');
    } else {
      this.error.set(e.error?.mensaje ?? porDefecto);
    }
  }
}
