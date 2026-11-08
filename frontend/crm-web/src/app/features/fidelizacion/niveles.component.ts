import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  ConfiguracionNiveles,
  conflictosUmbrales,
  Nivel,
  NIVELES,
  NivelesService,
  NOMBRES_NIVEL,
  rangoTexto,
  SolicitudNiveles,
} from './niveles.service';

/**
 * Niveles de fidelización (SCRUM-49): el Administrador de CRM define los umbrales de puntos de Bronce,
 * Plata y Oro y el periodo de evaluación; al guardar, el CRM reclasifica a todos los clientes. Los
 * umbrales que se superponen se marcan antes de enviar y el servicio los rechaza indicando el conflicto.
 */
@Component({
  selector: 'app-niveles',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe],
  template: `
    <header class="cabecera">
      <div>
        <p class="eyebrow">Administración · Fidelización</p>
        <h1>Niveles de fidelización</h1>
        <p class="subtitulo">Reconoce a los clientes según los puntos que acumulan en el periodo de evaluación.</p>
      </div>
      @if (vigente() && !abierto()) {
        <div class="acciones">
          <button type="button" class="secundario" [disabled]="recalculando()" (click)="recalcular()">
            {{ recalculando() ? 'Reclasificando…' : 'Reclasificar ahora' }}
          </button>
          <button type="button" (click)="editar()">Editar niveles</button>
        </div>
      }
    </header>

    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (exito()) { <p class="exito" role="status">{{ exito() }}</p> }
    @if (cargando()) { <p role="status">Cargando niveles…</p> }

    @if (vigente(); as config) {
      <section class="niveles" aria-label="Niveles vigentes">
        @for (umbral of config.umbrales; track umbral.nivel) {
          <article class="tarjeta nivel" [attr.data-nivel]="umbral.nivel">
            <span class="insignia">{{ nombres[umbral.nivel] }}</span>
            <strong>{{ rango(umbral) }}</strong>
            <small>acumulados en los últimos {{ config.periodoMeses }} meses</small>
          </article>
        }
      </section>
      <p class="pie">Versión {{ config.version }} · vigente desde {{ config.vigenteDesde | date: 'dd/MM/yyyy HH:mm' }}
        · {{ config.modificadoPor }}. El nivel se recalcula con cada compra y cada noche.</p>
    }

    @if (abierto() && vigente(); as config) {
      <section class="tarjeta editor" aria-labelledby="titulo-editor">
        <h2 id="titulo-editor">Editar niveles (versión {{ config.version }})</h2>
        <form (ngSubmit)="guardar()" novalidate>
          <fieldset [disabled]="guardando()">
            <div class="periodo">
              <label for="periodo-meses">Periodo de evaluación (meses)</label>
              <input id="periodo-meses" name="periodoMeses" type="number" min="1" max="120" step="1"
                [(ngModel)]="formulario.periodoMeses" [attr.aria-invalid]="!!errorPeriodo()" />
              <small>Solo cuentan los puntos acumulados en este periodo. Entre 1 y 120 meses.</small>
              @if (errorPeriodo()) { <p class="error-campo" role="alert">{{ errorPeriodo() }}</p> }
            </div>
            <table class="umbrales">
              <thead><tr><th>Nivel</th><th>Desde (pts)</th><th>Hasta (pts)</th></tr></thead>
              <tbody>
                @for (umbral of formulario.niveles; track umbral.nivel; let i = $index) {
                  <tr [attr.data-nivel]="umbral.nivel">
                    <th scope="row"><span class="insignia">{{ nombres[umbral.nivel] }}</span></th>
                    <td>
                      <input [id]="'minimo-' + umbral.nivel" [name]="'minimo-' + umbral.nivel" type="number" min="0" step="1"
                        [attr.aria-label]="'Mínimo de ' + nombres[umbral.nivel]" [(ngModel)]="umbral.puntosMinimos"
                        (ngModelChange)="revisar()" [attr.aria-invalid]="!!errores()[umbral.nivel]" />
                    </td>
                    <td>
                      @if (umbral.nivel === 'ORO') {
                        <span class="sin-maximo">Sin máximo</span>
                      } @else {
                        <input [id]="'maximo-' + umbral.nivel" [name]="'maximo-' + umbral.nivel" type="number" min="0" step="1"
                          [attr.aria-label]="'Máximo de ' + nombres[umbral.nivel]" [(ngModel)]="umbral.puntosMaximos"
                          (ngModelChange)="revisar()" [attr.aria-invalid]="!!errores()[umbral.nivel]" />
                      }
                    </td>
                  </tr>
                  @if (errores()[umbral.nivel]; as mensaje) {
                    <tr class="fila-error"><td colspan="3"><p class="error-campo" role="alert">{{ mensaje }}</p></td></tr>
                  }
                }
              </tbody>
            </table>
            <label for="motivo-niveles">Motivo del cambio (opcional)</label>
            <textarea id="motivo-niveles" name="motivo" maxlength="250" rows="2" [(ngModel)]="formulario.motivo"></textarea>
            <p class="ayuda">Los rangos incluyen sus límites y deben ser continuos: si Bronce llega hasta 499, Plata empieza en 500.
              Al guardar, todos los clientes se reclasifican con los nuevos umbrales.</p>
            @if (errorFormulario()) { <p class="error" role="alert">{{ errorFormulario() }}</p> }
            <div class="acciones">
              <button type="submit">{{ guardando() ? 'Guardando…' : 'Guardar niveles' }}</button>
              <button type="button" class="secundario" (click)="cancelar()">Cancelar</button>
            </div>
          </fieldset>
        </form>
      </section>
    }

    <section class="tarjeta" aria-labelledby="titulo-historial">
      <h2 id="titulo-historial">Historial de niveles</h2>
      @if (errorHistorial()) { <p class="error" role="alert">{{ errorHistorial() }}</p> }
      @if (cargandoHistorial()) { <p role="status">Cargando historial…</p> }
      @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Versión</th><th>Vigente desde</th><th>Cambiada por</th><th>Periodo</th><th>Bronce</th><th>Plata</th><th>Oro</th><th>Motivo</th></tr></thead>
            <tbody>
              @for (config of historial(); track config.version) {
                <tr>
                  <td>v{{ config.version }}</td>
                  <td>{{ config.vigenteDesde | date: 'dd/MM/yyyy HH:mm' }}</td>
                  <td>{{ config.modificadoPor }}</td>
                  <td>{{ config.periodoMeses | number }} meses</td>
                  @for (umbral of config.umbrales; track umbral.nivel) { <td>{{ rango(umbral) }}</td> }
                  <td>{{ config.motivo || '—' }}</td>
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
    .cabecera .acciones { margin-top: 1.5rem; flex-shrink: 0; }
    .eyebrow { color: var(--color-primario); text-transform: uppercase; letter-spacing: .08em; font-size: .72rem; font-weight: 700; margin: 0 0 .5rem; }
    .niveles { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; margin-bottom: .5rem; }
    .nivel { display: flex; flex-direction: column; gap: .3rem; padding: 1rem 1.25rem; border-top: 4px solid var(--color-nivel); }
    .nivel strong { font-size: 1.35rem; }
    .nivel small, .pie { color: var(--color-texto-suave); font-size: .82rem; }
    .pie { margin: 0 0 1.25rem; }
    [data-nivel='BRONCE'] { --color-nivel: #b0703a; }
    [data-nivel='PLATA'] { --color-nivel: #8a94a6; }
    [data-nivel='ORO'] { --color-nivel: #c9a227; }
    .insignia { align-self: flex-start; padding: .15rem .6rem; border-radius: 999px; font-size: .78rem; font-weight: 700; color: #fff; background: var(--color-nivel); }
    .editor { border-top: 3px solid var(--color-primario); margin-bottom: 1.25rem; }
    h2 { margin: 0 0 1rem; }
    fieldset { padding: 0; margin: 0; border: 0; min-width: 0; }
    .periodo { max-width: 320px; margin-bottom: 1rem; }
    .periodo small { display: block; color: var(--color-texto-suave); font-size: .8rem; margin-top: .25rem; }
    .umbrales { margin-bottom: 1rem; }
    .umbrales input { max-width: 160px; }
    .sin-maximo { color: var(--color-texto-suave); font-size: .85rem; }
    .fila-error td { padding-top: 0; border-top: 0; }
    .error-campo { color: var(--color-error, #b42318); font-size: .82rem; margin: .3rem 0 0; }
    input[aria-invalid='true'] { border-color: var(--color-error, #b42318); }
    .ayuda { color: var(--color-texto-suave); font-size: .82rem; background: var(--color-superficie-suave); padding: .8rem; border-radius: 6px; }
    .acciones { display: flex; flex-wrap: wrap; gap: .5rem; }
    .tabla { overflow-x: auto; }
    .tabla table { min-width: 760px; }
    @media (max-width: 720px) {
      .cabecera { flex-direction: column; } .cabecera .acciones { margin: 0; }
      .niveles { grid-template-columns: 1fr; }
    }
  `,
})
export class NivelesComponent {
  private readonly api = inject(NivelesService);
  readonly nombres = NOMBRES_NIVEL;
  readonly rango = rangoTexto;

