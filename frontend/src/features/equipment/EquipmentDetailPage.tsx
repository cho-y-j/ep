import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
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
import EquipmentDuePanel, { type DailyInsp } from './EquipmentDuePanel';
import DeployCheckCard, { type ComboDeployCheckResult } from '../readiness/DeployCheckCard';
import OnboardingBadge from '../onboarding/OnboardingBadge';
import { useEquipmentTypes } from './useEquipmentTypes';

type TabId = 'overview' | 'inspection' | 'operation' | 'maintenance' | 'note';

type Timeline = {
  inspections: Array<{ id: number; inspected_at: string; inspector?: string | null; title: string; result: string; note?: string | null; next_inspection_at?: string | null }>;
  operations: Array<{ id: number; started_at: string; ended_at?: string | null; site_name?: string | null; description?: string | null; utilization_pct?: number | null; status: string }>;
  maintenances: Array<{ id: number; maintained_at: string; maintainer?: string | null; title: string; description?: string | null; cost?: number | null }>;
  notes: Array<{ id: number; author_id?: number | null; content: string; created_at: string }>;
};

type EqDraft = {
  vehicleNo: string; category: string; model: string; manufacturer: string; year: string;
  isExternal: boolean; vehicleOwnerName: string; vehicleOwnerBusinessNo: string;
};

const EDIT_INPUT_CLS = 'border border-slate-300 rounded bg-white outline-none focus:border-brand-500 text-sm px-2 py-0.5 w-full';

