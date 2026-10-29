import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { BackendApiService } from './backend-api.service';
import {
  CurrencyContractDto,
  NbuRatesSnapshotContractDto,
  SyncNbuRatesContractResponse,
  UpdateCurrencyContractRequest
} from './currencies-contracts.model';

/** Звідки читати довідник: співробітник (лише читання) або оператор платформи (запис). */
export type ReferenceApiScope = 'admin' | 'platform';

@Injectable({ providedIn: 'root' })
export class CurrenciesApiService {
  private readonly http = inject(HttpClient);
  private readonly backendApi = inject(BackendApiService);

  async list(activeOnly = false, scope: ReferenceApiScope = 'admin'): Promise<CurrencyContractDto[]> {
    const params = new HttpParams().set('activeOnly', String(activeOnly));
    return firstValueFrom(this.http.get<CurrencyContractDto[]>(this.base_(scope), { params }));
  }

  /** Запис прапорця активності — лише ендпоінт оператора платформи. */
  async update(code: string, payload: UpdateCurrencyContractRequest): Promise<CurrencyContractDto> {
    return firstValueFrom(
      this.http.patch<CurrencyContractDto>(
        `${this.backendApi.platformCurrencies}/${encodeURIComponent(code)}`,
        payload
      )
    );
  }

  /** Синхронізація НБУ — лише ендпоінт оператора платформи. */
  async syncNbuRates(): Promise<SyncNbuRatesContractResponse> {
    return firstValueFrom(
      this.http.post<SyncNbuRatesContractResponse>(
        `${this.backendApi.platformCurrencies}/nbu-rates/sync`,
        null
      )
    );
  }

  async getLatestNbuRates(): Promise<NbuRatesSnapshotContractDto> {
    return this.getNbuRates();
  }

  async getNbuRates(
    rateDate?: string,
    scope: ReferenceApiScope = 'admin'
  ): Promise<NbuRatesSnapshotContractDto> {
    let params = new HttpParams();
    if (rateDate?.trim()) {
      params = params.set('rateDate', rateDate.trim());
    }
    return firstValueFrom(
      this.http.get<NbuRatesSnapshotContractDto>(`${this.base_(scope)}/nbu-rates`, { params })
    );
  }

  private base_(scope: ReferenceApiScope): string {
    return scope === 'platform' ? this.backendApi.platformCurrencies : this.backendApi.adminCurrencies;
  }
}
