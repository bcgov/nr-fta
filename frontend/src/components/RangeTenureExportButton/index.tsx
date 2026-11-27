import { Download } from '@carbon/icons-react';
import { Button, Loading } from '@carbon/react';
import { useState } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { safeErrorMessage } from '@/lib/errorMessage';

import type { FC } from 'react';

import '@/components/ExportCsvButton/ExportCsvButton.css';

interface RangeTenureExportButtonProps {
  /** Fetches and saves the workbook. */
  onExport: () => Promise<void>;
  kind?: 'ghost' | 'tertiary';
  size?: 'sm' | 'md';
}

/**
 * Legacy's Export button: the FTA001 Range Tenure Export, an Excel workbook
 * districts mail-merge into legal documents. Labelled as legacy labelled it.
 *
 * The button stays disabled, with a spinner beside it, until the file arrives;
 * a second click would run the whole report again.
 */
const RangeTenureExportButton: FC<RangeTenureExportButtonProps> = ({
  onExport,
  kind = 'ghost',
  size = 'sm',
}) => {
  const [exporting, setExporting] = useState(false);
  const { display } = useNotification();

  const run = async () => {
    setExporting(true);
    try {
      await onExport();
    } catch (e) {
      display({
        kind: 'error',
        title: 'Export failed.',
        subtitle: safeErrorMessage(e, 'The range tenure export could not be produced.'),
        timeout: 7000,
      });
    } finally {
      setExporting(false);
    }
  };

  return (
    <span className="export-csv">
      <Button
        kind={kind}
        size={size}
        renderIcon={Download}
        disabled={exporting}
        onClick={() => void run()}
      >
        Export
      </Button>
      {exporting && (
        <span className="export-csv__spinner" role="status" aria-live="polite">
          <Loading small withOverlay={false} description="Preparing range tenure export" />
        </span>
      )}
    </span>
  );
};

export default RangeTenureExportButton;
