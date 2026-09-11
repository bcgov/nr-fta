import { Content, HeaderContainer } from '@carbon/react';

import { LayoutProvider } from '@/context/layout/LayoutProvider';
import { useLayout } from '@/context/layout/useLayout';

import { LayoutHeader } from './LayoutHeader';

import type { FC, ReactNode } from 'react';
import './Layout.css';

/**
 * Wraps the Carbon shell so the page content slides right when the
 * SideNav drawer is open (CSS keys off the .bc-layout--nav-open class
 * to push .cds--content rather than letting the drawer overlay it).
 */
const LayoutShell: FC<{ children: ReactNode }> = ({ children }) => {
  const { isSideNavExpanded } = useLayout();
  return (
    <div className={`bc-layout${isSideNavExpanded ? ' bc-layout--nav-open' : ''}`}>
      <HeaderContainer render={LayoutHeader} />
      <Content>{children}</Content>
    </div>
  );
};

const Layout: FC<{ children: ReactNode }> = ({ children }) => {
  return (
    <LayoutProvider>
      <LayoutShell>{children}</LayoutShell>
    </LayoutProvider>
  );
};

export default Layout;
