import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  CANALES_INTERACCION,
  CanalInteraccion,
  DatosInteraccion,
  Interaccion,
  InteraccionesService,
  LARGO_MAXIMO_ASUNTO,
  LARGO_MAXIMO_DESCRIPCION,
  LARGO_MAXIMO_REFERENCIA,
  TIPOS_INTERACCION,
  TipoInteraccion,
} from './interacciones.service';

interface Formulario {
  tipo: TipoInteraccion | '';
  canal: CanalInteraccion | '';
  asunto: string;
  descripcion: string;
  referenciaCompra: string;
}

/**
 * Registro y edición de una interacción (SCRUM-52, SCRUM-434). Valida en la pantalla las mismas reglas que el
 * servicio (SCRUM-432) y muestra los errores por campo. Al editar envía la versión que se cargó: si otra persona
 * la cambió antes, el servicio responde 409 y se ofrece recargarla.
 */
@Component({
  selector: 'app-formulario-interaccion',
  standalone: true,
  imports: [FormsModule],
  template: `
    <form class="tarjeta formulario" (ngSubmit)="guardar()" novalidate [attr.aria-labelledby]="idTitulo">
      <h2 [id]="idTitulo">{{ interaccion() ? 'Editar interacción n.º ' + interaccion()!.id : 'Registrar interacción' }}</h2>

      <div class="fila">
        <fieldset class="tipo">
          <legend>Tipo</legend>
          @for (tipo of tipos; track tipo.valor) {
            <label><input type="radio" name="tipo" [value]="tipo.valor" [(ngModel)]="formulario.tipo" /> {{ tipo.nombre }}</label>
          }
          @if (errores()['tipo']; as mensaje) { <p class="error-campo" role="alert">{{ mensaje }}</p> }
        </fieldset>

        <div class="campo">
          <label [for]="id('canal')">Canal <span class="opcional">(opcional)</span></label>
          <select [id]="id('canal')" name="canal" [(ngModel)]="formulario.canal" [attr.aria-invalid]="!!errores()['canal']">
            <option value="">Sin indicar</option>
            @for (canal of canales; track canal.valor) { <option [value]="canal.valor">{{ canal.nombre }}</option> }
          </select>
          @if (errores()['canal']; as mensaje) { <p class="error-campo" role="alert">{{ mensaje }}</p> }
        </div>
      </div>

      <label [for]="id('asunto')">Asunto <span class="opcional">(opcional)</span></label>
      <input [id]="id('asunto')" name="asunto" [maxlength]="largoAsunto" [(ngModel)]="formulario.asunto"
        placeholder="Resumen en una línea" [attr.aria-invalid]="!!errores()['asunto']" />
      @if (errores()['asunto']; as mensaje) { <p class="error-campo" role="alert">{{ mensaje }}</p> }

      <label [for]="id('descripcion')">Descripción</label>
      <textarea [id]="id('descripcion')" name="descripcion" rows="5" [maxlength]="largoDescripcion"
        [(ngModel)]="formulario.descripcion" placeholder="Qué consultó o reclamó el cliente, o la nota de la atención"
        [attr.aria-invalid]="!!errores()['descripcion']"
        [attr.aria-describedby]="id('ayuda-descripcion')"></textarea>
      <small [id]="id('ayuda-descripcion')" class="ayuda">{{ formulario.descripcion.trim().length }}/{{ largoDescripcion }}</small>
      @if (errores()['descripcion']; as mensaje) { <p class="error-campo" role="alert">{{ mensaje }}</p> }

      <label [for]="id('referencia')">Compra relacionada <span class="opcional">(opcional)</span></label>
      <input [id]="id('referencia')" name="referenciaCompra" [maxlength]="largoReferencia"
        [(ngModel)]="formulario.referenciaCompra" placeholder="Por ejemplo V-100234"
        [attr.aria-invalid]="!!errores()['referenciaCompra']" />
      @if (errores()['referenciaCompra']; as mensaje) { <p class="error-campo" role="alert">{{ mensaje }}</p> }

      @if (error()) {
        <p class="error" role="alert">{{ error() }}
          @if (desactualizada()) { <button type="button" class="secundario" (click)="recargar.emit()">Recargar la interacción</button> }
        </p>
      }
      <div class="acciones">
        <button type="submit" [disabled]="guardando()">
          {{ guardando() ? 'Guardando…' : (interaccion() ? 'Guardar cambios' : 'Registrar') }}
        </button>
        <button type="button" class="secundario" [disabled]="guardando()" (click)="cancelada.emit()">Cancelar</button>
      </div>
    </form>
  `,
  styles: `
    .formulario { display: flex; flex-direction: column; gap: .35rem; padding: 1rem 1.25rem; margin-bottom: 1rem; }
    h2 { margin: 0 0 .4rem; font-size: .85rem; text-transform: uppercase; letter-spacing: .03em; color: var(--color-texto-suave); }
    .fila { display: flex; gap: 1.5rem; flex-wrap: wrap; align-items: flex-start; }
    .tipo { display: flex; gap: 1rem; flex-wrap: wrap; border: 0; padding: 0; margin: 0; }
    .tipo legend { font-weight: 600; margin-bottom: .35rem; padding: 0; }
    .tipo label { font-weight: 400; margin: 0; display: inline-flex; gap: .35rem; align-items: center; }
    .tipo .error-campo { flex-basis: 100%; }
    .campo { display: flex; flex-direction: column; gap: .35rem; min-width: 12rem; }
    label { font-weight: 600; margin-top: .5rem; }
    .campo label { margin-top: 0; }
    .opcional, .ayuda { color: var(--color-texto-suave); font-weight: 400; }
    .ayuda { align-self: flex-end; font-size: .8rem; }
    .error-campo { color: var(--color-error, #b42318); font-size: .82rem; margin: .1rem 0 0; }
    input[aria-invalid='true'], textarea[aria-invalid='true'], select[aria-invalid='true'] { border-color: var(--color-error, #b42318); }
    p.error button { margin-left: .5rem; }
    .acciones { display: flex; gap: .5rem; flex-wrap: wrap; margin-top: .75rem; }
  `,
})
export class FormularioInteraccionComponent implements OnInit {
  private static siguiente = 0;
  private readonly servicio = inject(InteraccionesService);

