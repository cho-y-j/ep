import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import ConfirmDialog from '../../components/ConfirmDialog';
import DocumentSection from '../document/DocumentSection';
import DocumentCollectionDialog from '../collection/DocumentCollectionDialog';
import SupplementRequestDialog from '../document/SupplementRequestDialog';
import { useSubSuppliers } from '../company/useSubSuppliers';
import EquipmentPhotoGallery from './EquipmentPhotoGallery';
import DonutChart from './DonutChart';
import ResourceAssignmentSection from '../assignment/ResourceAssignmentSection';
import AssignmentBadge from '../assignment/AssignmentBadge';
import { equipmentCategoryLabel, type EquipmentResponse } from '../../types/equipment';
import ClientOrgHistory from '../../components/ClientOrgHistory';
import EquipmentDefaultOperators from './EquipmentDefaultOperators';
import EquipmentDuePanel from './EquipmentDuePanel';
import DeployCheckCard from '../readiness/DeployCheckCard';
import OnboardingBadge from '../onboarding/OnboardingBadge';

type TabId = 'overview' | 'inspection' | 'operation' | 'location' | 'maintenance' | 'note';

type Timeline = {
  inspections: Array<{ id: number; inspected_at: string; inspector?: string | null; title: string; result: string; note?: string | null; next_inspection_at?: string | null }>;
  operations: Array<{ id: number; started_at: string; ended_at?: string | null; site_name?: string | null; description?: string | null; utilization_pct?: number | null; status: string }>;
  locations: Array<{ id: number; recorded_at: string; location_name: string; note?: string | null }>;
  maintenances: Array<{ id: number; maintained_at: string; maintainer?: string | null; title: string; description?: string | null; cost?: number | null }>;
  notes: Array<{ id: number; author_id?: number | null; content: string; created_at: string }>;
};

