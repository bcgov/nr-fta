import { Currency, Edit, Enterprise, Receipt, Wallet } from '@carbon/icons-react';
import { Button, DatePicker, DatePickerInput, Select, SelectItem, TextInput } from '@carbon/react';
import { useCallback, useEffect, useState, type FC, type ReactNode } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile, { type DetailField } from '@/components/DetailTile';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import type { CodeOption } from '@/services/codeLists';
import {
  getBctsCategories,
  getDepositTypes,
  getSaleMethods,
  getSaleTypes,
  getTenureSaleInfo,
  saveTenureSaleInfo,
  type SaleInfo,
  type SaleInfoRules,
} from '@/services/tenure_saleinfo';
import { formatDate } from '@/utils/formatDate';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

import type { TenurePanelProps } from './panelProps';

const money = new Intl.NumberFormat('en-CA', {
  style: 'currency',
  currency: 'CAD',
  minimumFractionDigits: 2,
});
const nf = new Intl.NumberFormat('en-CA', { maximumFractionDigits: 2 });

const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;
const date = (v: string | null | undefined) => dash(formatDate(v));
const dollars = (v: number | null | undefined) =>
  v === null || v === undefined ? '—' : money.format(v);
const volume = (v: number | null | undefined) =>
  v === null || v === undefined ? '—' : `${nf.format(v)} m³`;
const yesNo = (v: string | null | undefined) => (v === 'Y' ? 'Yes' : v === 'N' ? 'No' : '—');
const coded = (code: string | null, desc: string | null) => (code ? (desc ?? code) : '—');

/** yyyy-mm-dd in local time — what the backend's LocalDate expects. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

interface Form {
  saleMethodCode: string;
  saleTypeCode: string;
  salvageInd: string;
  minorFacilityInd: string;
  adminAreaInd: string;
  adminAreaFile: string;
  plannedSaleDate: string;
  tenderOpeningDate: string;
  cashSaleEstVol: string;
  cashSaleTotDol: string;
  saleVolume: string;
  totalBidders: string;
  ftaBonusBid: string;
  ftaBonusOffer: string;
  plannedSbCatCode: string;
  soldSbCatCode: string;
  scrtyDepositCode: string;
  scrtyDepositAmt: string;
  otherDepositCode: string;
  otherDepositAmt: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const str = (v: string | number | null | undefined) =>
  v === null || v === undefined ? '' : String(v);

const createForm = (s: SaleInfo): Form => ({
  saleMethodCode: s.saleMethodCode ?? s.rules.defaultSaleMethodCode ?? '',
  saleTypeCode: str(s.saleTypeCode),
  salvageInd: s.rules.salvage ? (s.salvageInd ?? 'N') : 'Y',
  minorFacilityInd: s.minorFacilityInd ?? 'N',
  adminAreaInd: s.adminAreaInd ?? 'N',
  adminAreaFile: str(s.adminAreaFile),
  plannedSaleDate: str(s.plannedSaleDate),
  tenderOpeningDate: str(s.tenderOpeningDate),
  cashSaleEstVol: str(s.cashSaleEstVol),
  cashSaleTotDol: str(s.cashSaleTotDol),
  saleVolume: str(s.saleVolume),
  totalBidders: str(s.totalBidders),
  ftaBonusBid: str(s.ftaBonusBid),
  ftaBonusOffer: str(s.ftaBonusOffer),
  plannedSbCatCode: str(s.plannedSbCatCode),
  soldSbCatCode: str(s.soldSbCatCode),
  scrtyDepositCode: str(s.scrtyDepositCode),
  scrtyDepositAmt: str(s.scrtyDepositAmt),
  otherDepositCode: str(s.otherDepositCode),
  otherDepositAmt: str(s.otherDepositAmt),
});

/** A decimal with at most `places` places, 0 to `max`; blank is fine here. */
const decimal = (v: string, places: number, max: number): string | undefined => {
  const t = v.trim();
  if (!t) return undefined;
  if (!new RegExp(`^\\d+(\\.\\d{1,${places}})?$`).test(t))
    return places === 1
      ? 'Enter a number with up to 1 decimal place.'
      : `Enter a number with up to ${places} decimal places.`;
  if (Number(t) > max) return `Cannot exceed ${nf.format(max)}.`;
  return undefined;
};

