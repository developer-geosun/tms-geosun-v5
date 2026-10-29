import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import {
  PlatformAuthState,
  PlatformAuthTokensResponse,
  PlatformLoginRequest,
  PlatformOperator
} from '../../shared/models';
import { ConfigService } from './config.service';
import { isSessionRejected } from './auth.service';

const PLATFORM_AUTH_STORAGE_KEY = 'tms_geosun_platform_auth';
const ACCESS_TOKEN_EXPIRY_SKEW_SECONDS = 30;

const EMPTY_STATE: PlatformAuthState = { accessToken: null, refreshToken: null, operator: null };

/**
 * Окрема сесія оператора платформи.
 * Не змішується з сесією співробітника: інший ключ localStorage, інші токени й endpoints.
 */
@Injectable({
  providedIn: 'root'
})
export class PlatformAuthService {
  private readonly http = inject(HttpClient);
  private readonly configService = inject(ConfigService);

  private readonly state = signal<PlatformAuthState>(this.loadInitialState_());
  private sessionEpoch = 0;
  private refreshInFlight$: Observable<string> | null = null;

  readonly operator = computed(() => this.state().operator);
  readonly accessToken = computed(() => this.state().accessToken);
  readonly isAuthenticated = computed(() => Boolean(this.state().accessToken && this.state().operator));

  login(payload: PlatformLoginRequest): Observable<PlatformOperator> {
    return this.http
      .post<PlatformAuthTokensResponse>(this.toApiUrl_('/platform/auth/login'), {
        email: payload.email.trim(),
        password: payload.password
      })
      .pipe(
        tap((response) => this.setSession_(response)),
        map((response) => response.operator)
      );
  }

  logout(): Observable<void> {
    if (!this.state().accessToken) {
      this.clearSession();
      return of(void 0);
    }
    return this.http.post<unknown>(this.toApiUrl_('/platform/auth/logout'), null).pipe(
      map(() => void 0),
      catchError(() => of(void 0)),
      tap(() => this.clearSession())
    );
  }

  /**
   * Перевірка сесії для guard: за потреби оновлює токен, далі підтягує оператора.
   * Сесію скидаємо лише при явній відмові сервера (401/403).
   */
  verifySession(): Observable<boolean> {
    const { accessToken, refreshToken } = this.state();
    if (!accessToken && !refreshToken) {
      return of(false);
    }

    const ensureAccess$ =
      accessToken && !this.isAccessTokenExpired_() ? of(accessToken) : this.refreshAccessToken();

    return ensureAccess$.pipe(
      switchMap(() => this.http.get<PlatformOperator>(this.toApiUrl_('/platform/auth/me'))),
      tap((operator) => this.state.update((current) => ({ ...current, operator }))),
      tap(() => this.persist_()),
      map(() => true),
      catchError((error: unknown) => {
        if (isSessionRejected(error)) {
          this.clearSession();
          return of(false);
        }
        // Мережева помилка / 5xx: лишаємо сесію, якщо вже є оператор у сховищі
        return of(this.isAuthenticated());
      })
    );
  }

  refreshAccessToken(): Observable<string> {
    const refreshToken = this.state().refreshToken;
    if (!refreshToken) {
      return throwError(() => new Error('Platform refresh token is missing'));
    }
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }

    const epoch = this.sessionEpoch;
    this.refreshInFlight$ = this.http
      .post<PlatformAuthTokensResponse>(this.toApiUrl_('/platform/auth/refresh'), { refreshToken })
      .pipe(
        tap((response) => {
          // Сесію вже скинули, поки запит був у дорозі — пізню відповідь не застосовуємо
          if (epoch !== this.sessionEpoch) {
            return;
          }
          this.setSession_(response);
        }),
        map((response) => response.accessToken),
        catchError((error: unknown) => {
          if (isSessionRejected(error)) {
            this.clearSession();
          }
          return throwError(() => error);
        }),
        finalize(() => {
          this.refreshInFlight$ = null;
        }),
        shareReplay({ refCount: false, bufferSize: 1 })
      );

    return this.refreshInFlight$;
  }

  clearSession(): void {
    this.sessionEpoch += 1;
    this.state.set({ ...EMPTY_STATE });
    this.persist_();
  }

  private setSession_(response: PlatformAuthTokensResponse): void {
    this.state.set({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      operator: response.operator
    });
    this.persist_();
  }

  private toApiUrl_(path: string): string {
    return `${this.configService.apiUrl}/api/v1${path}`;
  }

  private isAccessTokenExpired_(): boolean {
    const token = this.state().accessToken;
    if (!token) {
      return true;
    }
    const exp = decodeJwtExp_(token);
    if (exp === null) {
      return false;
    }
    return exp <= Math.floor(Date.now() / 1000) + ACCESS_TOKEN_EXPIRY_SKEW_SECONDS;
  }

  private loadInitialState_(): PlatformAuthState {
    if (typeof window === 'undefined') {
      return { ...EMPTY_STATE };
    }
    const raw = window.localStorage.getItem(PLATFORM_AUTH_STORAGE_KEY);
    if (!raw) {
      return { ...EMPTY_STATE };
    }
    try {
      const parsed = JSON.parse(raw) as PlatformAuthState;
      return {
        accessToken: parsed.accessToken ?? null,
        refreshToken: parsed.refreshToken ?? null,
        operator: parsed.operator ?? null
      };
    } catch {
      return { ...EMPTY_STATE };
    }
  }

  private persist_(): void {
    if (typeof window === 'undefined') {
      return;
    }
    window.localStorage.setItem(PLATFORM_AUTH_STORAGE_KEY, JSON.stringify(this.state()));
  }
}

function decodeJwtExp_(token: string): number | null {
  const parts = token.split('.');
  if (parts.length < 2) {
    return null;
  }
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const payload = JSON.parse(atob(padded)) as { exp?: unknown };
    return typeof payload.exp === 'number' ? payload.exp : null;
  } catch {
    return null;
  }
}
