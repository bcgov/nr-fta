import { InlineLoading, InlineNotification, Button } from '@carbon/react';
import type { FC, ReactNode } from 'react';

interface AsyncBoundaryProps {
  loading: boolean;
  error?: string;
  onRetry?: () => void;
  /** Message shown while loading. */
  loadingText?: string;
  children: ReactNode;
}

/**
 * Standard loading/error wrapper for screens that read from the backend.
 * Shows an inline loader while `loading`, an error notification (with an
 * optional Retry) when `error` is set, otherwise the children. Keeps the
 * loading/error treatment identical across every FTA screen.
 */
const AsyncBoundary: FC<AsyncBoundaryProps> = ({ loading, error, onRetry, loadingText = 'Loading…', children }) => {
  if (loading) {
    return <InlineLoading description={loadingText} status="active" />;
  }
  if (error) {
    return (
      <div>
        <InlineNotification
          kind="error"
          lowContrast
          title="Couldn’t load data"
          subtitle={error}
          hideCloseButton
        />
        {onRetry && (
          <Button kind="tertiary" size="sm" onClick={onRetry} style={{ marginTop: '1rem' }}>
            Retry
          </Button>
        )}
      </div>
    );
  }
  return <>{children}</>;
};

export default AsyncBoundary;
