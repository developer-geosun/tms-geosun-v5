import { Route } from '@angular/router';
import { routes } from './app.routes';

/** Знайти дочірній route за path у будь-якому layout-батьку. */
function findChildRoute(path: string): Route | undefined {
  for (const parent of routes) {
    const child = parent.children?.find((route) => route.path === path);
    if (child) {
      return child;
    }
  }
  return undefined;
}

describe('app routes', () => {
  it('redirects default path to login page', () => {
    const defaultRoute = routes.find((route) => route.path === '' && route.redirectTo);

    expect(defaultRoute?.redirectTo).toBe('/login');
    expect(defaultRoute?.pathMatch).toBe('full');
  });

  it('uses lazy loaded components for app pages', () => {
    const loginRoute = findChildRoute('login');
    const registerRoute = findChildRoute('register');
    const forgotPasswordRoute = findChildRoute('forgot-password');
    const resetPasswordRoute = findChildRoute('reset-password');
    const mainRoute = findChildRoute('main');
    const stopServiceRoute = routes.find((route) => route.path === 'stop-service');
    const notFoundRoute = routes.find((route) => route.path === '404');

    expect(loginRoute?.loadComponent).toBeDefined();
    expect(registerRoute?.loadComponent).toBeDefined();
    expect(forgotPasswordRoute?.loadComponent).toBeDefined();
    expect(resetPasswordRoute?.loadComponent).toBeDefined();
    expect(mainRoute?.loadComponent).toBeDefined();
    expect(stopServiceRoute?.loadComponent).toBeDefined();
    expect(notFoundRoute?.loadComponent).toBeDefined();
    expect(loginRoute?.component).toBeUndefined();
    expect(registerRoute?.component).toBeUndefined();
    expect(mainRoute?.component).toBeUndefined();
    expect(stopServiceRoute?.component).toBeUndefined();
    expect(notFoundRoute?.component).toBeUndefined();
    // Auth на AppShell (canActivate) + canActivateChild на дочірніх
    const appShell = routes.find((route) => route.path === '' && route.canActivateChild);
    expect(appShell?.canActivate?.length).toBeGreaterThan(0);
    expect(appShell?.canActivateChild?.length).toBeGreaterThan(0);
  });

  it('has separate platform operator login and cabinet routes', () => {
    const platformLogin = findChildRoute('platform/login');
    const platformShell = routes.find((route) => route.path === 'platform');
    const enterprises = platformShell?.children?.find((route) => route.path === 'enterprises');
    const currencies = platformShell?.children?.find((route) => route.path === 'currencies');
    const countries = platformShell?.children?.find((route) => route.path === 'country-reference');

    expect(platformLogin?.loadComponent).toBeDefined();
    expect(platformShell?.loadComponent).toBeDefined();
    expect(platformShell?.canActivate?.length).toBeGreaterThan(0);
    expect(enterprises?.loadComponent).toBeDefined();
    expect(currencies?.data?.['referenceScope']).toBe('platform');
    expect(countries?.data?.['referenceScope']).toBe('platform');
    // Вхід оператора не є дочірнім маршрутом форми співробітника
    expect(findChildRoute('login')?.children).toBeUndefined();
  });
});
