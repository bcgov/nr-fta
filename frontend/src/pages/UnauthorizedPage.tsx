import { Logout } from '@carbon/icons-react';
import { Button, Column, Grid } from '@carbon/react';

import { useAuth } from '@/context/auth/useAuth';
import { useTheme } from '@/context/theme/useTheme';

import type { FC } from 'react';

import './LandingPage.scss';

/**
 * Shown after a successful sign-in when the user's token carries no
 * recognised FTA_* role. AuthProvider intentionally keeps isLoggedIn=true
 * in that case so the routing layer can land here instead of bouncing the
 * user back through the IdP. The only action is "sign out" — getting a role
 * means an out-of-band role assignment in FAM (no self-service from inside
 * the app).
 *
 * <p>Wears the landing page's split layout — logo, title and action on the
 * left, forest photograph on the right — the way nr-fsp-new dresses the same
 * screen. The user has no role to navigate with, so there is no app shell
 * here, and the landing chrome is what remains recognisable. Sharing
 * LandingPage.scss keeps the two in step; a page of its own styling drifted
 * from the sign-in screen it sits next to.
 */
const UnauthorizedPage: FC = () => {
  const { theme } = useTheme();
  const { user, logout } = useAuth();
  const logoSrc = theme === 'g100' ? '/bc-gov-logo-rev.png' : '/bc-gov-logo.png';

  const displayName =
    user?.displayName ||
    [user?.firstName, user?.lastName].filter(Boolean).join(' ') ||
    user?.userName ||
    user?.providerUsername;

  return (
    <div className="landing-grid-container">
      <Grid fullWidth className="landing-grid">
        <Column className="landing-content-col" sm={4} md={8} lg={8}>
          <div className="landing-content-wrapper">
            <div>
              <img src={logoSrc} alt="BC Government" width={160} className="logo" />
            </div>

            <h1 data-testid="unauthorized-title" className="landing-title">
              Access not granted
            </h1>

            <h2 data-testid="unauthorized-subtitle" className="landing-subtitle">
              {displayName
                ? `You're signed in as ${displayName}, but your account isn't authorized to use FTA.`
                : 'Your account is signed in, but it isn’t authorized to use FTA.'}
            </h2>

            <div className="landing-actions">
              <div className="buttons-container single-row">
                <Button
                  type="button"
                  kind="secondary"
                  onClick={() => void logout()}
                  renderIcon={Logout}
                  size="md"
                  data-testid="unauthorized-button__logout"
                  className="login-btn"
                >
                  Sign out
                </Button>
              </div>

              {/* The only route to a role is an administrator granting one, so
                  say so — otherwise the page is a dead end with a button. */}
              <p className="landing-note" data-testid="unauthorized-note">
                Access to FTA requires a role assigned outside the application. Contact your
                administrator to request access.
              </p>
            </div>
          </div>
        </Column>

        <Column className="landing-img-col" sm={4} md={8} lg={8}>
          <img src="/landing.jpg" alt="BC forest landscape" className="landing-img" />
        </Column>
      </Grid>
    </div>
  );
};

export default UnauthorizedPage;
