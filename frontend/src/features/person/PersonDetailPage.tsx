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
import ResourceAssignmentSection from '../assignment/ResourceAssignmentSection';
import AssignmentBadge from '../assignment/AssignmentBadge';
import ClientOrgHistory from '../../components/ClientOrgHistory';
import PersonCredentialCard from './PersonCredentialCard';
import DeployCheckCard from '../readiness/DeployCheckCard';
import OnboardingBadge from '../onboarding/OnboardingBadge';
import {
  EMPLOYMENT_TYPE_LABEL, PERSON_STATUS_LABEL, HEALTH_RISK_LABEL, HEALTH_RISK_CHIP_CLS,
  type PersonResponse, type PersonStatus,
} from '../../types/person';
import {
  CHECK_TYPE_LABEL, CHECK_STATUS_LABEL, CHECK_STATUS_CHIP_CLS, type ResourceCheckResponse,
} from '../../types/resourceCheck';
import { equipmentCategoryLabel } from '../../types/equipment';

const STATUS_BADGE: Record<PersonStatus, string> = {
  WORKING: 'bg-emerald-100 text-emerald-700',
  VACATION: 'bg-amber-100 text-amber-700',
  RETIRED: 'bg-rose-100 text-rose-700',
};

export default function PersonDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [search] = useSearchParams();
  const fromCompanyId = search.get('fromCompany') ? Number(search.get('fromCompany')) : null;
  const [fromCompanyName, setFromCompanyName] = useState<string | null>(null);

  const subSuppliers = useSubSuppliers();
  const [person, setPerson] = useState<PersonResponse | null>(null);
  const [collectOpen, setCollectOpen] = useState(false);
  const [supplementOpen, setSupplementOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [photoNonce, setPhotoNonce] = useState(0);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  /** 전체 수정 모드: 헤더 "수정" 클릭 → 모든 칸이 input 으로. 저장 시 한 번에 PATCH. */
  const [editMode, setEditMode] = useState(false);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  const enterEdit = useCallback(() => {
    if (!person) return;
    setDraft({
      name: person.name ?? '',
      jobTitle: person.job_title ?? '',
      birth: person.birth ?? '',
      phone: person.phone ?? '',
      qualification: person.qualification ?? '',
      team: person.team ?? '',
      address: person.address ?? '',
      email: person.email ?? '',
    });
    setEditMode(true);
  }, [person]);

  const cancelEdit = useCallback(() => {
    setEditMode(false);
    setDraft({});
  }, []);

  const setField = (key: string, val: string) => setDraft((d) => ({ ...d, [key]: val }));

  const saveAll = useCallback(async () => {
    if (!person) return;
    setSaving(true);
    try {
      // 백엔드 Jackson SNAKE_CASE — camelCase 키는 snake_case 로 매핑.
      const keyMap: Record<string, string> = { jobTitle: 'job_title', employeeNo: 'employee_no', hiredAt: 'hired_at' };
      const body: Record<string, string | null> = {};
      for (const k of Object.keys(draft)) {
        body[keyMap[k] ?? k] = draft[k] === '' ? null : draft[k];
      }
      await api.patch(`/api/persons/${person.id}`, body);
      setEditMode(false);
      setDraft({});
      void load();
    } catch (e) {
      if (e instanceof AxiosError) alert(e.response?.data?.message ?? '저장 실패');
    } finally {
      setSaving(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [person, draft]);

  const canEdit = useMemo(() => {
    if (!person || !user) return false;
    if (user.role === 'ADMIN') return true;
    // #87: BP 직속 운전수도 Person.supplier_id 에 BP 회사 id 가 저장됨.
    // 즉 인원의 소속 회사가 자기 회사면 BP/EQUIPMENT_SUPPLIER/MANPOWER_SUPPLIER 모두 편집 가능.
    if (user.role === 'BP' || user.role === 'EQUIPMENT_SUPPLIER' || user.role === 'MANPOWER_SUPPLIER') {
      // V77: 본인 회사 인원 + 내 직속 하위 공급사(협력사) 소유 인원 대행 수정/삭제.
      return person.supplier_id === user.company_id
        || subSuppliers.some((c) => c.id === person.supplier_id);
    }
    return false;
  }, [person, user, subSuppliers]);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setLoadError(null);
    try {
      const res = await api.get<PersonResponse>(`/api/persons/${id}`);
      setPerson(res.data);
    } catch (err) {
      if (err instanceof AxiosError) {
        setLoadError(err.response?.data?.message ?? '인원 정보 불러오기 실패');
      } else {
        setLoadError('인원 정보 불러오기 실패');
      }
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (!fromCompanyId) { setFromCompanyName(null); return; }
    if (person?.supplier_id === fromCompanyId && person?.supplier_name) {
      setFromCompanyName(person.supplier_name);
      return;
    }
    let cancelled = false;
    api.get<{ name: string }>(`/api/companies/${fromCompanyId}`)
      .then((r) => { if (!cancelled) setFromCompanyName(r.data.name); })
      .catch(() => { if (!cancelled) setFromCompanyName(null); });
    return () => { cancelled = true; };
  }, [fromCompanyId, person?.supplier_id, person?.supplier_name]);

  async function handlePhotoFile(file: File) {
    if (!person) return;
    setPhotoBusy(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await api.post<PersonResponse>(`/api/persons/${person.id}/photo`, formData);
      setPerson(res.data);
      setPhotoNonce((n) => n + 1);
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '사진 업로드 실패');
    } finally {
      setPhotoBusy(false);
    }
  }

  async function doDelete() {
    if (!person) return;
    setDeleteBusy(true);
    try {
      await api.delete(`/api/persons/${person.id}`);
      navigate('/persons', { replace: true });
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '삭제 실패');
    } finally {
      setDeleteBusy(false);
    }
  }

  if (loading) {
    return <AppShell><p className="text-slate-400">불러오는 중...</p></AppShell>;
  }
  if (loadError || !person) {
    return (
      <AppShell>
        <div className="rounded-xl border border-slate-200 bg-white p-12 text-center">
          <p className="text-slate-700 mb-4">{loadError ?? '인원을 찾을 수 없습니다'}</p>
          <Link to="/persons" className="inline-flex px-4 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold">목록으로</Link>
        </div>
      </AppShell>
    );
  }

  const statusCls = STATUS_BADGE[person.status];
  // V77: 이 인원 소유사가 내 직속 하위 공급사면 부모로서 서류수집/보완요청 가능.
  const isParentOfOwner = subSuppliers.some((c) => c.id === person.supplier_id);

  return (
    <AppShell
      breadcrumb={fromCompanyId
        ? [
            { label: '공급사 관리', to: '/admin/suppliers' },
            { label: fromCompanyName ?? `회사 #${fromCompanyId}`, to: `/admin/companies/${fromCompanyId}?tab=persons` },
            { label: '인원', to: `/admin/companies/${fromCompanyId}?tab=persons` },
            { label: person.name },
          ]
        : [
            { label: '인원 관리', to: '/persons' },
            { label: '인원 목록', to: '/persons' },
            { label: person.name },
          ]}
    >
      <div className="space-y-6">
        {/* 원청기관 경험 이력 */}
        <div className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="text-sm font-semibold text-slate-700 mb-2">원청기관 경험</div>
          <ClientOrgHistory resourceType="person" resourceId={person.id} adminMode={user?.role === 'ADMIN'} />
        </div>

        {/* 기본 정보 카드 */}
        <div className="rounded-xl border border-slate-200 bg-white p-6">
          <div className="flex items-center justify-between mb-5">
            <h2 className="text-lg font-bold text-slate-900">기본 정보</h2>
            <div className="flex items-center gap-2">
              {canEdit && !editMode && (
                <button
                  type="button"
                  onClick={enterEdit}
                  className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-300 text-slate-700 text-sm font-semibold hover:bg-slate-50"
                >
                  수정
                </button>
              )}
              {canEdit && editMode && (
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
              <Link
                to="/persons"
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><line x1="8" y1="6" x2="21" y2="6" /><line x1="8" y1="12" x2="21" y2="12" /><line x1="8" y1="18" x2="21" y2="18" /><circle cx="3" cy="6" r="1" /><circle cx="3" cy="12" r="1" /><circle cx="3" cy="18" r="1" /></svg>
                목록으로
              </Link>
            </div>
          </div>

          <div className="flex flex-col lg:flex-row gap-6">
            {/* 좌측 사진 + 사진 변경 버튼 */}
            <div className="w-full lg:w-[260px] shrink-0 flex flex-col items-center gap-3">
              <PersonPhoto personId={person.id} hasPhoto={person.has_photo} name={person.name} nonce={photoNonce} />
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
              <div className="flex items-center gap-2 mb-1 flex-wrap">
                {editMode ? (
                  <input value={draft.name} disabled={saving}
                    onChange={(e) => setField('name', e.target.value)}
                    className="text-lg font-bold text-slate-900 border border-slate-300 rounded bg-white outline-none focus:border-brand-500 px-2 py-0.5" />
                ) : (
                  <h1 className="text-lg font-bold text-slate-900 break-keep">{person.name}</h1>
                )}
                <span className="inline-flex px-2 py-0.5 rounded-full bg-blue-100 text-blue-700 text-xs font-semibold">
                  {EMPLOYMENT_TYPE_LABEL[person.employment_type]}
                </span>
                {person.assignment_status && (
                  <AssignmentBadge status={person.assignment_status} />
                )}
                <OnboardingBadge ownerType="PERSON" ownerId={person.id} />
                {/* P5-W4: 건강 위험군(표시만) — NORMAL 은 배지 생략. */}
                {person.health_risk_level && person.health_risk_level !== 'NORMAL' && (
                  <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${HEALTH_RISK_CHIP_CLS[person.health_risk_level]}`}>
                    {HEALTH_RISK_LABEL[person.health_risk_level]}
                  </span>
                )}
                {/* 자격/면허 만료 요약 — 기존 person.expiring_count 재사용(신규 조회 없음). */}
                {person.expiring_count > 0 && (
                  <span className="inline-flex px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 text-xs font-semibold">
                    만료 임박 {person.expiring_count}건
                  </span>
                )}
              </div>
              <div className="text-slate-500 mb-6 flex items-center gap-2 flex-wrap">
                {editMode ? (
                  <input value={draft.jobTitle} disabled={saving}
                    placeholder="예: 수석 운전사"
                    onChange={(e) => setField('jobTitle', e.target.value)}
                    className="border border-slate-300 rounded bg-white outline-none focus:border-brand-500 px-2 py-0.5 text-slate-700" />
                ) : (
                  <span>{person.job_title || '-'}</span>
                )}
                {person.current_site_id && (
                  <Link to={`/sites/${person.current_site_id}`} className="text-xs text-brand-700 hover:text-brand-800 font-semibold">
                    @ {person.current_site_name ?? `현장 #${person.current_site_id}`}
                  </Link>
                )}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-5">
                <InfoItem icon={<IconCalendar />} label="생년월일" value={
                  editMode ? (
                    <input type="date" value={draft.birth} disabled={saving}
                      onChange={(e) => setField('birth', e.target.value)}
                      className="border border-slate-300 rounded bg-white outline-none focus:border-brand-500 text-sm px-2 py-0.5 w-full" />
                  ) : <span>{fmtDate(person.birth)}</span>
                } />
                <InfoItem icon={<IconBadge />} label="사원번호" value={<span className="font-mono">{person.employee_no ?? '-'}</span>} />
                <InfoItem icon={<IconAward />} label="자격" value={
                  editMode ? (
                    <input value={draft.qualification} disabled={saving}
                      onChange={(e) => setField('qualification', e.target.value)}
                      className="border border-slate-300 rounded bg-white outline-none focus:border-brand-500 text-sm px-2 py-0.5 w-full" />
                  ) : <span>{person.qualification || '-'}</span>
                } />

                <InfoItem icon={<IconPhone />} label="연락처" value={
                  editMode ? (
                    <input value={draft.phone} disabled={saving}
                      placeholder="010-0000-0000"
                      onChange={(e) => setField('phone', e.target.value)}
                      className="border border-slate-300 rounded bg-white outline-none focus:border-brand-500 text-sm px-2 py-0.5 w-full" />
                  ) : <span>{person.phone ?? '-'}</span>
                } />
                <InfoItem icon={<IconUsers />} label="소속" value={
                  <div className="flex flex-col gap-0.5">
                    <span className="text-slate-900 font-medium">{person.supplier_name ?? '-'}</span>
                    {editMode ? (
                      <input value={draft.team} disabled={saving}
                        placeholder="예: 주간조 / 야간조"
                        onChange={(e) => setField('team', e.target.value)}
                        className="border border-slate-300 rounded bg-white outline-none focus:border-brand-500 text-xs px-2 py-0.5 w-full" />
                    ) : <span className="text-xs text-slate-500">{person.team ?? '소속팀 미지정'}</span>}
                  </div>
                } />
                <InfoItem icon={<IconHome />} label="주소" value={
                  editMode ? (
                    <input value={draft.address} disabled={saving}
                      onChange={(e) => setField('address', e.target.value)}
                      className="border border-slate-300 rounded bg-white outline-none focus:border-brand-500 text-sm px-2 py-0.5 w-full" />
                  ) : <span>{person.address ?? '-'}</span>
                } />

                <InfoItem icon={<IconMail />} label="이메일" value={
                  editMode ? (
                    <input type="email" value={draft.email} disabled={saving}
                      onChange={(e) => setField('email', e.target.value)}
                      className="border border-slate-300 rounded bg-white outline-none focus:border-brand-500 text-sm px-2 py-0.5 w-full" />
                  ) : <span>{person.email ?? '-'}</span>
                } />
                <InfoItem icon={<IconCalendar />} label="입사일" value={fmtDate(person.hired_at)} />
                <InfoItem icon={<IconCheck />} label="상태" value={
                  <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${statusCls}`}>
                    {PERSON_STATUS_LABEL[person.status]}
                  </span>
                } />
              </div>

            </div>
          </div>
        </div>

        {/* 매칭 장비 — 세트 허브 양방향(조종원→장비). 기본 정보 바로 아래로 올려 첫 화면에서 확인. 조종원 아니거나 매칭 없으면 숨김. */}
        <PersonMatchedEquipment personId={person.id} />

        {/* 등록 서류 카드 */}
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
          {collectOpen && (
            <DocumentCollectionDialog ownerType="PERSON" ownerId={person.id} ownerLabel={person.name} onClose={() => setCollectOpen(false)} />
          )}
          {supplementOpen && (
            <SupplementRequestDialog
              ownerType="PERSON"
              ownerId={person.id}
              ownerLabel={person.name}
              onClose={() => setSupplementOpen(false)}
              onSubmitted={() => setSupplementOpen(false)}
            />
          )}
          <DocumentSection
            ownerType="PERSON"
            ownerId={person.id}
            canEdit={canEdit}
            ownerRoles={person.roles}
            title="등록 서류"
          />
          <p className="mt-4 text-xs text-slate-400 flex items-center gap-1">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><line x1="12" y1="16" x2="12" y2="12" /><line x1="12" y1="8" x2="12.01" y2="8" /></svg>
            서류를 클릭하면 확대해서 확인할 수 있습니다.
          </p>
        </div>

        {/* 검진·교육 이력 — 자원별 점검 요청/승인 이력 */}
        <PersonCheckHistory personId={person.id} />

        {/* 앱 로그인 계정 */}
        {canEdit && <PersonCredentialCard person={person} onUpdated={setPerson} />}

        {/* L3: 현장 투입가능 사전판정 */}
        <DeployCheckCard ownerType="person" ownerId={person.id} />

        {/* 현장 배치 섹션 */}
        <ResourceAssignmentSection
          resourceKind="person"
          resourceId={person.id}
          resourceSupplierId={person.supplier_id}
          currentSiteId={person.current_site_id}
          currentSiteName={person.current_site_name}
          onChanged={() => void load()}
        />

        {canEdit && (
          <div className="flex justify-end">
            <button
              type="button"
              onClick={() => setConfirmDelete(true)}
              className="text-sm text-rose-600 hover:text-rose-700 px-4 py-2"
            >
              인원 삭제
            </button>
          </div>
        )}
      </div>

      <ConfirmDialog
        open={confirmDelete}
        title="인원 삭제"
        message={`${person.name} 을(를) 삭제합니다.\n복구할 수 없습니다.`}
        confirmLabel="삭제"
        variant="danger"
        busy={deleteBusy}
        onConfirm={doDelete}
        onCancel={() => setConfirmDelete(false)}
      />

    </AppShell>
  );
}

function PersonPhoto({ personId, hasPhoto, name, nonce }: { personId: number; hasPhoto: boolean; name: string; nonce: number }) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!hasPhoto) { setUrl(null); return; }
    let revoked: string | null = null;
    api.get(`/api/persons/${personId}/photo`, { responseType: 'blob' })
      .then((res) => {
        const u = URL.createObjectURL(res.data);
        revoked = u;
        setUrl(u);
      })
      .catch(() => setUrl(null));
    return () => { if (revoked) URL.revokeObjectURL(revoked); };
  }, [personId, hasPhoto, nonce]);

  return (
    <div className="w-full aspect-[4/5] overflow-hidden rounded-xl bg-slate-100 flex items-center justify-center">
      {url ? (
        <img src={url} alt={name} className="w-full h-full object-cover" />
      ) : (
        <div className="text-slate-300 flex flex-col items-center gap-2">
          <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
            <circle cx="12" cy="7" r="4" />
          </svg>
          <span className="text-sm">{name.charAt(0)}</span>
        </div>
      )}
    </div>
  );
}

/** 검진·교육 이력 — GET /api/resource-checks/by-owner (권한=자원 조회 스코프). 실패/없음 시 조용히 숨김. */
function PersonCheckHistory({ personId }: { personId: number }) {
  const [items, setItems] = useState<ResourceCheckResponse[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.get<ResourceCheckResponse[]>('/api/resource-checks/by-owner', {
      params: { owner_type: 'PERSON', owner_id: personId },
    })
      .then((r) => { if (!cancelled) setItems(r.data); })
      .catch(() => { if (!cancelled) setItems([]); });
    return () => { cancelled = true; };
  }, [personId]);

  if (items === null || items.length === 0) return null;

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6">
      <h2 className="mb-4 text-lg font-bold text-slate-900">검진 · 교육 이력</h2>
      <ul className="divide-y divide-slate-100">
        {items.map((c) => (
          <li key={c.id} className="flex items-center justify-between gap-3 py-3">
            <div className="min-w-0">
              <div className="text-sm font-semibold text-slate-800">{CHECK_TYPE_LABEL[c.check_type]}</div>
              <div className="mt-0.5 text-xs text-slate-500">
                요청일 {c.issued_at.slice(0, 10)}
                {c.due_date ? ` · 마감 ${c.due_date}` : ''}
                {c.supplier_company_name ? ` · ${c.supplier_company_name}` : ''}
              </div>
            </div>
            <span className={`inline-flex shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold ${CHECK_STATUS_CHIP_CLS[c.status]}`}>
              {CHECK_STATUS_LABEL[c.status]}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

type MatchedEquipment = { id: number; vehicle_no?: string | null; category: string; model?: string | null };

/** 매칭 장비 — GET /api/equipment/matched-by-person (조합 원장 역조회, 권한=인원 조회 스코프).
 *  조종원이 아니거나 매칭 없으면 응답이 비어 섹션 숨김. 실패도 조용히 숨김(과잉 표시 방지). */
function PersonMatchedEquipment({ personId }: { personId: number }) {
  const [items, setItems] = useState<MatchedEquipment[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.get<MatchedEquipment[]>(`/api/equipment/matched-by-person/${personId}`)
      .then((r) => { if (!cancelled) setItems(r.data); })
      .catch(() => { if (!cancelled) setItems([]); });
    return () => { cancelled = true; };
  }, [personId]);

  if (items === null || items.length === 0) return null;

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6">
      <h2 className="mb-1 text-lg font-bold text-slate-900">매칭 장비</h2>
      <p className="mb-4 text-xs text-slate-500">
        이 조종원이 조합(교대조)으로 묶인 장비입니다. 장비 상세에서 세트(차량+조종원+각자 서류)를 함께 관리합니다.
      </p>
      <ul className="divide-y divide-slate-100">
        {items.map((e) => (
          <li key={e.id} className="flex items-center justify-between gap-3 py-3">
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold text-slate-800">
                {e.vehicle_no || e.model || equipmentCategoryLabel(e.category)}
              </div>
              <div className="mt-0.5 text-xs text-slate-500">
                {equipmentCategoryLabel(e.category)}{e.model && e.vehicle_no ? ` · ${e.model}` : ''}
              </div>
            </div>
            <Link to={`/equipment/${e.id}`}
              className="shrink-0 rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50">
              장비 보기
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

function InfoItem({ icon, label, value }: { icon: ReactNode; label: string; value: ReactNode }) {
  return (
    <div className="flex items-start gap-3 min-w-0">
      <div className="shrink-0 w-9 h-9 rounded-lg bg-slate-100 flex items-center justify-center text-slate-500">
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <div className="text-xs text-slate-500">{label}</div>
        <div className="mt-0.5 text-sm text-slate-900 truncate">{value}</div>
      </div>
    </div>
  );
}

function fmtDate(d: string | null | undefined) {
  if (!d) return '-';
  return d.replace(/-/g, '. ');
}

function IconCalendar() { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" /></svg>; }
function IconBadge() { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M20 7h-9" /><path d="M14 17H5" /><circle cx="17" cy="17" r="3" /><circle cx="7" cy="7" r="3" /></svg>; }
function IconAward() { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="8" r="6" /><polyline points="8.21 13.89 7 22 12 19 17 22 15.79 13.88" /></svg>; }
function IconPhone() { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" /></svg>; }
function IconUsers() { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></svg>; }
function IconHome() { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" /><circle cx="12" cy="10" r="3" /></svg>; }
function IconMail() { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" /><polyline points="22,6 12,13 2,6" /></svg>; }
function IconCheck() { return <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M9 12l2 2 4-4" /><path d="M21 12c0 4.97-4.03 9-9 9s-9-4.03-9-9 4.03-9 9-9c1.66 0 3.22.45 4.56 1.24" /></svg>; }
