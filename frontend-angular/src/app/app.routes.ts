import { Routes } from '@angular/router';
import { serviceStopGuard } from './core/guards/service-stop.guard';
import { authGuard } from './core/guards/auth.guard';
import { authAvailabilityGuard } from './core/guards/auth-availability.guard';
import { guestGuard } from './core/guards/guest.guard';
import { platformAuthGuard, platformGuestGuard } from './core/guards/platform-auth.guard';
import { AppShellComponent } from './layout/app-shell/app-shell.component';
import { AuthShellComponent } from './layout/auth-shell/auth-shell.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },

  // Guest auth — AuthShell (theme/lang + footer на brand-панелі)
  {
    path: '',
    component: AuthShellComponent,
    children: [
      {
        path: 'login',
        canActivate: [authAvailabilityGuard, guestGuard],
        loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent)
      },
      {
        path: 'register',
        canActivate: [authAvailabilityGuard, guestGuard],
        loadComponent: () => import('./pages/register/register.component').then((m) => m.RegisterComponent)
      },
      {
        path: 'verify-email',
        canActivate: [authAvailabilityGuard, guestGuard],
        loadComponent: () =>
          import('./pages/verify-email/verify-email.component').then((m) => m.VerifyEmailComponent)
      },
      {
        path: 'forgot-password',
        canActivate: [authAvailabilityGuard, guestGuard],
        loadComponent: () =>
          import('./pages/forgot-password/forgot-password.component').then((m) => m.ForgotPasswordComponent)
      },
      {
        path: 'reset-password',
        canActivate: [authAvailabilityGuard, guestGuard],
        loadComponent: () =>
          import('./pages/reset-password/reset-password.component').then((m) => m.ResetPasswordComponent)
      },
      // Вхід оператора платформи — окремий маршрут, не вкладка входу співробітника
      {
        path: 'platform/login',
        canActivate: [authAvailabilityGuard, platformGuestGuard],
        loadComponent: () =>
          import('./pages/platform-login/platform-login.component').then((m) => m.PlatformLoginComponent)
      }
    ]
  },

  // Кабінет оператора платформи — окрема сесія, поза AppShell співробітника
  {
    path: 'platform',
    canActivate: [authAvailabilityGuard, platformAuthGuard],
    loadComponent: () =>
      import('./pages/platform-shell/platform-shell.component').then((m) => m.PlatformShellComponent),
    children: [
      { path: '', redirectTo: 'enterprises', pathMatch: 'full' },
      {
        path: 'enterprises',
        loadComponent: () =>
          import('./pages/platform-enterprises/platform-enterprises.component').then(
            (m) => m.PlatformEnterprisesComponent
          )
      },
      {
        path: 'currencies',
        data: { referenceScope: 'platform' },
        loadComponent: () =>
          import('./pages/admin-currencies/admin-currencies.component').then((m) => m.AdminCurrenciesComponent)
      },
      {
        path: 'country-reference',
        data: { referenceScope: 'platform' },
        loadComponent: () =>
          import('./pages/admin-country-reference/admin-country-reference.component').then(
            (m) => m.AdminCountryReferenceComponent
          )
      }
    ]
  },

  // System pages — full-screen поза shell
  {
    path: 'stop-service',
    loadComponent: () => import('./pages/stop-service/stop-service.component').then((m) => m.StopServiceComponent)
  },
  {
    path: '404',
    loadComponent: () => import('./pages/not-found/not-found.component').then((m) => m.NotFoundComponent)
  },

  // Authenticated AppShell (ролі — canActivateChild на data.roles дочірніх)
  {
    path: '',
    component: AppShellComponent,
    canActivate: [authAvailabilityGuard, serviceStopGuard, authGuard],
    canActivateChild: [authGuard],
    children: [
      {
        path: 'main',
        data: { roles: ['admin', 'manager', 'driver', 'user'], titleKey: 'navigation.main' },
        loadComponent: () => import('./pages/main/main.component').then((m) => m.MainComponent)
      },
      {
        path: 'profile',
        data: { roles: ['admin', 'manager', 'driver', 'user'], titleKey: 'navigation.profile' },
        loadComponent: () => import('./pages/profile/profile.component').then((m) => m.ProfileComponent)
      },
      {
        path: 'route-builder',
        data: { roles: ['user'], titleKey: 'pages.routeBuilder.newRoute' },
        loadComponent: () =>
          import('./pages/route-builder/route-builder.component').then((m) => m.RouteBuilderComponent)
      },
      {
        path: 'routes',
        data: { roles: ['user'], titleKey: 'navigation.routes' },
        loadComponent: () => import('./pages/routes/routes.component').then((m) => m.RoutesComponent)
      },
      {
        path: 'my-freight-requests',
        data: { roles: ['user'], titleKey: 'navigation.myFreightRequests' },
        loadComponent: () =>
          import('./pages/my-freight-requests/my-freight-requests.component').then(
            (m) => m.MyFreightRequestsComponent
          )
      },
      {
        path: 'admin/route-requests',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminRouteRequests' },
        loadComponent: () =>
          import('./pages/admin-route-requests/admin-route-requests.component').then(
            (m) => m.AdminRouteRequestsComponent
          )
      },
      {
        path: 'admin/currencies',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminCurrencies' },
        loadComponent: () =>
          import('./pages/admin-currencies/admin-currencies.component').then((m) => m.AdminCurrenciesComponent)
      },
      {
        path: 'admin/country-reference',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminCountryReference' },
        loadComponent: () =>
          import('./pages/admin-country-reference/admin-country-reference.component').then(
            (m) => m.AdminCountryReferenceComponent
          )
      },
      {
        path: 'admin/freight-numeric-scenarios',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminFreightNumericScenarios' },
        loadComponent: () =>
          import('./pages/admin-freight-numeric-scenarios/admin-freight-numeric-scenarios.component').then(
            (m) => m.AdminFreightNumericScenariosComponent
          )
      },
      {
        path: 'admin/toll-tariff-sets',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminTollTariffSets' },
        loadComponent: () =>
          import('./pages/admin-toll-tariff-sets/admin-toll-tariff-sets.component').then(
            (m) => m.AdminTollTariffSetsComponent
          )
      },
      {
        path: 'admin/chatbots',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminChatbots' },
        loadComponent: () =>
          import('./pages/admin-chatbots/admin-chatbots.component').then((m) => m.AdminChatbotsComponent)
      },
      {
        path: 'admin/users',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminUsers' },
        loadComponent: () =>
          import('./pages/admin-users/admin-users.component').then((m) => m.AdminUsersComponent)
      },
      {
        path: 'admin/users/:userId',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminUsers' },
        loadComponent: () =>
          import('./pages/admin-users/admin-users.component').then((m) => m.AdminUsersComponent)
      },
      {
        path: 'admin/document-types',
        data: { roles: ['admin'], titleKey: 'navigation.adminDocumentTypes' },
        loadComponent: () =>
          import('./pages/admin-document-types/admin-document-types.component').then(
            (m) => m.AdminDocumentTypesComponent
          )
      },
      {
        path: 'admin/file-storage-test',
        data: { roles: ['admin'], titleKey: 'navigation.adminFileStorageTest' },
        loadComponent: () =>
          import('./pages/admin-file-storage-test/admin-file-storage-test.component').then(
            (m) => m.AdminFileStorageTestComponent
          )
      },
      {
        path: 'admin/vehicles',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminVehicles' },
        loadComponent: () =>
          import('./pages/admin-vehicles/admin-vehicles.component').then((m) => m.AdminVehiclesComponent)
      },
      {
        path: 'admin/drivers',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminDrivers' },
        loadComponent: () =>
          import('./pages/admin-drivers/admin-drivers.component').then((m) => m.AdminDriversComponent)
      },
      {
        path: 'admin/vehicle-combinations',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminVehicleCombinations' },
        loadComponent: () =>
          import('./pages/admin-vehicle-combinations/admin-vehicle-combinations.component').then(
            (m) => m.AdminVehicleCombinationsComponent
          )
      },
      {
        path: 'admin/trips',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminTrips' },
        loadComponent: () =>
          import('./pages/admin-trips/admin-trips.component').then((m) => m.AdminTripsComponent)
      },
      {
        path: 'admin/trips/new',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminTrips' },
        loadComponent: () =>
          import('./pages/admin-trips/admin-trip-detail.component').then((m) => m.AdminTripDetailComponent)
      },
      {
        path: 'admin/trips/:id',
        data: { roles: ['admin', 'manager'], titleKey: 'navigation.adminTrips' },
        loadComponent: () =>
          import('./pages/admin-trips/admin-trip-detail.component').then((m) => m.AdminTripDetailComponent)
      },
      {
        path: 'my-trips',
        data: { roles: ['admin', 'manager', 'driver'], titleKey: 'navigation.myTrips' },
        loadComponent: () => import('./pages/my-trips/my-trips.component').then((m) => m.MyTripsComponent)
      }
    ]
  },

  { path: '**', redirectTo: '/404' }
];
