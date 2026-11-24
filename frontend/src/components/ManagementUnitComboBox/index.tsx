import { ComboBox } from '@carbon/react';
import { useMemo, useState } from 'react';

import type { ManagementUnit } from '@/services/codeLists';

import type { FC } from 'react';

/**
 * One entry in the autocomplete.
 *
 * Two kinds share the list: a whole type ("every TSA") and a single unit.
 * Legacy let a user search on the type alone with the unit id blank, and a
 * single combined field would otherwise lose that, so types are offered too.
 */
type MgmtUnitOption = {
  id: string;
  typeCode: string;
  /** Absent on a type entry, which searches every unit of that type. */
  unitId?: string;
  label: string;
  /** Lowercased words the filter matches typed words against. */
  words: string[];
};

/** Lowercased words of a text, split at spaces and punctuation. */
const wordsOf = (text: string) =>
  text
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter(Boolean);

/** "T - Timber Supply Area" → "tsa", so a type is found by the acronym people use. */
const acronymOf = (typeDescription: string) => {
  const name = typeDescription.replace(/^\s*\S+\s+-\s+/, '');
  const initials = wordsOf(name)
    .map((w) => w[0])
    .join('');
  return initials.length > 1 ? [initials] : [];
};

/** Digits compared as numbers, so "1" finds unit "01". */
const sameNumber = (a: string, b: string) =>
  /^\d+$/.test(a) && /^\d+$/.test(b) && Number(a) === Number(b);

/**
 * How well the typed words match an entry, or null when one of them doesn't. Every typed word
 * must match a word of the entry: whole (or the same number) scores most, then the start of a
 * word, then anywhere in it. So "tsa 1", "1 tsa" and "arrow" each narrow the list at once, with
 * the best matches first.
 */
const scoreOf = (option: MgmtUnitOption, typed: string[]): number | null => {
  let total = 0;
  for (const t of typed) {
    let best = 0;
    for (const w of option.words) {
      if (w === t || sameNumber(w, t)) best = Math.max(best, 3);
      else if (w.startsWith(t)) best = Math.max(best, 2);
      else if (w.includes(t)) best = Math.max(best, 1);
    }
    if (best === 0) return null;
    total += best;
  }
  return total;
};

/**
 * The autocomplete's entries: each type once, then every unit.
 *
 * The type entries are derived from the units themselves rather than from the
 * type code list, so a type with no units never appears as a dead end.
 */
const buildOptions = (units: ManagementUnit[], typeSuffix: string): MgmtUnitOption[] => {
  const types = new Map<string, MgmtUnitOption>();
  const unitItems: MgmtUnitOption[] = [];

  for (const u of units) {
    if (!types.has(u.mgmtUnitTypeCode)) {
      types.set(u.mgmtUnitTypeCode, {
        id: u.mgmtUnitTypeCode,
        typeCode: u.mgmtUnitTypeCode,
        label: `${u.typeDescription}${typeSuffix}`,
        words: [
          ...wordsOf(`${u.mgmtUnitTypeCode} ${u.typeDescription}`),
          ...acronymOf(u.typeDescription),
        ],
      });
    }

    // FOREST_MGMT_UNIT.MGMT_UNIT_ID is nullable, and a row without one stands
    // for the type as a whole — "Z - Outside Managed Units" has no units under
    // it at all. The type entry above already covers it, so listing it again
    // would duplicate every such type in the dropdown.
    if (!u.mgmtUnitId) {
      continue;
    }

    const name = u.description ?? '';
    unitItems.push({
      id: `${u.mgmtUnitTypeCode}|${u.mgmtUnitId}`,
      typeCode: u.mgmtUnitTypeCode,
      unitId: u.mgmtUnitId,
      label: `${u.mgmtUnitTypeCode} ${u.mgmtUnitId}${name ? ` — ${name}` : ''}`,
      words: [
        ...wordsOf(`${u.mgmtUnitTypeCode} ${u.mgmtUnitId} ${name} ${u.typeDescription}`),
        ...acronymOf(u.typeDescription),
        // "T01" / "tsa01" typed without a space.
        `${u.mgmtUnitTypeCode}${u.mgmtUnitId}`.toLowerCase(),
        ...acronymOf(u.typeDescription).map((a) => `${a}${u.mgmtUnitId}`.toLowerCase()),
      ],
    });
  }

  return [...types.values(), ...unitItems];
};

