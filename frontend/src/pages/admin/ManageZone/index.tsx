import { Add, Edit, Location, Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  Select,
  SelectItem,
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
import { useEffect, useState, type FC } from 'react';

import AsyncBoundary from '@/components/AsyncBoundary';
import { EmptyState } from '@/components/EmptyState/EmptyState';
import { Modal } from '@/components/Modal';
import SectionTile from '@/components/SectionTile';
import UserName from '@/components/UserName';
import { useNotification } from '@/context/notification/useNotification';
import PageLayout from '@/pages/PageLayout';
import { getDistrictOrgUnits, type CodeOption } from '@/services/codeLists';
import {
  saveRangeZone,
  searchRangeZones,
  type ManageZone as RangeZone,
} from '@/services/manage_zone';

interface Form {
  district: string;
  zone: string;
  description: string;
  contactUserId: string;
  phone: string;
  email: string;
}

type Errors = Partial<Record<keyof Form, string>>;

/** fta631RangeZone.jsp's mandatory fields, with Fta631RangeZoneForm's labels. */
const validate = (f: Form): Errors => {
  const e: Errors = {};
  if (!f.district) e.district = 'District is mandatory.';
  if (!f.zone.trim()) e.zone = 'Zone is mandatory.';
  if (!f.contactUserId.trim()) e.contactUserId = 'Contact is mandatory.';
  if (!f.phone.trim()) e.phone = 'Contact phone number is mandatory.';
  if (!f.email.trim()) e.email = 'Contact email is mandatory.';
  return e;
};

const districtLabel = (z: RangeZone) =>
  z.orgUnitName
    ? `${z.orgUnitCode ?? ''} - ${z.orgUnitName}`
    : (z.orgUnitCode ?? String(z.adminForestDistrictNo ?? '—'));

/**
 * FTA631R — Manage Zone, as legacy THE.FTA_631_RANGE_ZONE: pick a district to list its range
 * zones, add one or update one (the zone code is fixed once saved). Legacy picked the contact
 * from the district's range technicians in WebADE; here the contact is an IDIR user id, which the
 * server checks against IDIR and takes the name from.
 */
const ManageZone: FC = () => {
  const notify = useNotification();
  const [districts, setDistricts] = useState<CodeOption[]>([]);
  const [district, setDistrict] = useState('');
  const [rows, setRows] = useState<RangeZone[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<RangeZone | null>(null);
  const [form, setForm] = useState<Form>({
    district: '',
    zone: '',
    description: '',
    contactUserId: '',
    phone: '',
    email: '',
  });
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getDistrictOrgUnits()
      .then((d) => {
        if (!cancelled) setDistricts(d);
      })
      .catch(() => {
        if (cancelled) return;
        notify.display({
          kind: 'error',
          title: 'Could not load the district list.',
          subtitle: 'Reload the page to try again.',
          timeout: 6000,
        });
      });
    return () => {
      cancelled = true;
    };
    // Load once; notify is stable for the page's life.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const load = async (d: string) => {
    setLoading(true);
    setError(undefined);
    try {
      setRows(await searchRangeZones({ adminForestDistrictNo: d }));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load range zones');
      setRows(null);
    } finally {
      setLoading(false);
    }
  };

  const onDistrict = (d: string) => {
    setDistrict(d);
    setRows(null);
    if (d) void load(d);
  };

  const set = (key: keyof Form, value: string) => {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
  };

  const openDialog = (z: RangeZone | null) => {
    setEditing(z);
    setForm({
      district: z?.adminForestDistrictNo != null ? String(z.adminForestDistrictNo) : district,
      zone: z?.rangeZoneCode ?? '',
      description: z?.zoneDescription ?? '',
      contactUserId: z?.contactUserId ?? '',
      phone: z?.contactPhoneNumber ?? '',
      email: z?.contactEmailAddress ?? '',
    });
    setErrors({});
    setOpen(true);
  };

  const submit = async () => {
    const found = validate(form);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setSaving(true);
    try {
      const { rangeZoneCode } = await saveRangeZone({
        adminForestDistrictNo: form.district,
        rangeZoneCode: form.zone.trim(),
        zoneDescription: form.description.trim() || null,
        contactUserId: form.contactUserId.trim(),
        contactPhoneNumber: form.phone.trim(),
        contactEmailAddress: form.email.trim(),
      });
      notify.display({
        kind: 'success',
        title: editing ? 'Zone updated' : 'Zone added',
        subtitle: `Range zone ${rangeZoneCode} saved.`,
        timeout: 5000,
      });
      setOpen(false);
      // Show the district the zone was saved in, as legacy kept it selected.
      setDistrict(form.district);
      await load(form.district);
    } catch (e) {
      notify.display({
        kind: 'error',
        title: 'Could not save zone.',
        subtitle: e instanceof Error ? e.message : 'Request failed',
        timeout: 9000,
      });
    } finally {
      setSaving(false);
    }
  };

  const addButton = (
    <Button size="md" renderIcon={Add} onClick={() => openDialog(null)} disabled={loading}>
      Add zone
    </Button>
  );

  return (
    <PageLayout
      title="Manage Range Zone"
      subtitle="View, add and update the range zones and their contacts for a district."
    >
      <SectionTile title="District" icon={SearchIcon}>
        <div className="fsp-search__field-grid">
          <div className="fsp-search__wide-cell">
            <Select
              id="zone-district"
              labelText="District"
              value={district}
              onChange={(e) => onDistrict(e.target.value)}
            >
              <SelectItem value="" text="Choose a district" />
              {districts.map((d) => (
                <SelectItem key={d.code} value={d.code} text={d.description} />
              ))}
            </Select>
          </div>
        </div>
      </SectionTile>

      {district && (
        <SectionTile
          title="Range zones"
          icon={Location}
          description={rows ? `${rows.length} zone(s)` : undefined}
          actions={addButton}
        >
          <AsyncBoundary
            loading={loading}
            error={error}
            onRetry={() => void load(district)}
            loadingText="Loading range zones…"
          >
            {rows &&
              (rows.length === 0 ? (
                <EmptyState title="No range zones" body="This district has no range zones yet." />
              ) : (
                <div className="bordered-table">
                  <TableContainer>
                    <Table size="md" useZebraStyles>
                      <TableHead>
                        <TableRow>
                          <TableHeader>District</TableHeader>
                          <TableHeader>Zone</TableHeader>
                          <TableHeader>Zone Description/Area of Resp.</TableHeader>
                          <TableHeader>Contact</TableHeader>
                          <TableHeader>Phone #</TableHeader>
                          <TableHeader>Email</TableHeader>
                          <TableHeader>Actions</TableHeader>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {rows.map((z) => (
                          <TableRow key={`${z.adminForestDistrictNo ?? ''}-${z.rangeZoneCode}`}>
                            <TableCell>{districtLabel(z)}</TableCell>
                            <TableCell>{z.rangeZoneCode}</TableCell>
                            <TableCell>{z.zoneDescription ?? '—'}</TableCell>
                            <TableCell>
                              {z.contact ?? <UserName userId={z.contactUserId} />}
                            </TableCell>
                            <TableCell>{z.contactPhoneNumber ?? '—'}</TableCell>
                            <TableCell>{z.contactEmailAddress ?? '—'}</TableCell>
                            <TableCell>
                              <Button
                                size="sm"
                                kind="ghost"
                                hasIconOnly
                                renderIcon={Edit}
                                iconDescription="Update zone"
                                onClick={() => openDialog(z)}
                              />
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </div>
              ))}
          </AsyncBoundary>
        </SectionTile>
      )}

      <Modal
        open={open}
        passiveModal
        size="sm"
        className="detail-dialog"
        modalHeading={editing ? `Update zone ${editing.rangeZoneCode}` : 'Add zone'}
        onRequestClose={() => !saving && setOpen(false)}
        preventCloseOnClickOutside
      >
        <Stack gap={5}>
          <Select
            id="zone-form-district"
            labelText="District"
            value={form.district}
            invalid={!!errors.district}
            invalidText={errors.district}
            disabled={saving}
            onChange={(e) => set('district', e.target.value)}
          >
            <SelectItem value="" text="Choose a district" />
            {districts.map((d) => (
              <SelectItem key={d.code} value={d.code} text={d.description} />
            ))}
          </Select>
          <TextInput
            id="zone-form-zone"
            labelText="Zone"
            value={form.zone}
            maxLength={4}
            autoComplete="off"
            invalid={!!errors.zone}
            invalidText={errors.zone}
            disabled={saving || editing !== null}
            onChange={(e) => set('zone', e.target.value.toUpperCase())}
          />
          <TextInput
            id="zone-form-description"
            labelText="Zone Description/Area of Resp. (optional)"
            value={form.description}
            maxLength={120}
            autoComplete="off"
            disabled={saving}
            onChange={(e) => set('description', e.target.value)}
          />
          <TextInput
            id="zone-form-contact"
            labelText="Contact (IDIR user id)"
            value={form.contactUserId}
            maxLength={64}
            autoComplete="off"
            invalid={!!errors.contactUserId}
            invalidText={errors.contactUserId}
            disabled={saving}
            onChange={(e) => set('contactUserId', e.target.value)}
          />
          <div className="detail-dialog__pair">
            <TextInput
              id="zone-form-phone"
              labelText="Phone #"
              value={form.phone}
              maxLength={10}
              inputMode="tel"
              autoComplete="off"
              invalid={!!errors.phone}
              invalidText={errors.phone}
              disabled={saving}
              onChange={(e) => set('phone', e.target.value)}
            />
            <TextInput
              id="zone-form-email"
              labelText="Email"
              value={form.email}
              maxLength={120}
              autoComplete="off"
              invalid={!!errors.email}
              invalidText={errors.email}
              disabled={saving}
              onChange={(e) => set('email', e.target.value)}
            />
          </div>
        </Stack>
        <div className="detail-dialog__actions">
          <Button kind="tertiary" disabled={saving} onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button kind="primary" disabled={saving} onClick={() => void submit()}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </Modal>
    </PageLayout>
  );
};

export default ManageZone;
