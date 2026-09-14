import { SideNav, SideNavItems, SideNavLink } from '@carbon/react';
import { type FC } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { useAuth } from '@/context/auth/useAuth';
import { useLayout } from '@/context/layout/useLayout';
import { getMenuEntries, type MenuLeaf } from '@/routes/routePaths';
import './LayoutSideNav.css';

export const LayoutSideNav: FC = () => {
  const { isSideNavExpanded } = useLayout();
  const location = useLocation();
  const { user } = useAuth();
  const roles = user?.roles ?? [];

  // Note: the drawer no longer auto-closes on link click or outside
  // pointer-down. The only way to dismiss it is the header X button,
  // which is the behaviour the team wanted ("stay popped out").

  const renderLeaf = (route: MenuLeaf) => (
    <SideNavLink
      data-testid={`side-nav-link-${route.id}`}
      key={route.id}
      as={Link}
      to={route.path}
      isActive={route.path === location.pathname}
      renderIcon={route.icon}
    >
      {route.label}
    </SideNavLink>
  );

  return (
    <SideNav
      expanded
      isPersistent={false}
      isChildOfHeader
      className={`side-nav-drawer${isSideNavExpanded ? ' side-nav-drawer--open' : ''}`}
      aria-label="Main navigation"
    >
      <SideNavItems>{getMenuEntries(roles).map(renderLeaf)}</SideNavItems>
    </SideNav>
  );
};

export default LayoutSideNav;
