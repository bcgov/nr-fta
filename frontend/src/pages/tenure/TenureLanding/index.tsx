import { ArrowRight, DocumentAdd, Document } from '@carbon/icons-react';
import { Button, TextInput } from '@carbon/react';
import { useState, type FC, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import SectionTile from '@/components/SectionTile';
import { useAuth } from '@/context/auth/useAuth';
import PageLayout from '@/pages/PageLayout';
import AddTenureModal from '@/pages/tenure/AddTenureModal';
import { isPathAllowedForUser } from '@/routes/access';
import './TenureLanding.scss';

const ADD_PATH = '/tenures/add';

/**
 * The Tenures menu's "Tenure List" entry — legacy's "Tenure" pick (FTA100 with
 * `isMenuPick`), which opened an empty tenure screen with a File ID box and Go.
 * Legacy kept no list of recent files, so neither does this page. Tenure Search
 * stays under the Search menu, as in legacy.
 */
const TenureLanding: FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const mayAdd = isPathAllowedForUser(user, ADD_PATH);
  const [fileId, setFileId] = useState('');

  // The Add New Tenure dialog opens over this page from its button. A direct link to
  // /tenures/add renders this page with it open too.
  const onAddRoute = location.pathname === ADD_PATH;
  const [addOpen, setAddOpen] = useState(false);
  const closeAdd = () => {
    setAddOpen(false);
    if (onAddRoute) navigate('/tenures', { replace: true });
  };

  const onGo = (e: FormEvent) => {
    e.preventDefault();
    const id = fileId.trim();
    if (id) navigate(`/tenures/${encodeURIComponent(id)}`);
  };

  return (
    <PageLayout
      title="Tenure List"
      subtitle="Open a forest tenure file by its ID."
      actions={
        mayAdd ? (
          <Button size="md" renderIcon={DocumentAdd} onClick={() => setAddOpen(true)}>
            Add New Tenure
          </Button>
        ) : undefined
      }
    >
      <AddTenureModal open={mayAdd && (addOpen || onAddRoute)} onClose={closeAdd} />
      <SectionTile title="Open a tenure" icon={Document}>
        <form className="tenure-landing__form" onSubmit={onGo}>
          <TextInput
            id="tenure-landing-fileId"
            labelText="Forest File ID"
            placeholder="e.g. A19999"
            maxLength={10}
            value={fileId}
            onChange={(e) => setFileId(e.target.value.toUpperCase())}
          />
          <Button type="submit" size="md" renderIcon={ArrowRight} disabled={!fileId.trim()}>
            Go
          </Button>
        </form>
      </SectionTile>
    </PageLayout>
  );
};

export default TenureLanding;
