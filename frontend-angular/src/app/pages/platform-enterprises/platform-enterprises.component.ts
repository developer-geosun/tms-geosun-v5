import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { PlatformEnterprisesApiService } from '../../core/api/platform-enterprises-api.service';
import { LayoutService } from '../../core/layout/layout.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { Enterprise } from '../../shared/models';
import { showAppSnack } from '../../shared/utils/app-snackbar';
import { syncPageLoadingToToolbar } from '../../shared/utils/sync-page-loading-to-toolbar';
import { getHandsetFriendlyDialogConfig } from '../../shared/utils/handset-friendly-dialog-config';
import { CreateEnterpriseDialogComponent } from './create-enterprise-dialog.component';

/** Кабінет оператора платформи: підприємства, створення, призупинення / повернення. */
@Component({
  selector: 'app-platform-enterprises',
  templateUrl: './platform-enterprises.component.html',
  styleUrls: ['./platform-enterprises.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTooltipModule,
    TranslateModule
  ]
})
export class PlatformEnterprisesComponent implements OnInit {
  private readonly api = inject(PlatformEnterprisesApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly layout = inject(LayoutService);

  readonly isHandset = this.layout.isHandset;
  readonly displayedColumns = ['code', 'name', 'status', 'createdAt', 'actions'];

  readonly enterprises = signal<Enterprise[]>([]);
  readonly isLoading = signal(false);
  readonly hasLoaded = signal(false);
  readonly loadError = signal(false);
  /** Ідентифікатори підприємств, для яких виконується suspend/activate. */
  readonly busyIds = signal<ReadonlySet<string>>(new Set());

  readonly isEmpty = computed(
    () => this.hasLoaded() && !this.isLoading() && !this.loadError() && this.enterprises().length === 0
  );
  readonly showList = computed(() => !this.loadError() && this.enterprises().length > 0);

  constructor() {
    syncPageLoadingToToolbar(this.isLoading);
  }

  ngOnInit(): void {
    void this.reload();
  }

  async reload(): Promise<void> {
    if (this.isLoading()) {
      return;
    }
    this.isLoading.set(true);
    this.loadError.set(false);
    try {
      this.enterprises.set(await this.api.list());
    } catch {
      this.loadError.set(true);
    } finally {
      this.isLoading.set(false);
      this.hasLoaded.set(true);
    }
  }

  openCreateDialog(): void {
    const ref = this.dialog.open<CreateEnterpriseDialogComponent, void, Enterprise | undefined>(
      CreateEnterpriseDialogComponent,
      getHandsetFriendlyDialogConfig({ autoFocus: 'first-tabbable' })
    );
    ref.afterClosed().subscribe((created) => {
      if (!created) {
        return;
      }
      this.enterprises.update((list) => [created, ...list.filter((item) => item.id !== created.id)]);
      this.hasLoaded.set(true);
      this.loadError.set(false);
      showAppSnack(this.snackBar, this.translate, 'pages.platformEnterprises.createSuccess', 'success', {
        code: created.code
      });
    });
  }

  async toggleActive(enterprise: Enterprise): Promise<void> {
    if (this.isBusy(enterprise.id)) {
      return;
    }

    if (enterprise.active) {
      const confirmed = await firstValueFrom(
        this.dialog
          .open<ConfirmDialogComponent, { messageKey: string }, boolean>(ConfirmDialogComponent, {
            data: { messageKey: 'pages.platformEnterprises.suspendConfirm' }
          })
          .afterClosed()
      );
      if (!confirmed) {
        return;
      }
    }

    this.setBusy_(enterprise.id, true);
    try {
      const updated = enterprise.active
        ? await this.api.suspend(enterprise.id)
        : await this.api.activate(enterprise.id);
      this.enterprises.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
      showAppSnack(
        this.snackBar,
        this.translate,
        updated.active
          ? 'pages.platformEnterprises.activateSuccess'
          : 'pages.platformEnterprises.suspendSuccess',
        'success',
        { code: updated.code }
      );
    } catch {
      showAppSnack(this.snackBar, this.translate, 'pages.platformEnterprises.actionFailed', 'error');
    } finally {
      this.setBusy_(enterprise.id, false);
    }
  }

  isBusy(id: string): boolean {
    return this.busyIds().has(id);
  }

  private setBusy_(id: string, busy: boolean): void {
    this.busyIds.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }
}
