import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { PageLoadingService } from '../../core/services/page-loading.service';
import { PlatformAuthService } from '../../core/services';

/** Оболонка кабінету оператора: підприємства, валюти, країни. */
@Component({
  selector: 'app-platform-shell',
  templateUrl: './platform-shell.component.html',
  styleUrl: './platform-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    TranslateModule
  ]
})
export class PlatformShellComponent {
  private readonly platformAuth = inject(PlatformAuthService);
  private readonly router = inject(Router);
  readonly pageLoading = inject(PageLoadingService);

  readonly operatorEmail = computed(() => this.platformAuth.operator()?.email ?? '');

  logout(): void {
    this.platformAuth.logout().subscribe({
      next: () => void this.router.navigate(['/platform/login'])
    });
  }
}
