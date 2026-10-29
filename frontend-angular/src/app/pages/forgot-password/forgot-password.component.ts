import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../core/services';
import { ENTERPRISE_CODE_MAX_LENGTH } from '../../core/utils/enterprise-code';

@Component({
  selector: 'app-forgot-password',
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.scss'],
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
export class ForgotPasswordComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  readonly isLoading = signal(false);
  readonly hasSuccess = signal(false);
  readonly errorCode = signal<
    '429' | 'account_disabled' | 'user_deleted' | 'email_not_verified' | 'generic' | null
  >(null);

  protected readonly enterpriseCodeMaxLength = ENTERPRISE_CODE_MAX_LENGTH;

  readonly form = this.formBuilder.nonNullable.group({
    enterpriseCode: ['', [Validators.required, Validators.maxLength(ENTERPRISE_CODE_MAX_LENGTH)]],
    email: ['', [Validators.required, Validators.email]]
  });

  submit(): void {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorCode.set(null);
    this.hasSuccess.set(false);
    this.isLoading.set(true);

    this.authService.forgotPassword(this.form.getRawValue()).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.hasSuccess.set(true);
      },
      error: (error: { status?: number; error?: { code?: string } }) => {
        this.isLoading.set(false);
        this.errorCode.set(this.mapErrorCode(error));
      }
    });
  }

  private mapErrorCode(
    error: { status?: number; error?: { code?: string } }
  ): '429' | 'account_disabled' | 'user_deleted' | 'email_not_verified' | 'generic' {
    const code = error.error?.code;
    if (error.status === 403 && code === 'ACCOUNT_DISABLED') {
      return 'account_disabled';
    }
    if (error.status === 403 && code === 'USER_DELETED') {
      return 'user_deleted';
    }
    if (error.status === 403 && code === 'EMAIL_NOT_VERIFIED') {
      return 'email_not_verified';
    }
    if (error.status === 429) {
      return '429';
    }
    return 'generic';
  }
}
