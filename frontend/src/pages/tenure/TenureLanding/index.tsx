import { Search as SearchIcon, DocumentAdd, Time } from '@carbon/icons-react';
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
import { Link, useNavigate } from 'react-router-dom';

import SectionTile from '@/components/SectionTile';
import { MOCK_TENURES } from '@/mocks/tenures';
import PageLayout from '@/pages/PageLayout';

import type { FC } from 'react';

/**
 * Landing for the "Tenures" menu entry. In the legacy app you reach a tenure
 * via search; this page offers that entry point plus a quick list of recent
 * tenures (mock) that link straight into the detail (FTA100).
 */
const TenureLanding: FC = () => {
  const navigate = useNavigate();
  const recent = MOCK_TENURES.slice(0, 5);

  return (
    <PageLayout
      title="Tenures"
      subtitle="Search for a forest tenure file, add a new one, or jump back into a recent file."
      actions={
        <>
          <Button size="md" renderIcon={SearchIcon} onClick={() => navigate('/search/tenure')}>
            Tenure Search
          </Button>
          <Button
            size="md"
            kind="tertiary"
            renderIcon={DocumentAdd}
            onClick={() => navigate('/tenures/add')}
          >
            Add New Tenure
          </Button>
        </>
      }
    >
      <SectionTile
        title="Recently accessed tenures"
        icon={Time}
        description="Mock — pending backend history"
      >
        <div className="bordered-table">
          <TableContainer>
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
                    <TableCell>
                      <Link to={`/tenures/${t.fileId}`}>{t.fileId}</Link>
                    </TableCell>
                    <TableCell>{t.fileType}</TableCell>
                    <TableCell>
                      <Tag
                        type={
                          t.status === 'Active' ? 'green' : t.status === 'Pending' ? 'blue' : 'gray'
                        }
                      >
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
        </div>
      </SectionTile>
    </PageLayout>
  );
};

export default TenureLanding;