  readonly vigente = signal<ConfiguracionNiveles | null>(null);
  readonly historial = signal<ConfiguracionNiveles[]>([]);
  readonly cargando = signal(false);
  readonly cargandoHistorial = signal(false);
  readonly guardando = signal(false);
  readonly recalculando = signal(false);
  readonly abierto = signal(false);
  readonly error = signal('');
  readonly exito = signal('');
  readonly errorHistorial = signal('');
  readonly errorFormulario = signal('');
  readonly errorPeriodo = signal('');
  readonly errores = signal<Partial<Record<Nivel, string>>>({});

  formulario: SolicitudNiveles = { periodoMeses: 12, niveles: [], motivo: '', version: 0 };

  constructor() {
    this.cargar();
  }

  cargar() {
    this.cargando.set(true);
    this.error.set('');
    this.api.vigente().pipe(finalize(() => this.cargando.set(false))).subscribe({
      next: config => this.vigente.set(config),
      error: e => this.error.set(this.mensaje(e)),
    });
    this.cargarHistorial();
  }

  editar() {
    const config = this.vigente();
    if (!config) return;
    this.formulario = {
      periodoMeses: config.periodoMeses,
      niveles: NIVELES.map(nivel => {
        const umbral = config.umbrales.find(u => u.nivel === nivel);
        return { nivel, puntosMinimos: umbral?.puntosMinimos ?? null, puntosMaximos: umbral?.puntosMaximos ?? null };
      }),
      motivo: '',
      version: config.version,
    };
    this.errores.set({});
    this.errorPeriodo.set('');
    this.errorFormulario.set('');
    this.exito.set('');
    this.abierto.set(true);
  }

