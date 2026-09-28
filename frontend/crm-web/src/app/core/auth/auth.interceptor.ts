import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Agrega el token a las peticiones al API Gateway y cierra la sesión
 * si el Gateway responde 401 (token ausente, inválido o vencido).
 */
export const authInterceptor: HttpInterceptorFn = (solicitud, siguiente) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const esLogin = solicitud.url.endsWith('/api/auth/login');

  const conToken =
    token && solicitud.url.startsWith('/api') && !esLogin
      ? solicitud.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : solicitud;

  return siguiente(conToken).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !esLogin) {
        auth.cerrarSesion();
      }
      return throwError(() => error);
    }),
  );
};
