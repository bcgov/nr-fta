import { SideNav, SideNavItems, SideNavLink, SideNavMenu, SideNavMenuItem } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { useAuth } from '@/context/auth/useAuth';
import { useLayout } from '@/context/layout/useLayout';
import {
  getMenuSections,
  sectionIdForPath,
  type MenuLeaf,
  type MenuSection,
} from '@/routes/routePaths';
import './LayoutSideNav.css';

/**
 * The application's side navigation.
 *
 * <p>Two shapes, one source of truth:
 *
 * <ul>
 *   <li><b>Expanded</b> — the legacy FTA menu's five headings, each a
 *       collapsible {@code SideNavMenu} holding its destinations. The section
 *       containing the current route opens on load; the rest stay closed.
 *   <li><b>Collapsed</b> — a 48px icon rail showing one icon per section, five
 *       in all. The icons do not navigate: clicking one expands the nav and
 *       opens that section, so the rail is a way back into the menu rather
 *       than a shortcut past it. Hovering names the section.
 * </ul>
 *
 * <p>Carbon's {@code SideNavMenu} can track the open/closed state itself, but
 * only reacts to the nav collapsing when its context reports {@code isRail} —
 * and this nav is not a rail in Carbon's sense ({@code isPersistent={false}}
 * with {@code expanded} pinned true). So the open section is driven from the
 * current route instead, via {@code defaultExpanded}.
 */
export const LayoutSideNav: FC = () => {
  const { isSideNavExpanded, openSideNav } = useLayout();
  const location = useLocation();
  const { user } = useAuth();
  const roles = user?.roles ?? [];

  // Note: the drawer no longer auto-closes on link click or outside
  // pointer-down. The only way to dismiss it is the header X button,
  // which is the behaviour the team wanted ("stay popped out").

  // The section the user opened from the rail, if any. It takes precedence
  // over the route's own section so the nav expands showing what was clicked,
  // then falls back to the current page's section on the next navigation.
  const [railSectionId, setRailSectionId] = useState<string | undefined>(undefined);

  const routeSectionId = sectionIdForPath(roles, location.pathname);
  const activeSectionId = railSectionId ?? routeSectionId;

  // Once the nav is closed again, forget the rail choice so reopening follows
  // the current route rather than a stale click.
  useEffect(() => {
    if (!isSideNavExpanded) setRailSectionId(undefined);
  }, [isSideNavExpanded]);

  /**
   * A section in the collapsed rail: one icon per heading, five in all.
   *
   * <p>Rendered as a {@code SideNavLink} with a button element rather than a
   * router link — the rail's job here is to open the nav, not to navigate. The
   * label still renders (Carbon wraps it in the link-text span the rail's CSS
   * turns into a hover tooltip), so each icon names its section on hover.
   *
   * <p>Opening also marks the section as the one to expand, so the nav comes
   * up showing what the user pointed at.
   */
  const renderRailSection = (section: MenuSection) => (
    <SideNavLink
      data-testid={`side-nav-rail-${section.id}`}
      key={section.id}
      as="button"
      type="button"
      onClick={() => {
        setRailSectionId(section.id);
        openSideNav();
      }}
      isActive={section.id === activeSectionId}
      renderIcon={section.icon}
    >
      {section.label}
    </SideNavLink>
  );

  /**
   * A destination inside an expanded section.
   *
   * <p>The icon is rendered as a child rather than through a prop: unlike
   * {@code SideNavLink}, {@code SideNavMenuItem} has no {@code renderIcon}.
   * Carbon wraps whatever it is given in its own link-text span, so the icon
   * and label are laid out as a flex row from there (see LayoutSideNav.css).
   */
  const renderItem = (route: MenuLeaf) => {
    const Icon = route.icon;
    return (
      <SideNavMenuItem
        data-testid={`side-nav-link-${route.id}`}
        key={route.id}
        as={Link}
        to={route.path}
        isActive={route.path === location.pathname}
      >
        <span className="side-nav-item">
          {Icon ? (
            <span className="side-nav-item__icon" aria-hidden="true">
              <Icon />
            </span>
          ) : null}
          <span className="side-nav-item__label">{route.label}</span>
        </span>
      </SideNavMenuItem>
    );
  };

  const renderSection = (section: MenuSection) => {
    // A heading kept for parity with legacy that has nothing to point at yet
    // (Recreation: FTA007 and FTA701 were never built here). Shown so the menu
    // matches legacy's five, but it opens onto nothing, so it is inert.
    if (section.placeholder) {
      return (
        <SideNavMenu
          key={section.id}
          title={section.label}
          // No renderIcon: headings carry no icon, so the placeholder looks
          // like its siblings rather than the odd one out.
          //
          // No data-testid either: SideNavMenu destructures a fixed prop list
          // with no rest spread, so arbitrary attributes never reach the DOM.
          // className is the only hook that lands. (SideNavMenuItem does
          // spread, so the per-destination testids below work.)
          className={`side-nav-section side-nav-section--placeholder side-nav-section--${section.id}`}
        >
          <SideNavMenuItem as="span" className="side-nav-section__empty">
            No screens yet
          </SideNavMenuItem>
        </SideNavMenu>
      );
    }

    return (
      <SideNavMenu
        key={section.id}
        title={section.label}
        defaultExpanded={section.id === activeSectionId}
        className={`side-nav-section side-nav-section--${section.id}`}
      >
        {section.items.map(renderItem)}
      </SideNavMenu>
    );
  };

  return (
    <SideNav
      expanded
      isPersistent={false}
      isChildOfHeader
      className={`side-nav-drawer${isSideNavExpanded ? ' side-nav-drawer--open' : ''}`}
      aria-label="Main navigation"
    >
      <SideNavItems>
        {isSideNavExpanded
          ? getMenuSections(roles).map(renderSection)
          : getMenuSections(roles).map(renderRailSection)}
      </SideNavItems>
    </SideNav>
  );
};

export default LayoutSideNav;
