import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
} from '@carbon/react';
import { Search as SearchIcon, DocumentAdd } from '@carbon/icons-react';
import type { FC } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import PageLayout from '@/pages/PageLayout';
import { MOCK_TENURES } from '@/mocks/tenures';
import './TenureLanding.scss';

/**
 * Landing for the "Tenures" menu entry. In the legacy app you reach a tenure
 * via search; this page offers that entry point plus a quick list of recent
 * tenures (mock) that link straight into the detail (FTA100).
 */
const TenureLanding: FC = () => {
  const navigate = useNavigate();
  const recent = MOCK_TENURES.slice(0, 5);

  return (
    <PageLayout title="Tenures">
      <div className="tenure-landing__actions">
        <Button renderIcon={SearchIcon} onClick={() => navigate('/search/tenure')}>
          Tenure Search
        </Button>
        <Button kind="tertiary" renderIcon={DocumentAdd} onClick={() => navigate('/tenures/add')}>
          Add New Tenure
        </Button>
      </div>

      <TableContainer title="Recently accessed tenures" description="Mock — pending backend history">
        <Table>
          <TableHead>
            <TableRow>
              <TableHeader>File ID</TableHeader>
              <TableHeader>File Type</TableHeader>
              <TableHeader>Status</TableHeader>
              <TableHeader>Licensee</TableHeader>
              <TableHeader>Org Unit</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {recent.map((t) => (
              <TableRow key={t.fileId}>
                <TableCell><Link to={`/tenures/${t.fileId}`}>{t.fileId}</Link></TableCell>
                <TableCell>{t.fileType}</TableCell>
                <TableCell>
                  <Tag type={t.status === 'Active' ? 'green' : t.status === 'Pending' ? 'blue' : 'gray'}>
                    {t.status}
                  </Tag>
                </TableCell>
                <TableCell>{t.licensee}</TableCell>
                <TableCell>{t.orgUnit}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </PageLayout>
  );
};

export default TenureLanding;
