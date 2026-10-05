import { Copy, Information } from '@carbon/icons-react';
import { Button, RadioButton, RadioButtonGroup, TextInput } from '@carbon/react';
import { useCallback, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import DetailTile, { type DetailField } from '@/components/DetailTile';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import {
  copyRotations,
  getCopyRotationTab,
  type CopyRotationRequest,
} from '@/services/tenure_rotations';

import type { TenurePanelProps } from './panelProps';

/** The most target years legacy's form had boxes for. */
const MAX_TARGET_YEARS = 9;

type Mode = 'listed' | 'everyOther';

interface Form {
  source: string;
  sourceYear: string;
  rangeUnit: string;
  mode: Mode;
  targetYears: string;
  startYear: string;
}

type Errors = Partial<Record<keyof Form, string>>;

const emptyForm = (): Form => ({
  source: '',
  sourceYear: '',
  rangeUnit: '',
  mode: 'listed',
  targetYears: '',
  startYear: '',
});

const splitYears = (raw: string) => raw.split(/[\s,]+/).filter(Boolean);

const yearError = (raw: string, label: string): string | undefined => {
  if (!/^\d+$/.test(raw)) return `${label} must be an integer.`;
  const n = Number(raw);
  return n < 1900 || n > 9999 ? `${label} field must be between 1900 and 9999.` : undefined;
};

/** Fta613CopyGhRotaForm's checks; the backend repeats them and FTA_613's own. */
const validate = (f: Form): Errors => {
  const e: Errors = {};
  const source = f.source.trim();
  if (!source) e.source = 'Source Tenure is mandatory.';
  else if (!/^[A-Za-z0-9]+$/.test(source))
    e.source = 'Source Tenure must be alpha numeric [A-Z, 0-9]';
  if (!f.sourceYear.trim()) e.sourceYear = 'Source Year is mandatory.';
  else {
    const err = yearError(f.sourceYear.trim(), 'Source Year');
    if (err) e.sourceYear = err;
  }
  if (f.rangeUnit.trim() && !/^[A-Za-z0-9]+$/.test(f.rangeUnit.trim()))
    e.rangeUnit = 'For Range Unit must be alpha numeric [A-Z, 0-9]';
  if (f.mode === 'listed') {
    const years = splitYears(f.targetYears);
    if (years.length === 0) e.targetYears = 'List the year(s) you wish to copy to.';
    else if (years.length > MAX_TARGET_YEARS)
      e.targetYears = `At most ${MAX_TARGET_YEARS} target years can be listed.`;
    else {
      const bad = years.map((y, i) => yearError(y, `Year ${i + 1}`)).find(Boolean);
      if (bad) e.targetYears = bad;
      else if (new Set(years).size < years.length)
        e.targetYears = 'Duplicate Target year(s) exists.';
    }
  } else if (!f.startYear.trim()) {
    e.startYear = 'Enter the year to start from.';
  } else {
    const err = yearError(f.startYear.trim(), 'Copy to Every other Target Year starting with Year');
    if (err) e.startYear = err;
  }
  return e;
};

/**
 * The Copy rotation tab of the tenure detail — legacy FTA613 (Copy Grazing/Hay Cutting
 * Rotations). Copies a source tenure's year (its range provision and rotations: livestock
 * for a grazing tenure, meadow for a hay cutting one) onto this tenure's listed target
 * years, or every other year from a start year to the end of the term. When target years
 * already have rotations, nothing is written until the user confirms overwriting them, as
 * legacy's second Save did.
 */
const CopyRotationPanel: FC<TenurePanelProps> = ({ tenure, canEdit }) => {
  const forestFileId = tenure.forestFileId;
  const { display } = useNotification();
  const fetcher = useCallback(() => getCopyRotationTab(forestFileId), [forestFileId]);
  const { data, loading, error, reload } = useApiResource(fetcher, [forestFileId]);

  const rules = data?.rules;
  const allowed = canEdit && !!rules?.edit;
  const disabledReason = canEdit && rules && !rules.edit ? rules.reason : null;
  const grazing = data?.kind === 'GRAZING';

  const [form, setForm] = useState<Form>(emptyForm);
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [confirm, setConfirm] = useState<string | null>(null);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const request = (overwrite: boolean): CopyRotationRequest => ({
    sourceForestFileId: form.source.trim().toUpperCase(),
    sourceYear: form.sourceYear.trim(),
    sourceRangeUnitId: grazing ? form.rangeUnit.trim().toUpperCase() : '',
    targetYears: form.mode === 'listed' ? splitYears(form.targetYears) : [],
    everyOtherYearFrom: form.mode === 'everyOther' ? form.startYear.trim() : '',
    overwrite,
    pfuRevisionCount: data?.pfuRevisionCount ?? null,
  });

  const run = async (overwrite: boolean) => {
    if (!overwrite) {
      const found = validate(form);
      if (Object.keys(found).length > 0) {
        setErrors(found);
        return;
      }
    }
    setSaving(true);
    try {
      const result = await copyRotations(forestFileId, request(overwrite));
      if (!result.copied) {
        setConfirm(result.message);
        return;
      }
      display({ kind: 'success', title: result.message, timeout: 8000 });
      setConfirm(null);
      // The copy bumps the file's revision count; re-read it for the next copy.
      reload();
    } catch (err) {
      setConfirm(null);
      display({
        kind: 'error',
        title: 'Could not copy the rotations',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 12000,
      });
    } finally {
      setSaving(false);
    }
  };

  const input = (
    key: 'source' | 'sourceYear' | 'rangeUnit' | 'targetYears' | 'startYear',
    label: string,
    maxLength: number,
    size: 'cell' | 'sm' = 'cell',
    helperText?: string,
  ) => (
    <div className={`detail-edit__input detail-edit__input--${size}`}>
      <TextInput
        id={`copy-rotation-${key}`}
        labelText={label}
        hideLabel
        maxLength={maxLength}
        helperText={helperText}
        value={form[key]}
        invalid={!!errors[key]}
        invalidText={errors[key]}
        disabled={saving || !allowed}
        onChange={(e) =>
          set(
            key,
            key === 'source' || key === 'rangeUnit' ? e.target.value.toUpperCase() : e.target.value,
          )
        }
      />
    </div>
  );

  const tenureFields: DetailField[] = data
    ? [
        {
          label: 'Copies',
          value: grazing
            ? 'Range provision and livestock (grazing) rotations'
            : 'Range provision and meadow (hay cutting) rotations',
        },
        {
          label: 'Tenure term',
          value:
            data.termStartYear !== null && data.termEndYear !== null
              ? `${data.termStartYear} – ${data.termEndYear}`
              : '—',
        },
        {
          label: 'Target years',
          value: 'Their rotations and range provision are replaced by the source year’s.',
          span2: true,
        },
      ]
    : [];

  const formFields: DetailField[] = [
    { label: 'Source Tenure', value: input('source', 'Source Tenure', 10) },
    { label: 'Source Year', value: input('sourceYear', 'Source Year', 4, 'sm') },
    ...(grazing
      ? [
          {
            label: 'For Range Unit (optional)',
            value: input(
              'rangeUnit',
              'For Range Unit',
              10,
              'cell',
              'Only that range unit’s rotations.',
            ),
          },
        ]
      : []),
    {
      label: 'Copy to',
      rowStart: true,
      span2: true,
      value: (
        <RadioButtonGroup
          name="copy-rotation-mode"
          legendText={<span className="cds--visually-hidden">Copy to</span>}
          valueSelected={form.mode}
          disabled={saving || !allowed}
          onChange={(v) => {
            set('mode', v as Mode);
            setErrors({});
          }}
        >
          <RadioButton id="copy-rotation-listed" value="listed" labelText="Listed target years" />
          <RadioButton
            id="copy-rotation-every-other"
            value="everyOther"
            labelText="Every other year, starting with"
          />
        </RadioButtonGroup>
      ),
    },
    form.mode === 'listed'
      ? {
          label: 'Target years',
          span2: true,
          value: input(
            'targetYears',
            'Target years',
            60,
            'cell',
            `Up to ${MAX_TARGET_YEARS} years, separated by commas.`,
          ),
        }
      : {
          label: 'Starting year',
          value: input(
            'startYear',
            'Starting year',
            4,
            'sm',
            'Then every second year to the end of the term.',
          ),
        },
  ];

  return (
    <div className="fsp-info__tab-panel detail-edit">
      <AsyncBoundary loading={loading} error={error} onRetry={reload} loadingText="Loading…">
        {data &&
          (!data.rules.applies ? (
            <EmptyState
              icon={<Copy size={48} />}
              title="Rotations can't be copied to this tenure"
              body={data.rules.reason ?? 'This tenure does not take rotations.'}
              action={
                <div className="detail-tab__empty-action">
                  <Button renderIcon={Copy} disabled>
                    Copy rotations
                  </Button>
                </div>
              }
            />
          ) : (
            <>
              <DetailTile title="This tenure" icon={Information} fields={tenureFields} />
              <DetailTile title="Copy rotations from" icon={Copy} fields={formFields} />
              <div className="detail-edit__actions">
                {disabledReason && <p className="detail-tab__reason">{disabledReason}</p>}
                <Button
                  kind="tertiary"
                  size="md"
                  disabled={saving || !allowed}
                  onClick={() => {
                    setForm(emptyForm());
                    setErrors({});
                  }}
                >
                  Clear
                </Button>
                <Button
                  kind="primary"
                  size="md"
                  renderIcon={Copy}
                  disabled={saving || !allowed}
                  onClick={() => void run(false)}
                >
                  {saving && !confirm ? 'Copying…' : 'Copy rotations'}
                </Button>
              </div>
            </>
          ))}
      </AsyncBoundary>

      {/* Overwrite confirmation — legacy's "Press Save again to overwrite". */}
      <Modal
        open={!!confirm}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading="Overwrite existing rotations?"
        onRequestClose={() => {
          if (!saving) setConfirm(null);
        }}
        preventCloseOnClickOutside
      >
        <p className="detail-dialog__subtitle">{confirm}</p>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={() => setConfirm(null)}>
            Cancel
          </Button>
          <Button kind="danger" disabled={saving} onClick={() => void run(true)}>
            {saving ? 'Copying…' : 'Overwrite'}
          </Button>
        </div>
      </Modal>
    </div>
  );
};

export default CopyRotationPanel;