  /** Cliente de la interacción. */
  readonly clienteId = input.required<number>();
  /** La interacción que se edita; sin ella, se registra una nueva. */
  readonly interaccion = input<Interaccion | null>(null);
  readonly guardada = output<Interaccion>();
  readonly cancelada = output<void>();
  /** Pide volver a cargar la interacción cuando otra persona la cambió. */
  readonly recargar = output<void>();

  readonly tipos = TIPOS_INTERACCION;
  readonly canales = CANALES_INTERACCION;
  readonly largoAsunto = LARGO_MAXIMO_ASUNTO;
  readonly largoDescripcion = LARGO_MAXIMO_DESCRIPCION;
  readonly largoReferencia = LARGO_MAXIMO_REFERENCIA;
  readonly idTitulo = `titulo-interaccion-${++FormularioInteraccionComponent.siguiente}`;

  readonly guardando = signal(false);
  readonly errores = signal<Record<string, string>>({});
  readonly error = signal('');
  readonly desactualizada = signal(false);

  formulario: Formulario = { tipo: '', canal: '', asunto: '', descripcion: '', referenciaCompra: '' };

  ngOnInit(): void {
    const actual = this.interaccion();
    if (actual) {
      this.formulario = {
        tipo: actual.tipo,
        canal: actual.canal ?? '',
        asunto: actual.asunto ?? '',
        descripcion: actual.descripcion,
        referenciaCompra: actual.referenciaCompra ?? '',
      };
    }
  }

  /** Identificador único de un control, para que dos formularios abiertos no compartan ids. */
  id(campo: string): string {
    return `${this.idTitulo}-${campo}`;
  }

  guardar(): void {
    if (this.guardando()) return;
    const datos = this.validar();
    if (!datos) return;
    const actual = this.interaccion();
    const solicitud = actual
      ? this.servicio.modificar(actual.clienteId, actual.id, datos, actual.version)
      : this.servicio.registrar(this.clienteId(), datos);
    this.guardando.set(true);
    this.error.set('');
    this.desactualizada.set(false);
    solicitud.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: guardada => this.guardada.emit(guardada),
      error: (e: HttpErrorResponse) => this.rechazada(e),
    });
  }

  /** Las mismas reglas que el servicio (SCRUM-432): tipo y descripción obligatorios y los largos máximos. */
  private validar(): DatosInteraccion | null {
    const f = this.formulario;
    const errores: Record<string, string> = {};
    const descripcion = f.descripcion.trim();
    if (!f.tipo) errores['tipo'] = 'Elija si es una consulta, un reclamo o una nota.';
    if (!descripcion) errores['descripcion'] = 'Describa la consulta, el reclamo o la nota.';
    else if (descripcion.length > LARGO_MAXIMO_DESCRIPCION) {
      errores['descripcion'] = `Admite hasta ${LARGO_MAXIMO_DESCRIPCION.toLocaleString('es-BO')} caracteres.`;
    }
    if (f.asunto.trim().length > LARGO_MAXIMO_ASUNTO) errores['asunto'] = `Admite hasta ${LARGO_MAXIMO_ASUNTO} caracteres.`;
    if (f.referenciaCompra.trim().length > LARGO_MAXIMO_REFERENCIA) {
      errores['referenciaCompra'] = `Admite hasta ${LARGO_MAXIMO_REFERENCIA} caracteres.`;
    }
    this.errores.set(errores);
    this.error.set(Object.keys(errores).length ? 'Revise los campos marcados.' : '');
    if (Object.keys(errores).length) return null;
    return {
      tipo: f.tipo as TipoInteraccion,
      canal: f.canal || null,
      asunto: f.asunto.trim() || null,
      descripcion,
      referenciaCompra: f.referenciaCompra.trim() || null,
    };
  }

  private rechazada(e: HttpErrorResponse): void {
    const campos = (e.error?.campos ?? {}) as Record<string, string>;
    if (e.status === 400 && Object.keys(campos).length) {
      this.errores.set(Object.fromEntries(Object.entries(campos)
        .map(([campo, motivo]) => [campo, motivo.charAt(0).toUpperCase() + motivo.slice(1) + '.'])));
      this.error.set('Revise los campos marcados.');
      return;
    }
    if (e.status === 409) {
      this.desactualizada.set(true);
      this.error.set('Otra persona modificó esta interacción mientras la editaba. Recárguela y repita el cambio.');
      return;
    }
    if (e.status === 401) this.error.set('La sesión ha vencido. Inicie sesión nuevamente.');
    else if (e.status === 403) this.error.set(e.error?.mensaje ?? 'No tiene permiso para registrar interacciones.');
    else if (e.status === 0) this.error.set('No se pudo conectar con el servidor. Compruebe la conexión e intente de nuevo.');
    else this.error.set(e.error?.mensaje ?? 'No se pudo guardar la interacción. Intente de nuevo.');
  }
}