/**
 * Fta940SaleInfoForm's "Save" checks; the backend repeats them, then applies the
 * package's own (sale method by file type, small scale salvage, B07 volumes…).
 */
const validate = (f: Form, r: SaleInfoRules, status: string): Errors => {
  const e: Errors = {};
  const need = (key: keyof Form, when: boolean, text: string) => {
    if (when && !f[key].trim() && !e[key]) e[key] = text;
  };
  const num = (key: keyof Form, places: number, max: number) => {
    const found = decimal(f[key], places, max);
    if (found) e[key] = found;
  };
  num('scrtyDepositAmt', 2, 999999999.99);
  num('otherDepositAmt', 2, 999999999.99);
  num('cashSaleTotDol', 2, 999999999.99);
  num('cashSaleEstVol', 2, 9999999999.99);
  num('saleVolume', 1, 99999999.9);
  if (!r.bcts) {
    num('ftaBonusBid', 2, 999999999.99);
    num('ftaBonusOffer', 2, 999999999.99);
    if (f.ftaBonusBid.trim() && f.ftaBonusOffer.trim())
      e.ftaBonusBid = 'Only one of Bonus Bid or Bonus Offer may be entered.';
  }
  const bidders = f.totalBidders.trim();
  if (bidders && (!/^\d{1,4}$/.test(bidders) || Number(bidders) > 9999))
    e.totalBidders = 'Enter a whole number from 0 to 9999.';
  need('saleMethodCode', r.saleMethodMandatory, 'Sale Method is required for this file type.');
  if (r.saleMethodAllowed.length > 0 && !r.saleMethodAllowed.includes(f.saleMethodCode))
    e.saleMethodCode = `Sales Method Code must be ${r.saleMethodAllowed.join(' or ')} for this File Type.`;
  need('plannedSbCatCode', r.bctsFileType, 'Planned BCTS Cat. is mandatory.');
  need('soldSbCatCode', r.soldCatRequired, 'Sold BCTS Cat. is mandatory.');
  need('plannedSaleDate', r.bctsFileType, 'Planned Sale Date is mandatory.');
  need('tenderOpeningDate', r.tenderRequired, 'Required when not a cash sale and in an H status.');
  need('cashSaleEstVol', r.cashFieldsRequired, 'Required for this file type, status and payment.');
  need('cashSaleTotDol', r.cashFieldsRequired, 'Required for this file type, status and payment.');
  if (!r.category3Allowed && f.plannedSbCatCode === '3')
    e.plannedSbCatCode = 'Category 3 is not allowed for this file type.';
  if (!r.category3Allowed && f.soldSbCatCode === '3')
    e.soldSbCatCode = 'Category 3 is not allowed for this file type.';
  const deposit = (code: keyof Form, amount: keyof Form, name: string) => {
    if (!f[amount].trim() && f[code] && !f[code].startsWith('N') && !e[amount])
      e[amount] = `${name} Amount must not be blank when ${name} Type is provided.`;
    else if (!f[code] && f[amount].trim())
      e[code] = `${name} Type must not be blank when ${name} Amount is provided.`;
  };
  deposit('scrtyDepositCode', 'scrtyDepositAmt', 'Security Deposit');
  deposit('otherDepositCode', 'otherDepositAmt', 'Other Deposit');
  if (
    f.adminAreaInd === 'Y' &&
    !f.adminAreaFile.trim() &&
    status.startsWith('P') &&
    r.adminAreaFile
  )
    e.adminAreaFile = 'Admin Area File is required.';
  return e;
};

