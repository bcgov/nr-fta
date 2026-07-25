import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
} from '@carbon/react';
import { DocumentTasks } from '@carbon/icons-react';
import { useState, type FC } from 'react';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import { submitBilling } from '@/services/billing_write';
import { MOCK_BILLING, billingTotal, type BillingLine } from '@/mocks/billing';

const cur = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' });

const STATUS_TAG: Record<BillingLine['status'], 'gray' | 'blue' | 'green' | 'teal'> = {
  Draft: 'gray',
  Submitted: 'blue',
  Approved: 'green',
  Invoiced: 'teal',
};

interface BillingReportScreenProps {
  title: string;
  legacyId: string;
  /** Label for the primary submit/generate action. */
  actionLabel: string;
  /** Short description of what the screen does. */
  description?: string;
}

/**
 * Shared billing/report screen for the FTA admin billing flows (Annual Rents &
 * Fees Prep FTA670, Tenure Billing Instructions FTA680, Pre/Post Billing
 * FTA685/686, Tenure Approval FTA690, Invoice Preview FTA695). Renders the
 * billing lines + total and a primary action that is mock-only.
 */
const BillingReportScreen: FC<BillingReportScreenProps> = ({ title, legacyId, actionLabel, description }) => {
  const notify = useNotification();
  const lines = MOCK_BILLING;
  const [saving, setSaving] = useState(false);

  const onAction = async () => {
    setSaving(true);
    try {
      const { submitted } = await submitBilling({
        calendarYear: String(new Date().getFullYear()),
        orgUnitNo: lines[0]?.orgUnitNo ?? '',
      });
      notify.display({
        kind: 'success',
        title: `${actionLabel} — done`,
        subtitle: `${submitted} billing request(s) queued for invoicing.`,
        timeout: 5000,
      });
    } catch (err) {
      notify.display({
        kind: 'error',
        title: `${actionLabel} failed`,
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout title={title}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
        <Tag type="outline">{legacyId}</Tag>
      </div>
      {description ? <p style={{ maxWidth: '44rem', marginBottom: '1.5rem' }}>{description}</p> : null}

      <TableContainer
        title="Billing lines"
        description={`${lines.length} line(s) — ${cur.format(billingTotal(lines))} total`}
      >
        <Table>
          <TableHead>
            <TableRow>
              <TableHeader>File / Agreement</TableHeader>
              <TableHeader>Client</TableHeader>
              <TableHeader>Org Unit</TableHeader>
              <TableHeader>Rent Due</TableHeader>
              <TableHeader>Fee Due</TableHeader>
              <TableHeader>Total</TableHeader>
              <TableHeader>Status</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {lines.map((l) => (
              <TableRow key={l.fileId}>
                <TableCell>{l.fileId}</TableCell>
                <TableCell>{l.client}</TableCell>
                <TableCell>{l.orgUnit}</TableCell>
                <TableCell>{cur.format(l.rentDue)}</TableCell>
                <TableCell>{cur.format(l.feeDue)}</TableCell>
                <TableCell>{cur.format(l.total)}</TableCell>
                <TableCell><Tag type={STATUS_TAG[l.status]}>{l.status}</Tag></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <div style={{ marginTop: '1.5rem' }}>
        <Button renderIcon={DocumentTasks} onClick={onAction} disabled={saving}>
          {saving ? `${actionLabel}…` : actionLabel}
        </Button>
      </div>
    </PageLayout>
  );
};

export default BillingReportScreen;
