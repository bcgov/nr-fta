import { DatePicker, DatePickerInput, Select, SelectItem, TextInput } from '@carbon/react';

import type { SuspensionCode } from '@/services/suspend_blocks';
import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

import type { SuspensionForm } from './suspensionForm';
import type { FC } from 'react';

const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

interface Props {
  idPrefix: string;
  value: SuspensionForm;
  onChange: (next: SuspensionForm) => void;
  partitionCodes: SuspensionCode[];
  disabled: boolean;
}

/** The four mandatory fields legacy FTA912 and FTA914 share. */
const SuspensionFields: FC<Props> = ({ idPrefix, value, onChange, partitionCodes, disabled }) => {
  const dateInput = (key: 'startDate' | 'endDate', label: string) => (
    <DatePicker
      datePickerType="single"
      dateFormat="Y-m-d"
      className="detail-dialog__date"
      value={value[key]}
      onChange={(dates: Date[]) =>
        onChange({ ...value, [key]: dates[0] ? toIsoDate(dates[0]) : '' })
      }
    >
      <DatePickerInput
        id={`${idPrefix}-${key}`}
        labelText={label}
        placeholder="yyyy-mm-dd"
        disabled={disabled}
        pattern={TYPED_DATE_PATTERN}
        onChange={(e) => {
          const text = e.target.value;
          if (text.trim() === '') onChange({ ...value, [key]: '' });
          else {
            const typed = parseTypedDate(text);
            if (typed) onChange({ ...value, [key]: typed });
          }
        }}
      />
    </DatePicker>
  );

  return (
    <div style={{ display: 'grid', gap: '1rem', maxWidth: '40rem' }}>
      <Select
        id={`${idPrefix}-partition`}
        labelText="Suspension Partition Code"
        value={value.partitionCode}
        disabled={disabled}
        onChange={(e) => onChange({ ...value, partitionCode: e.target.value })}
      >
        <SelectItem value="" text="" />
        {partitionCodes.map((c) => (
          <SelectItem key={c.code} value={c.code} text={`${c.code} - ${c.description}`} />
        ))}
      </Select>
      <TextInput
        id={`${idPrefix}-order`}
        labelText="Suspension Order Number"
        maxLength={10}
        value={value.orderNumber}
        disabled={disabled}
        onChange={(e) => onChange({ ...value, orderNumber: e.target.value })}
      />
      {dateInput('startDate', 'Suspension Start Date')}
      {dateInput('endDate', 'Suspension End Date')}
    </div>
  );
};

export default SuspensionFields;
