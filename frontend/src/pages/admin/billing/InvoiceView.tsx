import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';

import DefinitionGrid from '@/components/DefinitionGrid';
import type { InvoicePreview } from '@/services/billing';

import type { FC } from 'react';

const money = new Intl.NumberFormat('en-CA', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const fmt = (n: number | null) => (n == null ? '' : money.format(n));

/**
 * FTA695's invoice: who it is for, then the charge lines, subtotal, GST and
 * total. Shared by the Invoice Preview screen and FTA680's Preview.
 */
const InvoiceView: FC<{ invoice: InvoicePreview }> = ({ invoice: v }) => {
  const address = [v.address1, v.address2, v.address3].filter(Boolean).join(', ');
  return (
    <>
      <h4 className="billing__invoice-title">
        {v.calendarYear} Fees for {v.fileTypeDescription}
      </h4>
      <DefinitionGrid
        items={[
          { label: 'Tenure', value: v.forestFileId },
          { label: 'Region', value: v.regionCode ?? '—' },
          { label: 'District', value: v.districtCode ?? '—' },
          { label: 'Name', value: v.clientName },
          { label: 'Address', value: address || '—' },
          { label: 'City', value: v.city ?? '—' },
          { label: 'Province', value: v.province ?? '—' },
          { label: 'Postal Code', value: v.postalCode ?? '—' },
        ]}
      />
      <div className="bordered-table billing__invoice-lines">
        <TableContainer>
          <Table size="sm">
            <TableHead>
              <TableRow>
                <TableHeader className="billing__num">Quantity</TableHeader>
                <TableHeader>Description</TableHeader>
                <TableHeader className="billing__num">Amount ($)</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {v.lines.map((l) => (
                <TableRow key={l.description}>
                  <TableCell className="billing__num">{l.quantity ?? ''}</TableCell>
                  <TableCell>{l.description}</TableCell>
                  <TableCell className="billing__num">{fmt(l.amount)}</TableCell>
                </TableRow>
              ))}
              <TableRow>
                <TableCell />
                <TableCell>Sub-Total</TableCell>
                <TableCell className="billing__num">{fmt(v.subtotal)}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell />
                <TableCell>GST @ {v.taxRate}%</TableCell>
                <TableCell className="billing__num">{fmt(v.taxAmount)}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell />
                <TableCell>
                  <strong>Total Charge</strong>
                </TableCell>
                <TableCell className="billing__num">
                  <strong>{fmt(v.total)}</strong>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </TableContainer>
      </div>
    </>
  );
};

export default InvoiceView;
