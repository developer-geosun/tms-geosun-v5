import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { PlatformAuthService } from '../services/platform-auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  const routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate'], { url: '/main' });

  const platformRefreshSpy = jasmine.createSpy('platformRefresh');
  const platformClearSpy = jasmine.createSpy('platformClear');

  beforeEach(() => {
    routerSpy.navigate.calls.reset();
    platformRefreshSpy.calls.reset();
    platformRefreshSpy.and.returnValue(of('new-platform-token'));
    platformClearSpy.calls.reset();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            accessToken: () => 'access-token',
            refreshAccessToken: () => of('new-access-token'),
            clearSession: jasmine.createSpy('clearSession'),
            sessionRestored: () => true
          }
        },
        {
          provide: PlatformAuthService,
          useValue: {
            accessToken: () => 'platform-access-token',
            refreshAccessToken: platformRefreshSpy,
            clearSession: platformClearSpy
          }
        },
        { provide: Router, useValue: routerSpy }
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('adds bearer token to outgoing request', () => {
    http.get('/data').subscribe();
    const request = httpMock.expectOne('/data');
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-token');
    request.flush({ ok: true });
  });

  it('clears session and redirects when refresh fails with 401', () => {
    const authService = TestBed.inject(AuthService) as unknown as {
      refreshAccessToken: () => ReturnType<typeof throwError>;
      clearSession: jasmine.Spy;
    };
    authService.refreshAccessToken = () =>
      throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }));

    http.get('/secure').subscribe({
      error: () => undefined
    });
    const request = httpMock.expectOne('/secure');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authService.clearSession).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/main' }
    });
  });

  it('does not clear session when refresh fails with 429', () => {
    const authService = TestBed.inject(AuthService) as unknown as {
      refreshAccessToken: () => ReturnType<typeof throwError>;
      clearSession: jasmine.Spy;
    };
    authService.refreshAccessToken = () =>
      throwError(() => new HttpErrorResponse({ status: 429, statusText: 'Too Many Requests' }));

    http.get('/secure').subscribe({
      error: () => undefined
    });
    const request = httpMock.expectOne('/secure');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authService.clearSession).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('attaches platform token (not employee token) to platform URLs', () => {
    http.get('http://api/api/v1/platform/enterprises').subscribe();
    const request = httpMock.expectOne('http://api/api/v1/platform/enterprises');
    expect(request.request.headers.get('Authorization')).toBe('Bearer platform-access-token');
    request.flush([]);
  });

  it('does not use employee refresh for platform URLs on 401', () => {
    const authService = TestBed.inject(AuthService) as unknown as {
      refreshAccessToken: () => unknown;
    };
    const employeeRefreshSpy = jasmine.createSpy('employeeRefresh');
    authService.refreshAccessToken = employeeRefreshSpy as unknown as () => ReturnType<typeof of>;

    http.get('http://api/api/v1/platform/enterprises').subscribe({ error: () => undefined });
    httpMock
      .expectOne('http://api/api/v1/platform/enterprises')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    const retried = httpMock.expectOne('http://api/api/v1/platform/enterprises');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer new-platform-token');
    retried.flush([]);

    expect(employeeRefreshSpy).not.toHaveBeenCalled();
    expect(platformRefreshSpy).toHaveBeenCalledTimes(1);
  });

  it('redirects to platform login when platform refresh is rejected', () => {
    platformRefreshSpy.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }))
    );

    http.get('http://api/api/v1/platform/enterprises').subscribe({ error: () => undefined });
    httpMock
      .expectOne('http://api/api/v1/platform/enterprises')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(platformClearSpy).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/platform/login']);
  });

  it('does not attach a token to platform login request', () => {
    http.post('http://api/api/v1/platform/auth/login', {}).subscribe({ error: () => undefined });
    const request = httpMock.expectOne('http://api/api/v1/platform/auth/login');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(platformRefreshSpy).not.toHaveBeenCalled();
  });
});
