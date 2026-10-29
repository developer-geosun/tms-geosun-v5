import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ConfigService } from './config.service';
import { PlatformAuthService } from './platform-auth.service';

const PLATFORM_STORAGE_KEY = 'tms_geosun_platform_auth';
const EMPLOYEE_STORAGE_KEY = 'tms_geosun_auth';

describe('PlatformAuthService', () => {
  let service: PlatformAuthService;
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
    service = TestBed.inject(PlatformAuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('stores operator session separately from employee session', () => {
    service.login({ email: 'op@example.com', password: 'password123' }).subscribe();

    const request = httpMock.expectOne('http://localhost:8080/api/v1/platform/auth/login');
    expect(request.request.method).toBe('POST');
    request.flush({
      accessToken: 'p-access',
      refreshToken: 'p-refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
      operator: { id: 'op1', email: 'op@example.com' }
    });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.accessToken()).toBe('p-access');
    expect(localStorage.getItem(PLATFORM_STORAGE_KEY)).toContain('p-refresh');
    expect(localStorage.getItem(EMPLOYEE_STORAGE_KEY)).toBeNull();
  });

  it('clears session when platform refresh is rejected with 401', () => {
    service.login({ email: 'op@example.com', password: 'password123' }).subscribe();
    httpMock.expectOne('http://localhost:8080/api/v1/platform/auth/login').flush({
      accessToken: 'p-access',
      refreshToken: 'p-refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
      operator: { id: 'op1', email: 'op@example.com' }
    });

    service.refreshAccessToken().subscribe({ error: () => undefined });
    httpMock
      .expectOne('http://localhost:8080/api/v1/platform/auth/refresh')
      .flush({ message: 'invalid' }, { status: 401, statusText: 'Unauthorized' });

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.accessToken()).toBeNull();
  });
});
