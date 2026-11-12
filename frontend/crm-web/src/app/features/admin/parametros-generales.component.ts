import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import {
  CambioRegistrado,
  ParametroGeneral,
  ParametrosGeneralesService,
  ServicioParametros,
} from './parametros-generales.service';

interface Grupo {
  servicio: ServicioParametros;
  titulo: string;
  efecto: string;
}

const GRUPOS: Grupo[] = [
  {
    servicio: 'comportamiento',
    titulo: 'Clientes y consumo',
    efecto: 'Al guardar se vuelve a identificar a los clientes inactivos o a clasificar el nivel de consumo de toda la '
      + 'cartera con el valor nuevo.',
  },
  {
    servicio: 'fidelizacion',
    titulo: 'Programa de fidelización',
    efecto: 'Las consultas de saldo usan la vigencia nueva desde que se guarda, y la caducidad diaria siguiente vence '
      + 'los puntos con ella.',
  },
];

/**
 * Parámetros generales del CRM (SCRUM-65, SCRUM-516): el Administrador de CRM consulta y cambia el periodo de
 * inactividad, los umbrales del nivel de consumo y la vigencia de los puntos sin cambiar el código. Cada grupo se
 * guarda junto (todo o nada), se valida el rango antes de enviar y el historial muestra el valor anterior, el nuevo,
 * quién y cuándo.
 */
