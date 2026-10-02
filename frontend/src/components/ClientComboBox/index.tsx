import { ComboBox } from '@carbon/react';
import { useEffect, useMemo, useRef, useState } from 'react';

import { suggestClients, type ClientSearchResult } from '@/services/client_search';

import type { FC } from 'react';

/** Characters typed before the first request — shorter prefixes match far too much. */
const MIN_CHARS = 3;

/** Idle time before the request goes out, so a fast typist makes one call, not eight. */
const DEBOUNCE_MS = 350;

type ClientOption = {
  id: string;
  clientNumber: string;
  clientLocnCode: string;
  label: string;
};

const optionFrom = (c: ClientSearchResult): ClientOption => {
  const name = c.clientName ?? '';
  const place = c.city ?? c.clientLocnName ?? '';
  return {
    id: `${c.clientNumber}|${c.clientLocnCode}`,
    clientNumber: c.clientNumber ?? '',
    clientLocnCode: c.clientLocnCode ?? '',
    label: `${c.clientNumber ?? ''} ${c.clientLocnCode ?? ''} — ${name}${
      place ? ` (${place})` : ''
    }`,
  };
};

interface ClientComboBoxProps {
  /** Unique per screen — several search screens render this field. */
  id: string;
  /** Client number criterion, as held in the search form. */
  clientNumber?: string;
  /** Client location criterion, as held in the search form. */
  clientLocnCode?: string;
  /** Client name criterion — what a typed-but-unpicked value becomes. */
  clientName?: string;
  onChange: (next: { clientNumber: string; clientLocnCode: string; clientName: string }) => void;
  /** Overrides the default hint where a screen needs to say something else. */
  helperText?: string;
}

/**
 * One field in place of the client number, location and name inputs.
 *
 * Two ways to use it, because both are real:
 *
 * <ul>
 *   <li>Pick a suggestion — that exact client and location become the criteria,
 *       and the name criterion is cleared so it cannot contradict them.</li>
 *   <li>Type and search without picking — the text becomes the client-name
 *       prefix, which is how the old name field behaved. Searching every client
 *       whose name starts with "canfor" stays possible.</li>
 * </ul>
 *
 * Suggestions come from the server: {@code FOREST_CLIENT} is far too large to
 * load into the browser, unlike the code lists behind the other dropdowns. The
 * client number or name is matched as a prefix, after {@link MIN_CHARS}
 * characters and {@link DEBOUNCE_MS} of quiet.
 */
const ClientComboBox: FC<ClientComboBoxProps> = ({
  id,
  clientNumber,
  clientLocnCode,
  clientName,
  onChange,
  helperText = 'Pick a client, or type a name to search every client starting with it',
}) => {
  const [options, setOptions] = useState<ClientOption[]>([]);
  const [typed, setTyped] = useState('');
  // Guards against an earlier, slower response overwriting a later one.
  const latest = useRef(0);
  // The labels currently on offer. Carbon reports the label it writes into the
  // input after a selection through onInputChange too, and — for a click — it
  // does so BEFORE onChange, so a flag set in onChange cannot guard against it.
  // Recognising the text as one of the offered labels is order-independent.
  const offeredLabels = useMemo(() => new Set(options.map((o) => o.label)), [options]);

  useEffect(() => {
    const text = typed.trim();
    if (text.length < MIN_CHARS) {
      setOptions([]);
      return;
    }
    const ticket = ++latest.current;
    const timer = setTimeout(() => {
      suggestClients(text)
        .then((rows) => {
          if (latest.current === ticket) setOptions(rows.map(optionFrom));
        })
        .catch(() => {
          // A failed suggest is not worth a toast — the user is mid-word, and
          // the field still works as a name search.
          if (latest.current === ticket) setOptions([]);
        });
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [typed]);

  // A pinned client shows as a selection; otherwise the field carries the name
  // criterion as plain text. Derived from the criteria, so the screen's "Clear
  // all" empties the field without this component tracking it.
  const selectedItem = useMemo(() => {
    if (!clientNumber) return null;
    return (
      options.find(
        (o) => o.clientNumber === clientNumber && o.clientLocnCode === (clientLocnCode ?? ''),
      ) ?? {
        id: `${clientNumber}|${clientLocnCode ?? ''}`,
        clientNumber,
        clientLocnCode: clientLocnCode ?? '',
        label: `${clientNumber} ${clientLocnCode ?? ''}`.trim(),
      }
    );
  }, [options, clientNumber, clientLocnCode]);

  // Carbon owns the input's text (its ComboBox takes no value prop), so the box
  // cannot be emptied by passing empty criteria in. Remounting it does empty it.
  // The key flips only when the criteria are cleared from outside while this
  // field still holds text — a screen's "Clear all". A user deleting the text
  // themselves clears `typed` in the same render, so the key does not flip and
  // their focus is left alone.
  const clearedFromOutside = !clientNumber && !clientName && typed !== '';

  return (
    <ComboBox
      key={clearedFromOutside ? 'cleared' : 'live'}
      id={id}
      titleText="Client"
      placeholder="Client number or name"
      helperText={helperText}
      items={options}
      itemToString={(item) => item?.label ?? ''}
      selectedItem={selectedItem}
      // The server has already filtered; filtering again would hide matches
      // whose label is composed differently from the typed text.
      shouldFilterItem={() => true}
      // Keeps text that matches no suggestion, which is what makes the
      // name-prefix half of this field work: without it Carbon discards the
      // typed value and the criterion would vanish on blur.
      allowCustomValue
      onInputChange={(value) => {
        const text = value ?? '';
        // Carbon reports the label it writes into the input here as well as real
        // typing, so both echoes have to be recognised and ignored:
        //   - a click reports the label BEFORE the selection handler runs, so
        //     the pin is not set yet — the text matching a suggestion on offer
        //     is what identifies it;
        //   - later echoes (blur, re-render) arrive once the pin is set, and
        //     match its label.
        // Returning before `setTyped` matters: querying for a full label would
        // come back empty, drop every suggestion, and leave the next echo
        // looking like typing — which cleared the selection a moment after it
        // was made.
        if (offeredLabels.has(text) || text === selectedItem?.label) {
          return;
        }
        setTyped(text);
        // Typing after a pick releases it: the text is a name search again.
        onChange({ clientNumber: '', clientLocnCode: '', clientName: text });
      }}
      onChange={({ selectedItem: picked }) =>
        onChange({
          clientNumber: picked?.clientNumber ?? '',
          clientLocnCode: picked?.clientLocnCode ?? '',
          clientName: '',
        })
      }
    />
  );
};

export default ClientComboBox;
