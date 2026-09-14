import { type ReactNode } from 'react';

import PageTitle from '@/components/PageTitle';

import './PageLayout.css';

interface PageLayoutProps {
  title: string;
  /** Supporting line under the title, where the screen needs one. */
  subtitle?: string;
  /** Screen-level buttons, laid out level with the title. */
  actions?: ReactNode;
  children: ReactNode;
}

/**
 * The frame every screen renders inside: the page heading, then the screen's
 * own sections beneath it.
 *
 * The horizontal padding and the gap below the header belong to Carbon's
 * `<Content>` (see `.cds--content` in styles/_overrides.scss), so this adds
 * neither — duplicating them was what pushed FTA's pages out of alignment
 * with the sibling apps. Sections inside `children` are expected to be
 * `<SectionTile>`s; the body spaces them so a page doesn't have to.
 */
export default function PageLayout({ title, subtitle, actions, children }: PageLayoutProps) {
  return (
    <main className="page-layout" id="main-content">
      <PageTitle title={title} subtitle={subtitle} actions={actions} />
      <div className="page-layout__body">{children}</div>
    </main>
  );
}