interface ManagementUnitComboBoxProps {
  /** Unique per screen — several search screens render this field. */
  id: string;
  /** The list from `getManagementUnits()`; the screen loads it with its other code lists. */
  units: ManagementUnit[];
  /** Currently selected management unit type code, as held in the search form. */
  typeCode?: string;
  /** Currently selected management unit id, as held in the search form. */
  unitId?: string;
  /** Receives both criteria at once; empty strings when the field is cleared. */
  onChange: (next: { mgmtUnitType: string; mgmtUnitId: string }) => void;
  /** Defaults suit a search filter; an edit form passes its own. */
  titleText?: string;
  helperText?: string;
  hideLabel?: boolean;
  /**
   * Appended to a type-only entry. A search reads it as "every unit of the
   * type"; on a record it is a unit with no id (e.g. Z), so an edit form
   * passes ''.
   */
  typeEntrySuffix?: string;
  invalid?: boolean;
  invalidText?: string;
  disabled?: boolean;
}

/**
 * One field for a whole management unit, replacing the separate type and ID
 * inputs the legacy screens carried.
 *
 * Legacy filled those two fields from its SIL004 popup search; nobody should
 * have to know a unit id by heart, so this searches type code, unit id, unit
 * name and spelled-out type as the user types. Selecting an entry sets both
 * criteria, leaving each screen's search contract unchanged.
 *
 * The selection is derived from the passed-in criteria rather than held here,
 * so a screen's "Clear all" and its export's criteria snapshot stay correct
 * without this component knowing about either.
 */
const ManagementUnitComboBox: FC<ManagementUnitComboBoxProps> = ({
  id,
  units,
  typeCode,
  unitId,
  onChange,
  titleText = 'Management unit',
  helperText = 'Leave blank to search every management unit',
  hideLabel = false,
  typeEntrySuffix = ' (all units)',
  invalid,
  invalidText,
  disabled,
}) => {
  const allItems = useMemo(() => buildOptions(units, typeEntrySuffix), [units, typeEntrySuffix]);
  const [typed, setTyped] = useState('');

  const selectedItem = useMemo(
    () =>
      allItems.find(
        (o) => o.typeCode === (typeCode ?? '') && (o.unitId ?? '') === (unitId ?? ''),
      ) ?? null,
    [allItems, typeCode, unitId],
  );

  // The entries matching what is typed, best first. The selected entry's own label in the
  // input (as after a pick) lists everything, as an empty input does.
  const items = useMemo(() => {
    const words = wordsOf(typed);
    if (words.length === 0 || (selectedItem && typed === selectedItem.label)) return allItems;
    return allItems
      .map((o, i) => ({ o, i, score: scoreOf(o, words) }))
      .filter((x): x is { o: MgmtUnitOption; i: number; score: number } => x.score !== null)
      .sort((a, b) => b.score - a.score || a.i - b.i)
      .map((x) => x.o);
  }, [allItems, typed, selectedItem]);

  return (
    <ComboBox
      id={id}
      // Carbon's ComboBox has no hideLabel; a visually hidden title keeps the
      // field named for screen readers when the screen shows its own label.
      titleText={hideLabel ? <span className="cds--visually-hidden">{titleText}</span> : titleText}
      placeholder="Type e.g. TSA 1, TFL 48 or a name"
      helperText={helperText}
      invalid={invalid}
      invalidText={invalidText}
      disabled={disabled}
      items={items}
      itemToString={(item) => item?.label ?? ''}
      selectedItem={selectedItem}
      // Filtered and ranked above, as Carbon's filter can't reorder.
      shouldFilterItem={() => true}
      onInputChange={(text) => setTyped(text ?? '')}
      onChange={({ selectedItem: picked }) =>
        onChange({ mgmtUnitType: picked?.typeCode ?? '', mgmtUnitId: picked?.unitId ?? '' })
      }
    />
  );
};

export default ManagementUnitComboBox;
