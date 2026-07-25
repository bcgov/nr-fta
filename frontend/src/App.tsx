import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import type { FC, ReactNode } from 'react';

import Layout from './components/Layout';
import { useAuth } from './context/auth/useAuth';
import { defaultRouteForUser, isPathAllowedForUser } from './routes/access';

// Core pages
import LandingPage from './pages/LandingPage';
import UnauthorizedPage from './pages/UnauthorizedPage';
import ForbiddenPage from './pages/ForbiddenPage';
import NotFound from './pages/NotFound';
import Welcome from './pages/Welcome';

// Ported screens
import TenureSearch from './pages/search/TenureSearch';
import TenureDetail from './pages/tenure/TenureDetail';
import HarvestingAuthoritySearch from './pages/search/HarvestingAuthoritySearch';
import CutBlockSearch from './pages/search/CutBlockSearch';
import CuttingPermitDetail from './pages/harvesting/CuttingPermitDetail';
import CutBlockDetail from './pages/harvesting/CutBlockDetail';
import SuspendBlocks from './pages/harvesting/SuspendBlocks';
import AssignMarks from './pages/harvesting/AssignMarks';
import CutBlockAction from './pages/harvesting/CutBlockAction';
import TenureLanding from './pages/tenure/TenureLanding';
import AddTenure from './pages/tenure/AddTenure';
import RoadDetail from './pages/tenure/RoadDetail';
import Inbox from './pages/inbox/Inbox';
import ApplicationDetail from './pages/inbox/ApplicationDetail';
import ExhibitAMap from './pages/inbox/ExhibitAMap';
import UploadExhibitA from './pages/inbox/UploadExhibitA';
import MarkList from './pages/marks/MarkList';
import MarkDetail from './pages/marks/MarkDetail';
import MarkApplication from './pages/marks/MarkApplication';
import RangeTenureSearch from './pages/search/RangeTenureSearch';
import RangeUnitSearch from './pages/search/RangeUnitSearch';
import RangeTenureDetail from './pages/range/RangeTenureDetail';
import RangeUnitDetail from './pages/range/RangeUnitDetail';
import TimberMarkSearch from './pages/search/TimberMarkSearch';
import ClientSearch from './pages/search/ClientSearch';
import ManagementUnitSearch from './pages/search/ManagementUnitSearch';
import ApplicationMetrics from './pages/search/ApplicationMetrics';
import BillingReportScreen from './pages/admin/BillingReportScreen';
import AuditReport from './pages/admin/AuditReport';
import RatesMaintenance from './pages/admin/RatesMaintenance';
import ManageZone from './pages/admin/ManageZone';
import OrgUnitMaintenance from './pages/admin/OrgUnitMaintenance';
import MarkTransfer from './pages/admin/MarkTransfer';
import ArchiveTenures from './pages/admin/ArchiveTenures';

import './App.css';

// Wraps a page in the Carbon UI Shell.
const withLayout = (node: ReactNode) => <Layout>{node}</Layout>;

/**
 * Per-role route guard. If the user's role doesn't permit the current
 * pathname, swap the page for {@link ForbiddenPage} (still inside the shell
 * so the SideNav lets them click to a page they DO have access to).
 */
const RoleGuarded: FC<{ children: ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const location = useLocation();
  if (!isPathAllowedForUser(user, location.pathname)) {
    return <Layout><ForbiddenPage /></Layout>;
  }
  return <>{children}</>;
};

const guarded = (node: ReactNode) => <RoleGuarded>{node}</RoleGuarded>;

