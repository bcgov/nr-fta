import { ComboBox } from '@carbon/react';
import { useMemo } from 'react';

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
  /** Lowercased haystack the filter matches against. */
  haystack: string;
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
        haystack: `${u.mgmtUnitTypeCode} ${u.typeDescription} all units`.toLowerCase(),
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
      haystack: `${u.mgmtUnitTypeCode} ${u.mgmtUnitId} ${name} ${u.typeDescription}`.toLowerCase(),
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
  const items = useMemo(() => buildOptions(units, typeEntrySuffix), [units, typeEntrySuffix]);

  const selectedItem = useMemo(
    () =>
      items.find((o) => o.typeCode === (typeCode ?? '') && (o.unitId ?? '') === (unitId ?? '')) ??
      null,
    [items, typeCode, unitId],
  );

  return (
    <ComboBox
      id={id}
      // Carbon's ComboBox has no hideLabel; a visually hidden title keeps the
      // field named for screen readers when the screen shows its own label.
      titleText={hideLabel ? <span className="cds--visually-hidden">{titleText}</span> : titleText}
      placeholder="Search by type, ID or name"
      helperText={helperText}
      invalid={invalid}
      invalidText={invalidText}
      disabled={disabled}
      items={items}
      itemToString={(item) => item?.label ?? ''}
      selectedItem={selectedItem}
      shouldFilterItem={({ item, inputValue }) =>
        !inputValue || item.haystack.includes(inputValue.toLowerCase())
      }
      onChange={({ selectedItem: picked }) =>
        onChange({ mgmtUnitType: picked?.typeCode ?? '', mgmtUnitId: picked?.unitId ?? '' })
      }
    />
  );
};

export default ManagementUnitComboBox;
