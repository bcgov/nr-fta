import { ClickableTile } from '@carbon/react';
import { Search, Document, Tag, Task } from '@carbon/icons-react';
import type { FC } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/auth/useAuth';
import PageLayout from '@/pages/PageLayout';
import './Welcome.scss';

const QUICK_LINKS = [
  { to: '/search/tenure', label: 'Tenure Search', desc: 'Find forest tenures and files', Icon: Search },
  { to: '/tenures/add', label: 'Add Tenure', desc: 'Create a new forest tenure', Icon: Document },
  { to: '/marks', label: 'Private Marks', desc: 'Timber mark applications & amendments', Icon: Tag },
  { to: '/inbox', label: 'Inbox', desc: 'Adjudicate tenure applications', Icon: Task },
];

/**
 * Home / welcome landing shown after login — the modern equivalent of the
 * legacy fta00Welcome screen. Greets the user and offers quick links into
 * the main workflows.
 */
const Welcome: FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const firstName = user?.firstName || user?.displayName || 'there';

  return (
    <PageLayout title="Forest Tenure Administration">
      <p className="welcome__greeting">Welcome, {firstName}.</p>
      <p className="welcome__intro">
        Administer harvest authorizations &amp; tenures, range agreements, and private timber
        marks. Choose a task below or use the menu to search.
      </p>
      <div className="welcome__tiles">
        {QUICK_LINKS.map(({ to, label, desc, Icon }) => (
          <ClickableTile key={to} onClick={() => navigate(to)} className="welcome__tile">
            <Icon size={24} />
            <span className="welcome__tile-label">{label}</span>
            <span className="welcome__tile-desc">{desc}</span>
          </ClickableTile>
        ))}
      </div>
    </PageLayout>
  );
};

export default Welcome;
