import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <main class="pantalla">
      <section class="presentacion">
        <div class="marca">
          <span class="logo">MC</span>
          <span><strong>MaxiConecta</strong> CRM</span>
        </div>
        <div class="mensaje">
          <h1>Conozca a cada cliente en una sola ficha</h1>
          <p>
            El CRM recibe los clientes, compras y anulaciones del módulo Marketplace y Ventas, arma un perfil único
            y confiable, y calcula cómo compra cada cliente.
          </p>
          <ul>
            <li>Perfil único y auditable de cada cliente</li>
            <li>Eventos de venta sin pérdidas ni duplicados</li>
            <li>Historial de compras e indicadores de comportamiento</li>
          </ul>
        </div>
        <small class="pie">Equipo B · Ingeniería de Software · UCB</small>
      </section>

      <section class="acceso">
        <form [formGroup]="formulario" (ngSubmit)="ingresar()">
          <h2>Iniciar sesión</h2>
          <p class="subtitulo">Ingrese con su usuario interno del CRM.</p>

          <div class="campo">
            <label for="usuario">Usuario</label>
            <input id="usuario" formControlName="usuario" autocomplete="username" placeholder="usuario" />
            @if (formulario.controls.usuario.touched && formulario.controls.usuario.invalid) {
              <small class="error">Ingrese su usuario</small>
            }
          </div>

          <div class="campo">
            <label for="password">Contraseña</label>
            <input id="password" type="password" formControlName="password" autocomplete="current-password" placeholder="••••••••" />
            @if (formulario.controls.password.touched && formulario.controls.password.invalid) {
              <small class="error">Ingrese su contraseña</small>
            }
          </div>

          @if (error()) {
            <p class="error" role="alert">{{ error() }}</p>
          }

          <button type="submit" [disabled]="enviando()">
            {{ enviando() ? 'Ingresando…' : 'Ingresar' }}
          </button>
          <small class="nota">Cada usuario ve solo las funciones de su rol. Los accesos quedan auditados.</small>
        </form>
      </section>
    </main>
  `,
  styles: `
    .pantalla { min-height: 100vh; display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); }
    .presentacion {
      display: flex; flex-direction: column; justify-content: space-between; gap: 2rem;
      padding: 2.5rem 3rem; color: #dfe5f5;
      background:
        radial-gradient(circle at 20% 15%, rgba(94, 129, 244, 0.35), transparent 45%),
        radial-gradient(circle at 85% 90%, rgba(14, 165, 164, 0.28), transparent 45%),
        var(--color-barra);
    }
    .marca { display: flex; align-items: center; gap: 0.7rem; font-size: 1.05rem; color: #fff; }
    .logo {
      display: grid; place-items: center; width: 38px; height: 38px; border-radius: 10px;
      background: linear-gradient(135deg, var(--color-primario), var(--color-acento));
      font-weight: 800; font-size: 0.85rem;
    }
    .mensaje { max-width: 30rem; }
    .mensaje h1 { font-size: 2.1rem; line-height: 1.15; color: #fff; margin-bottom: 1rem; }
    .mensaje p { color: #b9c2dc; margin: 0 0 1.5rem; }
    ul { list-style: none; padding: 0; margin: 0; display: grid; gap: 0.7rem; }
    li { display: flex; align-items: center; gap: 0.65rem; color: #dfe5f5; }
    li::before {
      content: '✓'; display: grid; place-items: center; width: 22px; height: 22px; border-radius: 50%;
      background: rgba(14, 165, 164, 0.22); color: #5eead4; font-size: 0.75rem; font-weight: 700;
    }
    .pie { color: #7d88a8; }

    .acceso { display: grid; place-items: center; padding: 2rem 1.5rem; background: var(--color-superficie); }
    form { width: 100%; max-width: 360px; display: grid; gap: 1rem; }
    h2 { margin: 0; font-size: 1.5rem; }
    .subtitulo { margin: -0.5rem 0 0.25rem; }
    .campo small { display: block; margin-top: 0.3rem; }
    button { padding: 0.7rem; font-size: 0.95rem; }
    .nota { color: var(--color-texto-suave); font-size: 0.8rem; text-align: center; }

    @media (max-width: 860px) {
      .pantalla { grid-template-columns: 1fr; }
      .presentacion { padding: 1.75rem 1.5rem; }
      .mensaje h1 { font-size: 1.5rem; }
      .mensaje ul, .pie { display: none; }
    }
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly formulario = inject(NonNullableFormBuilder).group({
    usuario: ['', Validators.required],
    password: ['', Validators.required],
  });
  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);

  ingresar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    const { usuario, password } = this.formulario.getRawValue();
    this.auth.iniciarSesion(usuario, password).subscribe({
      next: () => this.router.navigate(['/']),
      error: (respuesta: HttpErrorResponse) => {
        this.enviando.set(false);
        this.error.set(
          respuesta.status === 401
            ? 'Usuario o contraseña incorrectos'
            : 'No se pudo conectar con el servidor. Intente nuevamente.',
        );
      },
    });
  }
}