@Component({
  selector: 'app-parametros-generales',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe],
  template: `
    <header>
      <p class="eyebrow">Administración</p>
      <h1>Parámetros generales</h1>
      <p class="subtitulo">Ajuste cómo el CRM identifica a los clientes inactivos, clasifica el consumo y hace caducar los
        puntos, sin cambiar el código ni reiniciar los servicios.</p>
    </header>

    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (exito()) { <p class="exito" role="status">{{ exito() }}</p> }
    @if (cargando()) { <p role="status">Cargando parámetros…</p> }

    @for (grupo of grupos; track grupo.servicio) {
      @if (parametros()[grupo.servicio]; as lista) {
        <section class="tarjeta grupo" [attr.aria-labelledby]="'titulo-' + grupo.servicio">
          <h2 [id]="'titulo-' + grupo.servicio">{{ grupo.titulo }}</h2>
          <form (ngSubmit)="guardar(grupo)" novalidate>
            <fieldset [disabled]="guardando() === grupo.servicio">
              <div class="campos">
                @for (p of lista; track p.codigo) {
                  <div class="campo">
                    <label [for]="p.codigo">{{ p.nombre }} ({{ p.unidad }})</label>
                    <input [id]="p.codigo" [name]="p.codigo" type="number" [min]="p.minimo" [max]="p.maximo"
                      [step]="p.decimales ? 0.01 : 1" [(ngModel)]="valores[p.codigo]" (ngModelChange)="limpiar(p.codigo)"
                      [attr.aria-invalid]="!!errores()[p.codigo]" [attr.aria-describedby]="'ayuda-' + p.codigo" />
                    <small [id]="'ayuda-' + p.codigo">{{ p.descripcion }} Entre {{ p.minimo | number: formato(p) }} y
                      {{ p.maximo | number: formato(p) }} {{ p.unidad }}.</small>
                    <small class="vigente">Vigente: {{ p.valor | number: formato(p) }} {{ p.unidad }} · versión {{ p.version }}
                      · {{ p.modificadoPor }}, {{ p.modificadoEn | date: 'dd/MM/yyyy HH:mm' }}</small>
                    @if (errores()[p.codigo]; as mensaje) { <p class="error-campo" role="alert">{{ mensaje }}</p> }
                  </div>
                }
              </div>
              <p class="ayuda">{{ grupo.efecto }}</p>
              <div class="acciones">
                <button type="submit">{{ guardando() === grupo.servicio ? 'Guardando…' : 'Guardar cambios' }}</button>
                <button type="button" class="secundario" (click)="descartar(grupo.servicio)">Descartar</button>
              </div>
            </fieldset>
          </form>
        </section>
      }
    }

    <section class="tarjeta" aria-labelledby="titulo-historial">
      <h2 id="titulo-historial">Historial de cambios</h2>
      @if (errorHistorial()) { <p class="error" role="alert">{{ errorHistorial() }}</p> }
      @if (!historial().length && !errorHistorial()) { <p class="vacio">Todavía no hay cambios registrados.</p> }
      @else if (historial().length) {
        <div class="tabla">
          <table>
            <thead><tr><th>Fecha</th><th>Usuario</th><th>Parámetro</th><th>Valor anterior</th><th>Valor nuevo</th></tr></thead>
            <tbody>
              @for (cambio of historial(); track $index) {
                <tr>
                  <td>{{ cambio.fecha | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                  <td>{{ cambio.usuario }}</td>
                  <td>{{ cambio.nombre }}</td>
                  <td>{{ cambio.valorAnterior }} {{ cambio.unidad }}</td>
                  <td><strong>{{ cambio.valorNuevo }} {{ cambio.unidad }}</strong></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: `
    .eyebrow { color: var(--color-primario); text-transform: uppercase; letter-spacing: .08em; font-size: .72rem; font-weight: 700; margin: 0 0 .5rem; }
    .grupo { border-top: 3px solid var(--color-primario); margin-bottom: 1.25rem; }
    h2 { margin: 0 0 1rem; }
    fieldset { padding: 0; margin: 0; border: 0; min-width: 0; }
    .campos { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1.25rem; }
    .campo small { display: block; color: var(--color-texto-suave); font-size: .8rem; margin-top: .3rem; }
    .campo .vigente { color: var(--color-texto); }
    .error-campo { color: var(--color-error, #b42318); font-size: .82rem; margin: .3rem 0 0; }
    input[aria-invalid='true'] { border-color: var(--color-error, #b42318); }
    .ayuda { color: var(--color-texto-suave); font-size: .82rem; background: var(--color-superficie-suave); padding: .8rem; border-radius: 6px; }
    .acciones { display: flex; flex-wrap: wrap; gap: .5rem; }
    .tabla { overflow-x: auto; }
    table { min-width: 600px; }
    .vacio { padding: 1.5rem 1rem; text-align: center; color: var(--color-texto-suave); }
    @media (max-width: 720px) {
      .tabla { overflow: visible; } table { min-width: 0; }
      thead { display: none; } tbody, tr, td { display: block; }
      tbody tr { padding: .8rem 0; border-bottom: 1px solid var(--color-borde); }
      td { border: 0; padding: .2rem 0; }
    }
  `,
})
export class ParametrosGeneralesComponent {
  private readonly api = inject(ParametrosGeneralesService);

  readonly grupos = GRUPOS;
  readonly parametros = signal<Partial<Record<ServicioParametros, ParametroGeneral[]>>>({});
  readonly historial = signal<CambioRegistrado[]>([]);
  readonly cargando = signal(false);
  readonly guardando = signal<ServicioParametros | null>(null);
  readonly error = signal('');
  readonly exito = signal('');
  readonly errorHistorial = signal('');
  readonly errores = signal<Record<string, string>>({});
  valores: Record<string, number | null> = {};

  constructor() {
    this.cargar();
  }

  formato(p: ParametroGeneral) {
    return p.decimales ? '1.2-2' : '1.0-0';
  }

  limpiar(codigo: string) {
    this.errores.update(errores => ({ ...errores, [codigo]: '' }));
  }

  descartar(servicio: ServicioParametros) {
    for (const p of this.parametros()[servicio] ?? []) {
      this.valores[p.codigo] = p.valor;
      this.limpiar(p.codigo);
    }
  }

  guardar(grupo: Grupo) {
    const lista = this.parametros()[grupo.servicio] ?? [];
    this.exito.set('');
    this.error.set('');
    const errores = this.validar(lista);
    this.errores.update(actuales => ({ ...actuales, ...errores }));
    if (Object.values(errores).some(Boolean)) {
      this.error.set('Revise los valores marcados.');
      return;
    }
    const cambios = lista.filter(p => Number(this.valores[p.codigo]) !== p.valor)
      .map(p => ({ codigo: p.codigo, valor: Number(this.valores[p.codigo]), version: p.version }));
    if (!cambios.length) {
      this.error.set('No cambió ningún valor de ' + grupo.titulo.toLowerCase() + '.');
      return;
    }
    this.guardando.set(grupo.servicio);
    this.api.guardar(grupo.servicio, cambios).pipe(finalize(() => this.guardando.set(null))).subscribe({
      next: actualizados => {
        this.mostrar(grupo.servicio, actualizados);
        const nombres = lista.filter(p => cambios.some(c => c.codigo === p.codigo)).map(p => p.nombre.toLowerCase());
        this.exito.set(`Se guardó ${nombres.join(' y ')}. Los cálculos siguientes ya usan el valor nuevo.`);
        this.cargarHistorial();
      },
      error: (e: HttpErrorResponse) => {
        const campos = (e.error?.campos ?? {}) as Record<string, string>;
        this.errores.update(actuales => ({ ...actuales,
          ...Object.fromEntries(Object.entries(campos).map(([codigo, motivo]) => [codigo, `El valor ${motivo}.`])) }));
        this.error.set(e.status === 409
          ? 'Otro usuario cambió estos parámetros mientras tanto. Se cargaron los valores actuales: revíselos y vuelva a guardar.'
          : this.mensaje(e));
        if (e.status === 409) this.recargar(grupo.servicio);
      },
    });
  }

  private cargar() {
    this.cargando.set(true);
    forkJoin({ comportamiento: this.api.listar('comportamiento'), fidelizacion: this.api.listar('fidelizacion') })
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: datos => {
          this.mostrar('comportamiento', datos.comportamiento);
          this.mostrar('fidelizacion', datos.fidelizacion);
        },
        error: e => this.error.set(this.mensaje(e)),
      });
    this.cargarHistorial();
  }

  private recargar(servicio: ServicioParametros) {
    this.api.listar(servicio).subscribe({ next: lista => this.mostrar(servicio, lista) });
  }

  private mostrar(servicio: ServicioParametros, lista: ParametroGeneral[]) {
    this.parametros.update(actuales => ({ ...actuales, [servicio]: lista }));
    for (const p of lista) this.valores[p.codigo] = p.valor;
  }

  /** Une el historial de los dos servicios, del cambio más reciente al más antiguo. */
  private cargarHistorial() {
    this.errorHistorial.set('');
    forkJoin([this.api.historial('comportamiento'), this.api.historial('fidelizacion')]).subscribe({
      next: ([a, b]) => this.historial.set([...a, ...b].sort((x, y) => Date.parse(y.fecha) - Date.parse(x.fecha))),
      error: e => this.errorHistorial.set(this.mensaje(e)),
    });
  }

  /** El mismo rango que exige el servicio, y el umbral alto mayor que el medio. */
  private validar(lista: ParametroGeneral[]): Record<string, string> {
    const errores: Record<string, string> = {};
    for (const p of lista) {
      const valor = this.valores[p.codigo];
      if (valor === null || valor === undefined || (valor as unknown) === '') {
        errores[p.codigo] = 'Indique un valor.';
      } else if (valor < p.minimo || valor > p.maximo) {
        errores[p.codigo] = `Debe estar entre ${p.minimo} y ${p.maximo} ${p.unidad}.`;
      } else if (!conDecimales(valor, p.decimales)) {
        errores[p.codigo] = p.decimales ? `Use como máximo ${p.decimales} decimales.` : 'Use un número entero.';
      }
    }
    const medio = this.valores['NIVEL_CONSUMO_UMBRAL_MEDIO'];
    const alto = this.valores['NIVEL_CONSUMO_UMBRAL_ALTO'];
    if (lista.some(p => p.codigo === 'NIVEL_CONSUMO_UMBRAL_ALTO') && !errores['NIVEL_CONSUMO_UMBRAL_ALTO']
      && medio != null && alto != null && Number(alto) <= Number(medio)) {
      errores['NIVEL_CONSUMO_UMBRAL_ALTO'] = 'Debe ser mayor que el umbral de consumo medio.';
    }
    return errores;
  }

  private mensaje(e: HttpErrorResponse) {
    if (e.status === 403) return 'Solo el Administrador de CRM puede configurar los parámetros generales.';
    return e.error?.mensaje ?? 'No se pudo completar la operación. Compruebe la conexión e intente de nuevo.';
  }
}

function conDecimales(valor: number, decimales: number) {
  const factor = 10 ** decimales;
  return Math.abs(Math.round(valor * factor) - valor * factor) < 1e-6;
}
