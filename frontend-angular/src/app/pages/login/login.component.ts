import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services';
import { ENTERPRISE_CODE_MAX_LENGTH } from '../../core/utils/enterprise-code';
import { TranslateModule } from '@ngx-translate/core';

type LoginErrorCode =
  | '401'
  | '403'
  | 'account_disabled'
  | 'user_deleted'
  | 'email_not_verified'
  | 'enterprise_suspended'
  | 'generic';

type ResendState = 'idle' | 'loading' | 'success' | 'error';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslateModule
  ]
})
export class LoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly enterpriseCodeMaxLength = ENTERPRISE_CODE_MAX_LENGTH;

  readonly isLoading = signal(false);
  readonly hasSuccess = signal(false);
  readonly errorCode = signal<LoginErrorCode | null>(null);
  readonly resendState = signal<ResendState>('idle');
  readonly isPasswordVisible = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    enterpriseCode: ['', [Validators.required, Validators.maxLength(ENTERPRISE_CODE_MAX_LENGTH)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  submit(): void {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorCode.set(null);
    this.hasSuccess.set(false);
    this.resendState.set('idle');
    this.isLoading.set(true);

    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.hasSuccess.set(true);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        const fallback = this.authService.hasAnyRole(['user']) ? '/routes' : '/main';
        const target =
          returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : fallback;
        void this.router.navigateByUrl(target);
      },
      error: (error: { status?: number; error?: { code?: string } }) => {
        this.isLoading.set(false);
        this.errorCode.set(this.mapErrorCode(error));
      }
    });
  }

  /** Повторна відправка листа підтвердження (код підприємства + email з форми входу). */
  resendVerification(): void {
    const { enterpriseCode, email } = this.form.getRawValue();
    if (this.resendState() === 'loading' || !enterpriseCode.trim() || !email.trim()) {
      this.form.controls.enterpriseCode.markAsTouched();
      this.form.controls.email.markAsTouched();
      return;
    }

    this.resendState.set('loading');
    this.authService.resendVerification({ enterpriseCode, email: email.trim() }).subscribe({
      next: () => this.resendState.set('success'),
      error: () => this.resendState.set('error')
    });
  }

  private mapErrorCode(error: { status?: number; error?: { code?: string } }): LoginErrorCode {
    const code = error.error?.code;
    if (error.status === 403 && code === 'EMAIL_NOT_VERIFIED') {
      return 'email_not_verified';
    }
    if (error.status === 403 && code === 'ACCOUNT_DISABLED') {
      return 'account_disabled';
    }
    if (error.status === 403 && code === 'USER_DELETED') {
      return 'user_deleted';
    }
    if (error.status === 403 && code === 'ENTERPRISE_SUSPENDED') {
      return 'enterprise_suspended';
    }
    // Невірний код / email / пароль — одна й та сама відповідь без уточнення, яке поле хибне
    if (error.status === 401) {
      return '401';
    }
    if (error.status === 403) {
      return '403';
    }
    return 'generic';
  }
}
