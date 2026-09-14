import { Logout } from '@carbon/icons-react';
import { Button, Column, Grid } from '@carbon/react';

import { useAuth } from '@/context/auth/useAuth';
import { useTheme } from '@/context/theme/useTheme';

import type { FC } from 'react';

import './LandingPage.scss';
import './UnauthorizedPage.css';

/**
 * Signed in to BC Gov SSO, but holding no recognised FTA_* role.
 *
 * <p>Deliberately a page rather than a redirect back to the landing screen: the
 * session is valid, so the landing screen would send them straight back here and
 * the pair would flicker between themselves. AuthProvider keeps isLoggedIn=true
 * for exactly this reason. Sign out is the only action that changes anything, so
 * it is the only one offered — a role has to be granted in the CSS console, and
 * there is no self-service path to ask for one from in here.
 *
 * <p>Laid out as the landing page is, and reusing its stylesheet: this is the
 * same moment in the same journey — a person who has just arrived and cannot get
 * in — and it reads as a continuation of the screen they came from rather than
 * as an error page from somewhere else. nr-fam and nr-fsp-new pair their
 * unauthorized pages with their landing pages the same way.
 *
 * <p>It names them. "You do not have access" alone invites the reading that FTA
 * is broken; naming the account says which identity was judged, which is the
 * useful thing when somebody has two and has signed in with the one that was
 * never granted anything.
 */
const UnauthorizedPage: FC = () => {
  const { user, logout } = useAuth();
  const { theme } = useTheme();
  const logoSrc = theme === 'g100' ? '/bc-gov-logo-rev.png' : '/bc-gov-logo.png';

  const signedInAs =
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

            <h1 id="unauthorized-title" data-testid="unauthorized-title" className="landing-title">
              You do not have access in FTA
            </h1>

            <h2
              id="unauthorized-subtitle"
              data-testid="unauthorized-subtitle"
              className="landing-subtitle"
            >
              {signedInAs
                ? `You're signed in as ${signedInAs}, but this account has not been granted any FTA role.`
                : 'This account has not been granted any FTA role.'}
            </h2>

            <div className="landing-actions">
              <div className="buttons-container single-row">
                <Button
                  type="button"
                  kind="tertiary"
                  size="md"
                  renderIcon={Logout}
                  className="login-btn"
                  onClick={() => void logout()}
                  data-testid="unauthorized-button__logout"
                >
                  Sign out
                </Button>
              </div>

              <p id="unauthorized-note" className="landing-note">
                Ask an FTA administrator to grant you access.
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
