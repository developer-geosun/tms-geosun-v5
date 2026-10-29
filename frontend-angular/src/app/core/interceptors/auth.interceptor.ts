import { HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService, isSessionRejected } from '../services/auth.service';
import { PlatformAuthService } from '../services/platform-auth.service';

/** Префікс API оператора платформи — окрема сесія, не змішується з співробітником. */
const PLATFORM_API_PATH = '/api/v1/platform/';

/**
 * Interceptor додає access token та виконує одноразовий refresh при 401.
 * URL оператора платформи обробляються окремо: токен і refresh співробітника туди не потрапляють.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (isPlatformUrl_(request.url)) {
    return handlePlatformRequest_(request, next);
  }

  const authService = inject(AuthService);
  const router = inject(Router);
  const accessToken = authService.accessToken();
  const requestWithAuth = attachAccessToken_(request, accessToken);

  return next(requestWithAuth).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthEndpoint_(request.url)) {
        return throwError(() => error);
      }

      return authService.refreshAccessToken().pipe(
        switchMap((newAccessToken) => next(attachAccessToken_(request, newAccessToken))),
        catchError((refreshError) => {
          if (isSessionRejected(refreshError)) {
            authService.clearSession();
            if (authService.sessionRestored()) {
              void router.navigate(['/login'], {
                queryParams: { returnUrl: router.url }
              });
            }
          }
          return throwError(() => refreshError);
        })
      );
    })
  );
};

/** Запити оператора: лише platform access/refresh, при відмові — на /platform/login. */
function handlePlatformRequest_(request: HttpRequest<unknown>, next: HttpHandlerFn) {
  const platformAuth = inject(PlatformAuthService);
  const router = inject(Router);
  const isAuthCall = isPlatformAuthEndpoint_(request.url);
  // login/refresh — без Bearer; logout/me — з токеном оператора
  const requestWithAuth =
    isAuthCall && !isPlatformSessionEndpoint_(request.url)
      ? request
      : attachAccessToken_(request, platformAuth.accessToken());

  return next(requestWithAuth).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthCall) {
        return throwError(() => error);
      }

      return platformAuth.refreshAccessToken().pipe(
        switchMap((newAccessToken) => next(attachAccessToken_(request, newAccessToken))),
        catchError((refreshError) => {
          if (isSessionRejected(refreshError)) {
            platformAuth.clearSession();
            void router.navigate(['/platform/login']);
          }
          return throwError(() => refreshError);
        })
      );
    })
  );
}

function attachAccessToken_(request: HttpRequest<unknown>, accessToken: string | null): HttpRequest<unknown> {
  if (!accessToken) {
    return request;
  }
  return request.clone({
    setHeaders: {
      Authorization: `Bearer ${accessToken}`
    }
  });
}

function isPlatformUrl_(url: string): boolean {
  return url.includes(PLATFORM_API_PATH);
}

function isPlatformAuthEndpoint_(url: string): boolean {
  return url.includes(`${PLATFORM_API_PATH}auth/`);
}

function isPlatformSessionEndpoint_(url: string): boolean {
  return url.includes(`${PLATFORM_API_PATH}auth/logout`) || url.includes(`${PLATFORM_API_PATH}auth/me`);
}

function isAuthEndpoint_(url: string): boolean {
  return (
    url.includes('/auth/login') ||
    url.includes('/auth/register') ||
    url.includes('/auth/refresh') ||
    url.includes('/auth/logout') ||
    url.includes('/auth/verify-email') ||
    url.includes('/auth/resend-verification') ||
    url.includes('/auth/forgot-password') ||
    url.includes('/auth/reset-password-info') ||
    url.includes('/auth/reset-password')
  );
}
