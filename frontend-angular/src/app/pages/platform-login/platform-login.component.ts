import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { PlatformAuthService } from '../../core/services';

type PlatformLoginErrorCode = '401' | 'account_disabled' | '429' | 'generic';

/** Вхід оператора платформи — окрема форма, не вкладка входу співробітника. */
@Component({
  selector: 'app-platform-login',
  templateUrl: './platform-login.component.html',
  styleUrls: ['./platform-login.component.scss'],
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
export class PlatformLoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly platformAuth = inject(PlatformAuthService);
  private readonly router = inject(Router);

  readonly isLoading = signal(false);
  readonly hasSuccess = signal(false);
  readonly errorCode = signal<PlatformLoginErrorCode | null>(null);
  readonly isPasswordVisible = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  submit(): void {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorCode.set(null);
    this.hasSuccess.set(false);
    this.isLoading.set(true);

    this.platformAuth.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.hasSuccess.set(true);
        void this.router.navigate(['/platform/enterprises']);
      },
      error: (error: { status?: number; error?: { code?: string } }) => {
        this.isLoading.set(false);
        this.errorCode.set(this.mapErrorCode_(error));
      }
    });
  }

  private mapErrorCode_(error: { status?: number; error?: { code?: string } }): PlatformLoginErrorCode {
    if (error.status === 403 && error.error?.code === 'ACCOUNT_DISABLED') {
      return 'account_disabled';
    }
    if (error.status === 401) {
      return '401';
    }
    if (error.status === 429) {
      return '429';
    }
    return 'generic';
  }
}
