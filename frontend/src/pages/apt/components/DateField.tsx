import { DatePicker, DatePickerInput } from '@carbon/react';

import { parseTypedDate, TYPED_DATE_PATTERN } from '@/utils/typedDate';

import type { FC } from 'react';

/** yyyy-mm-dd in local time. */
const toIsoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

interface DateFieldProps {
  id: string;
  labelText: string;
  /** `yyyy-mm-dd`, or '' when empty. */
  value: string;
  onChange: (value: string) => void;
  invalidText?: string | null;
  disabled?: boolean;
}

/** A single date, picked from the calendar or typed as `yyyy-mm-dd`, filling its column. */
const DateField: FC<DateFieldProps> = ({
  id,
  labelText,
  value,
  onChange,
  invalidText,
  disabled,
}) => (
  <DatePicker
    datePickerType="single"
    dateFormat="Y-m-d"
    className="detail-dialog__date"
    value={value}
    invalid={!!invalidText}
    onChange={(dates: Date[]) => onChange(dates[0] ? toIsoDate(dates[0]) : '')}
  >
    <DatePickerInput
      id={id}
      labelText={labelText}
      placeholder="yyyy-mm-dd"
      pattern={TYPED_DATE_PATTERN}
      invalidText={invalidText ?? undefined}
      disabled={disabled}
      onChange={(e) => {
        const text = e.target.value;
        if (text.trim() === '') onChange('');
        else {
          const typed = parseTypedDate(text);
          if (typed) onChange(typed);
        }
      }}
    />
  </DatePicker>
);

export default DateField;
