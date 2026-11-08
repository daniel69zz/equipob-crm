import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  CambioReglaAcumulacion,
  puntosPorCompra,
  ReglaAcumulacion,
  ReglasAcumulacionService,
  SolicitudReglaAcumulacion,
} from './reglas-acumulacion.service';

type Campo = 'bolivianosPorPunto' | 'montoMinimo' | 'motivo';

/**
 * Configuración de la regla de acumulación de puntos (SCRUM-46, SCRUM-391): el Administrador de CRM ve
 * la regla vigente, la cambia (bolivianos por punto y monto mínimo de compra) con una vista previa del
 * cálculo, y revisa el historial de versiones con quién y cuándo las cambió.
 */
@Component({
  selector: 'app-reglas-acumulacion',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe],
  template: `
    <header class="cabecera">
      <div>
        <p class="eyebrow">Administración · Fidelización</p>
        <h1>Reglas de acumulación de puntos</h1>
        <p class="subtitulo">Define cuántos puntos gana un cliente por cada compra, sin cambiar el código.</p>
      </div>
      @if (vigente() && !abierto()) {
        <button type="button" (click)="editar()">Editar regla</button>
      }
    </header>

    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (exito()) { <p class="exito" role="status">{{ exito() }}</p> }
    @if (cargando()) { <p role="status">Cargando la regla vigente…</p> }

    @if (vigente(); as regla) {
      <section class="resumen" aria-label="Regla vigente">
        <article class="tarjeta">
          <span>Tasa de acumulación</span>
          <strong>1 punto</strong>
          <small>por cada Bs {{ regla.bolivianosPorPunto | number: '1.2-2' }} consumidos</small>
        </article>
        <article class="tarjeta">
          <span>Monto mínimo de compra</span>
          <strong>Bs {{ regla.montoMinimo | number: '1.2-2' }}</strong>
          <small>{{ regla.montoMinimo > 0 ? 'Las compras menores no suman puntos' : 'Todas las compras suman puntos' }}</small>
        </article>
        <article class="tarjeta">
          <span>Versión vigente</span>
          <strong>v{{ regla.version }}</strong>
          <small>desde {{ regla.vigenteDesde | date: 'dd/MM/yyyy HH:mm' }} · {{ regla.modificadoPor }}</small>
        </article>
      </section>
    }

    @if (abierto() && vigente(); as regla) {
      <section class="tarjeta editor" aria-labelledby="titulo-editor">
        <h2 id="titulo-editor">Editar la regla (versión {{ regla.version }})</h2>
        <form (ngSubmit)="guardar()" novalidate>
          <fieldset [disabled]="guardando()">
            <div class="campos">
              <div>
                <label for="bolivianos-por-punto">Bolivianos por punto</label>
                <input id="bolivianos-por-punto" name="bolivianosPorPunto" type="number" min="0.01" step="0.01"
                  [(ngModel)]="formulario.bolivianosPorPunto" (ngModelChange)="limpiarCampo('bolivianosPorPunto')"
                  [attr.aria-invalid]="!!errores()['bolivianosPorPunto']" aria-describedby="ayuda-tasa" />
                <small id="ayuda-tasa">Se otorga 1 punto por cada tramo completo de este valor. Mayor que cero.</small>
                @if (errores()['bolivianosPorPunto']; as mensaje) { <p class="error-campo" role="alert">{{ mensaje }}</p> }
              </div>
              <div>
                <label for="monto-minimo">Monto mínimo de compra (Bs)</label>
                <input id="monto-minimo" name="montoMinimo" type="number" min="0" step="0.01"
                  [(ngModel)]="formulario.montoMinimo" (ngModelChange)="limpiarCampo('montoMinimo')"
                  [attr.aria-invalid]="!!errores()['montoMinimo']" aria-describedby="ayuda-minimo" />
                <small id="ayuda-minimo">Las compras de menos de este monto no suman puntos. Use 0 para no exigir mínimo.</small>
                @if (errores()['montoMinimo']; as mensaje) { <p class="error-campo" role="alert">{{ mensaje }}</p> }
              </div>
              <div class="ancho">
                <label for="motivo-regla">Motivo del cambio (opcional)</label>
                <textarea id="motivo-regla" name="motivo" maxlength="250" rows="2" [(ngModel)]="formulario.motivo"
                  placeholder="Ej. Campaña de fin de año"></textarea>
                @if (errores()['motivo']; as mensaje) { <p class="error-campo" role="alert">{{ mensaje }}</p> }
              </div>
            </div>

            <div class="vista-previa">
              <label for="compra-ejemplo">Vista previa: una compra de Bs</label>
              <input id="compra-ejemplo" name="compraEjemplo" type="number" min="0" step="0.01" [(ngModel)]="compraEjemplo" />
              <p aria-live="polite">
                Con la regla vigente suma <strong>{{ puntosVigentes() }} pts</strong>;
                con la regla nueva sumaría <strong>{{ puntosNuevos() }} pts</strong>.
              </p>
            </div>

            <p class="ayuda">La regla nueva se aplica a las compras que se procesen desde que la guarde. Los puntos ya
              otorgados no se recalculan, y la versión actual queda en el historial.</p>
            @if (errorFormulario()) { <p class="error" role="alert">{{ errorFormulario() }}</p> }
            <div class="acciones">
              <button type="submit">{{ guardando() ? 'Guardando…' : 'Guardar regla' }}</button>
              <button type="button" class="secundario" (click)="cancelar()">Cancelar</button>
            </div>
          </fieldset>
        </form>
      </section>
    }

    <section class="tarjeta" aria-labelledby="titulo-historial">
      <h2 id="titulo-historial">Historial de la regla</h2>
      @if (errorHistorial()) { <p class="error" role="alert">{{ errorHistorial() }}</p> }
      @if (cargandoHistorial()) { <p role="status">Cargando historial…</p> }
      @else if (!historial().length && !errorHistorial()) { <p class="vacio">Todavía no hay cambios registrados.</p> }
      @else {
        <div class="tabla">
          <table>
            <thead>
              <tr><th>Versión</th><th>Vigente desde</th><th>Cambiada por</th><th>Bs por punto</th><th>Monto mínimo</th><th>Motivo</th></tr>
            </thead>
            <tbody>
              @for (cambio of historial(); track cambio.version) {
                <tr [class.actual]="cambio.version === vigente()?.version">
                  <td>v{{ cambio.version }} @if (cambio.version === vigente()?.version) { <span class="etiqueta">Vigente</span> }</td>
                  <td>{{ cambio.vigenteDesde | date: 'dd/MM/yyyy HH:mm' }}</td>
                  <td>{{ cambio.modificadoPor }}</td>
                  <td>
                    @if (cambio.anteriorBolivianosPorPunto !== null && cambio.anteriorBolivianosPorPunto !== cambio.bolivianosPorPunto) {
                      <span class="anterior">{{ cambio.anteriorBolivianosPorPunto | number: '1.2-2' }}</span> →
                    }
                    {{ cambio.bolivianosPorPunto | number: '1.2-2' }}
                  </td>
                  <td>
                    @if (cambio.anteriorMontoMinimo !== null && cambio.anteriorMontoMinimo !== cambio.montoMinimo) {
                      <span class="anterior">{{ cambio.anteriorMontoMinimo | number: '1.2-2' }}</span> →
                    }
                    {{ cambio.montoMinimo | number: '1.2-2' }}
                  </td>
                  <td>{{ cambio.motivo || '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: `
    .cabecera { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; }
    .cabecera button { flex-shrink: 0; margin-top: 1.5rem; }
    .eyebrow { color: var(--color-primario); text-transform: uppercase; letter-spacing: .08em; font-size: .72rem; font-weight: 700; margin: 0 0 .5rem; }
    .resumen { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; margin-bottom: 1.25rem; }
    .resumen article { display: flex; flex-direction: column; gap: .15rem; padding: 1rem 1.25rem; }
    .resumen span, .resumen small { color: var(--color-texto-suave); font-size: .85rem; }
    .resumen strong { font-size: 1.6rem; color: var(--color-primario); }
    .editor { border-top: 3px solid var(--color-primario); margin-bottom: 1.25rem; }
    h2 { margin: 0 0 1rem; }
    fieldset { padding: 0; margin: 0; border: 0; min-width: 0; }
    .campos { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    .campos small { display: block; color: var(--color-texto-suave); font-size: .8rem; margin-top: .25rem; }
    .ancho { grid-column: 1 / -1; }
    .error-campo { color: var(--color-error, #b42318); font-size: .82rem; margin: .3rem 0 0; }
    input[aria-invalid='true'] { border-color: var(--color-error, #b42318); }
    .vista-previa { margin-top: 1rem; padding: .9rem 1rem; border: 1px solid var(--color-borde); border-radius: 8px; background: var(--color-superficie-suave); }
    .vista-previa input { max-width: 200px; }
    .vista-previa p { margin: .5rem 0 0; font-size: .9rem; }
    .ayuda { color: var(--color-texto-suave); font-size: .82rem; background: var(--color-superficie-suave); padding: .8rem; border-radius: 6px; }
    .acciones { display: flex; flex-wrap: wrap; gap: .5rem; }
    .tabla { overflow-x: auto; }
    table { min-width: 640px; }
    .actual { background: var(--color-superficie-suave); }
    .anterior { color: var(--color-texto-suave); text-decoration: line-through; }
    .etiqueta { margin-left: .35rem; padding: .1rem .45rem; border-radius: 999px; font-size: .72rem; background: var(--color-exito-claro); color: var(--color-exito); }
    .vacio { padding: 1.5rem 1rem; text-align: center; color: var(--color-texto-suave); }
    @media (max-width: 720px) {
      .cabecera { flex-direction: column; } .cabecera button { margin: 0; }
      .resumen, .campos { grid-template-columns: 1fr; } .ancho { grid-column: auto; }
      .tabla { overflow: visible; } table { min-width: 0; }
      thead { display: none; } tbody, tr, td { display: block; }
      tbody tr { padding: .8rem 0; border-bottom: 1px solid var(--color-borde); }
      td { border: 0; padding: .2rem 0; }
    }
  `,
})
export class ReglasAcumulacionComponent {
  private readonly api = inject(ReglasAcumulacionService);

  readonly vigente = signal<ReglaAcumulacion | null>(null);
  readonly historial = signal<CambioReglaAcumulacion[]>([]);
  readonly cargando = signal(false);
  readonly cargandoHistorial = signal(false);
  readonly guardando = signal(false);
  readonly abierto = signal(false);
  readonly error = signal('');
  readonly exito = signal('');
  readonly errorHistorial = signal('');
  readonly errorFormulario = signal('');
  readonly errores = signal<Partial<Record<Campo, string>>>({});

  formulario: SolicitudReglaAcumulacion = { bolivianosPorPunto: null, montoMinimo: null, motivo: '', version: 0 };
  private readonly ejemplo = signal<number | null>(100);
  private readonly borrador = signal<{ tasa: number | null; minimo: number | null }>({ tasa: null, minimo: null });

  get compraEjemplo() { return this.ejemplo(); }
  set compraEjemplo(valor: number | null) { this.ejemplo.set(valor); }

  readonly puntosVigentes = computed(() => {
    const regla = this.vigente();
    return regla ? puntosPorCompra(this.ejemplo() ?? 0, regla.bolivianosPorPunto, regla.montoMinimo) : 0;
  });
  readonly puntosNuevos = computed(() => {
    const { tasa, minimo } = this.borrador();
    return tasa === null || minimo === null ? 0 : puntosPorCompra(this.ejemplo() ?? 0, tasa, minimo);
  });

  constructor() {
    this.cargar();
  }

  cargar() {
    this.cargando.set(true);
    this.error.set('');
    this.api.vigente().pipe(finalize(() => this.cargando.set(false))).subscribe({
      next: regla => this.vigente.set(regla),
      error: e => this.error.set(this.mensaje(e)),
    });
    this.cargarHistorial();
  }

  editar() {
    const regla = this.vigente();
    if (!regla) return;
    this.formulario = { bolivianosPorPunto: regla.bolivianosPorPunto, montoMinimo: regla.montoMinimo, motivo: '',
      version: regla.version };
    this.actualizarBorrador();
    this.errores.set({});
    this.errorFormulario.set('');
    this.exito.set('');
    this.abierto.set(true);
  }

  cancelar() {
    this.abierto.set(false);
    this.errores.set({});
    this.errorFormulario.set('');
  }

  limpiarCampo(campo: Campo) {
    this.errores.update(errores => ({ ...errores, [campo]: undefined }));
    this.actualizarBorrador();
  }

  guardar() {
    if (this.guardando()) return;
    this.errorFormulario.set('');
    const errores = this.validar();
    this.errores.set(errores);
    if (Object.keys(errores).length) {
      this.errorFormulario.set('Revise los campos marcados.');
      return;
    }
    this.guardando.set(true);
    this.api.guardar({ ...this.formulario, motivo: this.formulario.motivo.trim() })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: regla => {
          this.vigente.set(regla);
          this.abierto.set(false);
          this.exito.set(`Regla guardada como versión ${regla.version}. Se aplica a las compras que se procesen desde ahora.`);
          this.cargarHistorial();
        },
        error: (e: HttpErrorResponse) => {
          this.errores.set(this.erroresDelServidor(e));
          this.errorFormulario.set(this.mensaje(e));
          if (e.status === 409) this.recargarVigente();
        },
      });
  }

  private cargarHistorial() {
    this.cargandoHistorial.set(true);
    this.errorHistorial.set('');
    this.api.historial().pipe(finalize(() => this.cargandoHistorial.set(false))).subscribe({
      next: datos => this.historial.set(datos),
      error: e => this.errorHistorial.set(this.mensaje(e)),
    });
  }

  /** Tras un conflicto, muestra la regla que guardó otra persona y deja editar sobre ella. */
  private recargarVigente() {
    this.api.vigente().subscribe({
      next: regla => { this.vigente.set(regla); this.formulario.version = regla.version; this.cargarHistorial(); },
    });
  }

  private actualizarBorrador() {
    this.borrador.set({ tasa: this.formulario.bolivianosPorPunto, minimo: this.formulario.montoMinimo });
  }

  private validar(): Partial<Record<Campo, string>> {
    const errores: Partial<Record<Campo, string>> = {};
    const { bolivianosPorPunto: tasa, montoMinimo: minimo } = this.formulario;
    if (tasa === null || tasa === undefined || (tasa as unknown) === '') errores.bolivianosPorPunto = 'Indique los bolivianos por punto.';
    else if (tasa <= 0) errores.bolivianosPorPunto = 'Debe ser mayor que cero.';
    else if (!dosDecimales(tasa)) errores.bolivianosPorPunto = 'Use como máximo 2 decimales.';
    if (minimo === null || minimo === undefined || (minimo as unknown) === '') errores.montoMinimo = 'Indique el monto mínimo (0 si no hay mínimo).';
    else if (minimo < 0) errores.montoMinimo = 'No puede ser negativo.';
    else if (!dosDecimales(minimo)) errores.montoMinimo = 'Use como máximo 2 decimales.';
    return errores;
  }

  private erroresDelServidor(e: HttpErrorResponse): Partial<Record<Campo, string>> {
    const campos = (e.error?.campos ?? {}) as Record<string, string>;
    const errores: Partial<Record<Campo, string>> = {};
    for (const campo of ['bolivianosPorPunto', 'montoMinimo', 'motivo'] as Campo[]) {
      if (campos[campo]) errores[campo] = `El valor ${campos[campo]}.`;
    }
    return errores;
  }

  private mensaje(e: HttpErrorResponse) {
    if (e.status === 403) return 'Solo el Administrador de CRM puede configurar las reglas de puntos.';
    return e.error?.mensaje ?? 'No se pudo completar la operación. Compruebe la conexión e intente de nuevo.';
  }
}

function dosDecimales(valor: number) {
  return Math.abs(Math.round(valor * 100) - valor * 100) < 1e-6;
}
