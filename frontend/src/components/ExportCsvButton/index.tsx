import { Download } from '@carbon/icons-react';
import { Button, Loading } from '@carbon/react';
import { useState } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { safeErrorMessage } from '@/lib/errorMessage';
import { downloadCsv } from '@/services/csvExport';

import type { FC } from 'react';

import './ExportCsvButton.css';

interface ExportCsvButtonProps {
  /** The export endpoint, criteria already in the querystring. */
  path: string;
  /** Disabled while a search is in flight, or when there is nothing to export. */
  disabled?: boolean;
}

/**
 * Exports the current search results to CSV.
 *
 * Every matching row is exported, not just the page on screen, so a large
 * result set can take a while. A spinner appears beside the button for the
 * duration, and the button stays disabled until the file arrives — a second
 * click would run the whole query again.
 *
 * The label is fixed rather than naming the records, so every search screen
 * offers the same wording.
 */
const ExportCsvButton: FC<ExportCsvButtonProps> = ({ path, disabled }) => {
  const [exporting, setExporting] = useState(false);
  const { display } = useNotification();

  const onExport = async () => {
    setExporting(true);
    try {
      await downloadCsv(path);
    } catch (e) {
      display({
        kind: 'error',
        title: 'Export failed',
        subtitle: safeErrorMessage(e, 'The results could not be exported.'),
        timeout: 7000,
      });
    } finally {
      setExporting(false);
    }
  };

  return (
    <span className="export-csv">
      <Button
        kind="ghost"
        size="sm"
        renderIcon={Download}
        disabled={disabled || exporting}
        onClick={() => void onExport()}
      >
        Export results to CSV
      </Button>
      {exporting && (
        // aria-live so a screen reader announces that the export started; the
        // spinner itself is decorative, and Loading's description names it.
        <span className="export-csv__spinner" role="status" aria-live="polite">
          <Loading small withOverlay={false} description="Preparing CSV export" />
        </span>
      )}
    </span>
  );
};

export default ExportCsvButton;
