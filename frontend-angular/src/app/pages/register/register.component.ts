import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../core/services';
import { ENTERPRISE_CODE_MAX_LENGTH } from '../../core/utils/enterprise-code';
import { PASSWORD_PATTERN } from '../../shared/constants';
import { SyncBrowserAutofillDirective } from '../../shared/directives/sync-browser-autofill.directive';

type RegisterErrorCode = '409' | '429' | 'enterprise_unavailable' | 'generic';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslateModule,
    SyncBrowserAutofillDirective
  ]
})
export class RegisterComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly enterpriseCodeMaxLength = ENTERPRISE_CODE_MAX_LENGTH;

  readonly isLoading = signal(false);
  readonly hasSuccess = signal(false);
  readonly errorCode = signal<RegisterErrorCode | null>(null);
  readonly isPasswordVisible = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    enterpriseCode: ['', [Validators.required, Validators.maxLength(ENTERPRISE_CODE_MAX_LENGTH)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.pattern(PASSWORD_PATTERN)]],
    confirmPassword: ['', [Validators.required, Validators.minLength(8)]]
  });

  submit(): void {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }

    const values = this.form.getRawValue();
    if (values.password !== values.confirmPassword) {
      this.form.controls.confirmPassword.setErrors({ ...(this.form.controls.confirmPassword.errors ?? {}), mismatch: true });
      this.form.controls.confirmPassword.markAsTouched();
      return;
    }

    this.errorCode.set(null);
    this.hasSuccess.set(false);
    this.isLoading.set(true);

    this.authService
      .register({ enterpriseCode: values.enterpriseCode, email: values.email, password: values.password })
      .subscribe({
        next: () => {
          this.isLoading.set(false);
          this.hasSuccess.set(true);
          this.router.navigate(['/login']);
        },
        error: (error: { status?: number; error?: { code?: string } }) => {
          this.isLoading.set(false);
          this.errorCode.set(this.mapErrorCode(error));
        }
      });
  }

  private mapErrorCode(error: { status?: number; error?: { code?: string } }): RegisterErrorCode {
    if (error.status === 400 && error.error?.code === 'ENTERPRISE_UNAVAILABLE') {
      return 'enterprise_unavailable';
    }
    if (error.status === 409) {
      return '409';
    }
    if (error.status === 429) {
      return '429';
    }
    return 'generic';
  }
}