const toNumber = (v: string) => (v.trim() ? Number(v.trim()) : null);
const orNull = (v: string) => (v.trim() ? v.trim() : null);

interface Lists {
  saleMethods: CodeOption[];
  saleTypes: CodeOption[];
  deposits: CodeOption[];
  categories: CodeOption[];
}

const NO_LISTS: Lists = { saleMethods: [], saleTypes: [], deposits: [], categories: [] };

/** The list, plus the stored value when the list no longer carries it (an expired code). */
const withCurrent = (options: CodeOption[], code: string | null, desc: string | null) =>
  code && !options.some((o) => o.code === code)
    ? [{ code, description: desc ?? code }, ...options]
    : options;

const YES_NO = [
  { code: 'Y', description: 'Yes' },
  { code: 'N', description: 'No' },
];

/**
 * The Sale info tab of the tenure detail — legacy FTA940 (Sale Info): one
 * record in tiles, with one Edit button above them that opens, in place, every
 * field legacy lets the user change (the private-mark detail's pattern). Which
 * fields open is `rules`, computed by the backend from FTA940's protection
 * states; the backend keeps every closed field's stored value on save.
 */
const SaleInfoPanel: FC<TenurePanelProps> = ({ tenure, canEdit }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const fetcher = useCallback(() => getTenureSaleInfo(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);

  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<Form | null>(null);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [lists, setLists] = useState<Lists>(NO_LISTS);

  // The edit lists, once the form opens (cached for the page).
  useEffect(() => {
    if (!editing) return;
    let cancelled = false;
    Promise.all([getSaleMethods(), getSaleTypes(), getDepositTypes(), getBctsCategories()])
      .then(([saleMethods, saleTypes, deposits, categories]) => {
        if (!cancelled) setLists({ saleMethods, saleTypes, deposits, categories });
      })
      .catch(() => {
        if (!cancelled) {
          display({
            kind: 'error',
            title: 'Could not load the edit lists',
            subtitle: 'Some dropdowns show only their current value.',
            timeout: 6000,
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [editing, display]);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const onEdit = () => {
    if (!data) return;
    setForm(createForm(data));
    setErrors({});
    setEditing(true);
  };

  const onCancel = () => {
    setEditing(false);
    setErrors({});
  };

  const onSave = async () => {
    if (!data || !form) return;
    const found = validate(form, data.rules, data.fileStatusCode ?? '');
    if (Object.keys(found).length > 0) {
      setErrors(found);
      display({ kind: 'error', title: 'Check the highlighted fields', timeout: 6000 });
      return;
    }
    setSaving(true);
    try {
      const { warnings } = await saveTenureSaleInfo(forestFileId, {
        saleMethodCode: orNull(form.saleMethodCode),
        saleTypeCode: orNull(form.saleTypeCode),
        salvageInd: orNull(form.salvageInd),
        minorFacilityInd: orNull(form.minorFacilityInd),
        adminAreaInd: orNull(form.adminAreaInd),
        adminAreaFile: orNull(form.adminAreaFile.toUpperCase()),
        plannedSaleDate: orNull(form.plannedSaleDate),
        tenderOpeningDate: orNull(form.tenderOpeningDate),
        cashSaleEstVol: toNumber(form.cashSaleEstVol),
        cashSaleTotDol: toNumber(form.cashSaleTotDol),
        saleVolume: toNumber(form.saleVolume),
        totalBidders: toNumber(form.totalBidders),
        ftaBonusBid: toNumber(form.ftaBonusBid),
        ftaBonusOffer: toNumber(form.ftaBonusOffer),
        plannedSbCatCode: orNull(form.plannedSbCatCode),
        soldSbCatCode: orNull(form.soldSbCatCode),
        scrtyDepositCode: orNull(form.scrtyDepositCode),
        scrtyDepositAmt: toNumber(form.scrtyDepositAmt),
        otherDepositCode: orNull(form.otherDepositCode),
        otherDepositAmt: toNumber(form.otherDepositAmt),
        hsRevisionCount: data.hsRevisionCount,
        tdRevisionCount: data.tdRevisionCount,
        auRevisionCount: data.auRevisionCount,
      });
      setEditing(false);
      // One toast: the provider shows a single notification at a time.
      display(
        warnings.length > 0
          ? {
              kind: 'warning',
              title: 'Sale information saved, with a warning',
              subtitle: warnings.join(' '),
              timeout: 9000,
            }
          : { kind: 'success', title: 'Sale information saved', timeout: 5000 },
      );
      reload();
    } catch (err) {
      display({
        kind: 'error',
        title: 'Could not save the sale information',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  // ─── Field builders: the read-only value, or its input while editing ──────

  const rules = data?.rules;
  const isOpen = (rule = true) => editing && !!form && !!rules?.editable && rule;

  const field = (
    label: string,
    view: ReactNode,
    open: boolean,
    editor: () => ReactNode,
    opts: { optional?: boolean; span2?: boolean } = {},
  ): DetailField => ({
    label: open && opts.optional ? `${label} (optional)` : label,
    value: open ? editor() : view,
    span2: open && opts.span2,
  });

  const text = (key: keyof Form, label: string, size: 'cell' | 'sm' = 'sm', maxLength?: number) => (
    <div className={`detail-edit__input detail-edit__input--${size}`}>
      <TextInput
        id={`sale-info-${key}`}
        labelText={label}
        hideLabel
        value={form?.[key] ?? ''}
        maxLength={maxLength}
        invalid={!!errors[key]}
        invalidText={errors[key]}
        disabled={saving}
        onChange={(e) => set(key, e.target.value)}
      />
    </div>
  );

  const select = (
    key: keyof Form,
    label: string,
    options: CodeOption[],
    placeholder: string | null = 'None',
  ) => (
    <div className="detail-edit__input detail-edit__input--cell">
      <Select
        id={`sale-info-${key}`}
        labelText={label}
        hideLabel
        value={form?.[key] ?? ''}
        invalid={!!errors[key]}
        invalidText={errors[key]}
        disabled={saving}
        onChange={(e) => set(key, e.target.value)}
      >
        {placeholder !== null && <SelectItem value="" text={placeholder} />}
        {options.map((o) => (
          <SelectItem key={o.code} value={o.code} text={o.description || o.code} />
        ))}
      </Select>
    </div>
  );

  const dateInput = (key: keyof Form, label: string) => (
    <div className="detail-edit__input detail-edit__input--cell">
      <DatePicker
        datePickerType="single"
        dateFormat="Y-m-d"
        value={form?.[key] ?? ''}
        invalid={!!errors[key]}
        onChange={(dates: Date[]) => set(key, dates[0] ? toIsoDate(dates[0]) : '')}
      >
        <DatePickerInput
          id={`sale-info-${key}`}
          labelText={label}
          hideLabel
          placeholder="yyyy-mm-dd"
          invalidText={errors[key]}
          disabled={saving}
          pattern={TYPED_DATE_PATTERN}
          onChange={(e) => {
            const text = e.target.value;
            if (text.trim() === '') set(key, '');
            else {
              const typed = parseTypedDate(text);
              if (typed) set(key, typed);
            }
          }}
        />
      </DatePicker>
    </div>
  );

  // ─── Sections ─────────────────────────────────────────────────────────────

  const sections = (s: SaleInfo, r: SaleInfoRules) => {
    const saleMethods =
      r.saleMethodAllowed.length > 0
        ? lists.saleMethods.filter(
            (o) => r.saleMethodAllowed.includes(o.code) || o.code === s.saleMethodCode,
          )
        : lists.saleMethods;
    const categories = r.category3Allowed
      ? lists.categories
      : lists.categories.filter((o) => o.code !== '3');

    const sale: DetailField[] = [
      field(
        'Sale Method',
        coded(s.saleMethodCode, s.saleMethodDesc),
        isOpen(),
        () =>
          select(
            'saleMethodCode',
            'Sale Method',
            withCurrent(saleMethods, s.saleMethodCode, s.saleMethodDesc),
          ),
        { optional: !r.saleMethodMandatory && r.saleMethodAllowed.length === 0 },
      ),
      field(
        'Sale Type',
        coded(s.saleTypeCode, s.saleTypeDesc),
        isOpen(),
        () =>
          select(
            'saleTypeCode',
            'Sale Type',
            withCurrent(lists.saleTypes, s.saleTypeCode, s.saleTypeDesc),
          ),
        { optional: true },
      ),
      { label: 'Payment Method', value: coded(s.paymentMethodCode, s.paymentMethodDesc) },
      field(
        'Planned Sale Date',
        date(s.plannedSaleDate),
        isOpen(),
        () => dateInput('plannedSaleDate', 'Planned Sale Date'),
        { optional: !r.bctsFileType },
      ),
      field(
        'Tender Date',
        date(s.tenderOpeningDate),
        isOpen(),
        () => dateInput('tenderOpeningDate', 'Tender Date'),
        { optional: !r.tenderRequired },
      ),
      field(
        'Sales Volume',
        volume(s.saleVolume),
        isOpen(),
        () => text('saleVolume', 'Sales Volume'),
        {
          optional: true,
        },
      ),
      field(
        'Total Bidders',
        dash(s.totalBidders),
        isOpen(),
        () => text('totalBidders', 'Total Bidders', 'sm', 4),
        { optional: true },
      ),
      field('Salvage Indicator', yesNo(s.salvageInd), isOpen(r.salvage), () =>
        select('salvageInd', 'Salvage Indicator', YES_NO, null),
      ),
      field('Minor Processing Facility', yesNo(s.minorFacilityInd), isOpen(r.minorFacility), () =>
        select('minorFacilityInd', 'Minor Processing Facility', YES_NO, null),
      ),
    ];

    const deposits: DetailField[] = [
      field(
        'Security Deposit Amount',
        dollars(s.scrtyDepositAmt),
        isOpen(),
        () => text('scrtyDepositAmt', 'Security Deposit Amount'),
        { optional: s.fileTypeCode !== 'A29' },
      ),
      field(
        'Security Deposit Type',
        coded(s.scrtyDepositCode, s.scrtyDepositDesc),
        isOpen(),
        () =>
          select(
            'scrtyDepositCode',
            'Security Deposit Type',
            withCurrent(lists.deposits, s.scrtyDepositCode, s.scrtyDepositDesc),
          ),
        { optional: true },
      ),
      field(
        'Other Deposit Amount',
        dollars(s.otherDepositAmt),
        isOpen(),
        () => text('otherDepositAmt', 'Other Deposit Amount'),
        { optional: true },
      ),
      field(
        'Other Deposit Type',
        coded(s.otherDepositCode, s.otherDepositDesc),
        isOpen(),
        () =>
          select(
            'otherDepositCode',
            'Other Deposit Type',
            withCurrent(lists.deposits, s.otherDepositCode, s.otherDepositDesc),
          ),
        { optional: true },
      ),
      {
        label: 'Annual Rent',
        value:
          s.annualRent === null
            ? '—'
            : s.annualRent === 'N/A'
              ? 'N/A'
              : money.format(Number(s.annualRent)),
      },
      r.bcts
        ? { label: 'Lump Sum Bonus Offer', value: dollars(s.bctsLumpSumBonusOffer) }
        : field(
            'Lump Sum Bonus Offer',
            dollars(s.ftaBonusOffer),
            isOpen(),
            () => text('ftaBonusOffer', 'Lump Sum Bonus Offer'),
            { optional: true },
          ),
      r.bcts
        ? { label: 'Bonus Bid', value: dollars(s.bctsBonusBid) }
        : field(
            'Bonus Bid',
            dollars(s.ftaBonusBid),
            isOpen(),
            () => text('ftaBonusBid', 'Bonus Bid'),
            { optional: true },
          ),
    ];

    const bcts: DetailField[] = [
      field(
        'Planned BCTS Cat.',
        coded(s.plannedSbCatCode, s.plannedSbCatDesc),
        isOpen(),
        () =>
          select(
            'plannedSbCatCode',
            'Planned BCTS Cat.',
            withCurrent(categories, s.plannedSbCatCode, s.plannedSbCatDesc),
          ),
        { optional: !r.bctsFileType },
      ),
      field(
        'Sold BCTS Cat.',
        coded(s.soldSbCatCode, s.soldSbCatDesc),
        isOpen(),
        () =>
          select(
            'soldSbCatCode',
            'Sold BCTS Cat.',
            withCurrent(categories, s.soldSbCatCode, s.soldSbCatDesc),
          ),
        { optional: !r.soldCatRequired },
      ),
      { label: 'BCTS Fund Indicator', value: yesNo(s.bctsFundInd) },
      { label: 'BCTS Org', value: dash(s.bctsOrgDesc) },
      field('Within Admin Area', yesNo(s.adminAreaInd), isOpen(r.withinAdminArea), () =>
        select('adminAreaInd', 'Within Admin Area', YES_NO, null),
      ),
      field(
        'Admin Area File',
        dash(s.adminAreaFile),
        isOpen(r.adminAreaFile),
        () => text('adminAreaFile', 'Admin Area File', 'sm', 10),
        { optional: !(form?.adminAreaInd === 'Y') },
      ),
    ];

    const cash: DetailField[] = [
      field(
        'Estimated Volume',
        volume(s.cashSaleEstVol),
        isOpen(r.estimatedVolume),
        () => text('cashSaleEstVol', 'Estimated Volume'),
        { optional: !r.cashFieldsRequired },
      ),
      field(
        'Total Dollars Received',
        dollars(s.cashSaleTotDol),
        isOpen(r.cashSale),
        () => text('cashSaleTotDol', 'Total Dollars Received'),
        { optional: !r.cashFieldsRequired },
      ),
    ];

    return { sale, deposits, bcts, cash };
  };

  return (
    <div className={editing ? 'fsp-info__tab-panel detail-edit' : 'fsp-info__tab-panel'}>
      <AsyncBoundary
        loading={loading && !data}
        error={data ? undefined : error}
        onRetry={reload}
        loadingText="Loading sale information…"
      >
        {data &&
          (() => {
            const s = sections(data, data.rules);
            return (
              <>
                <div className="detail-edit__toolbar">
                  {editing ? (
                    <p className="detail-edit__strap">
                      All fields are required unless marked optional.
                    </p>
                  ) : (
                    <>
                      <Button
                        kind="tertiary"
                        size="sm"
                        renderIcon={Edit}
                        disabled={!canEdit || !data.rules.editable}
                        onClick={onEdit}
                      >
                        Edit
                      </Button>
                    </>
                  )}
                </div>

                <div className="fsp-info__tile-row">
                  <DetailTile title="Sale" icon={Currency} fields={s.sale} />
                  <DetailTile title="Deposits and bonus" icon={Wallet} fields={s.deposits} />
                </div>
                <div className="fsp-info__tile-row">
                  <DetailTile title="BCTS and admin area" icon={Enterprise} fields={s.bcts} />
                  {data.rules.cashSale && (
                    <DetailTile title="Cash sale" icon={Receipt} fields={s.cash} />
                  )}
                </div>

                {editing && (
                  <div className="detail-edit__actions">
                    <Button kind="tertiary" size="md" disabled={saving} onClick={onCancel}>
                      Cancel
                    </Button>
                    <Button
                      kind="primary"
                      size="md"
                      disabled={saving}
                      onClick={() => void onSave()}
                    >
                      {saving ? 'Saving…' : 'Save changes'}
                    </Button>
                  </div>
                )}
              </>
            );
          })()}
      </AsyncBoundary>
    </div>
  );
};

export default SaleInfoPanel;
