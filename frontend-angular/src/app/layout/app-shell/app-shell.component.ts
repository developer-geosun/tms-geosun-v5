import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
  ViewEncapsulation
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { filter, map, startWith } from 'rxjs/operators';
import { firstValueFrom } from 'rxjs';
import { LayoutService } from '../../core/layout/layout.service';
import { AuthService } from '../../core/services/auth.service';
import { Language, LanguageService } from '../../core/services/language.service';
import { PageLoadingService } from '../../core/services/page-loading.service';
import { Theme, ThemeService } from '../../core/services/theme.service';
import { AppInfoDialogComponent } from '../../shared/components/app-info-dialog/app-info-dialog.component';
import { UserRole } from '../../shared/models';
import { showAppSnack } from '../../shared/utils/app-snackbar';
import { getHandsetFriendlyDialogConfig } from '../../shared/utils/handset-friendly-dialog-config';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  findNavigationItem,
  NAV_SECTION_LABEL_KEYS,
  NAV_SECTION_ORDER,
  NAVIGATION_ITEMS,
  NavigationItem,
  NavSectionId
} from '../navigation.config';

interface NavSectionView {
  id: NavSectionId;
  labelKey: string;
  items: NavigationItem[];
}

/**
 * Authenticated AppShell: permanent sidenav (≥960) / temporary drawer + toolbar + outlet.
 */
