import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { BackendApiService } from './backend-api.service';
import { ReferenceApiScope } from './currencies-api.service';
import { CountryReferenceContractDto } from './country-reference-contracts.model';

@Injectable({ providedIn: 'root' })
export class CountryReferenceApiService {
  private readonly http = inject(HttpClient);
  private readonly backendApi = inject(BackendApiService);

  async list(search?: string, scope: ReferenceApiScope = 'admin'): Promise<CountryReferenceContractDto[]> {
    let params = new HttpParams();
    if (search?.trim()) {
      params = params.set('search', search.trim());
    }
    const base =
      scope === 'platform' ? this.backendApi.platformCountryReference : this.backendApi.adminCountryReference;
    return firstValueFrom(this.http.get<CountryReferenceContractDto[]>(base, { params }));
  }

  async getByCode(codeAlpha2: string): Promise<CountryReferenceContractDto> {
    return firstValueFrom(
      this.http.get<CountryReferenceContractDto>(
        `${this.backendApi.adminCountryReference}/${encodeURIComponent(codeAlpha2)}`
      )
    );
  }
}
