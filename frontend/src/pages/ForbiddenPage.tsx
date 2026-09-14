import { ArrowRight, Locked, Logout } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useNavigate } from 'react-router-dom';

import { EmptyState } from '@/components/EmptyState/EmptyState';
import { useAuth } from '@/context/auth/useAuth';
import PageLayout from '@/pages/PageLayout';
import { defaultRouteForUser } from '@/routes/access';

import type { FC } from 'react';

/**
 * Friendly "you don't have access to this page" screen. Shown by the
 * route guard when an authenticated user navigates to a path their role
 * is not permitted to use (e.g. a BCeID submitter loading /search).
 *
 * <p>Distinct from {@link UnauthorizedPage}, which is for users whose
 * token carries no FTA role at all. This page is reached by users
 * with a valid role who simply don't have rights to the requested URL,
 * so it renders inside the app shell (the SideNav still lets them click
 * to a page they CAN use); the primary action steers them back into
 * such a page rather than just offering Sign Out.
 */
const ForbiddenPage: FC = () => {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const displayName =
    user?.displayName ||
    [user?.firstName, user?.lastName].filter(Boolean).join(' ') ||
    user?.userName ||
    user?.providerUsername;

  const homeRoute = defaultRouteForUser(user);

  return (
    <PageLayout
      title="You don't have access to view this page"
      subtitle={
        displayName
          ? `${displayName}, your account doesn't have permission to use this part of FTA.`
          : `Your account doesn't have permission to use this part of FTA.`
      }
    >
      <EmptyState
        icon={<Locked size={80} />}
        title="What you can do next"
        body="Head back to a page you have access to, or sign out and switch to an account with the right permissions."
        action={
          <>
            <Button
              type="button"
              kind="primary"
              onClick={() => navigate(homeRoute)}
              renderIcon={ArrowRight}
              size="md"
              data-testid="forbidden-button__home"
            >
              Go to my landing page
            </Button>{' '}
            <Button
              type="button"
              kind="tertiary"
              onClick={() => void logout()}
              renderIcon={Logout}
              size="md"
              data-testid="forbidden-button__logout"
            >
              Sign out
            </Button>
          </>
        }
      />
    </PageLayout>
  );
};

export default ForbiddenPage;
