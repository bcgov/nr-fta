import { useEffect, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import { Modal } from '@/components/Modal';
import { useNotification } from '@/context/notification/useNotification';
import { useApiResource } from '@/hooks/useApiResource';
import { safeErrorMessage } from '@/lib/errorMessage';
import type { InvoicePreview } from '@/services/billing';

import InvoiceView from './InvoiceView';

interface Props {
  forestFileId: string;
  /** Reads the FTA695 invoice; must be stable (memoized) for the dialog's life. */
  fetcher: () => Promise<InvoicePreview>;
  onClose: () => void;
}

/**
 * The FTA695 invoice for one tenure, over the screen that asked for it: FTA680's Preview and
 * FTA100R's Preview Invoice. When FTA695 refuses the tenure (wrong file status, no client, no
 * range provision) legacy showed only its errors, so the dialog closes and the errors are a
 * snackbar, one per line.
 */
const InvoicePreviewModal: FC<Props> = ({ forestFileId, fetcher, onClose }) => {
  const notify = useNotification();
  const { data, loading, error } = useApiResource<InvoicePreview>(fetcher, [fetcher]);

  useEffect(() => {
    if (!error) return;
    notify.display({
      kind: 'error',
      title: `Could not preview the invoice for ${forestFileId}.`,
      subtitle: safeErrorMessage(error),
      timeout: 0,
    });
    onClose();
    // Once per failure.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [error]);

  if (error) return null;
  return (
    <Modal
      open
      passiveModal
      size="md"
      modalHeading={`Invoice Preview — ${forestFileId}`}
      onRequestClose={onClose}
    >
      <AsyncBoundary loading={loading} loadingText="Pricing the invoice…">
        {data && <InvoiceView invoice={data} />}
      </AsyncBoundary>
    </Modal>
  );
};

export default InvoicePreviewModal;
