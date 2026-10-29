import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { PlatformEnterprisesApiService } from '../../core/api/platform-enterprises-api.service';
import { extractApiError } from '../../core/utils/api-error';
import {
  ENTERPRISE_CODE_MAX_LENGTH,
  ENTERPRISE_CODE_PATTERN,
  ENTERPRISE_CODE_RESERVED,
  normalizeEnterpriseCode
} from '../../core/utils/enterprise-code';
import { PASSWORD_PATTERN } from '../../shared/constants';
import { Enterprise } from '../../shared/models';

type CreateErrorKey = 'validation' | 'generic';

/** Валідатор коду підприємства: формат + заборонений зарезервований код. */
function enterpriseCodeValidator_(control: AbstractControl<string>): ValidationErrors | null {
  const code = normalizeEnterpriseCode(control.value ?? '');
  if (!code) {
    return null;
  }
  if (code === ENTERPRISE_CODE_RESERVED) {
    return { reserved: true };
  }
  return ENTERPRISE_CODE_PATTERN.test(code) ? null : { codeFormat: true };
}

/** Діалог створення підприємства з першим адміністратором. */
@Component({
  selector: 'app-create-enterprise-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslateModule
  ],
  template: `
    <h2 mat-dialog-title>{{ 'pages.platformEnterprises.createDialog.title' | translate }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content class="create-enterprise-dialog__content">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'pages.platformEnterprises.createDialog.name' | translate }}</mat-label>
          <input matInput formControlName="name" maxlength="200" autocomplete="off" />
          @if (form.controls.name.touched && form.controls.name.hasError('required')) {
            <mat-error>{{ 'pages.platformEnterprises.createDialog.nameRequired' | translate }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'pages.platformEnterprises.createDialog.code' | translate }}</mat-label>
          <input
            matInput
            formControlName="code"
            autocomplete="off"
            autocapitalize="none"
            spellcheck="false"
            [attr.maxlength]="codeMaxLength" />
          <mat-hint>{{ 'pages.platformEnterprises.createDialog.codeHint' | translate }}</mat-hint>
          @if (form.controls.code.touched && form.controls.code.hasError('required')) {
            <mat-error>{{ 'pages.platformEnterprises.createDialog.codeRequired' | translate }}</mat-error>
          } @else if (form.controls.code.hasError('reserved')) {
            <mat-error>{{ 'pages.platformEnterprises.createDialog.codeReserved' | translate }}</mat-error>
          } @else if (form.controls.code.hasError('codeFormat')) {
            <mat-error>{{ 'pages.platformEnterprises.createDialog.codeFormat' | translate }}</mat-error>
          } @else if (form.controls.code.hasError('codeExists')) {
            <mat-error>{{ 'pages.platformEnterprises.createDialog.codeExists' | translate }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'pages.platformEnterprises.createDialog.adminEmail' | translate }}</mat-label>
          <input matInput type="email" formControlName="adminEmail" autocomplete="off" />
          @if (form.controls.adminEmail.touched && form.controls.adminEmail.invalid) {
            <mat-error>{{ 'pages.platformEnterprises.createDialog.adminEmailInvalid' | translate }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'pages.platformEnterprises.createDialog.adminPassword' | translate }}</mat-label>
          <input
            matInput
            [type]="isPasswordVisible() ? 'text' : 'password'"
            formControlName="adminPassword"
            autocomplete="new-password" />
          <button
            type="button"
            mat-icon-button
            matSuffix
            [attr.aria-label]="isPasswordVisible() ? 'Hide password' : 'Show password'"
            [attr.aria-pressed]="isPasswordVisible()"
            (click)="isPasswordVisible.set(!isPasswordVisible())">
            <mat-icon [fontIcon]="isPasswordVisible() ? 'visibility_off' : 'visibility'"></mat-icon>
          </button>
          @if (form.controls.adminPassword.touched && form.controls.adminPassword.invalid) {
            <mat-error>{{ 'pages.platformEnterprises.createDialog.adminPasswordRule' | translate }}</mat-error>
          }
        </mat-form-field>

        @if (errorKey(); as key) {
          <p class="create-enterprise-dialog__error" role="alert">
            <mat-icon fontIcon="error_outline" aria-hidden="true"></mat-icon>
            <span>
              {{
                (key === 'validation'
                  ? 'pages.platformEnterprises.createDialog.errorValidation'
                  : 'pages.platformEnterprises.createDialog.errorGeneric'
                ) | translate
              }}
            </span>
          </p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-stroked-button type="button" [disabled]="isSubmitting()" (click)="cancel()">
          {{ 'pages.platformEnterprises.createDialog.cancel' | translate }}
        </button>
        <button mat-flat-button color="primary" type="submit" [disabled]="isSubmitting()">
          @if (isSubmitting()) {
            <span class="create-enterprise-dialog__loading">
              <mat-spinner diameter="18"></mat-spinner>
              <span>{{ 'pages.platformEnterprises.createDialog.submitting' | translate }}</span>
            </span>
          } @else {
            {{ 'pages.platformEnterprises.createDialog.submit' | translate }}
          }
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    :host {
      display: block;
    }

    .create-enterprise-dialog__content {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      padding-top: 0.5rem;
    }

    .create-enterprise-dialog__error {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      color: var(--mat-sys-error);
    }

    .create-enterprise-dialog__loading {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
    }

    mat-dialog-actions button {
      min-height: 48px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CreateEnterpriseDialogComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(PlatformEnterprisesApiService);
  private readonly dialogRef = inject(MatDialogRef<CreateEnterpriseDialogComponent, Enterprise | undefined>);

  protected readonly codeMaxLength = ENTERPRISE_CODE_MAX_LENGTH;

  readonly isSubmitting = signal(false);
  readonly isPasswordVisible = signal(false);
  readonly errorKey = signal<CreateErrorKey | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    code: ['', [Validators.required, enterpriseCodeValidator_]],
    adminEmail: ['', [Validators.required, Validators.email]],
    adminPassword: ['', [Validators.required, Validators.pattern(PASSWORD_PATTERN)]]
  });

  cancel(): void {
    this.dialogRef.close(undefined);
  }

  submit(): void {
    if (this.form.invalid || this.isSubmitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const values = this.form.getRawValue();
    this.errorKey.set(null);
    this.isSubmitting.set(true);

    this.api
      .create({
        name: values.name.trim(),
        code: normalizeEnterpriseCode(values.code),
        adminEmail: values.adminEmail.trim(),
        adminPassword: values.adminPassword
      })
      .then((enterprise) => this.dialogRef.close(enterprise))
      .catch((error: unknown) => {
        const { code, status } = extractApiError(error);
        if (status === 409 || code === 'ENTERPRISE_CODE_EXISTS') {
          this.form.controls.code.setErrors({ codeExists: true });
          this.form.controls.code.markAsTouched();
        } else if (status === 400) {
          this.errorKey.set('validation');
        } else {
          this.errorKey.set('generic');
        }
        this.isSubmitting.set(false);
      });
  }
}
