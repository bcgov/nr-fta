import { useCallback, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import { Modal } from '@/components/Modal';
import { useApiResource } from '@/hooks/useApiResource';
import { getInvoicePreview, type InvoicePreview } from '@/services/billing';

import InvoiceView from '../InvoiceView';

interface Props {
  forestFileId: string;
  calendarYear: string;
  onClose: () => void;
}

/** FTA680's Preview: the FTA695 invoice for one row, over the list. */
const InvoicePreviewModal: FC<Props> = ({ forestFileId, calendarYear, onClose }) => {
  const fetcher = useCallback(
    () => getInvoicePreview(forestFileId, calendarYear),
    [forestFileId, calendarYear],
  );
  const { data, loading, error, reload } = useApiResource<InvoicePreview>(fetcher, [fetcher]);
  return (
    <Modal
      open
      passiveModal
      size="md"
      modalHeading={`Invoice Preview — ${forestFileId}`}
      onRequestClose={onClose}
    >
      <AsyncBoundary
        loading={loading}
        error={error}
        onRetry={reload}
        loadingText="Pricing the invoice…"
      >
        {data && <InvoiceView invoice={data} />}
      </AsyncBoundary>
    </Modal>
  );
};

export default InvoicePreviewModal;
