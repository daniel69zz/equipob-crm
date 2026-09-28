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
    <main class="contenedor">
      <form class="tarjeta" [formGroup]="formulario" (ngSubmit)="ingresar()">
        <h1>CRM MaxiConecta</h1>
        <p class="subtitulo">Inicie sesión con su usuario interno</p>

        <div class="campo">
          <label for="usuario">Usuario</label>
          <input id="usuario" formControlName="usuario" autocomplete="username" />
          @if (formulario.controls.usuario.touched && formulario.controls.usuario.invalid) {
            <small class="error">Ingrese su usuario</small>
          }
        </div>

        <div class="campo">
          <label for="password">Contraseña</label>
          <input id="password" type="password" formControlName="password" autocomplete="current-password" />
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
      </form>
    </main>
  `,
  styles: `
    .contenedor { min-height: 100vh; display: grid; place-items: center; padding: 1rem; }
    form { width: 100%; max-width: 380px; display: grid; gap: 1rem; }
    h1 { margin: 0; color: var(--color-primario); }
    .subtitulo { margin: 0; color: var(--color-texto-suave); }
    .campo small { display: block; margin-top: 0.25rem; }
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
