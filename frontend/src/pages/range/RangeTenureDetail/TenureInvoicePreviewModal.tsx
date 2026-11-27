import { useCallback, type FC } from 'react';

import InvoicePreviewModal from '@/pages/admin/billing/InvoicePreviewModal';
import { getRangeInvoicePreview } from '@/services/range_tenure_tab';

interface Props {
  forestFileId: string;
  onClose: () => void;
}

/**
 * FTA100R's Preview Invoice: the FTA695 invoice for this file, this year (695's default year),
 * over the tab — the same invoice FTA680's Preview shows.
 */
const TenureInvoicePreviewModal: FC<Props> = ({ forestFileId, onClose }) => {
  const fetcher = useCallback(() => getRangeInvoicePreview(forestFileId), [forestFileId]);
  return <InvoicePreviewModal forestFileId={forestFileId} fetcher={fetcher} onClose={onClose} />;
};

export default TenureInvoicePreviewModal;
