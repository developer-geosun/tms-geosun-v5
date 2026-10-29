import { Injectable, inject } from '@angular/core';
import { ConfigService } from '../services/config.service';

/**
 * Тонка обгортка для побудови URL backend API за contract-first підходом.
 * Мережеві виклики будуть додані у наступних фазах.
 */
@Injectable({
  providedIn: 'root'
})
export class BackendApiService {
  private readonly configService = inject(ConfigService);
  private readonly basePath = '/api/v1';

  get routes(): string {
    return this.build('/routes');
  }

  get myRoutes(): string {
    return this.build('/routes/my');
  }

  get routeRequests(): string {
    return this.build('/route-requests');
  }

  get myRouteRequests(): string {
    return this.build('/route-requests/my');
  }

  get adminRouteRequests(): string {
    return this.build('/admin/route-requests');
  }

  get adminQuotes(): string {
    return this.build('/admin/quotes');
  }

  get adminCurrencies(): string {
    return this.build('/admin/currencies');
  }

  /** Підприємства (кабінет оператора платформи). */
  get platformEnterprises(): string {
    return this.build('/platform/enterprises');
  }

  /** Запис валют і синхронізація НБУ — лише оператор платформи. */
  get platformCurrencies(): string {
    return this.build('/platform/currencies');
  }

  /** Читання довідника країн оператором платформи. */
  get platformCountryReference(): string {
    return this.build('/platform/country-reference');
  }

  get adminCountryReference(): string {
    return this.build('/admin/country-reference');
  }

  get adminDocumentTypes(): string {
    return this.build('/admin/document-types');
  }

  get adminFreightNumericScenarios(): string {
    return this.build('/admin/freight-numeric-scenarios');
  }

  get adminTollTariffSets(): string {
    return this.build('/admin/toll-tariff-sets');
  }

  get adminUsers(): string {
    return this.build('/admin/users');
  }

  get myProfile(): string {
    return this.build('/users/me/profile');
  }

  get myBotLinkCodes(): string {
    return this.build('/users/me/bot-link-codes');
  }

  get myBotIdentities(): string {
    return this.build('/users/me/bot-identities');
  }

  get adminChatbots(): string {
    return this.build('/admin/chatbots');
  }

  get adminStoredFiles(): string {
    return this.build('/admin/stored-files');
  }

  get adminVehicles(): string {
    return this.build('/admin/vehicles');
  }

  get adminDrivers(): string {
    return this.build('/admin/drivers');
  }

  get adminVehicleCombinations(): string {
    return this.build('/admin/vehicle-combinations');
  }

  get adminTrips(): string {
    return this.build('/admin/trips');
  }

  get myTrips(): string {
    return this.build('/my/trips');
  }

  /** Cost preview/calculations — вкладені під admin route requests. */
  get adminFreightCostCalculations(): string {
    return this.adminRouteRequests;
  }

  adminRouteRequestCostPreview(requestId: number | string): string {
    return `${this.adminRouteRequests}/${encodeURIComponent(String(requestId))}/cost-preview`;
  }

  adminRouteRequestCostCalculations(requestId: number | string): string {
    return `${this.adminRouteRequests}/${encodeURIComponent(String(requestId))}/cost-calculations`;
  }

  private build(path: string): string {
    return `${this.configService.apiUrl}${this.basePath}${path}`;
  }
}