  cancelar() {
    this.abierto.set(false);
  }

  revisar() {
    this.errores.set(conflictosUmbrales(this.formulario.niveles));
  }

  guardar() {
    if (this.guardando()) return;
    this.errorFormulario.set('');
    const periodo = this.formulario.periodoMeses;
    this.errorPeriodo.set(periodo === null || !Number.isInteger(periodo) || periodo < 1 || periodo > 120
      ? 'Indique un periodo entero entre 1 y 120 meses.' : '');
    this.revisar();
    if (this.errorPeriodo() || Object.keys(this.errores()).length) {
      this.errorFormulario.set('Revise los umbrales marcados: no pueden superponerse ni dejar huecos.');
      return;
    }
    this.guardando.set(true);
    const solicitud: SolicitudNiveles = {
      ...this.formulario,
      motivo: this.formulario.motivo.trim(),
      niveles: this.formulario.niveles.map(u => ({ ...u, puntosMaximos: u.nivel === 'ORO' ? null : u.puntosMaximos })),
    };
    this.api.guardar(solicitud).pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: resultado => {
        this.vigente.set(resultado.configuracion);
        this.abierto.set(false);
        this.exito.set(`Niveles guardados como versión ${resultado.configuracion.version}. `
          + `${resultado.clientesReclasificados} cliente(s) cambiaron de nivel.`);
        this.cargarHistorial();
      },
      error: (e: HttpErrorResponse) => {
        this.errores.set(this.erroresDelServidor(e));
        this.errorFormulario.set(this.mensaje(e));
      },
    });
  }

  recalcular() {
    this.recalculando.set(true);
    this.error.set('');
    this.exito.set('');
    this.api.recalcular().pipe(finalize(() => this.recalculando.set(false))).subscribe({
      next: r => this.exito.set(`Reclasificación completa: ${r.clientesReclasificados} cliente(s) cambiaron de nivel.`),
      error: e => this.error.set(this.mensaje(e)),
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

  private erroresDelServidor(e: HttpErrorResponse): Partial<Record<Nivel, string>> {
    const campos = (e.error?.campos ?? {}) as Record<string, string>;
    const errores: Partial<Record<Nivel, string>> = {};
    for (const [campo, motivo] of Object.entries(campos)) {
      const nivel = NIVELES.find(n => campo.startsWith(n));
      if (nivel) errores[nivel] = motivo.charAt(0).toUpperCase() + motivo.slice(1) + '.';
    }
    return errores;
  }

  private mensaje(e: HttpErrorResponse) {
    if (e.status === 403) return 'Solo el Administrador de CRM puede configurar los niveles de fidelización.';
    return e.error?.mensaje ?? 'No se pudo completar la operación. Compruebe la conexión e intente de nuevo.';
  }
}
