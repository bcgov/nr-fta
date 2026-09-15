import { Locked, Logout } from '@carbon/icons-react';
import { Button } from '@carbon/react';

import { EmptyState } from '@/components/EmptyState/EmptyState';
import { useAuth } from '@/context/auth/useAuth';
import PageLayout from '@/pages/PageLayout';

import type { FC } from 'react';

import './UnauthorizedPage.css';

/**
 * Shown after a successful sign-in when the user's token carries no
 * recognised FTA_* role. AuthProvider intentionally keeps isLoggedIn=true
 * in that case so the routing layer can land here instead of bouncing the
 * user back through the IdP. The only action is "sign out" — getting a role
 * means an out-of-band role assignment in the CSS console (no self-service
 * from inside the app).
 *
 * <p>Rendered outside the app shell (no SideNav/Header — the user has no
 * role to navigate with), so this wraps PageLayout in its own padded frame
 * rather than relying on Carbon's `<Content>` for that spacing.
 */
const UnauthorizedPage: FC = () => {
  const { user, logout } = useAuth();

  const displayName =
    user?.displayName ||
    [user?.firstName, user?.lastName].filter(Boolean).join(' ') ||
    user?.userName ||
    user?.providerUsername;

  return (
    <div className="unauthorized-page">
      <PageLayout
        title="Access not granted"
        subtitle={
          displayName
            ? `You're signed in as ${displayName}, but your account isn't authorized to use FTA.`
            : 'Your account is signed in, but it isn’t authorized to use FTA.'
        }
      >
        <EmptyState
          icon={<Locked size={80} />}
          title="No FTA role assigned"
          body="Access to FTA requires a role assignment made outside the application. Sign out, or contact your administrator to request access."
          action={
            <Button
              type="button"
              onClick={() => void logout()}
              renderIcon={Logout}
              size="md"
              data-testid="unauthorized-button__logout"
            >
              Sign out
            </Button>
          }
        />
      </PageLayout>
    </div>
  );
};

export default UnauthorizedPage;
