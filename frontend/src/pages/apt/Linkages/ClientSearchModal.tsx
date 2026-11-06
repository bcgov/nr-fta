import {
  Button,
  InlineNotification,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { useState, type FC, type FormEvent } from 'react';

import { Modal } from '@/components/Modal';
import { searchAptClients, type AptClient } from '@/services/apt/linkages';

interface Props {
  open: boolean;
  /** What is being picked, for the heading: "corporation" or "licensee". */
  subject: string;
  onClose: () => void;
  onSelect: (client: AptClient) => void;
}

/**
 * APT2's ClientSearchModal: find a forest client by partial number and/or name
 * (APT_CLIENTS_GET), then pick one. Mounted only while open, so each use starts empty.
 */
const ClientSearchModal: FC<Props> = ({ open, subject, onClose, onSelect }) => {
  const [clientNumber, setClientNumber] = useState('');
  const [clientName, setClientName] = useState('');
  const [results, setResults] = useState<AptClient[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const search = async (e?: FormEvent) => {
    e?.preventDefault();
    if (!clientNumber.trim() && !clientName.trim()) {
      setError('Enter a client number or name to search.');
      return;
    }
    setSearching(true);
    setError(null);
    try {
      setResults(
        await searchAptClients({
          clientNumber: clientNumber.trim(),
          clientName: clientName.trim(),
        }),
      );
    } catch (err) {
      setResults(null);
      setError(err instanceof Error ? err.message : 'The search failed.');
    } finally {
      setSearching(false);
    }
  };

  return (
    <Modal
      open={open}
      passiveModal
      size="lg"
      className="detail-dialog"
      modalHeading={`Find ${subject}`}
      onRequestClose={onClose}
    >
      <Stack gap={5}>
        <form className="apt-linkages__search" onSubmit={(e) => void search(e)}>
          <TextInput
            id="apt-client-search-number"
            labelText="Client number"
            inputMode="numeric"
            maxLength={8}
            value={clientNumber}
            onChange={(e) => setClientNumber(e.target.value.replace(/\D/g, ''))}
          />
          <TextInput
            id="apt-client-search-name"
            labelText="Client name"
            value={clientName}
            onChange={(e) => setClientName(e.target.value)}
          />
          <Button type="submit" size="md" disabled={searching}>
            {searching ? 'Searching…' : 'Search'}
          </Button>
        </form>

        {error && (
          <InlineNotification
            kind="error"
            lowContrast
            title="Search"
            subtitle={error}
            onCloseButtonClick={() => setError(null)}
          />
        )}

        {results && results.length === 0 && <p>No clients match.</p>}
        {results && results.length > 0 && (
          <TableContainer>
            <Table size="sm" aria-label="Matching clients">
              <TableHead>
                <TableRow>
                  <TableHeader>Client number</TableHeader>
                  <TableHeader>Client name</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {results.map((c) => (
                  <TableRow key={c.clientNumber}>
                    <TableCell>
                      <Button kind="ghost" size="sm" onClick={() => onSelect(c)}>
                        {c.clientNumber}
                      </Button>
                    </TableCell>
                    <TableCell>{c.clientName ?? '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Stack>
    </Modal>
  );
};

export default ClientSearchModal;
