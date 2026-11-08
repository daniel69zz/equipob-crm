import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, map } from 'rxjs';
import {
  AjustePuntos,
  AjustesPuntosService,
  LARGO_MINIMO_MOTIVO,
  nuevoIdSolicitud,
  PUNTOS_MAXIMOS_AJUSTE,
  SolicitudAjuste,
} from '../fidelizacion/ajustes-puntos.service';
import { NOMBRES_NIVEL } from '../fidelizacion/niveles.service';
import { PestaniasClienteComponent } from './pestanias-cliente.component';
import { SaldoPuntosService } from './saldo-puntos.service';

type Operacion = 'SUMAR' | 'RESTAR';
type Paso = 'formulario' | 'confirmacion' | 'aplicado';

interface Formulario {
  operacion: Operacion;
  puntos: number | null;
  motivo: string;
  referencia: string;
}

/**
 * Ajuste manual del saldo de puntos de un cliente por el Administrador de CRM (SCRUM-51, SCRUM-426,
 * docs/fidelizacion/ajuste-manual-puntos.md). El ajuste se revisa antes de aplicarlo: la confirmación muestra el
 * saldo actual y el resultante, y el servicio rechaza el ajuste si el saldo cambió mientras se revisaba o si lo
 * dejaría negativo.
 */
