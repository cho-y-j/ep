import { Navigate, Route, Routes } from 'react-router-dom';
import LoginPage from './features/auth/LoginPage';
import SignupPage from './features/auth/SignupPage';
import PendingApprovalPage from './features/auth/PendingApprovalPage';
import AdminUsersPage from './features/user/AdminUsersPage';
import AdminAnnouncementsPage from './features/announcement/AdminAnnouncementsPage';
import CompanyUsersPage from './features/user/CompanyUsersPage';
import AdminCompaniesPage from './features/company/AdminCompaniesPage';
import AdminBpCompaniesPage from './features/company/AdminBpCompaniesPage';
import AdminSupplierCompaniesPage from './features/company/AdminSupplierCompaniesPage';
import CompanyDetailPage from './features/company/CompanyDetailPage';
import ClientOrgsPage from './features/admin/ClientOrgsPage';
import OpenBidCreatePage from './features/quotation/OpenBidCreatePage';
import OpenBidsBoardPage from './features/quotation/OpenBidsBoardPage';
import MyProposalsPage from './features/quotation/MyProposalsPage';
import OutgoingNewPage from './features/outgoing/OutgoingNewPage';
import DocumentTypeAdminPage from './features/admin/DocumentTypeAdminPage';
import OutgoingSentPage from './features/outgoing/OutgoingSentPage';
import InboxPage from './features/outgoing/InboxPage';
import EquipmentPage from './features/equipment/EquipmentPage';
import EquipmentDetailPage from './features/equipment/EquipmentDetailPage';
import EquipmentDeploymentStatsPage from './features/equipment/EquipmentDeploymentStatsPage';
import PersonPage from './features/person/PersonPage';
import PersonDetailPage from './features/person/PersonDetailPage';
import SitePage from './features/site/SitePage';
import SiteDetailPage from './features/site/SiteDetailPage';
import SafetyInspectionsPage from './features/safety/SafetyInspectionsPage';
import SafetyAlertsPage from './features/safetyAlerts/SafetyAlertsPage';
import WorkPlanPage from './features/workPlan/WorkPlanPage';
import WorkPlanPendingPage from './features/workPlan/WorkPlanPendingPage';
import WorkPlanDetailPage from './features/workPlan/WorkPlanDetailPage';
import WorkPlanPrintPage from './features/workPlan/WorkPlanPrintPage';
import DocxTemplatesPage from './features/workPlan/DocxTemplatesPage';
import WorkPlanEditPage from './features/workPlan/WorkPlanEditPage';
import WorkPlanCreatePage from './features/workPlan/create/WorkPlanCreatePage';
import MyCompanyPage from './features/company/MyCompanyPage';
import SubSuppliersPage from './features/company/SubSuppliersPage';
import QuotationListPage from './features/quotation/QuotationListPage';
import QuotationCreatePage from './features/quotation/QuotationCreatePage';
import AlimTalkSendPage from './features/alimtalk/AlimTalkSendPage';
import QuotationDetailPage from './features/quotation/QuotationDetailPage';
import QuotationBundleDetailPage from './features/quotation/QuotationBundleDetailPage';
import DocumentManagementPage from './features/compliance/DocumentManagementPage';
import ComplianceOrdersPage from './features/compliance/ComplianceOrdersPage';
import ResourceCheckBpList from './features/resourceCheck/BpListPage';
import ResourceCheckSupplierInbox from './features/resourceCheck/SupplierInboxPage';
import SupplierReceivedPage from './features/supplier/SupplierReceivedPage';
import WorksheetEditorPage from './features/workPlan/edit/WorksheetEditorPage';
import SignaturePage from './features/signature/SignaturePage';
import CollectPublicPage from './features/collection/CollectPublicPage';
import DocumentCollectionPage from './features/collection/DocumentCollectionPage';
import DashboardRedirect from './features/dashboard/DashboardRedirect';
import AdminDashboardPage from './features/dashboard/AdminDashboardPage';
import BpDashboardPage from './features/dashboard/BpDashboardPage';
import EquipmentSupplierDashboardPage from './features/dashboard/EquipmentSupplierDashboardPage';
import ManpowerSupplierDashboardPage from './features/dashboard/ManpowerSupplierDashboardPage';
import WorkerDashboardPage from './features/dashboard/WorkerDashboardPage';
import AuditLogPage from './features/dashboard/AuditLogPage';
import ReviewQueuePage from './features/document/ReviewQueuePage';
import DocumentReviewSendPage from './features/document/DocumentReviewSendPage';
import BpReceivedReviewsPage from './features/document/BpReceivedReviewsPage';
import AdminExpiringDocumentsPage from './features/document/AdminExpiringDocumentsPage';
import NotificationsPage from './features/notification/NotificationsPage';
import DispatchedEquipmentBpPage from './features/dispatched/DispatchedEquipmentBpPage';
import DispatchedPersonsBpPage from './features/dispatched/DispatchedPersonsBpPage';
import FieldDeploymentSupplierPage from './features/fieldDeployment/SupplierRequestPage';
import MonthlyWorkConfirmationPage from './features/workConfirmation/MonthlyWorkConfirmationPage';
import FieldDeploymentBpInbox from './features/fieldDeployment/BpInboxPage';
import BpActiveSitesPage from './features/fieldDeployment/BpActiveSitesPage';
import SiteBoardPage from './features/fieldDeployment/SiteBoardPage';
import ProtectedRoute from './components/ProtectedRoute';
import { AuthProvider } from './features/auth/AuthContext';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/pending-approval" element={<PendingApprovalPage />} />
        {/* S-12: 공개 전자서명 페이지 — 토큰만으로 접근 (인증 불필요) */}
        <Route path="/sign/:token" element={<SignaturePage />} />
        <Route path="/collect/:token" element={<CollectPublicPage />} />

        {/* 루트 / 공통 dashboard 진입 — 역할별 dashboard 로 redirect */}
        <Route path="/" element={<ProtectedRoute><DashboardRedirect /></ProtectedRoute>} />
        <Route path="/dashboard" element={<ProtectedRoute><DashboardRedirect /></ProtectedRoute>} />

        {/* 역할별 dashboard */}
        <Route
          path="/admin/dashboard"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AdminDashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/bp/dashboard"
          element={
            <ProtectedRoute roles={['BP']}>
              <BpDashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/equipment-supplier/dashboard"
          element={
            <ProtectedRoute roles={['EQUIPMENT_SUPPLIER']}>
              <EquipmentSupplierDashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/manpower-supplier/dashboard"
          element={
            <ProtectedRoute roles={['MANPOWER_SUPPLIER']}>
              <ManpowerSupplierDashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/worker/dashboard"
          element={
            <ProtectedRoute roles={['WORKER']}>
              <WorkerDashboardPage />
            </ProtectedRoute>
          }
        />

        {/* Audit log */}
        <Route
          path="/audit-logs"
          element={
            <ProtectedRoute>
              <AuditLogPage />
            </ProtectedRoute>
          }
        />

        {/* S-4 단계 4: 알림 + ADMIN 검토 큐 */}
        <Route
          path="/notifications"
          element={<ProtectedRoute><NotificationsPage /></ProtectedRoute>}
        />
        <Route
          path="/admin/document-review"
          element={<ProtectedRoute roles={['ADMIN']}><ReviewQueuePage /></ProtectedRoute>}
        />
        <Route
          path="/admin/expiring-documents"
          element={<ProtectedRoute roles={['ADMIN']}><AdminExpiringDocumentsPage /></ProtectedRoute>}
        />

        {/* 어드민 전용 */}
        <Route
          path="/admin/users"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AdminUsersPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/announcements"
          element={
            <ProtectedRoute roles={['ADMIN', 'BP']}>
              <AdminAnnouncementsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/companies"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AdminCompaniesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/bp"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AdminBpCompaniesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/suppliers"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AdminSupplierCompaniesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/companies/:id"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <CompanyDetailPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/client-orgs"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <ClientOrgsPage />
            </ProtectedRoute>
          }
        />
        <Route path="/quotations/new/open-bid"
               element={<ProtectedRoute roles={['BP', 'ADMIN']}><OpenBidCreatePage /></ProtectedRoute>} />
        <Route path="/quotations/open-bids"
               element={<ProtectedRoute><OpenBidsBoardPage /></ProtectedRoute>} />
        <Route path="/my-proposals"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}><MyProposalsPage /></ProtectedRoute>} />
        <Route path="/document-review-send"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}><DocumentReviewSendPage /></ProtectedRoute>} />
        <Route path="/document-reviews/received"
               element={<ProtectedRoute roles={['BP', 'ADMIN']}><BpReceivedReviewsPage /></ProtectedRoute>} />
        <Route path="/outgoing-quotations"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER', 'ADMIN']}><OutgoingSentPage /></ProtectedRoute>} />
        <Route path="/outgoing-quotations/new"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER', 'ADMIN']}><OutgoingNewPage /></ProtectedRoute>} />
        <Route path="/inbox"
               element={<ProtectedRoute roles={['BP', 'ADMIN']}><InboxPage /></ProtectedRoute>} />
        <Route path="/dispatched-equipment"
               element={<ProtectedRoute roles={['BP', 'ADMIN']}><DispatchedEquipmentBpPage /></ProtectedRoute>} />
        <Route path="/dispatched-persons"
               element={<ProtectedRoute roles={['BP', 'ADMIN']}><DispatchedPersonsBpPage /></ProtectedRoute>} />
        <Route path="/field-deployments/supplier"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER', 'ADMIN']}><FieldDeploymentSupplierPage /></ProtectedRoute>} />
        <Route path="/field-deployments/bp"
               element={<ProtectedRoute roles={['BP', 'ADMIN']}><FieldDeploymentBpInbox /></ProtectedRoute>} />

        {/* 도메인 페이지 — 권한은 페이지/API 단에서 강제 */}
        <Route
          path="/my-company"
          element={
            <ProtectedRoute roles={['BP', 'EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}>
              <MyCompanyPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/company/users"
          element={
            <ProtectedRoute roles={['BP', 'EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}>
              <CompanyUsersPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/sub-suppliers"
          element={
            <ProtectedRoute roles={['EQUIPMENT_SUPPLIER']}>
              <SubSuppliersPage />
            </ProtectedRoute>
          }
        />
        <Route path="/sites" element={<ProtectedRoute><SitePage /></ProtectedRoute>} />
        <Route path="/sites/:id" element={<ProtectedRoute><SiteDetailPage /></ProtectedRoute>} />
        <Route path="/safety-inspections" element={<ProtectedRoute><SafetyInspectionsPage /></ProtectedRoute>} />
        <Route path="/safety-alerts" element={<ProtectedRoute roles={['ADMIN','BP']}><SafetyAlertsPage /></ProtectedRoute>} />
        <Route path="/equipment" element={<ProtectedRoute><EquipmentPage /></ProtectedRoute>} />
        <Route path="/equipment/:id" element={<ProtectedRoute><EquipmentDetailPage /></ProtectedRoute>} />
        <Route path="/equipment-stats" element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER','ADMIN']}><EquipmentDeploymentStatsPage /></ProtectedRoute>} />
        <Route path="/document-collections" element={<ProtectedRoute roles={['ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER']}><DocumentCollectionPage /></ProtectedRoute>} />
        <Route path="/persons" element={<ProtectedRoute><PersonPage /></ProtectedRoute>} />
        <Route path="/persons/:id" element={<ProtectedRoute><PersonDetailPage /></ProtectedRoute>} />
        <Route path="/work-plans" element={<ProtectedRoute><WorkPlanPage /></ProtectedRoute>} />
        <Route path="/work-plans/pending" element={<ProtectedRoute roles={['BP', 'ADMIN']}><WorkPlanPendingPage /></ProtectedRoute>} />
        <Route path="/work-plans/active" element={<ProtectedRoute roles={['BP', 'ADMIN']}><BpActiveSitesPage /></ProtectedRoute>} />
        <Route path="/work-plans/active/sites/:siteId" element={<ProtectedRoute roles={['BP', 'ADMIN']}><SiteBoardPage /></ProtectedRoute>} />
        <Route path="/work-plans/new" element={<ProtectedRoute><WorkPlanCreatePage /></ProtectedRoute>} />
        <Route path="/work-plans/:id" element={<ProtectedRoute><WorkPlanDetailPage /></ProtectedRoute>} />
        <Route path="/work-plans/:id/print" element={<ProtectedRoute><WorkPlanPrintPage /></ProtectedRoute>} />
        <Route path="/work-confirmations/monthly"
               element={<ProtectedRoute roles={['ADMIN', 'BP', 'EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}><MonthlyWorkConfirmationPage /></ProtectedRoute>} />
        <Route
          path="/admin/docx-templates"
          element={<ProtectedRoute roles={['ADMIN', 'BP']}><DocxTemplatesPage /></ProtectedRoute>}
        />
        <Route
          path="/admin/document-types"
          element={<ProtectedRoute roles={['ADMIN']}><DocumentTypeAdminPage /></ProtectedRoute>}
        />
        <Route path="/work-plans/:id/edit" element={<ProtectedRoute><WorkPlanEditPage /></ProtectedRoute>} />

        {/* S-10: 견적 요청 */}
        <Route path="/quotations" element={<ProtectedRoute><QuotationListPage /></ProtectedRoute>} />
        <Route
          path="/quotations/new"
          element={<ProtectedRoute roles={['ADMIN', 'BP']}><QuotationCreatePage /></ProtectedRoute>}
        />
        <Route path="/alimtalk" element={<ProtectedRoute roles={['ADMIN', 'BP']}><AlimTalkSendPage /></ProtectedRoute>} />
        <Route path="/quotations/bundles/:bundleId" element={<ProtectedRoute><QuotationBundleDetailPage /></ProtectedRoute>} />
        <Route path="/quotations/:id" element={<ProtectedRoute><QuotationDetailPage /></ProtectedRoute>} />

        {/* S-11: 서류관리 (BP/ADMIN 사이트별 보드 + 보낸/받은 보완요청) */}
        <Route
          path="/document-management"
          element={<ProtectedRoute><DocumentManagementPage /></ProtectedRoute>}
        />

        {/* Compl: 이행지시 — BP 발행 + 공급사 증빙 업로드 + BP 검토 */}
        <Route
          path="/compliance-orders"
          element={<ProtectedRoute><ComplianceOrdersPage /></ProtectedRoute>}
        />

        {/* DocReq: 자원 점검 요청 (자동차 안전점검/건강검진/안전교육) */}
        <Route
          path="/resource-checks/bp"
          element={<ProtectedRoute roles={['ADMIN', 'BP']}><ResourceCheckBpList /></ProtectedRoute>}
        />
        <Route
          path="/resource-checks/supplier"
          element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}><ResourceCheckSupplierInbox /></ProtectedRoute>}
        />
        {/* 공급사 "받은 요청" 통합 탭 (점검요청/이행지시/보완요청) — 기존 라우트도 유지 */}
        <Route
          path="/supplier/received"
          element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}><SupplierReceivedPage /></ProtectedRoute>}
        />

        {/* S-12: 작업계획서 생성 중 OnlyOffice 새 탭 편집기 (wp.id 없이 임시 세션) */}
        <Route
          path="/worksheet/edit/:sessionId"
          element={<ProtectedRoute roles={['ADMIN', 'BP']}><WorksheetEditorPage /></ProtectedRoute>}
        />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
