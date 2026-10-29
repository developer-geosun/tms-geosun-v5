import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { RegisterComponent } from './register.component';
import { AuthService } from '../../core/services/auth.service';

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;
  let navigateSpy: jasmine.Spy<(commands: readonly string[]) => Promise<boolean>>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['register']);

    await TestBed.configureTestingModule({
      imports: [RegisterComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);

    const fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
  });

  it('does not call register when form is invalid', () => {
    component.submit();

    expect(authServiceSpy.register).not.toHaveBeenCalled();
  });

  it('does not call register when passwords do not match', () => {
    component.form.setValue({
      enterpriseCode: 'acme',
      email: 'user@example.com',
      password: 'password123',
      confirmPassword: 'password124'
    });

    component.submit();

    expect(authServiceSpy.register).not.toHaveBeenCalled();
    expect(component.form.controls.confirmPassword.hasError('mismatch')).toBeTrue();
    expect(component.errorCode()).toBeNull();
  });

  it('does not call register when password has no digits or letters', () => {
    component.form.setValue({
      enterpriseCode: 'acme',
      email: 'user@example.com',
      password: 'abcdefgh',
      confirmPassword: 'abcdefgh'
    });

    component.submit();

    expect(authServiceSpy.register).not.toHaveBeenCalled();
    expect(component.form.controls.password.hasError('pattern')).toBeTrue();
  });

  it('shows conflict error on 409', () => {
    authServiceSpy.register.and.returnValue(throwError(() => ({ status: 409 })));
    component.form.setValue({
      enterpriseCode: 'acme',
      email: 'user@example.com',
      password: 'password123',
      confirmPassword: 'password123'
    });

    component.submit();

    expect(component.errorCode()).toBe('409');
    expect(component.isLoading()).toBeFalse();
  });

  it('shows rate limit error on 429', () => {
    authServiceSpy.register.and.returnValue(throwError(() => ({ status: 429 })));
    component.form.setValue({
      enterpriseCode: 'acme',
      email: 'user@example.com',
      password: 'password123',
      confirmPassword: 'password123'
    });

    component.submit();

    expect(component.errorCode()).toBe('429');
    expect(component.isLoading()).toBeFalse();
  });

  it('navigates to login after successful registration', () => {
    authServiceSpy.register.and.returnValue(
      of({
        id: 'u1',
        email: 'user@example.com',
        role: 'user'
      })
    );
    component.form.setValue({
      enterpriseCode: 'acme',
      email: 'user@example.com',
      password: 'password123',
      confirmPassword: 'password123'
    });

    component.submit();

    expect(authServiceSpy.register).toHaveBeenCalledWith({
      enterpriseCode: 'acme',
      email: 'user@example.com',
      password: 'password123'
    });
    expect(component.hasSuccess()).toBeTrue();
    expect(component.errorCode()).toBeNull();
    expect(component.isLoading()).toBeFalse();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });
});