@Component({
  selector: 'app-ajuste-puntos',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, RouterLink, PestaniasClienteComponent],
  template: `
    <app-pestanias-cliente [id]="clienteId()" />
    <header>
      <p class="eyebrow">Administración · Fidelización</p>
      <h1>Ajustar saldo de puntos</h1>
      <p class="subtitulo">Cliente {{ clienteId() }} · Corrige errores del saldo. Cada ajuste queda registrado con su motivo,
        el responsable y la fecha.</p>
    </header>

    @if (errorSaldo()) {
      <div class="error" role="alert">
        <p>{{ errorSaldo() }}</p>
        <button type="button" class="secundario" (click)="cargarSaldo()">Reintentar</button>
      </div>
    }

    @if (saldo() !== null) {
      <section class="tarjeta saldo" aria-label="Saldo disponible">
        <h2>Saldo disponible</h2>
        <strong>{{ saldo() | number: '1.0-0' }} pts</strong>
        @if (puntosCaducados()) {
          <small>{{ puntosCaducados() | number: '1.0-0' }} pts caducaron y no se pueden ajustar.</small>
        }
      </section>
    } @else if (!errorSaldo()) {
      <p role="status">Consultando el saldo…</p>
    }

    @switch (paso()) {
      @case ('formulario') {
        @if (saldo() !== null) {
          <section class="tarjeta formulario" aria-labelledby="titulo-ajuste">
            <h2 id="titulo-ajuste">Datos del ajuste</h2>
            <form (ngSubmit)="revisar()" novalidate>
              <fieldset class="operacion">
                <legend>Operación</legend>
                <label><input type="radio" name="operacion" value="SUMAR" [(ngModel)]="formulario.operacion" /> Sumar puntos</label>
                <label><input type="radio" name="operacion" value="RESTAR" [(ngModel)]="formulario.operacion" /> Restar puntos</label>
              </fieldset>

              <label for="puntos">Puntos</label>
              <input id="puntos" name="puntos" type="number" min="1" [max]="maximo" step="1" inputmode="numeric"
                [(ngModel)]="formulario.puntos" [attr.aria-invalid]="!!errores()['puntos']"
                [attr.aria-describedby]="errores()['puntos'] ? 'error-puntos' : null" />
              @if (errores()['puntos']; as mensaje) { <p id="error-puntos" class="error-campo" role="alert">{{ mensaje }}</p> }

              <label for="motivo">Motivo</label>
              <textarea id="motivo" name="motivo" rows="3" maxlength="250" [(ngModel)]="formulario.motivo"
                placeholder="Por ejemplo: la compra V-100236 del 18/10 no acumuló puntos"
                [attr.aria-invalid]="!!errores()['motivo']"
                [attr.aria-describedby]="errores()['motivo'] ? 'error-motivo' : 'ayuda-motivo'"></textarea>
              <small id="ayuda-motivo" class="ayuda">De {{ largoMinimo }} a 250 caracteres · {{ formulario.motivo.trim().length }}/250</small>
              @if (errores()['motivo']; as mensaje) { <p id="error-motivo" class="error-campo" role="alert">{{ mensaje }}</p> }

              <label for="referencia">Referencia <span class="opcional">(opcional)</span></label>
              <input id="referencia" name="referencia" maxlength="64" [(ngModel)]="formulario.referencia"
                placeholder="Transacción, ticket o caso" [attr.aria-invalid]="!!errores()['referencia']" />
              @if (errores()['referencia']; as mensaje) { <p class="error-campo" role="alert">{{ mensaje }}</p> }

              @if (errorFormulario()) { <p class="error" role="alert">{{ errorFormulario() }}</p> }
              <div class="acciones">
                <button type="submit">Revisar ajuste</button>
                <button type="button" class="secundario" [routerLink]="['/clientes', clienteId(), 'puntos']">Cancelar</button>
              </div>
            </form>
          </section>
        }
      }
      @case ('confirmacion') {
        <section class="tarjeta confirmacion" aria-labelledby="titulo-confirmacion">
          <h2 id="titulo-confirmacion">Confirme el ajuste</h2>
          <dl>
            <div><dt>Saldo actual</dt><dd>{{ solicitud()!.saldoActual | number: '1.0-0' }} pts</dd></div>
            <div><dt>Ajuste</dt>
              <dd [class.resta]="solicitud()!.puntos < 0">{{ solicitud()!.puntos > 0 ? '+' : '' }}{{ solicitud()!.puntos | number: '1.0-0' }} pts</dd></div>
            <div><dt>Saldo resultante</dt><dd><strong>{{ saldoResultante() | number: '1.0-0' }} pts</strong></dd></div>
            <div class="ancho"><dt>Motivo</dt><dd>{{ solicitud()!.motivo }}</dd></div>
            @if (solicitud()!.referencia) { <div><dt>Referencia</dt><dd>{{ solicitud()!.referencia }}</dd></div> }
          </dl>
          @if (errorFormulario()) { <p class="error" role="alert">{{ errorFormulario() }}</p> }
          <div class="acciones">
            <button type="button" [disabled]="aplicando()" (click)="confirmar()">
              {{ aplicando() ? 'Aplicando…' : 'Confirmar ajuste' }}
            </button>
            <button type="button" class="secundario" [disabled]="aplicando()" (click)="corregir()">Corregir</button>
          </div>
        </section>
      }
      @case ('aplicado') {
        @if (aplicado(); as ajuste) {
          <section class="tarjeta aplicado" role="status" aria-labelledby="titulo-aplicado">
            <h2 id="titulo-aplicado">Ajuste aplicado</h2>
            <p>El saldo pasó de <strong>{{ ajuste.saldoAnterior | number: '1.0-0' }}</strong> a
              <strong>{{ ajuste.saldoResultante | number: '1.0-0' }} pts</strong>
              @if (ajuste.nivel) { · nivel {{ nombreNivel(ajuste.nivel) }} }.</p>
            <small>Ajuste n.º {{ ajuste.id }} · {{ ajuste.usuario }} · {{ ajuste.registradoEn | date: 'dd/MM/yyyy HH:mm' }}</small>
            <div class="acciones">
              <button type="button" [routerLink]="['/clientes', clienteId(), 'puntos']">Ver puntos del cliente</button>
              <button type="button" class="secundario" (click)="otroAjuste()">Hacer otro ajuste</button>
            </div>
          </section>
        }
      }
    }
  `,
  styles: `
    .eyebrow { color: var(--color-primario); text-transform: uppercase; letter-spacing: .08em; font-size: .72rem; font-weight: 700; margin: 0 0 .5rem; }
    .saldo, .formulario, .confirmacion, .aplicado { padding: 1rem 1.25rem; margin-bottom: 1rem; }
    .saldo { display: flex; flex-direction: column; gap: .2rem; max-width: 22rem; }
    .saldo strong { font-size: 1.6rem; }
    h2 { margin: 0 0 .6rem; font-size: .85rem; text-transform: uppercase; letter-spacing: .03em; color: var(--color-texto-suave); }
    form { display: flex; flex-direction: column; gap: .35rem; max-width: 36rem; }
    form label { font-weight: 600; margin-top: .5rem; }
    .operacion { display: flex; gap: 1.25rem; border: 0; padding: 0; margin: 0; }
    .operacion legend { font-weight: 600; margin-bottom: .35rem; padding: 0; }
    .operacion label { font-weight: 400; margin: 0; display: inline-flex; gap: .35rem; align-items: center; }
    .opcional, .ayuda, small { color: var(--color-texto-suave); font-weight: 400; }
    .error-campo { color: var(--color-error, #b42318); font-size: .82rem; margin: .1rem 0 0; }
    input[aria-invalid='true'], textarea[aria-invalid='true'] { border-color: var(--color-error, #b42318); }
    .acciones { display: flex; gap: .5rem; flex-wrap: wrap; margin-top: 1rem; }
    dl { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: .75rem; margin: 0; }
    dt { color: var(--color-texto-suave); font-size: .8rem; }
    dd { margin: 0; }
    .ancho { grid-column: 1 / -1; }
    .resta { color: #b42318; }
  `,
})
export class AjustePuntosComponent {
  private readonly saldos = inject(SaldoPuntosService);
  private readonly ajustes = inject(AjustesPuntosService);