export default function App() {
  const { isLoggedIn, isLoading, user } = useAuth();

  // Minimal placeholder during the initial auth bootstrap so a mid-session
  // reload doesn't briefly flash the LandingPage.
  if (isLoading) {
    return <div aria-busy="true" />;
  }

  // Authenticated-but-unauthorized: token decoded fine but carries no
  // recognised FTA_* role.
  const hasFtaRole = isLoggedIn && (user?.roles?.length ?? 0) > 0;

  return (
    <BrowserRouter>
      {isLoggedIn && !hasFtaRole ? (
        <Routes>
          <Route path="*" element={<UnauthorizedPage />} />
        </Routes>
      ) : isLoggedIn ? (
        <Routes>
          <Route path="/auth/callback" element={<Navigate to={defaultRouteForUser(user)} replace />} />
          <Route path="/" element={<Navigate to={defaultRouteForUser(user)} replace />} />

          {/* Home */}
          <Route path="/welcome" element={guarded(withLayout(<Welcome />))} />

          {/* ── Inbox / adjudication ───────────────────────────────── */}
          <Route path="/inbox" element={guarded(withLayout(<Inbox />))} />
          <Route path="/inbox/:esfId" element={guarded(withLayout(<ApplicationDetail />))} />
          <Route path="/exhibit-a/:esfId" element={guarded(withLayout(<ExhibitAMap />))} />
          <Route path="/exhibit-a/:esfId/upload" element={guarded(withLayout(<UploadExhibitA />))} />

          {/* ── Search ─────────────────────────────────────────────── */}
          <Route path="/search/tenure" element={guarded(withLayout(<TenureSearch />))} />
          <Route path="/search/harvesting-authority" element={guarded(withLayout(<HarvestingAuthoritySearch />))} />
          <Route path="/search/timber-mark" element={guarded(withLayout(<TimberMarkSearch />))} />
          <Route path="/search/cut-block" element={guarded(withLayout(<CutBlockSearch />))} />
          <Route path="/search/range-tenure" element={guarded(withLayout(<RangeTenureSearch />))} />
          <Route path="/search/range-unit" element={guarded(withLayout(<RangeUnitSearch />))} />
          <Route path="/search/metrics" element={guarded(withLayout(<ApplicationMetrics />))} />
          <Route path="/search/client" element={guarded(withLayout(<ClientSearch />))} />
          <Route path="/search/management-unit" element={guarded(withLayout(<ManagementUnitSearch />))} />

          {/* ── Tenures ────────────────────────────────────────────── */}
          <Route path="/tenures" element={guarded(withLayout(<TenureLanding />))} />
          <Route path="/tenures/add" element={guarded(withLayout(<AddTenure />))} />
          <Route path="/tenures/:fileId" element={guarded(withLayout(<TenureDetail />))} />

          {/* ── Harvesting Authority / Cutting Permits & Cut Blocks ── */}
          <Route path="/harvesting-authority/:cpId" element={guarded(withLayout(<CuttingPermitDetail />))} />
          <Route path="/harvesting-authority/:cpId/suspend-blocks" element={guarded(withLayout(<SuspendBlocks />))} />
          <Route path="/harvesting-authority/:cpId/assign-marks" element={guarded(withLayout(<AssignMarks />))} />
          <Route path="/cut-block/:blockId" element={guarded(withLayout(<CutBlockDetail />))} />
          <Route path="/cut-block/:blockId/:action" element={guarded(withLayout(<CutBlockAction />))} />
          <Route path="/road/:roadId" element={guarded(withLayout(<RoadDetail />))} />

          {/* ── Private Marks ──────────────────────────────────────── */}
          <Route path="/marks" element={guarded(withLayout(<MarkList />))} />
          <Route path="/marks/application" element={guarded(withLayout(<MarkApplication />))} />
          <Route path="/marks/:markNumber" element={guarded(withLayout(<MarkDetail />))} />

          {/* ── Range ──────────────────────────────────────────────── */}
          <Route path="/range/:agreementId" element={guarded(withLayout(<RangeTenureDetail />))} />
          <Route path="/range-unit/:unitId" element={guarded(withLayout(<RangeUnitDetail />))} />

          {/* ── Admin (FTA_ADMIN only) ─────────────────────────────── */}
          <Route path="/admin/audit" element={guarded(withLayout(<AuditReport />))} />
          <Route path="/admin/rents-fees" element={guarded(withLayout(
            <BillingReportScreen title="Annual Rents & Fees Preparation" legacyId="FTA670"
              actionLabel="Run rents & fees preparation"
              description="Prepare the annual rent and fee charges for all active tenures in the billing cycle." />
          ))} />
          <Route path="/admin/mark-transfer" element={guarded(withLayout(<MarkTransfer />))} />
          <Route path="/admin/range-zone" element={guarded(withLayout(<ManageZone />))} />
          <Route path="/admin/org-unit" element={guarded(withLayout(<OrgUnitMaintenance />))} />
          <Route path="/admin/billing/tenure" element={guarded(withLayout(
            <BillingReportScreen title="Tenure Billing Instructions" legacyId="FTA680"
              actionLabel="Save billing instructions"
              description="Review and confirm the billing instructions for each tenure before the billing run." />
          ))} />
          <Route path="/admin/billing/invoice-preview" element={guarded(withLayout(
            <BillingReportScreen title="Invoice Preview" legacyId="FTA695"
              actionLabel="Generate invoices"
              description="Preview the invoices that will be generated for the current billing cycle." />
          ))} />
          <Route path="/admin/billing/pre-billing" element={guarded(withLayout(
            <BillingReportScreen title="Pre Billing Report Submission" legacyId="FTA685"
              actionLabel="Submit pre-billing report"
              description="Submit the pre-billing report for review before invoices are issued." />
          ))} />
          <Route path="/admin/billing/post-billing" element={guarded(withLayout(
            <BillingReportScreen title="Post Billing Report Submission" legacyId="FTA686"
              actionLabel="Submit post-billing report"
              description="Submit the post-billing reconciliation report after invoices are issued." />
          ))} />
          <Route path="/admin/billing/approval" element={guarded(withLayout(
            <BillingReportScreen title="Tenure Approval Submission" legacyId="FTA690"
              actionLabel="Submit for approval"
              description="Submit the prepared billing lines for management approval." />
          ))} />
          <Route path="/admin/rates-fees" element={guarded(withLayout(<RatesMaintenance />))} />
          <Route path="/admin/archive" element={guarded(withLayout(<ArchiveTenures />))} />

          {/* Unknown in-shell path → 404 */}
          <Route path="*" element={guarded(withLayout(<NotFound />))} />
        </Routes>
      ) : (
        <Routes>
          {/* Unauthenticated — every URL falls through to Landing. Amplify
              handles ?code=&state= at boot (main.tsx) before AuthProvider
              hydrates and the authenticated branch takes over. */}
          <Route path="*" element={<LandingPage />} />
        </Routes>
      )}
    </BrowserRouter>
  );
}
