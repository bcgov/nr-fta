import { Asleep, Light, Logout } from '@carbon/icons-react';
import { SideNavLink } from '@carbon/react';
import { type FC } from 'react';
import AvatarImage from '@/components/Layout/AvatarImage';
import type { ROLE_TYPE } from '@/context/auth/types';
import { useAuth } from '@/context/auth/useAuth';
import { useTheme } from '@/context/theme/useTheme';
import './HeaderPanelProfile.css';

// Cognito's idpProvider claim arrives in shouty all-caps. FTA is IDIR-only,
// but keep the map so an unrecognised claim falls back to its raw value
// rather than silently mislabelling the IDP.
const PROVIDER_LABEL: Record<string, string> = {
  IDIR: 'IDIR',
};

// Friendly labels for the FTA roles shown beside the user's name.
const ROLE_LABEL: Record<ROLE_TYPE, string> = {
  FTA_ADMIN: 'Administrator',
  FTA_VIEWER: 'Viewer',
};

// Most-privileged first, so a user's "current role" shows their highest.
const ROLE_PRIORITY: ROLE_TYPE[] = ['FTA_ADMIN', 'FTA_VIEWER'];

const HeaderPanelProfile: FC = () => {
  const { theme, toggleTheme } = useTheme();
  const { user, logout } = useAuth();

  const fullName = [user?.firstName, user?.lastName].filter(Boolean).join(' ').trim()
    || user?.displayName
    || '';

  const providerLabel = user?.idpProvider
    ? PROVIDER_LABEL[user.idpProvider] ?? user.idpProvider
    : 'IDIR';

  const primaryRole = ROLE_PRIORITY.find((r) => user?.roles?.includes(r));
  const roleLabel = primaryRole ? ROLE_LABEL[primaryRole] : null;
  const nameWithRole = roleLabel
    ? `${fullName || 'User'} (${roleLabel})`
    : fullName || 'User';

  return (
    <div className="my-profile-container">
      <div className="user-info-section">
        <div className="user-image">
          <AvatarImage userName={fullName} size="large" />
        </div>
        <div className="user-data">
          <p className="user-name">{nameWithRole}</p>
          {user?.userName ? <p>{`${providerLabel}: ${user.userName}`}</p> : null}
          {user?.email ? <p>{`Email: ${user.email}`}</p> : null}
        </div>
      </div>
      <hr className="divisory" />
      <nav className="account-nav">
        <ul>
          <SideNavLink
            className="cursor-pointer"
            renderIcon={theme === 'g100' ? Light : Asleep}
            onClick={toggleTheme}
          >
            Change theme
          </SideNavLink>
          <SideNavLink className="cursor-pointer" renderIcon={Logout} onClick={logout}>
            Log out
          </SideNavLink>
        </ul>
      </nav>
    </div>
  );
};

export default HeaderPanelProfile;
