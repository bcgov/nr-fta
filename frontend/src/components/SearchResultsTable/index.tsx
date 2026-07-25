import {
  DataTable,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import type { ReactNode } from 'react';
import { EmptyState } from '@/components/EmptyState/EmptyState';

export interface ColumnDef {
  key: string;
  header: string;
}

interface SearchResultsTableProps<T extends { id: string }> {
  rows: T[];
  headers: ColumnDef[];
  title?: string;
  /** Custom renderer for a cell; return undefined to fall back to the raw value. */
  renderCell?: (row: T, columnKey: string) => ReactNode | undefined;
  emptyTitle?: string;
  emptyBody?: ReactNode;
}

/**
 * Shared results table for the FTA search screens. Wraps Carbon's DataTable
 * with a consistent header/empty-state treatment; callers supply a
 * `renderCell` for links, tags, and formatting. Render it only after a search
 * has run (pass `rows`); show nothing before the first search.
 */
export function SearchResultsTable<T extends { id: string }>({
  rows,
  headers,
  title = 'Results',
  renderCell,
  emptyTitle = 'No results found',
  emptyBody = 'Adjust your search criteria and try again.',
}: SearchResultsTableProps<T>) {
  if (rows.length === 0) {
    return <EmptyState title={emptyTitle} body={emptyBody} />;
  }

  return (
    <DataTable rows={rows as never} headers={headers}>
      {({ rows: dtRows, headers: dtHeaders, getHeaderProps, getTableProps }) => (
        <TableContainer
          title={title}
          description={`${rows.length} result${rows.length === 1 ? '' : 's'} found`}
        >
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                {dtHeaders.map((header) => {
                  const { key, ...rest } = getHeaderProps({ header });
                  return (
                    <TableHeader key={key} {...rest}>
                      {header.header}
                    </TableHeader>
                  );
                })}
              </TableRow>
            </TableHead>
            <TableBody>
              {dtRows.map((row) => {
                const src = rows.find((r) => r.id === row.id)!;
                return (
                  <TableRow key={row.id}>
                    {row.cells.map((cell) => {
                      const custom = renderCell?.(src, cell.info.header);
                      return (
                        <TableCell key={cell.id}>
                          {custom !== undefined ? custom : cell.value}
                        </TableCell>
                      );
                    })}
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
}

export default SearchResultsTable;