export default function EquipmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [search] = useSearchParams();
  const fromCompanyId = search.get('fromCompany') ? Number(search.get('fromCompany')) : null;
  const [fromCompanyName, setFromCompanyName] = useState<string | null>(null);

  const subSuppliers = useSubSuppliers();
  const [equipment, setEquipment] = useState<EquipmentResponse | null>(null);
  const [collectOpen, setCollectOpen] = useState(false);
  const [supplementOpen, setSupplementOpen] = useState(false);
  const [timeline, setTimeline] = useState<Timeline | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>('overview');
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const canEdit = useMemo(() => {
    if (!equipment || !user) return false;
    if (user.role === 'ADMIN') return true;
    // 장비 소유 회사 (#5: BP 직속 장비 케이스 포함 — Person.supplier_id 와 동일 패턴)
    if (equipment.supplier_id === user.company_id) return true;
    // V77: 소유사가 내 직속 하위 공급사(협력사)면 부모로서 대행 수정/삭제.
    if (subSuppliers.some((c) => c.id === equipment.supplier_id)) return true;
    return false;
  }, [equipment, user, subSuppliers]);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setLoadError(null);
    try {
      const [eqRes, tlRes] = await Promise.all([
        api.get<EquipmentResponse>(`/api/equipment/${id}`),
        api.get<Timeline>(`/api/equipment/${id}/timeline`).catch(() => ({ data: { inspections: [], operations: [], locations: [], maintenances: [], notes: [] } as Timeline })),
      ]);
      setEquipment(eqRes.data);
      setTimeline(tlRes.data);
    } catch (err) {
      if (err instanceof AxiosError) {
        setLoadError(err.response?.data?.message ?? '장비 정보 불러오기 실패');
      } else {
        setLoadError('장비 정보 불러오기 실패');
      }
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (!fromCompanyId) { setFromCompanyName(null); return; }
    if (equipment?.supplier_id === fromCompanyId && equipment?.supplier_name) {
      setFromCompanyName(equipment.supplier_name);
      return;
    }
    let cancelled = false;
    api.get<{ name: string }>(`/api/companies/${fromCompanyId}`)
      .then((r) => { if (!cancelled) setFromCompanyName(r.data.name); })
      .catch(() => { if (!cancelled) setFromCompanyName(null); });
    return () => { cancelled = true; };
  }, [fromCompanyId, equipment?.supplier_id, equipment?.supplier_name]);

  async function doDelete() {
    if (!equipment) return;
    setDeleteBusy(true);
    try {
      await api.delete(`/api/equipment/${equipment.id}`);
      navigate('/equipment', { replace: true });
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '삭제 실패');
    } finally {
      setDeleteBusy(false);
    }
  }

  if (loading) {
    return <AppShell><p className="text-slate-400">불러오는 중...</p></AppShell>;
  }
  if (loadError || !equipment) {
    return (
      <AppShell>
        <div className="rounded-xl border border-slate-200 bg-white p-12 text-center">
          <p className="text-slate-700 mb-4">{loadError ?? '장비를 찾을 수 없습니다'}</p>
          <Link to="/equipment" className="inline-flex px-4 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold">목록으로</Link>
        </div>
      </AppShell>
    );
  }

  const title = equipment.vehicle_no || equipment.model || equipmentCategoryLabel(equipment.category);
  // V77: 이 장비 소유사가 내 직속 하위 공급사면 부모로서 서류수집/보완요청 가능.
  const isParentOfOwner = subSuppliers.some((c) => c.id === equipment.supplier_id);
  const status = equipment.expiring_count > 0
    ? '점검 필요'
    : (equipment.utilization_pct ?? 0) === 0 ? '미사용' : '가동 중';
  const statusCls = status === '가동 중'
    ? 'bg-emerald-100 text-emerald-700'
    : status === '점검 필요' ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-600';
  const statusDot = status === '가동 중' ? 'bg-emerald-500' : status === '점검 필요' ? 'bg-amber-500' : 'bg-slate-400';

  const tabs: Array<{ id: TabId; label: string; badge?: number }> = [
    { id: 'overview', label: '개요' },
    { id: 'inspection', label: '점검 이력', badge: timeline?.inspections.length },
    { id: 'operation', label: '가동 이력', badge: timeline?.operations.length },
    { id: 'location', label: '위치 이력', badge: timeline?.locations.length },
    { id: 'maintenance', label: '정비 이력', badge: timeline?.maintenances.length },
    { id: 'note', label: '메모', badge: timeline?.notes.length },
  ];

  return (
    <AppShell
      breadcrumb={fromCompanyId
        ? [
            { label: '공급사 관리', to: '/admin/suppliers' },
            { label: fromCompanyName ?? `회사 #${fromCompanyId}`, to: `/admin/companies/${fromCompanyId}?tab=equipment` },
            { label: '장비', to: `/admin/companies/${fromCompanyId}?tab=equipment` },
            { label: `${equipmentCategoryLabel(equipment.category)} ${title}` },
          ]
        : [
            { label: '장비 관리', to: '/equipment' },
            { label: '장비 목록', to: '/equipment' },
            { label: `${equipmentCategoryLabel(equipment.category)} ${title}` },
          ]}
    >
      <div className="space-y-6">
        {/* 원청기관 경험 이력 */}
        <div className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="text-sm font-semibold text-slate-700 mb-2">원청기관 경험</div>
          <ClientOrgHistory resourceType="equipment" resourceId={equipment.id} adminMode={user?.role === 'ADMIN'} />
        </div>

        {/* 헤더 + 사진 + 스펙 */}
        <div className="rounded-xl border border-slate-200 bg-white p-6">
          <div className="flex flex-col lg:flex-row gap-6">
            {/* 좌측 사진 갤러리 */}
            <div className="w-full lg:w-[300px] shrink-0">
              <EquipmentPhotoGallery equipmentId={equipment.id} hasPhoto={equipment.has_photo} category={equipment.category} />
            </div>
            {/* 우측 정보 */}
            <div className="flex-1 min-w-0">
              <div className="flex items-start justify-between gap-3 mb-1">
                <div className="min-w-0">
                  <h1 className="text-lg font-bold text-slate-900 break-keep flex items-center gap-2 flex-wrap">
                    {equipmentCategoryLabel(equipment.category)} {title}
                    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold ${statusCls}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${statusDot}`} />
                      {status}
                    </span>
                    {equipment.assignment_status && (
                      <AssignmentBadge status={equipment.assignment_status} />
                    )}
                    <OnboardingBadge ownerType="EQUIPMENT" ownerId={equipment.id} />
                  </h1>
                  <div className="mt-1 text-sm text-slate-500 flex items-center gap-2 flex-wrap">
                    <span className="font-mono">{equipment.code ?? '-'}</span>
                    <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono bg-slate-100 text-slate-500 border border-slate-200">QR</span>
                    {equipment.current_site_id && (
                      <Link to={`/sites/${equipment.current_site_id}`} className="text-xs text-brand-700 hover:text-brand-800 font-semibold">
                        @ {equipment.current_site_name ?? `현장 #${equipment.current_site_id}`}
                      </Link>
                    )}
                  </div>
                </div>
                {canEdit && (
                  <div className="flex items-center gap-2 shrink-0">
                    <button type="button" onClick={() => setConfirmDelete(true)} className="btn-danger">
                      삭제
                    </button>
                  </div>
                )}
              </div>

              {/* 4-col spec grid */}
              <div className="mt-5 grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-x-6 gap-y-3 text-sm">
                <SpecItem label="장비 종류" value={equipmentCategoryLabel(equipment.category)} />
                <SpecItem label="구입일" value="-" />
                <SpecItem
                  label="소속 현장"
                  value={equipment.current_site_name
                    ? <span className="inline-flex items-center gap-1.5">{equipment.current_site_name} <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" /></span>
                    : '-'}
                />
                <SpecItem label="상태" value={<span className="inline-flex items-center gap-1.5"><span className={`w-1.5 h-1.5 rounded-full ${statusDot}`} />{status}</span>} />

                <SpecItem label="제조사" value={equipment.manufacturer ?? '-'} />
                <SpecItem label="구입가" value="-" />
                <SpecItem label="보관 위치" value="-" />
                <SpecItem label="가동률" value={
                  <div className="flex items-center gap-2">
                    <span className="font-semibold">{equipment.utilization_pct ?? 0}%</span>
                    <div className="flex-1 h-1.5 bg-slate-100 rounded-full overflow-hidden max-w-[80px]">
                      <div className="h-full bg-blue-500" style={{ width: `${equipment.utilization_pct ?? 0}%` }} />
                    </div>
                  </div>
                } />

                <SpecItem label="모델명" value={equipment.model ?? '-'} />
                <SpecItem label="사용 시간" value={equipment.usage_hours != null ? `${equipment.usage_hours.toLocaleString()} 시간` : '-'} />
                <SpecItem label="담당자" value="-" />
                <SpecItem label="연료 잔량" value="-" />

                <SpecItem label="제조번호" value={equipment.serial_number ?? '-'} />
                <SpecItem label="장비 중량" value={equipment.weight_kg != null ? `${equipment.weight_kg.toLocaleString()} kg` : '-'} />
                <SpecItem label="보험 만료일" value={equipment.insurance_expiry ?? '-'} />
                <SpecItem label="등록일" value={equipment.created_at?.slice(0, 10).replace(/-/g, '.') ?? '-'} />

                <SpecItem label="연식" value={equipment.year ?? '-'} />
                <SpecItem label="버킷 용량" value={equipment.bucket_capacity != null ? `${equipment.bucket_capacity} m³` : '-'} />
                <SpecItem label="차량번호" value={equipment.vehicle_no ?? '-'} />
                <SpecItem label="최근 업데이트" value={equipment.updated_at ? new Date(equipment.updated_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(0, 16) : '-'} />
              </div>
            </div>
          </div>
        </div>

        {equipment.is_external && (
          <div className="rounded-xl border border-amber-200 bg-amber-50/40 p-6">
            <h3 className="flex items-center gap-2 text-base font-bold text-slate-900">
              <span className="px-2 py-0.5 rounded text-xs font-semibold bg-amber-100 text-amber-800">외부 조달</span>
              외부 장비 — 소유주 · 기사
            </h3>
            <div className="mt-3 grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-3">
              <SpecItem label="소유주(사업자)명" value={equipment.vehicle_owner_name ?? '-'} />
              <SpecItem label="사업자등록번호" value={equipment.vehicle_owner_business_no ?? '-'} />
              <SpecItem label="조종원(기사) 계정" value={equipment.operator_person_id
                ? <Link to={`/persons/${equipment.operator_person_id}`} className="text-brand-700 hover:text-brand-800 font-semibold">연결됨 — 기사 보기</Link>
                : <span className="text-slate-400">미등록</span>} />
            </div>
            <button type="button" onClick={() => setCollectOpen(true)}
              className="mt-4 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700">
              서류 수집 요청 (차량주인에게 링크 보내기)
            </button>
          </div>
        )}

        {collectOpen && (
          <DocumentCollectionDialog ownerType="EQUIPMENT" ownerId={equipment.id} ownerLabel={title} onClose={() => setCollectOpen(false)} />
        )}

        {/* 등록 서류 카드 — 가장 중요한 영역이라 탭 위에 별도 노출 */}
        <div className="rounded-xl border border-slate-200 bg-white p-6">
          {(canEdit || isParentOfOwner) && (
            <div className="mb-4 flex flex-wrap gap-2">
              <button type="button" onClick={() => setCollectOpen(true)}
                className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700">
                서류 수집 요청
              </button>
              {isParentOfOwner && (
                <button type="button" onClick={() => setSupplementOpen(true)}
                  className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-2 text-sm font-semibold text-amber-700 hover:bg-amber-100">
                  보완 요청 (빠꾸)
                </button>
              )}
            </div>
          )}
          {supplementOpen && (
            <SupplementRequestDialog
              ownerType="EQUIPMENT"
              ownerId={equipment.id}
              ownerLabel={title}
              onClose={() => setSupplementOpen(false)}
              onSubmitted={() => setSupplementOpen(false)}
            />
          )}
          <DocumentSection
            ownerType="EQUIPMENT"
            ownerId={equipment.id}
            canEdit={canEdit}
            ownerCategory={equipment.category}
            title="등록 서류"
            excludeTypeName={equipment.is_external ? undefined : '사업자등록증(외부장비)'}
          />
          <p className="mt-4 text-xs text-slate-400 flex items-center gap-1">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><line x1="12" y1="16" x2="12" y2="12" /><line x1="12" y1="8" x2="12.01" y2="8" /></svg>
            서류를 클릭하면 확대해서 확인할 수 있습니다.
          </p>
        </div>

        {/* P4: 차량 관리 — 검사·오일·등록 만료 + 일상점검 이력 */}
        <EquipmentDuePanel equipment={equipment} canEdit={canEdit} onSaved={() => void load()} />

        {/* L3: 현장 투입가능 사전판정 */}
        <DeployCheckCard ownerType="equipment" ownerId={equipment.id} />

        {/* 탭 */}
        <div className="rounded-xl border border-slate-200 bg-white">
          <div className="border-b border-slate-200 px-2 flex gap-1 overflow-x-auto">
            {tabs.map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => setTab(t.id)}
                className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors whitespace-nowrap inline-flex items-center gap-1.5 ${
                  tab === t.id ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-600 hover:text-slate-900'
                }`}
              >
                {tabIcon(t.id)}
                {t.label}
                {t.badge != null && t.badge > 0 && (
                  <span className="inline-flex min-w-[18px] h-4 px-1 rounded-full bg-blue-100 text-blue-700 text-[10px] font-semibold items-center justify-center">{t.badge}</span>
                )}
              </button>
            ))}
          </div>

          <div className="p-6">
            {tab === 'overview' && <OverviewTab equipment={equipment} timeline={timeline} onTabChange={setTab} />}
            {tab === 'inspection' && <InspectionTab items={timeline?.inspections ?? []} />}
            {tab === 'operation' && <OperationTab items={timeline?.operations ?? []} />}
            {tab === 'location' && <LocationTab items={timeline?.locations ?? []} />}
            {tab === 'maintenance' && <MaintenanceTab items={timeline?.maintenances ?? []} />}
            {tab === 'note' && <NoteTab items={timeline?.notes ?? []} />}
          </div>
        </div>

        {/* R1 조합(교대조) 조종원 — 견적/작업계획서 자동 prefill. 수정은 소유 공급사(자기+직속자식)+ADMIN 만(BP 조회만). */}
        <EquipmentDefaultOperators
          equipmentId={equipment.id}
          supplierId={equipment.supplier_id}
          canEdit={canEdit}
        />

        {/* 현장 배치 섹션 */}
        <ResourceAssignmentSection
          resourceKind="equipment"
          resourceId={equipment.id}
          resourceSupplierId={equipment.supplier_id}
          currentSiteId={equipment.current_site_id}
          currentSiteName={equipment.current_site_name}
          onChanged={() => void load()}
        />
      </div>

      <ConfirmDialog
        open={confirmDelete}
        title="장비 삭제"
        message={`${title} 를 삭제합니다.\n복구할 수 없습니다.`}
        confirmLabel="삭제"
        variant="danger"
        busy={deleteBusy}
        onConfirm={doDelete}
        onCancel={() => setConfirmDelete(false)}
      />
    </AppShell>
  );
}

function SpecItem({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="min-w-0">
      <div className="text-xs text-slate-500">{label}</div>
      <div className="mt-0.5 text-slate-900 truncate">{value}</div>
    </div>
  );
}

function OverviewTab({ equipment, timeline, onTabChange }: {
  equipment: EquipmentResponse;
  timeline: Timeline | null;
  onTabChange: (t: TabId) => void;
}) {
  const op = equipment.operating_hours;
  const idle = equipment.idle_hours;
  const down = equipment.downtime_hours;
  const util = equipment.utilization_pct ?? 0;

  const recentInspect = timeline?.inspections[0];
  const recentLocation = timeline?.locations[0];
  const nextInspectDays = recentInspect?.next_inspection_at
    ? Math.round((new Date(recentInspect.next_inspection_at).getTime() - Date.now()) / (1000 * 60 * 60 * 24))
    : null;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
      {/* 가동 현황 */}
      <div className="rounded-xl border border-slate-100 bg-white p-5">
        <h3 className="text-base font-bold mb-4">가동 현황</h3>
        <div className="flex items-center gap-4">
          <DonutChart value={util} size={130} sub="가동률" />
          <div className="flex-1 space-y-2 text-sm min-w-0">
            <DlRow label="총 가동 시간" value={`${op.toLocaleString()} 시간`} />
            <DlRow label="총 대기 시간" value={`${idle.toLocaleString()} 시간`} />
            <DlRow label="총 비가동 시간" value={`${down.toLocaleString()} 시간`} />
          </div>
        </div>
        <p className="mt-4 text-xs text-slate-400 text-center border-t border-slate-100 pt-3">최근 30일 기준</p>
      </div>

      {/* 최근 점검 정보 */}
      <div className="rounded-xl border border-slate-100 bg-white p-5">
        <h3 className="text-base font-bold mb-4 flex items-center gap-2">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-500">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
          </svg>
          최근 점검 정보
        </h3>
        {recentInspect ? (
          <dl className="space-y-3 text-sm">
            <DlRow label="최근 점검일" value={recentInspect.inspected_at} />
            <DlRow label="다음 점검일" value={
              recentInspect.next_inspection_at ? (
                <span>
                  {recentInspect.next_inspection_at}
                  {nextInspectDays != null && (
                    <span className={`ml-1 font-semibold ${nextInspectDays < 30 ? 'text-rose-600' : 'text-slate-500'}`}>
                      (D{nextInspectDays >= 0 ? '-' : '+'}{Math.abs(nextInspectDays)})
                    </span>
                  )}
                </span>
              ) : '-'
            } />
            <DlRow label="점검 주기" value="3개월" />
            <DlRow label="점검 상태" value={
              <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${
                recentInspect.result === 'PASS' ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'
              }`}>
                {recentInspect.result === 'PASS' ? '정상' : '주의'}
              </span>
            } />
          </dl>
        ) : (
          <p className="text-sm text-slate-400 py-6 text-center">점검 이력 없음</p>
        )}
        <button type="button" onClick={() => onTabChange('inspection')} className="mt-4 w-full text-center text-sm text-slate-600 hover:text-slate-900 border-t border-slate-100 pt-3">
          점검 이력 보기 ›
        </button>
      </div>

      {/* 위치 정보 */}
      <div className="rounded-xl border border-slate-100 bg-white p-5">
        <h3 className="text-base font-bold mb-4">위치 정보</h3>
        <div className="aspect-[5/3] rounded-lg bg-gradient-to-br from-blue-50 via-emerald-50 to-amber-50 relative mb-3 overflow-hidden">
          <svg className="absolute inset-0 w-full h-full opacity-30" viewBox="0 0 200 120" preserveAspectRatio="none">
            <path d="M0,60 Q50,30 100,55 T200,40" stroke="#94a3b8" strokeWidth="1" fill="none" />
            <path d="M0,80 Q50,100 100,75 T200,90" stroke="#94a3b8" strokeWidth="1" fill="none" />
          </svg>
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="relative">
              <span className="absolute -inset-2 rounded-full bg-blue-500/20 animate-ping" />
              <svg width="32" height="32" viewBox="0 0 24 24" fill="#3b82f6" className="relative">
                <path d="M12 0C7.6 0 4 3.6 4 8c0 5.4 6.4 13.4 7.2 14.4.4.5 1.2.5 1.6 0C13.6 21.4 20 13.4 20 8c0-4.4-3.6-8-8-8zm0 11c-1.7 0-3-1.3-3-3s1.3-3 3-3 3 1.3 3 3-1.3 3-3 3z" />
              </svg>
            </div>
          </div>
        </div>
        {recentLocation ? (
          <div className="space-y-2 text-sm">
            <div>
              <div className="font-semibold text-slate-900">{recentLocation.location_name}</div>
              {recentLocation.note && <div className="text-xs text-slate-500 mt-0.5">{recentLocation.note}</div>}
            </div>
            <DlRow label="GPS 추적" value={<span className="inline-flex px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 text-xs font-semibold">정상</span>} />
            <DlRow label="최근 업데이트" value={new Date(recentLocation.recorded_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(0, 16)} />
          </div>
        ) : (
          <p className="text-sm text-slate-400 py-6 text-center">위치 이력 없음</p>
        )}
        <button type="button" onClick={() => onTabChange('location')} className="mt-4 w-full text-center text-sm text-slate-600 hover:text-slate-900 border-t border-slate-100 pt-3">
          위치 이력 보기 ›
        </button>
      </div>
    </div>
  );
}

function DlRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-slate-500 shrink-0">{label}</dt>
      <dd className="text-slate-900 font-medium text-right min-w-0 truncate">{value}</dd>
    </div>
  );
}

function InspectionTab({ items }: { items: Timeline['inspections'] }) {
  if (items.length === 0) return <Empty text="점검 이력 없음" />;
  return (
    <ul className="divide-y divide-slate-100">
      {items.map((it) => (
        <li key={it.id} className="py-4 flex items-center justify-between gap-3">
          <div className="flex-1 min-w-0">
            <div className="font-semibold">{it.title}</div>
            <div className="text-xs text-slate-500 mt-0.5">{it.inspected_at}{it.inspector ? ` · ${it.inspector}` : ''}</div>
            {it.note && <div className="text-xs text-slate-500 mt-1 truncate">{it.note}</div>}
          </div>
          <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold shrink-0 ${
            it.result === 'PASS' ? 'bg-emerald-100 text-emerald-700' : it.result === 'ATTENTION' ? 'bg-amber-100 text-amber-700' : 'bg-rose-100 text-rose-700'
          }`}>
            {it.result === 'PASS' ? '이상 없음' : it.result === 'ATTENTION' ? '주의' : '실패'}
          </span>
        </li>
      ))}
    </ul>
  );
}

function OperationTab({ items }: { items: Timeline['operations'] }) {
  if (items.length === 0) return <Empty text="가동 이력 없음" />;
  return (
    <ul className="divide-y divide-slate-100">
      {items.map((it) => {
        const start = new Date(it.started_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(0, 16);
        const end = it.ended_at ? new Date(it.ended_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(11, 16) : null;
        return (
          <li key={it.id} className="py-4 flex items-center justify-between gap-3">
            <div className="flex-1 min-w-0">
              <div className="font-semibold truncate">{it.description ?? '작업'}</div>
              <div className="text-xs text-slate-500 mt-0.5 truncate">
                {start}{end && ` ~ ${end}`}{it.site_name && ` · ${it.site_name}`}
              </div>
            </div>
            {it.utilization_pct != null && (
              <span className="text-sm font-semibold text-slate-700 shrink-0">{it.utilization_pct}%</span>
            )}
            <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold shrink-0 ${
              it.status === 'RUNNING' || it.status === 'DONE' ? 'bg-emerald-100 text-emerald-700' :
              it.status === 'IDLE' ? 'bg-slate-100 text-slate-600' : 'bg-rose-100 text-rose-700'
            }`}>
              {it.status === 'DONE' ? '완료' : it.status === 'RUNNING' ? '진행중' : it.status === 'IDLE' ? '대기' : '고장'}
            </span>
          </li>
        );
      })}
    </ul>
  );
}