  readonly maximo = PUNTOS_MAXIMOS_AJUSTE;
  readonly largoMinimo = LARGO_MINIMO_MOTIVO;

  readonly clienteId = signal('');
  readonly saldo = signal<number | null>(null);
  readonly puntosCaducados = signal(0);
  readonly errorSaldo = signal('');
  readonly paso = signal<Paso>('formulario');
  readonly solicitud = signal<SolicitudAjuste | null>(null);
  readonly aplicado = signal<AjustePuntos | null>(null);
  readonly aplicando = signal(false);
  readonly errores = signal<Record<string, string>>({});
  readonly errorFormulario = signal('');
  readonly saldoResultante = computed(() => {
    const s = this.solicitud();
    return s ? s.saldoActual + s.puntos : 0;
  });

  formulario: Formulario = this.vacio();

  constructor() {
    inject(ActivatedRoute).paramMap.pipe(map(p => p.get('id') ?? ''), takeUntilDestroyed()).subscribe(id => {
      this.clienteId.set(id);
      this.cargarSaldo();
    });
  }

  cargarSaldo(): void {
    const numero = this.numeroCliente();
    this.errorSaldo.set('');
    this.saldo.set(null);
    if (numero === null) {
      this.errorSaldo.set('El identificador del cliente no es válido.');
      return;
    }
    this.saldos.consultar(numero, 1).subscribe({
      next: s => {
        this.saldo.set(s.saldo);
        this.puntosCaducados.set(s.puntosCaducados ?? 0);
      },
      error: (e: HttpErrorResponse) => this.errorSaldo.set(this.mensaje(e, 'No se pudo consultar el saldo del cliente.')),
    });
  }

