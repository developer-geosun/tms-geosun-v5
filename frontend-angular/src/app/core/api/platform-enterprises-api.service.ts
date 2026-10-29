import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CreateEnterpriseRequest, Enterprise } from '../../shared/models';
import { BackendApiService } from './backend-api.service';

/** API підприємств для оператора платформи (/api/v1/platform/enterprises). */
@Injectable({ providedIn: 'root' })
export class PlatformEnterprisesApiService {
  private readonly http = inject(HttpClient);
  private readonly backendApi = inject(BackendApiService);

  list(): Promise<Enterprise[]> {
    return firstValueFrom(this.http.get<Enterprise[]>(this.backendApi.platformEnterprises));
  }

  create(payload: CreateEnterpriseRequest): Promise<Enterprise> {
    return firstValueFrom(this.http.post<Enterprise>(this.backendApi.platformEnterprises, payload));
  }

  suspend(id: string): Promise<Enterprise> {
    return firstValueFrom(
      this.http.post<Enterprise>(`${this.backendApi.platformEnterprises}/${encodeURIComponent(id)}/suspend`, null)
    );
  }

  activate(id: string): Promise<Enterprise> {
    return firstValueFrom(
      this.http.post<Enterprise>(`${this.backendApi.platformEnterprises}/${encodeURIComponent(id)}/activate`, null)
    );
  }
}
