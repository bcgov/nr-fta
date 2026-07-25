import { Button } from '@carbon/react';
import { ArrowRight } from '@carbon/icons-react';
import type { FC } from 'react';
import { useNavigate } from 'react-router-dom';
import PageLayout from '@/pages/PageLayout';

/**
 * In-shell 404 for authenticated users who hit an unknown path under a valid
 * area. (Unauthenticated / role-denied cases are handled by Landing /
 * Forbidden.)
 */
const NotFound: FC = () => {
  const navigate = useNavigate();
  return (
    <PageLayout title="Page not found">
      <p style={{ maxWidth: '40rem', marginBottom: '1.5rem' }}>
        The page you’re looking for doesn’t exist or may have moved.
      </p>
      <Button renderIcon={ArrowRight} onClick={() => navigate('/welcome')}>
        Go to home
      </Button>
    </PageLayout>
  );
};

export default NotFound;