  /** Valida en la pantalla las mismas reglas que el servicio (SCRUM-429) y pasa a la confirmación. */
  revisar(): void {
    const saldo = this.saldo();
    if (saldo === null) return;
    const errores: Record<string, string> = {};
    const puntos = this.formulario.puntos;
    const motivo = this.formulario.motivo.trim();
    const referencia = this.formulario.referencia.trim();
    if (puntos === null || !Number.isInteger(puntos) || puntos < 1) {
      errores['puntos'] = 'Indique un número entero de puntos mayor que 0.';
    } else if (puntos > PUNTOS_MAXIMOS_AJUSTE) {
      errores['puntos'] = `Un ajuste admite hasta ${PUNTOS_MAXIMOS_AJUSTE.toLocaleString('es-BO')} puntos.`;
    } else if (this.formulario.operacion === 'RESTAR' && puntos > saldo) {
      errores['puntos'] = `No puede restar más que el saldo disponible (${saldo.toLocaleString('es-BO')} pts): el saldo quedaría negativo.`;
    }
    if (motivo.length < LARGO_MINIMO_MOTIVO) {
      errores['motivo'] = `Explique el motivo en al menos ${LARGO_MINIMO_MOTIVO} caracteres.`;
    }
    if (referencia.length > 64) errores['referencia'] = 'Admite hasta 64 caracteres.';
    this.errores.set(errores);
    this.errorFormulario.set(Object.keys(errores).length ? 'Revise los campos marcados.' : '');
    if (Object.keys(errores).length) return;
    this.solicitud.set({
      idSolicitud: nuevoIdSolicitud(),
      puntos: this.formulario.operacion === 'SUMAR' ? puntos! : -puntos!,
      motivo,
      referencia: referencia || null,
      saldoActual: saldo,
    });
    this.paso.set('confirmacion');
  }

  confirmar(): void {
    const solicitud = this.solicitud();
    const numero = this.numeroCliente();
    if (!solicitud || numero === null || this.aplicando()) return;
    this.aplicando.set(true);
    this.errorFormulario.set('');
    this.ajustes.ajustar(numero, solicitud).pipe(finalize(() => this.aplicando.set(false))).subscribe({
      next: ajuste => {
        this.aplicado.set(ajuste);
        this.saldo.set(ajuste.saldoResultante);
        this.paso.set('aplicado');
      },
      error: (e: HttpErrorResponse) => this.rechazado(e),
    });
  }

  corregir(): void {
    this.errorFormulario.set('');
    this.paso.set('formulario');
  }

  otroAjuste(): void {
    this.formulario = this.vacio();
    this.solicitud.set(null);
    this.aplicado.set(null);
    this.errores.set({});
    this.errorFormulario.set('');
    this.paso.set('formulario');
  }

  nombreNivel(nivel: string): string {
    return NOMBRES_NIVEL[nivel] || nivel;
  }

  private rechazado(e: HttpErrorResponse): void {
    if (e.status === 409 && this.solicitud() && e.error?.mensaje !== 'Este ajuste ya se aplicó') {
      // El saldo cambió mientras se revisaba: se vuelve al formulario con el saldo nuevo.
      this.errorFormulario.set(this.mensaje(e, 'El saldo del cliente cambió. Revise el ajuste.'));
      this.paso.set('formulario');
      this.cargarSaldo();
      return;
    }
    const campos = (e.error?.campos ?? {}) as Record<string, string>;
    if (Object.keys(campos).length) {
      this.errores.set(Object.fromEntries(Object.entries(campos)
        .map(([campo, motivo]) => [campo, motivo.charAt(0).toUpperCase() + motivo.slice(1) + '.'])));
      this.paso.set('formulario');
    }
    this.errorFormulario.set(this.mensaje(e, 'No se pudo aplicar el ajuste. Compruebe la conexión e intente de nuevo.'));
  }

  private mensaje(e: HttpErrorResponse, porDefecto: string): string {
    if (e.status === 401) return 'La sesión ha vencido. Inicie sesión nuevamente.';
    if (e.status === 403) return 'Solo el Administrador de CRM puede ajustar el saldo de puntos.';
    return e.error?.mensaje ?? porDefecto;
  }

  private numeroCliente(): number | null {
    const id = this.clienteId();
    const numero = Number(id);
    return /^\d+$/.test(id) && Number.isSafeInteger(numero) && numero > 0 ? numero : null;
  }

  private vacio(): Formulario {
    return { operacion: 'SUMAR', puntos: null, motivo: '', referencia: '' };
  }
}
