import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { PlatformAuthService } from '../services/platform-auth.service';

/** Кабінет оператора платформи: без сесії оператора — на /platform/login. */
export const platformAuthGuard: CanActivateFn = () => {
  const platformAuth = inject(PlatformAuthService);
  const router = inject(Router);

  return platformAuth
    .verifySession()
    .pipe(map((isValid) => (isValid ? true : router.createUrlTree(['/platform/login']))));
};

/** Сторінка входу оператора: якщо сесія вже є — одразу в кабінет. */
export const platformGuestGuard: CanActivateFn = () => {
  const platformAuth = inject(PlatformAuthService);
  const router = inject(Router);

  return platformAuth
    .verifySession()
    .pipe(map((isValid) => (isValid ? router.createUrlTree(['/platform/enterprises']) : true)));
};