@Component({
  selector: 'app-shell',
  templateUrl: './app-shell.component.html',
  styleUrls: ['./app-shell.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  standalone: true,
  host: { class: 'app-shell-host' },
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    MatSidenavModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatProgressBarModule,
    MatTooltipModule,
    TranslateModule
  ]
})
export class AppShellComponent {
  private readonly layout = inject(LayoutService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly themeService = inject(ThemeService);
  private readonly languageService = inject(LanguageService);
  private readonly translateService = inject(TranslateService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  readonly pageLoading = inject(PageLoadingService);

  private readonly sidenav = viewChild<MatSidenav>('shellSidenav');
  private readonly menuBtn = viewChild<ElementRef<HTMLButtonElement>>('menuBtn');
  private readonly drawerCloseBtn = viewChild<ElementRef<HTMLButtonElement>>('drawerCloseBtn');

  readonly isShellNav = this.layout.isShellNav;
  readonly drawerOpen = signal(false);
  readonly isSwitchingRole = signal(false);
  readonly canSwitchRole = this.authService.canSwitchRole;
  readonly availableRoles = this.authService.availableRoles;
  readonly currentRole = computed(() => this.authService.user()?.role ?? null);
  readonly userEmail = computed(() => this.authService.user()?.email ?? '');
  /** Код підприємства поточного співробітника. */
  readonly enterpriseCode = this.authService.enterpriseCode;
  readonly currentLanguage = this.languageService.language;
  readonly currentTheme = this.themeService.theme;

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url)
    ),
    { initialValue: this.router.url }
  );

  readonly pageTitleKey = computed(() => {
    const item = findNavigationItem(this.currentUrl());
    return item?.labelKey ?? 'navigation.main';
  });

  readonly userInitials = computed(() => {
    const email = this.userEmail();
    if (!email) {
      return '?';
    }
    const local = email.split('@')[0] ?? email;
    const parts = local.split(/[._-]+/).filter(Boolean);
    if (parts.length >= 2) {
      return `${parts[0]!.charAt(0)}${parts[1]!.charAt(0)}`.toUpperCase();
    }
    return local.slice(0, 2).toUpperCase();
  });

  /** Баннер неповноти профілю — не на /profile. */
  readonly showProfileBanner = computed(() => {
    const url = this.currentUrl();
    if (url === '/profile' || url.startsWith('/profile?')) {
      return false;
    }
    const profile = this.authService.user()?.profile;
    return profile != null && profile.profileComplete === false;
  });

  readonly navSections = computed((): NavSectionView[] => {
    // Залежність від ролі / switch
    this.currentRole();
    const visible = NAVIGATION_ITEMS.filter((item) => this.authService.hasAnyRole(item.roles));
    const bySection = new Map<NavSectionId, NavigationItem[]>();
    for (const item of visible) {
      const list = bySection.get(item.section) ?? [];
      list.push(item);
      bySection.set(item.section, list);
    }
    return NAV_SECTION_ORDER.filter((id) => (bySection.get(id)?.length ?? 0) > 0).map((id) => ({
      id,
      labelKey: NAV_SECTION_LABEL_KEYS[id],
      items: bySection.get(id) ?? []
    }));
  });

  readonly languages: { code: Language; label: string }[] = [
    { code: 'uk', label: 'UA' },
    { code: 'en', label: 'EN' },
    { code: 'ru', label: 'RU' }
  ];

  readonly themes: { code: Theme; label: string }[] = [
    { code: 'azure-blue', label: 'Azure & Blue' },
    { code: 'rose-red', label: 'Rose & Red' },
    { code: 'magenta-violet', label: 'Magenta & Violet' },
    { code: 'cyan-orange', label: 'Cyan & Orange' }
  ];

  constructor() {
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe(() => this.closeDrawer());

    effect(() => {
      if (this.isShellNav()) {
        this.drawerOpen.set(false);
        const nav = this.sidenav();
        if (nav && !nav.opened) {
          void nav.open();
        }
      }
    });
  }

  isRouteActive(route: string): boolean {
    const url = this.currentUrl();
    return url === route || url.startsWith(`${route}/`) || url.startsWith(`${route}?`);
  }

  async toggleDrawer(): Promise<void> {
    if (this.isShellNav()) {
      return;
    }
    const nav = this.sidenav();
    if (!nav) {
      return;
    }
    if (nav.opened) {
      await nav.close();
      this.drawerOpen.set(false);
      this.menuBtn()?.nativeElement.focus();
    } else {
      await nav.open();
      this.drawerOpen.set(true);
      this.focusDrawerEntry();
    }
  }

  async closeDrawer(): Promise<void> {
    if (this.isShellNav()) {
      return;
    }
    const nav = this.sidenav();
    if (nav?.opened) {
      await nav.close();
    }
    this.drawerOpen.set(false);
  }

  onSidenavOpenedChange(opened: boolean): void {
    if (!this.isShellNav()) {
      this.drawerOpen.set(opened);
      if (!opened) {
        this.menuBtn()?.nativeElement.focus();
      } else {
        this.focusDrawerEntry();
      }
    }
  }

  /** Фокус у drawer після відкриття (a11y гамбургера з плану Stage 01). */
  private focusDrawerEntry(): void {
    queueMicrotask(() => {
      const closeBtn = this.drawerCloseBtn()?.nativeElement;
      if (closeBtn) {
        closeBtn.focus();
        return;
      }
      const firstNav = document.querySelector<HTMLElement>('.shell-sidenav .nav-item');
      firstNav?.focus();
    });
  }

  navigateTo(route: string): void {
    void this.closeDrawer();
    if (route === '/route-builder') {
      void this.router.navigate([route], {
        queryParams: { mode: 'create', routeId: null }
      });
      return;
    }
    void this.router.navigate([route]);
  }

  changeTheme(theme: Theme): void {
    this.themeService.setTheme(theme);
  }

  changeLanguage(language: Language): void {
    this.languageService.setLanguage(language);
  }

  navigateToProfile(): void {
    void this.closeDrawer();
    void this.router.navigate(['/profile']);
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => void this.router.navigate(['/login'])
    });
  }

  roleLabelKey(role: UserRole | null): string {
    return role ? `auth.roles.${role}` : 'auth.roles.user';
  }

  async switchRole(role: UserRole): Promise<void> {
    if (role === this.currentRole() || this.isSwitchingRole()) {
      return;
    }
    this.isSwitchingRole.set(true);
    try {
      await firstValueFrom(this.authService.switchRole(role));
      showAppSnack(this.snackBar, this.translateService, 'auth.switchRole.success', 'success');
      const url = this.router.url;
      const stillAllowed = NAVIGATION_ITEMS.some(
        (item) =>
          (url === item.route || url.startsWith(`${item.route}/`) || url.startsWith(`${item.route}?`)) &&
          this.authService.hasAnyRole(item.roles)
      );
      if (!stillAllowed) {
        await this.router.navigate(['/main']);
      }
    } catch {
      showAppSnack(this.snackBar, this.translateService, 'auth.switchRole.failed', 'error');
    } finally {
      this.isSwitchingRole.set(false);
    }
  }

  openAppInfoDialog(): void {
    this.dialog.open(AppInfoDialogComponent, getHandsetFriendlyDialogConfig({ autoFocus: false }));
  }

  langCodeLabel(code: Language): string {
    return code === 'uk' ? 'UA' : code.toUpperCase();
  }
}
