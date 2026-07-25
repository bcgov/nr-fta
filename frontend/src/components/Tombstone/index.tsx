import { Column, Grid } from '@carbon/react';
import type { FC, ReactNode } from 'react';
import './Tombstone.css';

export interface TombstoneItem {
  label: string;
  value: ReactNode;
}

interface TombstoneProps {
  /** Key/value identifiers pinned to the top of a detail screen. */
  items: TombstoneItem[];
  /** Optional right-aligned action (e.g. an Edit button, admin-gated). */
  action?: ReactNode;
  /** Accessible label for the summary region. */
  ariaLabel?: string;
}

/**
 * Persistent "tombstone" summary bar shown at the top of FTA record-detail
 * screens (mirrors the legacy ftaTombstone.jsp include). Renders key
 * identifiers as a responsive grid of label/value pairs.
 */
const Tombstone: FC<TombstoneProps> = ({ items, action, ariaLabel = 'Record summary' }) => (
  <section className="fta-tombstone" aria-label={ariaLabel}>
    <Grid narrow>
      {items.map((item) => (
        <Column key={item.label} sm={2} md={2} lg={3}>
          <dt className="fta-tombstone__label">{item.label}</dt>
          <dd className="fta-tombstone__value">{item.value}</dd>
        </Column>
      ))}
    </Grid>
    {action ? <div className="fta-tombstone__action">{action}</div> : null}
  </section>
);

export default Tombstone;