export default function EquipmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [search] = useSearchParams();
  const fromCompanyId = search.get('fromCompany') ? Number(search.get('fromCompany')) : null;
  const [fromCompanyName, setFromCompanyName] = useState<string | null>(null);

  const subSuppliers = useSubSuppliers();
  const { options: typeOptions, labelOf: categoryLabelOf } = useEquipmentTypes();
  const [equipment, setEquipment] = useState<EquipmentResponse | null>(null);
  const [collectOpen, setCollectOpen] = useState(false);
  const [supplementOpen, setSupplementOpen] = useState(false);
  const [timeline, setTimeline] = useState<Timeline | null>(null);
  const [combo, setCombo] = useState<ComboDeployCheckResult | null>(null);
  const [dailyInsp, setDailyInsp] = useState<DailyInsp[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>('overview');
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  // 사진 변경 (PersonDetailPage 패턴 복제)
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [photoNonce, setPhotoNonce] = useState(0);
  // 기본 정보 인라인 편집 (PersonDetailPage 패턴 복제)
  const [editMode, setEditMode] = useState(false);
  const [draft, setDraft] = useState<EqDraft>({
    vehicleNo: '', category: '', model: '', manufacturer: '', year: '',
    isExternal: false, vehicleOwnerName: '', vehicleOwnerBusinessNo: '',
  });
  const [saving, setSaving] = useState(false);

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
        api.get<Timeline>(`/api/equipment/${id}/timeline`).catch(() => ({ data: { inspections: [], operations: [], maintenances: [], notes: [] } as Timeline })),
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

  // R1 세트(차량+조종원) 투입 준비 종합 — 상단 요약 카드 + 조종원 서류 배지 공용(현장 미지정 기준 1회, 신규 판정 없음).
  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    api.get<ComboDeployCheckResult>(`/api/resources/equipment/${id}/deploy-check-combo`)
      .then((r) => { if (!cancelled) setCombo(r.data); })
      .catch(() => { if (!cancelled) setCombo(null); });
    return () => { cancelled = true; };
  }, [id]);

  // R3: 조종원 일상점검 이력(가동시간·운행거리 포함)을 페이지 단위로 1회 로드 — 상태대시 + 차량관리 패널 공용.
  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    api.get<DailyInsp[]>(`/api/equipment/${id}/daily-inspections`)
      .then((r) => { if (!cancelled) setDailyInsp(r.data); })
      .catch(() => { if (!cancelled) setDailyInsp([]); });
    return () => { cancelled = true; };
  }, [id]);

  // 가장 최근 '가동시간(아워미터)' 보고 — 상태대시 보조 표시용(정본 누적시간과 별개).
  const reportedHourMeter = useMemo(() => {
    const r = dailyInsp.find((h) => h.hour_meter != null);
    return r ? { value: r.hour_meter as number, date: r.inspect_date } : null;
  }, [dailyInsp]);

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

  async function handlePhotoFile(file: File) {
    if (!equipment) return;
    setPhotoBusy(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await api.post<EquipmentResponse>(`/api/equipment/${equipment.id}/photo`, formData);
      setEquipment(res.data);
      setPhotoNonce((n) => n + 1);
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '사진 업로드 실패');
    } finally {
      setPhotoBusy(false);
    }
  }

  const enterEdit = useCallback(() => {
    if (!equipment) return;
    setDraft({
      vehicleNo: equipment.vehicle_no ?? '',
      category: equipment.category ?? '',
      model: equipment.model ?? '',
      manufacturer: equipment.manufacturer ?? '',
      year: equipment.year != null ? String(equipment.year) : '',
      isExternal: !!equipment.is_external,
      vehicleOwnerName: equipment.vehicle_owner_name ?? '',
      vehicleOwnerBusinessNo: equipment.vehicle_owner_business_no ?? '',
    });
    setEditMode(true);
  }, [equipment]);

  const cancelEdit = useCallback(() => setEditMode(false), []);
  const setField = <K extends keyof EqDraft>(key: K, val: EqDraft[K]) => setDraft((d) => ({ ...d, [key]: val }));

  const saveAll = useCallback(async () => {
    if (!equipment) return;
    setSaving(true);
    try {
      // JSON 전역 SNAKE_CASE. 빈 문자열 → null (부분 수정: 백엔드는 null 을 '변경 없음'으로 처리).
      await api.patch(`/api/equipment/${equipment.id}`, {
        vehicle_no: draft.vehicleNo || null,
        category: draft.category || null,
        model: draft.model || null,
        manufacturer: draft.manufacturer || null,
        year: draft.year ? Number(draft.year) : null,
        is_external: draft.isExternal,
        vehicle_owner_name: draft.isExternal ? (draft.vehicleOwnerName || null) : null,
        vehicle_owner_business_no: draft.isExternal ? (draft.vehicleOwnerBusinessNo || null) : null,
      });
      setEditMode(false);
      void load();
    } catch (e) {
      if (e instanceof AxiosError) alert(e.response?.data?.message ?? '저장 실패');
    } finally {
      setSaving(false);
    }
  }, [equipment, draft, load]);

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
            {/* 좌측 사진 갤러리 + 사진 변경 */}
            <div className="w-full lg:w-[300px] shrink-0 space-y-3">
              <EquipmentPhotoGallery key={`gallery-${photoNonce}`} equipmentId={equipment.id} hasPhoto={equipment.has_photo} category={equipment.category} />
              {canEdit && (
                <>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    capture="environment"
                    className="hidden"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) void handlePhotoFile(f);
                      if (fileInputRef.current) fileInputRef.current.value = '';
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    disabled={photoBusy}
                    className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 text-sm text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" /></svg>
                    {photoBusy ? '업로드 중...' : '사진 변경'}
                  </button>
                </>
              )}
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
                    {!editMode ? (
                      <button
                        type="button"
                        onClick={enterEdit}
                        className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-300 text-slate-700 text-sm font-semibold hover:bg-slate-50"
                      >
                        수정
                      </button>
                    ) : (
                      <>
                        <button
                          type="button"
                          onClick={cancelEdit}
                          disabled={saving}
                          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-300 text-slate-700 text-sm font-semibold hover:bg-slate-50 disabled:opacity-50"
                        >
                          취소
                        </button>
                        <button
                          type="button"
                          onClick={saveAll}
                          disabled={saving}
                          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700 disabled:opacity-50"
                        >
                          {saving ? '저장 중...' : '저장'}
                        </button>
                      </>
                    )}
                    <button type="button" onClick={() => setConfirmDelete(true)} className="btn-danger">
                      삭제
                    </button>
                  </div>
                )}
              </div>

              {/* 4-col spec grid — 죽은 필드(구입일·구입가·보관위치·담당자) 제거, 편집 가능 필드는 인라인 input */}
              <div className="mt-5 grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-x-6 gap-y-3 text-sm">
                <SpecItem label="장비 종류" value={editMode ? (
                  <select value={draft.category} disabled={saving}
                    onChange={(e) => setField('category', e.target.value)} className={EDIT_INPUT_CLS}>
                    {typeOptions.length > 0
                      ? typeOptions.map((o) => <option key={o.code} value={o.code}>{o.name}</option>)
                      : <option value={draft.category}>{categoryLabelOf(draft.category)}</option>}
                  </select>
                ) : categoryLabelOf(equipment.category)} />
                <SpecItem label="차량번호" value={editMode ? (
                  <input value={draft.vehicleNo} disabled={saving}
                    onChange={(e) => setField('vehicleNo', e.target.value)} className={EDIT_INPUT_CLS} />
                ) : (equipment.vehicle_no ?? '-')} />
                <SpecItem
                  label="소속 현장"
                  value={equipment.current_site_name
                    ? <span className="inline-flex items-center gap-1.5">{equipment.current_site_name} <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" /></span>
                    : '-'}
                />
                <SpecItem label="상태" value={<span className="inline-flex items-center gap-1.5"><span className={`w-1.5 h-1.5 rounded-full ${statusDot}`} />{status}</span>} />

                <SpecItem label="제조사" value={editMode ? (
                  <input value={draft.manufacturer} disabled={saving}
                    onChange={(e) => setField('manufacturer', e.target.value)} className={EDIT_INPUT_CLS} />
                ) : (equipment.manufacturer ?? '-')} />
                <SpecItem label="모델명" value={editMode ? (
                  <input value={draft.model} disabled={saving}
                    onChange={(e) => setField('model', e.target.value)} className={EDIT_INPUT_CLS} />
                ) : (equipment.model ?? '-')} />
                <SpecItem label="연식" value={editMode ? (
                  <input type="number" value={draft.year} disabled={saving}
                    onChange={(e) => setField('year', e.target.value)} className={EDIT_INPUT_CLS} />
                ) : (equipment.year ?? '-')} />
                <SpecItem label="가동률" value={
                  <div className="flex items-center gap-2">
                    <span className="font-semibold">{equipment.utilization_pct ?? 0}%</span>
                    <div className="flex-1 h-1.5 bg-slate-100 rounded-full overflow-hidden max-w-[80px]">
                      <div className="h-full bg-blue-500" style={{ width: `${equipment.utilization_pct ?? 0}%` }} />
                    </div>
                  </div>
                } />

                <SpecItem label="제조번호" value={equipment.serial_number ?? '-'} />
                <SpecItem label="장비 중량" value={equipment.weight_kg != null ? `${equipment.weight_kg.toLocaleString()} kg` : '-'} />
                <SpecItem label="사용 시간" value={equipment.usage_hours != null ? `${equipment.usage_hours.toLocaleString()} 시간` : '-'} />
                {/* 연료 잔량 — 차량 단말(OBD) 연동 후 주입 예정(현재 하드코딩 '-' 제거). */}
                <SpecItem label="연료 잔량" value={<span className="text-slate-400">OBD 연동 예정</span>} />

                <SpecItem label="버킷 용량" value={equipment.bucket_capacity != null ? `${equipment.bucket_capacity} m³` : '-'} />
                <SpecItem label="보험 만료일" value={equipment.insurance_expiry ?? '-'} />
                <SpecItem label="등록일" value={equipment.created_at?.slice(0, 10).replace(/-/g, '.') ?? '-'} />
                <SpecItem label="최근 업데이트" value={equipment.updated_at ? new Date(equipment.updated_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(0, 16) : '-'} />
              </div>
            </div>
          </div>
        </div>

        {/* 매칭 조종원 상단 요약 — combo.operators(하단 조합 섹션과 동일 데이터) 재사용, 신규 판정 없음. */}
        <MatchedOperatorsSummary combo={combo} />

        {/* 상태 대시 — 응답에 이미 있는 값 표시만(새 계산 없음) */}
        <StatusDashboard equipment={equipment} reportedHourMeter={reportedHourMeter} />

        {/* R1 세트(차량+조종원) 투입 준비 종합 — deploy-check-combo 재사용(신규 판정 없음). */}
        <SetReadinessCard combo={combo} />

        {editMode ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50/40 p-6">
            <label className="flex items-center gap-2 text-sm font-semibold text-slate-800">
              <input type="checkbox" checked={draft.isExternal} disabled={saving}
                onChange={(e) => setField('isExternal', e.target.checked)} />
              외부 조달 장비 (소유주·차주 정보 별도)
            </label>
            {draft.isExternal && (
              <div className="mt-3 grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
                <label className="min-w-0">
                  <span className="text-xs text-slate-500">소유주(사업자)명</span>
                  <input value={draft.vehicleOwnerName} disabled={saving}
                    onChange={(e) => setField('vehicleOwnerName', e.target.value)} className={`mt-0.5 ${EDIT_INPUT_CLS}`} />
                </label>
                <label className="min-w-0">
                  <span className="text-xs text-slate-500">사업자등록번호</span>
                  <input value={draft.vehicleOwnerBusinessNo} disabled={saving}
                    onChange={(e) => setField('vehicleOwnerBusinessNo', e.target.value)} className={`mt-0.5 ${EDIT_INPUT_CLS}`} />
                </label>
              </div>
            )}
          </div>
        ) : equipment.is_external && (
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

        {/* R1 조합(교대조) 조종원 — 차량 서류 바로 아래 배치해 세트(차량+조종원+각자 서류)를 한 화면에.
            견적/작업계획서 자동 prefill. 수정은 소유 공급사(자기+직속자식)+ADMIN 만(BP 조회만).
            id: 상단 매칭 조종원 요약의 '아래에서 추가' 스크롤 앵커. */}
        <div id="combo-operators">
          <EquipmentDefaultOperators
            equipmentId={equipment.id}
            supplierId={equipment.supplier_id}
            canEdit={canEdit}
            operatorChecks={combo?.operators}
          />
        </div>

        {/* P4: 차량 관리 — 검사·오일·등록 만료 + 일상점검 이력 + R3 가동시간 추이 */}
        <EquipmentDuePanel equipment={equipment} canEdit={canEdit} history={dailyInsp} onSaved={() => void load()} />

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
            {tab === 'maintenance' && <MaintenanceTab items={timeline?.maintenances ?? []} equipmentId={equipment.id} canEdit={canEdit} onAdded={() => void load()} />}
            {tab === 'note' && <NoteTab items={timeline?.notes ?? []} />}
          </div>
        </div>

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

/** 상단 상태 대시 — 응답에 이미 있는 값만 표시(새 계산 없음). */
function StatusDashboard({ equipment, reportedHourMeter }: {
  equipment: EquipmentResponse;
  reportedHourMeter: { value: number; date: string } | null;
}) {
  const tiles: Array<{ label: string; node: ReactNode }> = [
    { label: '배치 상태', node: equipment.assignment_status
        ? <AssignmentBadge status={equipment.assignment_status} />
        : <span className="text-slate-400">미배치</span> },
    { label: '가동률', node: `${equipment.utilization_pct ?? 0}%` },
    { label: '누적 가동시간', node: (
      <span>{equipment.cumulative_work_hours}h
        {equipment.maintenance_interval_hours != null && (
          <span className="text-xs font-normal text-slate-400"> / 주기 {equipment.maintenance_interval_hours}h</span>
        )}
      </span>
    ) },
    // R3: 조종원이 앱에서 보고한 최신 아워미터 — 정본 누적시간과 구분되는 보조 표시.
    { label: '조종원 보고 가동시간', node: reportedHourMeter
        ? <span>{reportedHourMeter.value.toLocaleString()}h
            <span className="text-xs font-normal text-slate-400"> · {reportedHourMeter.date}</span>
          </span>
        : <span className="text-slate-400">미보고</span> },
    { label: '정비', node: equipment.maintenance_due
        ? <span className="text-rose-600">정비 도래</span>
        : <span className="text-slate-700">정상</span> },
    { label: '정기검사', node: equipment.inspection_due_date ?? <span className="text-slate-400">미설정</span> },
  ];
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
      {tiles.map((t) => (
        <div key={t.label} className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="text-xs text-slate-500">{t.label}</div>
          <div className="mt-1 font-semibold text-slate-900 truncate">{t.node}</div>
        </div>
      ))}
    </div>
  );
}

/** R1 세트(차량+조종원) 투입 준비 종합 — combo(deploy-check-combo) 결과를 요약만(신규 판정 없음).
 *  combo_ready / equipment.blocks / operators[].blocks 를 그대로 표시. 현장별·상세 판정은 하단 DeployCheckCard 담당. */
function SetReadinessCard({ combo }: { combo: ComboDeployCheckResult | null }) {
  if (!combo) return null;
  const opCount = combo.operators.length;
  const ready = combo.combo_ready && opCount > 0; // 세트 준비 = 차량 + 조종원(최소 1명) 전원 통과

  if (ready) {
    return (
      <div className="flex items-center gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-emerald-500 text-white">✓</span>
        <div className="min-w-0">
          <div className="text-sm font-bold text-emerald-800">세트 투입 준비됨</div>
          <div className="text-xs text-emerald-700">차량 + 조종원 {opCount}명 전원 통과 (서류·검사·안전점검·이행지시)</div>
        </div>
      </div>
    );
  }

  // 부족 사유 요약 — 차량(equipment.blocks) + 준비 안 된 조종원별(blocks).
  const reasons: { who: string; detail: string }[] = [];
  if (!combo.equipment.ready) {
    reasons.push({ who: '차량', detail: combo.equipment.blocks.map((b) => b.label).join(', ') });
  }
  combo.operators.filter((o) => !o.ready).forEach((o) => {
    reasons.push({ who: o.person_name, detail: o.blocks.map((b) => b.label).join(', ') });
  });

  return (
    <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
      <div className="flex items-center gap-2">
        <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-400 text-sm text-white">!</span>
        <div className="text-sm font-bold text-amber-800">세트 투입 준비 미완 — 아래를 해결하세요</div>
      </div>
      {opCount === 0 && (
        <p className="mt-2 text-xs text-amber-700">매칭된 조종원이 없습니다 — 아래 '조합(교대조) 조종원'에서 추가하세요.</p>
      )}
      {reasons.length > 0 && (
        <ul className="mt-2 space-y-1">
          {reasons.map((r, i) => (
            <li key={i} className="flex gap-2 text-xs text-amber-800">
              <span className="shrink-0 font-semibold">{r.who}:</span>
              <span className="min-w-0">{r.detail}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** 매칭 조종원 상단 요약 — combo.operators(하단 EquipmentDefaultOperators 와 동일 데이터) 재사용, 신규 판정 없음.
 *  서류색만: DOCUMENT 게이트 0건이면 완비(green), 있으면 미비(amber). 매칭 0명이면 '아래에서 추가' 안내(하단 앵커로 스크롤). */
function MatchedOperatorsSummary({ combo }: { combo: ComboDeployCheckResult | null }) {
  if (!combo) return null; // combo 미로드/실패 시 숨김(과잉·오표시 방지)
  const scrollToCombo = () => document.getElementById('combo-operators')?.scrollIntoView({ behavior: 'smooth', block: 'start' });

  if (combo.operators.length === 0) {
    return (
      <div className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3">
        <span className="text-sm font-semibold text-slate-700">매칭 조종원</span>
        <span className="text-sm text-slate-400">매칭된 조종원 없음</span>
        <button type="button" onClick={scrollToCombo}
          className="text-xs px-2 py-0.5 rounded border border-slate-300 text-slate-600 hover:bg-slate-50">
          아래에서 추가 ›
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3">
      <span className="shrink-0 text-sm font-semibold text-slate-700">매칭 조종원</span>
      {combo.operators.map((op) => {
        const docMissing = op.blocks.filter((b) => b.kind === 'DOCUMENT').length;
        return (
          <Link key={op.person_id} to={`/persons/${op.person_id}`}
            className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium hover:opacity-80 ${
              docMissing === 0 ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-amber-200 bg-amber-50 text-amber-800'}`}>
            <span className={`h-1.5 w-1.5 rounded-full ${docMissing === 0 ? 'bg-emerald-500' : 'bg-amber-500'}`} />
            <span className="max-w-[8rem] truncate">{op.person_name}</span>
            <span className="text-[10px] opacity-80">{docMissing === 0 ? '서류 완비' : `서류 ${docMissing}건 미비`}</span>
          </Link>
        );
      })}
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

      {/* 위치/연료 — 차량 단말(OBD/GPS) 연동 후 표시. 허위 지도·GPS 표기 제거. */}
      <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50/60 p-5 flex flex-col items-center justify-center text-center">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className="mb-2">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" /><circle cx="12" cy="10" r="3" />
        </svg>
        <div className="text-sm font-semibold text-slate-500">위치 · 연료</div>
        <p className="mt-1 text-xs text-slate-400">OBD/GPS 연동 예정<br />차량 단말 연동 후 실시간 표시됩니다</p>
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

function MaintenanceTab({ items, equipmentId, canEdit, onAdded }: {
  items: Timeline['maintenances']; equipmentId: number; canEdit: boolean; onAdded: () => void;
}) {
  const [adding, setAdding] = useState(false);
  const [maintainedAt, setMaintainedAt] = useState(() => new Date().toISOString().slice(0, 10));
  const [maintainer, setMaintainer] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [cost, setCost] = useState('');
  const [saving, setSaving] = useState(false);

  const reset = () => { setMaintainer(''); setTitle(''); setDescription(''); setCost(''); setMaintainedAt(new Date().toISOString().slice(0, 10)); };

  const submit = async () => {
    if (!title.trim() || !maintainedAt) { alert('정비일과 제목은 필수입니다'); return; }
    setSaving(true);
    try {
      await api.post(`/api/equipment/${equipmentId}/timeline/maintenance`, {
        maintained_at: maintainedAt,
        maintainer: maintainer.trim() || null,
        title: title.trim(),
        description: description.trim() || null,
        cost: cost ? Number(cost) : null,
      });
      setAdding(false);
      reset();
      onAdded();
    } catch (e) {
      if (e instanceof AxiosError) alert(e.response?.data?.message ?? '정비 이력 저장 실패');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      {canEdit && (
        <div className="mb-4">
          {!adding ? (
            <button type="button" onClick={() => setAdding(true)}
              className="rounded-lg border border-brand-100 bg-white px-3 py-2 text-sm font-semibold text-brand-700 shadow-sm hover:bg-brand-50">
              + 정비 추가
            </button>
          ) : (
            <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-4 space-y-3">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <label className="text-sm">
                  <span className="text-xs text-slate-500">정비일 *</span>
                  <input type="date" value={maintainedAt} disabled={saving}
                    onChange={(e) => setMaintainedAt(e.target.value)} className={`mt-0.5 ${EDIT_INPUT_CLS}`} />
                </label>
                <label className="text-sm">
                  <span className="text-xs text-slate-500">정비자</span>
                  <input value={maintainer} disabled={saving} placeholder="예: 센터 A 정비팀"
                    onChange={(e) => setMaintainer(e.target.value)} className={`mt-0.5 ${EDIT_INPUT_CLS}`} />
                </label>
              </div>
              <label className="block text-sm">
                <span className="text-xs text-slate-500">제목 *</span>
                <input value={title} disabled={saving} placeholder="예: 엔진 오일 교환"
                  onChange={(e) => setTitle(e.target.value)} className={`mt-0.5 ${EDIT_INPUT_CLS}`} />
              </label>
              <label className="block text-sm">
                <span className="text-xs text-slate-500">내용</span>
                <textarea value={description} disabled={saving} rows={2} placeholder="정비 내용"
                  onChange={(e) => setDescription(e.target.value)} className={`mt-0.5 ${EDIT_INPUT_CLS}`} />
              </label>
              <label className="block text-sm">
                <span className="text-xs text-slate-500">비용 (원)</span>
                <input type="number" value={cost} disabled={saving} placeholder="예: 180000"
                  onChange={(e) => setCost(e.target.value)} className={`mt-0.5 ${EDIT_INPUT_CLS}`} />
              </label>
              <div className="flex justify-end gap-2">
                <button type="button" onClick={() => { setAdding(false); reset(); }} disabled={saving}
                  className="rounded px-3 py-1.5 text-sm hover:bg-slate-100 disabled:opacity-50">취소</button>
                <button type="button" onClick={submit} disabled={saving}
                  className="rounded-lg bg-brand-600 px-4 py-1.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50">
                  {saving ? '저장 중...' : '저장'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}
      {items.length === 0 ? (
        <Empty text="정비 이력 없음" />
      ) : (
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
      )}
    </div>
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
    case 'maintenance': return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" /></svg>;
    case 'note': return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" /></svg>;
  }
}
