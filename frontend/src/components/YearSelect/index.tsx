import { Select, SelectItem } from '@carbon/react';
import { useMemo, type FC } from 'react';

import { yearOptions } from '@/lib/years';

interface YearSelectProps {
  id: string;
  labelText: string;
  value: string;
  onChange: (year: string) => void;
  /** The earliest year offered; the list runs from `last` down to it. */
  first: number;
  /** The latest year offered; the current year by default. */
  last?: number;
  /** Text of a blank first option, for a field with no default (e.g. "Choose a year"). */
  placeholder?: string;
  disabled?: boolean;
}

/** A year criterion as a drop-down of the years that make sense for the screen. */
const YearSelect: FC<YearSelectProps> = ({
  id,
  labelText,
  value,
  onChange,
  first,
  last,
  placeholder,
  disabled,
}) => {
  const years = useMemo(() => yearOptions(first, last), [first, last]);
  return (
    <Select
      id={id}
      labelText={labelText}
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
    >
      {placeholder !== undefined && <SelectItem value="" text={placeholder} />}
      {/* A year passed in from elsewhere (a link) that is outside the list still shows. */}
      {value !== '' && !years.includes(value) && <SelectItem value={value} text={value} />}
      {years.map((y) => (
        <SelectItem key={y} value={y} text={y} />
      ))}
    </Select>
  );
};

export default YearSelect;
