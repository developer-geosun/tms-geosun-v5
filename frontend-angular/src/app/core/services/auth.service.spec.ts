import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { ConfigService } from './config.service';

const AUTH_STORAGE_KEY = 'tms_geosun_auth';

/** JWT з exp у минулому (для тестів стартового refresh). */
const EXPIRED_ACCESS_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1MDAwMDAwMDB9.test-signature';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ConfigService,
          useValue: { apiUrl: 'http://localhost:8080', environment: { apiUrl: 'http://localhost:8080' } }
        }
      ]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('saves session after successful login', () => {
    let emittedEmail = '';
    service.login({ enterpriseCode: 'acme', email: 'user@example.com', password: 'password123' }).subscribe((user) => {
      emittedEmail = user.email;
    });

    const request = httpMock.expectOne('http://localhost:8080/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    request.flush({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: 'u1',
        email: 'user@example.com',
        role: 'user'
      }
    });

    expect(emittedEmail).toBe('user@example.com');
    expect(service.isAuthenticated()).toBeTrue();
    expect(service.accessToken()).toBe('access-token');
  });

  it('sends normalized enterprise code and keeps it on the user', () => {
    service.login({ enterpriseCode: '  ACME  ', email: 'user@example.com', password: 'password123' }).subscribe();

    const request = httpMock.expectOne('http://localhost:8080/api/v1/auth/login');
    expect(request.request.body.enterpriseCode).toBe('acme');
    request.flush({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: { id: 'u1', email: 'user@example.com', role: 'user', enterpriseCode: 'acme' }
    });

    expect(service.user()?.enterpriseCode).toBe('acme');
    expect(service.enterpriseCode()).toBe('acme');
  });

  it('sends enterprise code with resend verification', () => {
    service.resendVerification({ enterpriseCode: 'Acme', email: 'user@example.com' }).subscribe();

    const request = httpMock.expectOne('http://localhost:8080/api/v1/auth/resend-verification');
    expect(request.request.body).toEqual({ enterpriseCode: 'acme', email: 'user@example.com' });
    request.flush({ success: true, message: 'ok' });
  });

  it('clears session when refresh fails with 401', () => {
    service.login({ enterpriseCode: 'acme', email: 'user@example.com', password: 'password123' }).subscribe();
    httpMock.expectOne('http://localhost:8080/api/v1/auth/login').flush({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: 'u1',
        email: 'user@example.com',
        role: 'user'
      }
    });

    service.refreshAccessToken().subscribe({
      error: () => undefined
    });
    httpMock
      .expectOne('http://localhost:8080/api/v1/auth/refresh')
      .flush({ message: 'invalid' }, { status: 401, statusText: 'Unauthorized' });

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.accessToken()).toBeNull();
  });

  it('does not clear session when refresh fails with 429', () => {
    seedSession_();

    service.refreshAccessToken().subscribe({
      error: () => undefined
    });
    httpMock
      .expectOne('http://localhost:8080/api/v1/auth/refresh')
      .flush({ message: 'rate limit' }, { status: 429, statusText: 'Too Many Requests' });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.accessToken()).toBe('access-token');
  });

  it('does not clear session when refresh fails with 500', () => {
    seedSession_();

    service.refreshAccessToken().subscribe({
      error: () => undefined
    });
    httpMock
      .expectOne('http://localhost:8080/api/v1/auth/refresh')
      .flush({ message: 'error' }, { status: 500, statusText: 'Server Error' });

    expect(service.isAuthenticated()).toBeTrue();
  });

  it('does not restore tokens when refresh completes after clearSession', fakeAsync(() => {
    seedSession_();

    service.refreshAccessToken().subscribe({
      error: () => undefined
    });
    const refreshReq = httpMock.expectOne('http://localhost:8080/api/v1/auth/refresh');

    service.clearSession();
    expect(service.accessToken()).toBeNull();

    refreshReq.flush({
      accessToken: 'new-access',
      refreshToken: 'new-refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: { id: 'u1', email: 'user@example.com', role: 'user' }
    });
    tick();

    expect(service.accessToken()).toBeNull();
  }));

  it('performs startup refresh when access token is missing but refresh exists', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ConfigService,
          useValue: { apiUrl: 'http://localhost:8080', environment: { apiUrl: 'http://localhost:8080' } }
        }
      ]
    });

    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        accessToken: null,
        refreshToken: 'stored-refresh',
        user: { id: 'u1', email: 'user@example.com', role: 'user' }
      })
    );

    const freshService = TestBed.inject(AuthService);
    const freshHttpMock = TestBed.inject(HttpTestingController);

    freshService.verifySessionOnStartup().subscribe();

    const refreshReq = freshHttpMock.expectOne('http://localhost:8080/api/v1/auth/refresh');
    expect(refreshReq.request.body).toEqual({ refreshToken: 'stored-refresh' });
    refreshReq.flush({
      accessToken: 'new-access',
      refreshToken: 'new-refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: { id: 'u1', email: 'user@example.com', role: 'user' }
    });

    const meReq = freshHttpMock.expectOne('http://localhost:8080/api/v1/auth/me');
    meReq.flush({ id: 'u1', email: 'user@example.com', role: 'user' });

    expect(freshService.isAuthenticated()).toBeTrue();
    expect(freshService.sessionRestored()).toBeTrue();
    freshHttpMock.verify();
  });

  it('performs startup refresh when access token is expired', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ConfigService,
          useValue: { apiUrl: 'http://localhost:8080', environment: { apiUrl: 'http://localhost:8080' } }
        }
      ]
    });

    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        accessToken: EXPIRED_ACCESS_TOKEN,
        refreshToken: 'stored-refresh',
        user: { id: 'u1', email: 'user@example.com', role: 'user' }
      })
    );

    const freshService = TestBed.inject(AuthService);
    const freshHttpMock = TestBed.inject(HttpTestingController);

    freshService.verifySessionOnStartup().subscribe();

    const refreshReq = freshHttpMock.expectOne('http://localhost:8080/api/v1/auth/refresh');
    refreshReq.flush({
      accessToken: 'new-access',
      refreshToken: 'new-refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: { id: 'u1', email: 'user@example.com', role: 'user' }
    });

    const meReq = freshHttpMock.expectOne('http://localhost:8080/api/v1/auth/me');
    meReq.flush({ id: 'u1', email: 'user@example.com', role: 'user' });

    expect(freshService.isAuthenticated()).toBeTrue();
    freshHttpMock.verify();
  });

  it('does not clear session on startup network error', () => {
    seedSession_();

    service.verifySessionOnStartup().subscribe();

    const meReq = httpMock.expectOne('http://localhost:8080/api/v1/auth/me');
    meReq.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.sessionRestored()).toBeTrue();
  });

  it('clears session on startup 401 after failed refresh', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ConfigService,
          useValue: { apiUrl: 'http://localhost:8080', environment: { apiUrl: 'http://localhost:8080' } }
        }
      ]
    });

    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        accessToken: EXPIRED_ACCESS_TOKEN,
        refreshToken: 'stored-refresh',
        user: { id: 'u1', email: 'user@example.com', role: 'user' }
      })
    );

    const freshService = TestBed.inject(AuthService);
    const freshHttpMock = TestBed.inject(HttpTestingController);

    freshService.verifySessionOnStartup().subscribe();

    freshHttpMock
      .expectOne('http://localhost:8080/api/v1/auth/refresh')
      .flush({ message: 'invalid' }, { status: 401, statusText: 'Unauthorized' });

    expect(freshService.isAuthenticated()).toBeFalse();
    expect(freshService.sessionRestored()).toBeTrue();
    freshHttpMock.verify();
  });

  it('marks sessionRestored when no tokens are stored', () => {
    service.verifySessionOnStartup().subscribe();
    expect(service.sessionRestored()).toBeTrue();
  });

  it('treats API error envelope as failed login', () => {
    let status = 0;

    service.login({ enterpriseCode: 'acme', email: 'user@example.com', password: 'password123' }).subscribe({
      error: (error: { status: number }) => {
        status = error.status;
      }
    });

    httpMock.expectOne('http://localhost:8080/api/v1/auth/login').flush({
      status: 401,
      message: 'Invalid email or password'
    });

    expect(status).toBe(401);
    expect(service.isAuthenticated()).toBeFalse();
  });

  function seedSession_(): void {
    service.login({ enterpriseCode: 'acme', email: 'user@example.com', password: 'password123' }).subscribe();
    httpMock.expectOne('http://localhost:8080/api/v1/auth/login').flush({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: { id: 'u1', email: 'user@example.com', role: 'user' }
    });
  }
});
