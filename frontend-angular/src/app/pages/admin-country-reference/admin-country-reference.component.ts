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
import { debounceTime, distinctUntilChanged, tap } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatSort, MatSortModule, Sort } from '@angular/material/sort';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute } from '@angular/router';
import { CountryReferenceApiService, CountryReferenceContractDto, ReferenceApiScope } from '../../core/api';
import { LayoutService } from '../../core/layout';
import { syncPageLoadingToToolbar } from '../../shared/utils/sync-page-loading-to-toolbar';

@Component({
  selector: 'app-admin-country-reference',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslateModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatTooltipModule,
    MatIconModule
  ],
  templateUrl: './admin-country-reference.component.html',
  styleUrl: './admin-country-reference.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminCountryReferenceComponent implements AfterViewInit {
  private static readonly DESKTOP_DEFAULT_PAGE_SIZE = 10;
  private static readonly HANDSET_DEFAULT_PAGE_SIZE = 5;

  private readonly countryReferenceApi = inject(CountryReferenceApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  /** Оператор платформи читає той самий довідник зі свого API. */
  private readonly scope: ReferenceApiScope =
    this.route.snapshot.data['referenceScope'] === 'platform' ? 'platform' : 'admin';
  private readonly layout = inject(LayoutService);

  readonly displayedColumns = ['codeAlpha2', 'codeAlpha3', 'nameUk', 'nameEn', 'nameRu'];
  readonly dataSource = new MatTableDataSource<CountryReferenceContractDto>([]);
  readonly pageSizeOptions = [5, 10, 15, 25, 50];
  readonly pageSize = signal(AdminCountryReferenceComponent.DESKTOP_DEFAULT_PAGE_SIZE);

  readonly isLoading = signal(false);
  readonly loadError = signal('');
  readonly countries = signal<CountryReferenceContractDto[]>([]);
  readonly searchQuery = signal('');

  readonly searchForm = this.formBuilder.nonNullable.group({
    search: ['']
  });

  @ViewChild(MatPaginator) private paginator?: MatPaginator;
  @ViewChild(MatSort) private sort?: MatSort;
  @ViewChild('tableHeaderScroll') private tableHeaderScroll?: ElementRef<HTMLElement>;

  constructor() {
    syncPageLoadingToToolbar(this.isLoading);
    this.dataSource.sortData = this.sortCountries.bind(this);
    effect(() => {
      this.layout.isHandset();
      this.applyDefaultPageSizeForViewport();
    });
    this.searchForm.controls.search.valueChanges
      .pipe(
        tap((value) => this.searchQuery.set(value)),
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed()
      )
      .subscribe(() => void this.reload());
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
      const search = this.searchForm.controls.search.value.trim();
      const countries = await this.countryReferenceApi.list(search || undefined, this.scope);
      this.countries.set(countries);
      this.refreshTableData();
    } catch {
      this.loadError.set('pages.adminCountryReference.loadFailed');
      this.countries.set([]);
      this.refreshTableData();
    } finally {
      this.isLoading.set(false);
    }
  }

  clearSearch(): void {
    this.searchForm.patchValue({ search: '' });
  }

  /** Горизонтальний скрол тіла синхронізуємо з шапкою без власного скролбара. */
  protected syncHorizontalScroll(event: Event): void {
    const bodyScrollLeft = (event.target as HTMLElement).scrollLeft;
    if (this.tableHeaderScroll) {
      this.tableHeaderScroll.nativeElement.scrollLeft = bodyScrollLeft;
    }
  }

  private applyDefaultPageSizeForViewport(): void {
    const size = this.layout.handsetPageSize(
      AdminCountryReferenceComponent.DESKTOP_DEFAULT_PAGE_SIZE,
      AdminCountryReferenceComponent.HANDSET_DEFAULT_PAGE_SIZE
    );
    this.pageSize.set(size);
    if (this.paginator) {
      this.paginator.pageSize = size;
      this.paginator.pageIndex = 0;
    }
  }

  private refreshTableData(): void {
    this.dataSource.data = this.countries();
    this.paginator?.firstPage();
  }

  private sortCountries(data: CountryReferenceContractDto[], sort: Sort): CountryReferenceContractDto[] {
    if (!sort.active || sort.direction === '') {
      return data;
    }
    const direction = sort.direction === 'asc' ? 1 : -1;
    return [...data].sort((a, b) => direction * this.compareSortValues(a, b, sort.active));
  }

  private compareSortValues(
    a: CountryReferenceContractDto,
    b: CountryReferenceContractDto,
    column: string
  ): number {
    switch (column) {
      case 'codeAlpha2':
        return a.codeAlpha2.localeCompare(b.codeAlpha2);
      case 'codeAlpha3':
        return a.codeAlpha3.localeCompare(b.codeAlpha3);
      case 'nameUk':
        return a.nameUk.localeCompare(b.nameUk, 'uk');
      case 'nameEn':
        return a.nameEn.localeCompare(b.nameEn);
      case 'nameRu':
        return a.nameRu.localeCompare(b.nameRu);
      default:
        return 0;
    }
  }
}
