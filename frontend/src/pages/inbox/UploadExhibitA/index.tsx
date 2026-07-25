import { Button } from '@carbon/react';
import { ArrowLeft, Upload } from '@carbon/icons-react';
import { useState, type FC } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import DragDropFileInput from '@/components/DragDropFileInput';
import { useAuth } from '@/context/auth/useAuth';
import { useNotification } from '@/context/notification/useNotification';
import { canEdit } from '@/routes/access';
import PageLayout from '@/pages/PageLayout';
import { uploadExhibitA } from '@/services/exhibit_a_write';

// Guard the base64-in-JSON upload: a large file becomes a ~33%-bigger base64
// string held in memory in the browser, over the wire, and in the server's
// request buffer. Reject oversized files client-side so we never build a
// multi-MB JSON body that would blow past request-size limits.
const MAX_UPLOAD_BYTES = 20 * 1024 * 1024; // 20 MB

/** Read a File as a base64 string (without the data: URL prefix). */
function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      resolve(result.slice(result.indexOf(',') + 1));
    };
    reader.onerror = () => reject(reader.error ?? new Error('Could not read file'));
    reader.readAsDataURL(file);
  });
}

/**
 * FTA307 — Upload Exhibit A. Lets an adjudicator attach the spatial submission
 * (PDF map / GeoJSON) for an application. Gated to FTA_ADMIN; on submit it
 * raises a notification (mock — no backend yet) and returns to the map.
 */
const UploadExhibitA: FC = () => {
  const { esfId = '' } = useParams();
  const { user } = useAuth();
  const notify = useNotification();
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const readOnly = !canEdit(user);

  const onUpload = async () => {
    if (!file) return;
    if (file.size > MAX_UPLOAD_BYTES) {
      notify.display({
        kind: 'error',
        title: 'File too large',
        subtitle: `${file.name} is ${(file.size / 1024 / 1024).toFixed(1)} MB; the maximum is ${MAX_UPLOAD_BYTES / 1024 / 1024} MB.`,
        timeout: 6000,
      });
      return;
    }
    setSaving(true);
    try {
      const imageBase64 = await fileToBase64(file);
      await uploadExhibitA(esfId, {
        revisionCount: null,
        taiRevisionCount: null,
        imageBase64,
      });
      notify.display({
        kind: 'success',
        title: 'Exhibit A uploaded',
        subtitle: `${file.name} attached to ${esfId}.`,
        timeout: 5000,
      });
      navigate(`/exhibit-a/${esfId}`);
    } catch (err) {
      notify.display({
        kind: 'error',
        title: 'Could not upload Exhibit A',
        subtitle: err instanceof Error ? err.message : 'Request failed',
        timeout: 6000,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageLayout title={`Upload Exhibit A — ${esfId}`}>
      <Link to={`/exhibit-a/${esfId}`} className="fta-back">
        <ArrowLeft size={16} /> Back to Exhibit A map
      </Link>

      {readOnly && (
        <p style={{ marginBottom: '1.5rem', color: 'var(--cds-text-secondary)' }}>
          You have read-only access. Uploading requires the Administrator role.
        </p>
      )}

      <div style={{ maxWidth: '40rem' }}>
        <DragDropFileInput
          label="Exhibit A submission"
          helperText="PDF map or GeoJSON, up to 20 MB"
          accept={['.pdf', '.geojson', '.zip']}
          file={file}
          disabled={readOnly}
          onSelect={setFile}
          onRemove={() => setFile(null)}
        />
        <div style={{ marginTop: '1.5rem' }}>
          <Button renderIcon={Upload} disabled={readOnly || !file || saving} onClick={onUpload}>
            {saving ? 'Uploading…' : 'Upload Exhibit A'}
          </Button>
        </div>
      </div>
    </PageLayout>
  );
};

export default UploadExhibitA;
