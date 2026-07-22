import { Navigate, Route, Routes } from 'react-router-dom';
import LoginPage from './features/auth/LoginPage';
import SignupPage from './features/auth/SignupPage';
import PendingApprovalPage from './features/auth/PendingApprovalPage';
import AdminUsersPage from './features/user/AdminUsersPage';
import AdminAnnouncementsPage from './features/announcement/AdminAnnouncementsPage';
import SupplierAnnouncementPage from './features/announcement/SupplierAnnouncementPage';
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
import EquipmentTypeDocsPage from './features/admin/EquipmentTypeDocsPage';
import PersonRoleDocsPage from './features/admin/PersonRoleDocsPage';
import DocumentTypeRegionEditorPage from './features/admin/regionTemplate/DocumentTypeRegionEditorPage';
import SafetyCheckTemplatesPage from './features/admin/SafetyCheckTemplatesPage';
import InspectorPage from './features/inspector/InspectorPage';
import WorkerCalendarPage from './features/worker/WorkerCalendarPage';
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
import SafetySettingsPage from './features/safety/SafetySettingsPage';
import BpCheckinPage from './features/health/BpCheckinPage';
import SafetyReportPage from './features/safety/SafetyReportPage';
import SafetyReportPrintPage from './features/safety/SafetyReportPrintPage';
import SafetyAlertsPage from './features/safetyAlerts/SafetyAlertsPage';
import SafetyBoardPage from './features/safetyBoard/SafetyBoardPage';
import WorkPlanPage from './features/workPlan/WorkPlanPage';
import WorkPlanPendingPage from './features/workPlan/WorkPlanPendingPage';
import WorkPlanDetailPage from './features/workPlan/WorkPlanDetailPage';
import WorkPlanPrintPage from './features/workPlan/WorkPlanPrintPage';
import DocxTemplatesPage from './features/workPlan/DocxTemplatesPage';
import WorkPlanEditPage from './features/workPlan/WorkPlanEditPage';
import WorkPlanCreatePage from './features/workPlan/create/WorkPlanCreatePage';
import ResourceChangeListPage from './features/resourceChange/ResourceChangeListPage';
import ResourceChangeCreatePage from './features/resourceChange/ResourceChangeCreatePage';
import ResourceChangeDetailPage from './features/resourceChange/ResourceChangeDetailPage';
import MyCompanyPage from './features/company/MyCompanyPage';
import SubSuppliersPage from './features/company/SubSuppliersPage';
import HandledEquipmentTypesPage from './features/company/HandledEquipmentTypesPage';
import OnboardingWizardPage from './features/onboarding/OnboardingWizardPage';
import ContractsPage from './features/contract/ContractsPage';
import QuoteTemplatesPage from './features/quotetemplate/QuoteTemplatesPage';
import DailyWorkLogPage from './features/dailyWork/DailyWorkLogPage';
import BpWorkLogLedgerPage from './features/dailyWork/BpWorkLogLedgerPage';
import SupplierOnboardingPage from './features/onboarding/SupplierOnboardingPage';
import BpOnboardingApprovalPage from './features/onboarding/BpOnboardingApprovalPage';
import QuotationListPage from './features/quotation/QuotationListPage';
import QuotationCreatePage from './features/quotation/QuotationCreatePage';
import AlimTalkSendPage from './features/alimtalk/AlimTalkSendPage';
import QuotationDetailPage from './features/quotation/QuotationDetailPage';
import QuotationBundleDetailPage from './features/quotation/QuotationBundleDetailPage';
import DocumentManagementPage from './features/compliance/DocumentManagementPage';
import ComplianceOrdersPage from './features/compliance/ComplianceOrdersPage';
import ResourceCheckBpList from './features/resourceCheck/BpListPage';
import ResourceCheckSupplierInbox from './features/resourceCheck/SupplierInboxPage';
import DocumentReviewSendPage from './features/document/DocumentReviewSendPage';
import WorksheetEditorPage from './features/workPlan/edit/WorksheetEditorPage';
import SignaturePage from './features/signature/SignaturePage';
import CollectPublicPage from './features/collection/CollectPublicPage';
import DocumentCollectionPage from './features/collection/DocumentCollectionPage';
import SettlementPage from './features/settlement/SettlementPage';
import ResourcePipelinePage from './features/pipeline/ResourcePipelinePage';
import DashboardRedirect from './features/dashboard/DashboardRedirect';
import AdminDashboardPage from './features/dashboard/AdminDashboardPage';
import BpDashboardPage from './features/dashboard/BpDashboardPage';
import EquipmentSupplierDashboardPage from './features/dashboard/EquipmentSupplierDashboardPage';
import ManpowerSupplierDashboardPage from './features/dashboard/ManpowerSupplierDashboardPage';
import WorkerDashboardPage from './features/dashboard/WorkerDashboardPage';
import ClientDashboardPage from './features/client/ClientDashboardPage';
import AuditLogPage from './features/dashboard/AuditLogPage';
import ReviewQueuePage from './features/document/ReviewQueuePage';
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
import LandingPage from './features/landing/LandingPage';
import SignupApprovalsPage from './features/admin/SignupApprovalsPage';
import ConsultationsPage from './features/admin/ConsultationsPage';
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
        {/* S2′ 안전점검원 모바일웹 — 점검원 자가로그인(X-Field-Token), JWT 불필요 공개 라우트 */}
        <Route path="/inspector" element={<InspectorPage />} />
        {/* P4e 작업자 "내 작업 달력" 모바일웹 — 출근코드 로그인(X-Field-Token), JWT 불필요 공개 라우트 */}
        <Route path="/worker" element={<WorkerCalendarPage />} />

        {/* 루트 / — 공개 랜딩(비로그인). 로그인 사용자는 LandingPage 내부에서 역할별 dashboard 로 redirect */}
        <Route path="/" element={<LandingPage />} />
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
        <Route
          path="/client/dashboard"
          element={
            <ProtectedRoute roles={['CLIENT']}>
              <ClientDashboardPage />
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
          path="/admin/signup-approvals"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <SignupApprovalsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/consultations"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <ConsultationsPage />
            </ProtectedRoute>
          }
        />
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
          path="/announcements"
          element={
            <ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}>
              <SupplierAnnouncementPage />
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
        <Route path="/document-reviews/received"
               element={<ProtectedRoute roles={['BP', 'ADMIN']}><BpReceivedReviewsPage /></ProtectedRoute>} />
        <Route path="/outgoing-quotations"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER', 'ADMIN']}><OutgoingSentPage /></ProtectedRoute>} />
        <Route path="/outgoing-quotations/new"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER', 'ADMIN']}><OutgoingNewPage /></ProtectedRoute>} />
        <Route path="/quote-templates"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER', 'ADMIN']}><QuoteTemplatesPage /></ProtectedRoute>} />
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

        {/* P0.5a: 계약(단가 원천) + 기통과 소급/구두승인 */}
        <Route path="/contracts"
               element={<ProtectedRoute roles={['BP', 'EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER', 'ADMIN']}><ContractsPage /></ProtectedRoute>} />
        <Route path="/daily-work-logs"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}><DailyWorkLogPage /></ProtectedRoute>} />
        <Route path="/daily-work-logs/bp"
               element={<ProtectedRoute roles={['BP', 'ADMIN']}><BpWorkLogLedgerPage /></ProtectedRoute>} />
        <Route path="/resource-onboardings"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}><SupplierOnboardingPage /></ProtectedRoute>} />
        <Route path="/resource-onboardings/bp"
               element={<ProtectedRoute roles={['BP', 'ADMIN']}><BpOnboardingApprovalPage /></ProtectedRoute>} />

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
        <Route
          path="/onboarding/sub-suppliers"
          element={
            <ProtectedRoute roles={['EQUIPMENT_SUPPLIER']}>
              <OnboardingWizardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/settings/equipment-types"
          element={
            <ProtectedRoute roles={['EQUIPMENT_SUPPLIER']}>
              <HandledEquipmentTypesPage />
            </ProtectedRoute>
          }
        />
        <Route path="/sites" element={<ProtectedRoute><SitePage /></ProtectedRoute>} />
        <Route path="/sites/:id" element={<ProtectedRoute><SiteDetailPage /></ProtectedRoute>} />
        <Route path="/safety-board" element={<ProtectedRoute roles={['ADMIN','BP','CLIENT','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER']}><SafetyBoardPage /></ProtectedRoute>} />
        <Route path="/safety-inspections" element={<ProtectedRoute><SafetyInspectionsPage /></ProtectedRoute>} />
        <Route path="/safety-alerts" element={<ProtectedRoute roles={['ADMIN','BP']}><SafetyAlertsPage /></ProtectedRoute>} />
        <Route path="/safety-settings" element={<ProtectedRoute roles={['ADMIN','BP']}><SafetySettingsPage /></ProtectedRoute>} />
        <Route path="/bp-checkins" element={<ProtectedRoute roles={['ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER']}><BpCheckinPage /></ProtectedRoute>} />
        <Route path="/safety-reports" element={<ProtectedRoute roles={['ADMIN','BP','CLIENT']}><SafetyReportPage /></ProtectedRoute>} />
        <Route path="/safety-reports/print" element={<ProtectedRoute roles={['ADMIN','BP','CLIENT']}><SafetyReportPrintPage /></ProtectedRoute>} />
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
        <Route path="/resource-change-requests" element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER', 'BP', 'ADMIN']}><ResourceChangeListPage /></ProtectedRoute>} />
        <Route path="/resource-change-requests/new" element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER', 'BP', 'ADMIN']}><ResourceChangeCreatePage /></ProtectedRoute>} />
        <Route path="/resource-change-requests/:id" element={<ProtectedRoute><ResourceChangeDetailPage /></ProtectedRoute>} />
        <Route path="/work-confirmations/monthly"
               element={<ProtectedRoute roles={['ADMIN', 'BP', 'EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}><MonthlyWorkConfirmationPage /></ProtectedRoute>} />
        <Route path="/settlements"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER', 'ADMIN']}><SettlementPage /></ProtectedRoute>} />
        <Route path="/resource-pipeline"
               element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}><ResourcePipelinePage /></ProtectedRoute>} />
        <Route
          path="/admin/docx-templates"
          element={<ProtectedRoute roles={['ADMIN', 'BP']}><DocxTemplatesPage /></ProtectedRoute>}
        />
        <Route
          path="/admin/document-types"
          element={<ProtectedRoute roles={['ADMIN']}><DocumentTypeAdminPage /></ProtectedRoute>}
        />
        <Route
          path="/admin/equipment-type-docs"
          element={<ProtectedRoute roles={['ADMIN']}><EquipmentTypeDocsPage /></ProtectedRoute>}
        />
        <Route
          path="/admin/person-role-docs"
          element={<ProtectedRoute roles={['ADMIN']}><PersonRoleDocsPage /></ProtectedRoute>}
        />
        <Route
          path="/admin/safety-check-templates"
          element={<ProtectedRoute roles={['ADMIN']}><SafetyCheckTemplatesPage /></ProtectedRoute>}
        />
        <Route
          path="/admin/document-types/:id/regions"
          element={<ProtectedRoute roles={['ADMIN']}><DocumentTypeRegionEditorPage /></ProtectedRoute>}
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
        {/* 공급사 서류심사 발송 — 자원 서류를 zip 으로 묶어 이메일/BP계정 발송 (구 서류 허브 "서류심사" 채널) */}
        <Route
          path="/document-review-send"
          element={<ProtectedRoute roles={['EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER']}><DocumentReviewSendPage /></ProtectedRoute>}
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
