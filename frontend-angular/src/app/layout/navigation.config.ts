import { UserRole } from '../shared/models';

/** Секції sidenav (групування прототипу redesign). */
export type NavSectionId = 'overview' | 'user' | 'ops' | 'refs' | 'system';

export interface NavigationItem {
  route: string;
  labelKey: string;
  icon: string;
  roles: readonly UserRole[];
  section: NavSectionId;
}

/** Порядок секцій у sidenav. */
export const NAV_SECTION_ORDER: readonly NavSectionId[] = [
  'overview',
  'user',
  'ops',
  'refs',
  'system'
] as const;

/** i18n-ключі заголовків секцій. */
export const NAV_SECTION_LABEL_KEYS: Record<NavSectionId, string> = {
  overview: 'shell.sectionOverview',
  user: 'shell.sectionUser',
  ops: 'shell.sectionOps',
  refs: 'shell.sectionRefs',
  system: 'shell.sectionSystem'
};

/**
 * Канонічний список пунктів навігації (roles + icons з handoff pack).
 * Спільний для AppShell sidenav.
 */
export const NAVIGATION_ITEMS: readonly NavigationItem[] = [
  {
    route: '/main',
    labelKey: 'navigation.main',
    icon: 'home',
    roles: ['admin', 'manager', 'driver', 'user'],
    section: 'overview'
  },
  {
    route: '/profile',
    labelKey: 'navigation.profile',
    icon: 'person',
    roles: ['admin', 'manager', 'driver', 'user'],
    section: 'overview'
  },
  {
    route: '/my-trips',
    labelKey: 'navigation.myTrips',
    icon: 'explore',
    roles: ['admin', 'manager', 'driver'],
    section: 'overview'
  },
  {
    route: '/route-builder',
    labelKey: 'pages.routeBuilder.newRoute',
    icon: 'add',
    roles: ['user'],
    section: 'user'
  },
  {
    route: '/routes',
    labelKey: 'navigation.routes',
    icon: 'route',
    roles: ['user'],
    section: 'user'
  },
  {
    route: '/my-freight-requests',
    labelKey: 'navigation.myFreightRequests',
    icon: 'local_shipping',
    roles: ['user'],
    section: 'user'
  },
  {
    route: '/admin/route-requests',
    labelKey: 'navigation.adminRouteRequests',
    icon: 'inbox',
    roles: ['admin', 'manager'],
    section: 'ops'
  },
  {
    route: '/admin/trips',
    labelKey: 'navigation.adminTrips',
    icon: 'map',
    roles: ['admin', 'manager'],
    section: 'ops'
  },
  {
    route: '/admin/vehicles',
    labelKey: 'navigation.adminVehicles',
    icon: 'directions_car',
    roles: ['admin', 'manager'],
    section: 'ops'
  },
  {
    route: '/admin/drivers',
    labelKey: 'navigation.adminDrivers',
    icon: 'badge',
    roles: ['admin', 'manager'],
    section: 'ops'
  },
  {
    route: '/admin/vehicle-combinations',
    labelKey: 'navigation.adminVehicleCombinations',
    icon: 'airport_shuttle',
    roles: ['admin', 'manager'],
    section: 'ops'
  },
  // Валюти й країни прибрано з меню співробітника: запис довідників тепер на рівні платформи
  {
    route: '/admin/freight-numeric-scenarios',
    labelKey: 'navigation.adminFreightNumericScenarios',
    icon: 'analytics',
    roles: ['admin', 'manager'],
    section: 'refs'
  },
  {
    route: '/admin/toll-tariff-sets',
    labelKey: 'navigation.adminTollTariffSets',
    icon: 'toll',
    roles: ['admin', 'manager'],
    section: 'refs'
  },
  {
    route: '/admin/document-types',
    labelKey: 'navigation.adminDocumentTypes',
    icon: 'description',
    roles: ['admin'],
    section: 'refs'
  },
  {
    route: '/admin/chatbots',
    labelKey: 'navigation.adminChatbots',
    icon: 'smart_toy',
    roles: ['admin', 'manager'],
    section: 'system'
  },
  {
    route: '/admin/users',
    labelKey: 'navigation.adminUsers',
    icon: 'group',
    roles: ['admin', 'manager'],
    section: 'system'
  },
  {
    route: '/admin/file-storage-test',
    labelKey: 'navigation.adminFileStorageTest',
    icon: 'folder',
    roles: ['admin'],
    section: 'system'
  }
] as const;

/** Знайти пункт навігації за поточним URL (найдовший префікс — detail `/admin/trips/:id`). */
export function findNavigationItem(url: string): NavigationItem | undefined {
  const path = url.split('?')[0] ?? url;
  let best: NavigationItem | undefined;
  let bestLen = -1;
  for (const item of NAVIGATION_ITEMS) {
    if (path === item.route || path.startsWith(`${item.route}/`)) {
      if (item.route.length > bestLen) {
        best = item;
        bestLen = item.route.length;
      }
    }
  }
  return best;
}