function LocationTab({ items }: { items: Timeline['locations'] }) {
  if (items.length === 0) return <Empty text="위치 이력 없음" />;
  return (
    <ul className="divide-y divide-slate-100">
      {items.map((it) => (
        <li key={it.id} className="py-4 flex items-center justify-between gap-3">
          <div className="flex-1 min-w-0">
            <div className="font-semibold truncate">{it.location_name}</div>
            <div className="text-xs text-slate-500 mt-0.5">{new Date(it.recorded_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(0, 16)} 업데이트</div>
          </div>
          {it.note && <span className="text-sm text-slate-600 shrink-0">{it.note}</span>}
        </li>
      ))}
    </ul>
  );
}

function MaintenanceTab({ items }: { items: Timeline['maintenances'] }) {
  if (items.length === 0) return <Empty text="정비 이력 없음" />;
  return (
    <ul className="divide-y divide-slate-100">
      {items.map((it) => (
        <li key={it.id} className="py-4 flex items-center justify-between gap-3">
          <div className="flex-1 min-w-0">
            <div className="font-semibold truncate">{it.title}</div>
            <div className="text-xs text-slate-500 mt-0.5">{it.maintained_at}{it.maintainer ? ` · ${it.maintainer}` : ''}</div>
            {it.description && <div className="text-xs text-slate-500 mt-1 truncate">{it.description}</div>}
          </div>
          {it.cost != null && <span className="text-sm font-semibold text-slate-900 shrink-0">₩ {it.cost.toLocaleString()}</span>}
        </li>
      ))}
    </ul>
  );
}

function NoteTab({ items }: { items: Timeline['notes'] }) {
  if (items.length === 0) return <Empty text="메모 없음" />;
  return (
    <ul className="space-y-3">
      {items.map((it) => (
        <li key={it.id} className="rounded-lg border border-slate-200 bg-slate-50 p-3">
          <p className="text-sm text-slate-700 whitespace-pre-wrap">{it.content}</p>
          <div className="text-xs text-slate-400 mt-1">{new Date(it.created_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}</div>
        </li>
      ))}
    </ul>
  );
}

function Empty({ text }: { text: string }) {
  return <p className="text-sm text-slate-400 py-12 text-center">{text}</p>;
}

function tabIcon(id: TabId) {
  switch (id) {
    case 'overview': return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /><rect x="14" y="14" width="7" height="7" /></svg>;
    case 'inspection': return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M9 12l2 2 4-4" /><path d="M21 12c0 4.97-4.03 9-9 9s-9-4.03-9-9 4.03-9 9-9c1.66 0 3.22.45 4.56 1.24" /></svg>;
    case 'operation': return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg>;
    case 'location': return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" /><circle cx="12" cy="10" r="3" /></svg>;
    case 'maintenance': return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" /></svg>;
    case 'note': return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" /></svg>;
  }
}
