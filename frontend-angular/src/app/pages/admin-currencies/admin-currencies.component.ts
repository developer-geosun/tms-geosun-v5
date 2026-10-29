import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  signal,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { MatButtonModule } from '@angular/material/button';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule, Sort } from '@angular/material/sort';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateService } from '@ngx-translate/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatExpansionModule } from '@angular/material/expansion';
import { ActivatedRoute } from '@angular/router';
import {
  CurrenciesApiService,
  CurrencyContractDto,
  NbuRatesSnapshotContractDto,
  ReferenceApiScope
} from '../../core/api';
import { LayoutService } from '../../core/layout';
import { LanguageService } from '../../core/services/language.service';
import { currencyLocalizedName } from '../../core/utils/currency-localized-name';
import { showAppSnack } from '../../shared/utils/app-snackbar';
import { syncPageLoadingToToolbar } from '../../shared/utils/sync-page-loading-to-toolbar';

@Component({
  selector: 'app-admin-currencies',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslateModule,
    MatButtonModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatSlideToggleModule,
    MatTooltipModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatExpansionModule
  ],
  templateUrl: './admin-currencies.component.html',
  styleUrl: './admin-currencies.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminCurrenciesComponent implements AfterViewInit {
  private static readonly DESKTOP_DEFAULT_PAGE_SIZE = 10;
  private static readonly HANDSET_DEFAULT_PAGE_SIZE = 5;

  private readonly currenciesApi = inject(CurrenciesApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly layout = inject(LayoutService);
  /** Запис і синхронізація НБУ лише в кабінеті оператора платформи. */
  private readonly scope: ReferenceApiScope =
    this.route.snapshot.data['referenceScope'] === 'platform' ? 'platform' : 'admin';
  readonly canManage = this.scope === 'platform';
  private readonly formBuilder = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly languageService = inject(LanguageService);

  readonly displayedColumns = [
    'code',
    'name',
    'nbuUnits',
    'ratePerUnit',
    'rateDate',
    'isActive'
  ];
  readonly nbuSnapshotColumns = ['currencyCode', 'nbuUnits', 'ratePerUnit'];
  readonly dataSource = new MatTableDataSource<CurrencyContractDto>([]);
  readonly nbuSnapshotSource = new MatTableDataSource<NbuRatesSnapshotContractDto['rates'][number]>([]);
  readonly pageSizeOptions = [5, 10, 15, 25, 50];
  readonly pageSize = signal(AdminCurrenciesComponent.DESKTOP_DEFAULT_PAGE_SIZE);

  readonly isLoading = signal(false);
  readonly isSyncing = signal(false);
  readonly isLoadingNbuSnapshot = signal(false);
  readonly loadError = signal('');
  readonly currencies = signal<CurrencyContractDto[]>([]);
  readonly nbuSnapshot = signal<NbuRatesSnapshotContractDto | null>(null);
  readonly updatingCodes = signal<Set<string>>(new Set());
  /** Стан панелі курсів НБУ: користувач може згорнути її, щоб звільнити місце під таблицю. */
  readonly isNbuSnapshotExpanded = signal(true);

  readonly rateDateForm = this.formBuilder.nonNullable.group({
    rateDate: [new Date().toISOString().slice(0, 10)]
  });

  @ViewChild(MatPaginator) private paginator?: MatPaginator;
  @ViewChild(MatSort) private sort?: MatSort;
  @ViewChild('tableHeaderScroll') private tableHeaderScroll?: ElementRef<HTMLElement>;

  constructor() {
    syncPageLoadingToToolbar(this.isLoading);
    this.dataSource.sortData = this.sortCurrencies.bind(this);
    effect(() => {
      this.layout.isHandset();
      this.applyDefaultPageSizeForViewport();
    });
    // Зміна мови впливає на назви валют, тому перебудовуємо таблицю для коректного сортування
    effect(() => {
      this.languageService.language();
      this.refreshTableData();
    });
    void this.reload();
  }

  ngAfterViewInit(): void {
    if (this.paginator) {
      this.dataSource.paginator = this.paginator;
    }
    if (this.sort) {
      this.dataSource.sort = this.sort;
    }
    this.applyDefaultPageSizeForViewport();
    this.refreshTableData();
  }

  async reload(): Promise<void> {
    this.isLoading.set(true);
    this.loadError.set('');
    try {
      const currencies = await this.currenciesApi.list(false, this.scope);
      this.currencies.set(currencies);
      this.refreshTableData();
    } catch {
      this.loadError.set('pages.adminCurrencies.loadFailed');
      this.currencies.set([]);
      this.refreshTableData();
    } finally {
      this.isLoading.set(false);
    }
  }

  async loadNbuRatesByDate(): Promise<void> {
    const rateDate = this.rateDateForm.controls.rateDate.value.trim();
    if (!rateDate) {
      return;
    }
    this.isLoadingNbuSnapshot.set(true);
    this.nbuSnapshot.set(null);
    try {
      const snapshot = await this.currenciesApi.getNbuRates(rateDate, this.scope);
      this.nbuSnapshot.set(snapshot);
      this.nbuSnapshotSource.data = snapshot.rates;
    } catch {
      this.notify('pages.adminCurrencies.nbuRatesLoadFailed', 'error');
      this.nbuSnapshotSource.data = [];
    } finally {
      this.isLoadingNbuSnapshot.set(false);
    }
  }

  async syncNbuRates(): Promise<void> {
    this.isSyncing.set(true);
    try {
      await this.currenciesApi.syncNbuRates();
      await this.reload();
      this.notify('pages.adminCurrencies.syncSuccess');
    } catch {
      this.notify('pages.adminCurrencies.syncFailed', 'error');
    } finally {
      this.isSyncing.set(false);
    }
  }

  async toggleActive(row: CurrencyContractDto, isActive: boolean): Promise<void> {
    const code = row.code;
    this.updatingCodes.update((set) => new Set(set).add(code));
    try {
      const updated = await this.currenciesApi.update(code, { isActive });
      this.currencies.update((list) =>
        list.map((item) => (item.code === code ? updated : item))
      );
      this.refreshTableData();
    } catch {
      this.notify('pages.adminCurrencies.updateFailed', 'error');
    } finally {
      this.updatingCodes.update((set) => {
        const next = new Set(set);
        next.delete(code);
        return next;
      });
    }
  }

  localizedName(row: CurrencyContractDto): string {
    return currencyLocalizedName(row, this.languageService.language());
  }

  isUpdating(code: string): boolean {
    return this.updatingCodes().has(code);
  }

  formatRate(value: number | null | undefined): string {
    if (value == null) {
      return '—';
    }
    return value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 6 });
  }

  formatFetchedAt(iso: string | undefined): string {
    if (!iso) {
      return '—';
    }
    const parsed = Date.parse(iso);
    if (Number.isNaN(parsed)) {
      return iso;
    }
    return new Date(parsed).toLocaleString();
  }

  /** Горизонтальний скрол тіла синхронізуємо з шапкою без власного скролбара. */
  protected syncHorizontalScroll(event: Event): void {
    const bodyScrollLeft = (event.target as HTMLElement).scrollLeft;
    if (this.tableHeaderScroll) {
      this.tableHeaderScroll.nativeElement.scrollLeft = bodyScrollLeft;
    }
  }

  private notify(messageKey: string, kind: 'success' | 'error' = 'success'): void {
    showAppSnack(this.snackBar, this.translate, messageKey, kind);
  }

  private applyDefaultPageSizeForViewport(): void {
    const size = this.layout.handsetPageSize(
      AdminCurrenciesComponent.DESKTOP_DEFAULT_PAGE_SIZE,
      AdminCurrenciesComponent.HANDSET_DEFAULT_PAGE_SIZE
    );
    this.pageSize.set(size);
    if (this.paginator) {
      this.paginator.pageSize = size;
      this.paginator.pageIndex = 0;
    }
  }

  private refreshTableData(): void {
    this.dataSource.data = this.currencies();
    this.paginator?.firstPage();
  }

  private sortCurrencies(data: CurrencyContractDto[], sort: Sort): CurrencyContractDto[] {
    if (!sort.active || sort.direction === '') {
      return data;
    }

    const direction = sort.direction === 'asc' ? 1 : -1;
    return [...data].sort((a, b) => direction * this.compareSortValues(a, b, sort.active));
  }

  private compareSortValues(a: CurrencyContractDto, b: CurrencyContractDto, column: string): number {
    switch (column) {
      case 'code':
        return a.code.localeCompare(b.code);
      case 'name': {
        const language = this.languageService.language();
        return this.localizedName(a).localeCompare(this.localizedName(b), language);
      }
      case 'ratePerUnit':
        return this.compareNullableNumbers(a.latestNbuRatePerUnit, b.latestNbuRatePerUnit);
      case 'isActive':
        return Number(a.isActive) - Number(b.isActive);
      default:
        return 0;
    }
  }

  private compareNullableNumbers(
    a: number | null | undefined,
    b: number | null | undefined
  ): number {
    if (a == null && b == null) {
      return 0;
    }
    if (a == null) {
      return 1;
    }
    if (b == null) {
      return -1;
    }
    return a - b;
  }
}
