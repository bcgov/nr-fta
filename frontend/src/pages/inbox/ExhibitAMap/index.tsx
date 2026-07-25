import {
  Button,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
} from '@carbon/react';
import { ArrowLeft } from '@carbon/icons-react';
import type { FC } from 'react';
import { Link, useParams } from 'react-router-dom';
import { MapContainer, TileLayer, Polygon, CircleMarker, Popup } from 'react-leaflet';
import Tombstone from '@/components/Tombstone';
import PageLayout from '@/pages/PageLayout';
import { findExhibitA, type MapFeature } from '@/mocks/spatial';
import 'leaflet/dist/leaflet.css';
import './ExhibitAMap.scss';

const SEVERITY_TAG: Record<'High' | 'Medium' | 'Low', 'red' | 'magenta' | 'gray'> = {
  High: 'red',
  Medium: 'magenta',
  Low: 'gray',
};

const FEATURE_TAG: Record<MapFeature['type'], 'green' | 'blue' | 'teal' | 'red'> = {
  'Cut Block': 'green',
  Road: 'blue',
  Reserve: 'teal',
  Conflict: 'red',
};

/**
 * FTA304 / FTA315 / FTA316 — Exhibit A tenure map + feature list + conflicts.
 * Renders the submission's boundary and point features on a Leaflet map, with
 * the feature list and conflict report as tabs. Reached from the Inbox
 * application detail's Exhibit A tab. Backed by mock spatial data.
 */
const ExhibitAMap: FC = () => {
  const { esfId = '' } = useParams();
  const ex = findExhibitA(esfId);

  if (!ex) {
    return (
      <PageLayout title="Exhibit A not found">
        <p style={{ marginBottom: '1.5rem' }}>No spatial submission matches “{esfId}”.</p>
        <Button as={Link} to="/inbox" renderIcon={ArrowLeft} kind="tertiary">Back to Inbox</Button>
      </PageLayout>
    );
  }

  return (
    <PageLayout title={`Exhibit A — ${ex.esfId}`}>
      <Link to={`/inbox/${ex.esfId}`} className="fta-back">
        <ArrowLeft size={16} /> Back to Application {ex.esfId}
      </Link>

      <Tombstone
        ariaLabel="Exhibit A summary"
        items={[
          { label: 'ESF ID', value: ex.esfId },
          { label: 'Forest File', value: <Link to={`/tenures/${ex.fileId}`}>{ex.fileId}</Link> },
          { label: 'Features', value: String(ex.features.length) },
          { label: 'Conflicts', value: String(ex.conflicts.length) },
        ]}
      />

      <div className="exhibit-a__map">
        <MapContainer center={ex.centre} zoom={11} scrollWheelZoom={false} style={{ height: '100%', width: '100%' }}>
          <TileLayer
            attribution='&copy; OpenStreetMap contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <Polygon positions={ex.boundary} pathOptions={{ color: '#0f62fe', fillOpacity: 0.12 }}>
            <Popup>Tenure area — file {ex.fileId}</Popup>
          </Polygon>
          {ex.markers.map((m) => (
            <CircleMarker
              key={m.id}
              center={m.position}
              radius={9}
              pathOptions={{
                color: m.kind === 'conflict' ? '#da1e28' : '#0e6027',
                fillColor: m.kind === 'conflict' ? '#da1e28' : '#0e6027',
                fillOpacity: 0.7,
              }}
            >
              <Popup>{m.label}</Popup>
            </CircleMarker>
          ))}
        </MapContainer>
      </div>

      <Tabs>
        <TabList aria-label="Exhibit A sections" contained>
          <Tab>Feature List</Tab>
          <Tab>Conflicts</Tab>
        </TabList>
        <TabPanels>
          <TabPanel>
            <TableContainer title="Map features" description={`${ex.features.length} feature(s)`}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableHeader>Feature</TableHeader>
                    <TableHeader>Type</TableHeader>
                    <TableHeader>Label</TableHeader>
                    <TableHeader>Area (ha)</TableHeader>
                    <TableHeader>Status</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {ex.features.map((f) => (
                    <TableRow key={f.featureId}>
                      <TableCell>{f.featureId}</TableCell>
                      <TableCell><Tag type={FEATURE_TAG[f.type]}>{f.type}</Tag></TableCell>
                      <TableCell>{f.label}</TableCell>
                      <TableCell>{f.areaHa != null ? f.areaHa.toFixed(1) : '—'}</TableCell>
                      <TableCell>{f.status}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </TabPanel>

          <TabPanel>
            <TableContainer title="Spatial conflicts" description={`${ex.conflicts.length} conflict(s)`}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableHeader>Conflict</TableHeader>
                    <TableHeader>Against</TableHeader>
                    <TableHeader>Overlap (ha)</TableHeader>
                    <TableHeader>Severity</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {ex.conflicts.map((c) => (
                    <TableRow key={c.conflictId}>
                      <TableCell>{c.conflictId}</TableCell>
                      <TableCell>{c.against}</TableCell>
                      <TableCell>{c.overlapHa.toFixed(1)}</TableCell>
                      <TableCell><Tag type={SEVERITY_TAG[c.severity]}>{c.severity}</Tag></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </PageLayout>
  );
};

export default ExhibitAMap;
