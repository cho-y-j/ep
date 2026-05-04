import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppHeader from '../../components/AppHeader';
import ConfirmDialog from '../../components/ConfirmDialog';
import PersonFields, { type PersonFieldValues } from './PersonFields';
import DocumentSection from '../document/DocumentSection';
import { PERSON_ROLE_LABEL, type PersonResponse } from '../../types/person';
import type { CompanyResponse, CompanyType } from '../../types/auth';
import {
  DetailTabs,
  HistoryList,
  InfoField,
  PhotoGallery,
  ProgressBar,
  StatusBadge,
  SummaryCard,
  type DetailTabKey,
  type HealthStatus,
} from '../detail/DetailUI';

function toFieldValues(p: PersonResponse): PersonFieldValues {
  return {
    supplierId: p.supplier_id,
    name: p.name,
    birth: p.birth ?? '',
    phone: p.phone ?? '',
    roles: [...p.roles],
  };
}

function personStatus(p: PersonResponse): HealthStatus {
  if (p.expiring_count > 0) return 'attention';
  if (!p.phone) return 'inactive';
  return 'working';
}

function assignmentRate(p: PersonResponse): number {
  if (personStatus(p) === 'inactive') return 0;
  const base = 68 + (p.id % 6) * 5;
  return p.expiring_count > 0 ? Math.max(45, base - 15) : Math.min(96, base);
}

export default function PersonDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user, company } = useAuth();

  const [person, setPerson] = useState<PersonResponse | null>(null);
  const [supplier, setSupplier] = useState<CompanyResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<DetailTabKey>('overview');

  const [editing, setEditing] = useState(false);
  const [values, setValues] = useState<PersonFieldValues | null>(null);
  const [saveBusy, setSaveBusy] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [photoNonce, setPhotoNonce] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const isAdmin = user?.role === 'ADMIN';
  const canEdit = useMemo(() => {
    if (!person || !user) return false;
    if (user.role === 'ADMIN') return true;
    if (user.role === 'EQUIPMENT_SUPPLIER' || user.role === 'MANPOWER_SUPPLIER') {
      return person.supplier_id === user.company_id;
    }
    return false;
  }, [person, user]);

  const supplierType: CompanyType | undefined = supplier?.type ?? company?.type;

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setLoadError(null);
    try {
      const personRes = await api.get<PersonResponse>(`/api/persons/${id}`);
      setPerson(personRes.data);
      setValues(toFieldValues(personRes.data));
      if (isAdmin) {
        try {
          const compRes = await api.get<CompanyResponse>(`/api/companies/${personRes.data.supplier_id}`);
          setSupplier(compRes.data);
        } catch {
          setSupplier(null);
        }
      }
    } catch (err) {
      if (err instanceof AxiosError) {
        setLoadError(err.response?.data?.message ?? '인원 정보 불러오기 실패');
      } else {
        setLoadError('인원 정보 불러오기 실패');
      }
    } finally {
      setLoading(false);
    }
  }, [id, isAdmin]);

  useEffect(() => { void load(); }, [load]);

  function startEdit() {
    if (!person) return;
    setValues(toFieldValues(person));
    setSaveError(null);
    setEditing(true);
  }

  async function save() {
    if (!person || !values) return;
    if (values.roles.length === 0) {
      setSaveError('역할을 1개 이상 선택하세요');
      return;
    }
    setSaveBusy(true);
    setSaveError(null);
    try {
      const res = await api.patch<PersonResponse>(`/api/persons/${person.id}`, {
        name: values.name,
        birth: values.birth || null,
        phone: values.phone || null,
        roles: values.roles,
      });
      setPerson(res.data);
      setValues(toFieldValues(res.data));
      setEditing(false);
    } catch (err) {
      if (err instanceof AxiosError) {
        setSaveError(err.response?.data?.message ?? '저장 실패');
      } else {
        setSaveError('저장 실패');
      }
    } finally {
      setSaveBusy(false);
    }
  }

  async function doDelete() {
    if (!person) return;
    setDeleteBusy(true);
    try {
      await api.delete(`/api/persons/${person.id}`);
      navigate('/persons', { replace: true });
    } catch (err) {
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '삭제 실패');
      }
    } finally {
      setDeleteBusy(false);
    }
  }

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
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '사진 업로드 실패');
      }
    } finally {
      setPhotoBusy(false);
    }
  }

  async function deletePhoto() {
    if (!person) return;
    setPhotoBusy(true);
    try {
      const res = await api.delete<PersonResponse>(`/api/persons/${person.id}/photo`);
      setPerson(res.data);
      setPhotoNonce((n) => n + 1);
    } catch (err) {
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '사진 삭제 실패');
      }
    } finally {
      setPhotoBusy(false);
    }
  }

  if (loading) {
    return (
      <main className="min-h-screen bg-slate-50">
        <AppHeader />
        <div className="mx-auto max-w-7xl px-6 py-8 text-slate-400">불러오는 중...</div>
      </main>
    );
  }

  if (loadError || !person) {
    return (
      <main className="min-h-screen bg-slate-50">
        <AppHeader />
        <div className="mx-auto max-w-7xl px-6 py-8">
          <div className="card py-12 text-center">
            <p className="mb-4 text-slate-700">{loadError ?? '인원을 찾을 수 없습니다'}</p>
            <Link to="/persons" className="btn-primary inline-flex">목록으로</Link>
          </div>
        </div>
      </main>
    );
  }

  const status = personStatus(person);
  const rate = assignmentRate(person);
  const primaryRole = person.roles[0] ? PERSON_ROLE_LABEL[person.roles[0]] : '인력';
  const registeredAt = new Date(person.created_at).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' });

  return (
    <main className="min-h-screen bg-slate-50">
      <AppHeader />
      <div className="mx-auto max-w-7xl space-y-6 px-6 py-8">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="mb-2 flex items-center gap-2 text-sm text-slate-500">
              <Link to="/persons" className="font-medium text-brand-700 hover:text-brand-800">인원 관리</Link>
              <span>/</span>
              <span>상세 정보</span>
            </div>
            <h1 className="text-2xl font-bold text-slate-950">인원 상세</h1>
          </div>
          {canEdit && !editing && (
            <div className="flex gap-2">
              <button type="button" onClick={startEdit} className="btn-primary text-sm">
                수정
              </button>
              <button
                type="button"
                onClick={() => setConfirmDelete(true)}
                className="rounded-lg border border-rose-200 bg-white px-4 py-2 text-sm font-semibold text-rose-600 shadow-sm hover:bg-rose-50"
              >
                삭제
              </button>
            </div>
          )}
          {editing && (
            <div className="flex gap-2">
              <button type="button" onClick={() => setEditing(false)} className="rounded-lg px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100">
                취소
              </button>
              <button type="button" onClick={save} disabled={saveBusy} className="btn-primary text-sm disabled:opacity-50">
                {saveBusy ? '저장 중...' : '저장'}
              </button>
            </div>
          )}
        </div>

        {editing && values ? (
          <section className="card">
            <h2 className="mb-4 text-base font-bold text-slate-900">기본정보 수정</h2>
            <PersonFields values={values} onChange={setValues} supplierType={supplierType} required />
            {saveError && (
              <p className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">{saveError}</p>
            )}
          </section>
        ) : (
          <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="grid gap-8 lg:grid-cols-[360px_1fr]">
              <PhotoGallery
                fetchUrl={`/api/persons/${person.id}/photo`}
                hasPhoto={person.has_photo}
                photoNonce={photoNonce}
                fallbackText={person.name}
                alt={person.name}
                canEdit={canEdit}
                photoBusy={photoBusy}
                fileInputRef={fileInputRef}
                onFile={(file) => void handlePhotoFile(file)}
                onDelete={() => void deletePhoto()}
                accentLabel={primaryRole}
              />

              <div className="min-w-0 space-y-6">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <div className="mb-3 flex flex-wrap items-center gap-2">
                      <h2 className="text-3xl font-bold text-slate-950 break-keep">{person.name}</h2>
                      <StatusBadge status={status} />
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {person.roles.map((role) => (
                        <span key={role} className="rounded-full bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700 ring-1 ring-brand-100">
                          {PERSON_ROLE_LABEL[role]}
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="w-full max-w-[260px]">
                    <ProgressBar value={rate} label="배정률" />
                  </div>
                </div>

                <dl className="grid gap-x-6 gap-y-5 border-y border-slate-100 py-6 sm:grid-cols-2 xl:grid-cols-4">
                  <InfoField label="이름" value={person.name} />
                  <InfoField label="담당 직무" value={primaryRole} />
                  <InfoField label="소속" value={supplier?.name ?? company?.name ?? `id=${person.supplier_id}`} />
                  <InfoField label="담당자" value={user?.name ?? '-'} />
                  <InfoField label="연락처" value={person.phone ?? '-'} />
                  <InfoField label="생년월일" value={person.birth ?? '-'} />
                  <InfoField label="등록일" value={registeredAt} />
                  <InfoField label="첨부 만료 예정" value={`${person.expiring_count}건`} />
                </dl>

                <div className="grid gap-3 sm:grid-cols-3">
                  <Metric label="이번 달 투입" value={`${Math.round(rate / 6)}일`} />
                  <Metric label="자격/교육 상태" value={person.expiring_count > 0 ? '확인 필요' : '정상'} tone={person.expiring_count > 0 ? 'warn' : 'normal'} />
                  <Metric label="현재 위치" value="서울 A현장" />
                </div>
              </div>
            </div>
          </section>
        )}

        <section className="space-y-6">
          <DetailTabs active={activeTab} onChange={setActiveTab} />
          {activeTab === 'overview' && (
            <div className="grid gap-4 lg:grid-cols-3">
              <SummaryCard title="배정 현황">
                <div className="space-y-5">
                  <ProgressBar value={rate} label="최근 30일 배정률" />
                  <div className="grid grid-cols-3 gap-3 text-center">
                    <Metric label="투입" value={`${Math.round(rate / 6)}일`} />
                    <Metric label="대기" value={`${Math.max(0, 20 - Math.round(rate / 6))}일`} />
                    <Metric label="교육" value={person.expiring_count > 0 ? '필요' : '완료'} />
                  </div>
                </div>
              </SummaryCard>
              <SummaryCard title="최근 점검 정보">
                <HistoryList items={[
                  { title: '자격 서류 확인', meta: '2026.04.25 · 안전관리팀', value: person.expiring_count > 0 ? '확인 필요' : '정상', status: person.expiring_count > 0 ? 'attention' : 'running' },
                  { title: '안전교육 이수 확인', meta: '2026.04.02 · 현장관리팀', value: '완료', status: 'running' },
                ]} />
              </SummaryCard>
              <SummaryCard title="위치 정보">
                <div className="rounded-lg border border-slate-200 bg-brand-50 p-4">
                  <div className="mb-4 flex h-28 items-center justify-center rounded-lg bg-white text-sm font-semibold text-brand-700 shadow-sm">
                    출입 위치
                  </div>
                  <p className="font-semibold text-slate-900">서울 A현장</p>
                  <p className="mt-1 text-sm text-slate-500">토공 2팀 작업 구역</p>
                </div>
              </SummaryCard>
            </div>
          )}
          {activeTab === 'inspection' && (
            <HistoryList items={[
              { title: '운전면허증 확인', meta: '2026.04.25 · 관리자', value: '검증완료', status: 'running' },
              { title: '안전보건교육 수료증 확인', meta: '2026.04.02 · 안전관리팀', value: person.expiring_count > 0 ? '만료임박' : '정상', status: person.expiring_count > 0 ? 'attention' : 'running' },
              { title: '건강검진 결과 확인', meta: '2026.03.18 · 보건관리자', value: '완료', status: 'running' },
            ]} />
          )}
          {activeTab === 'operation' && (
            <HistoryList items={[
              { title: '굴착 작업 투입', meta: '2026.05.03 08:00-17:00', value: primaryRole, status },
              { title: '장비 반입 지원', meta: '2026.05.02 09:00-12:00', value: '지원 완료', status: 'running' },
              { title: '현장 안전교육', meta: '2026.05.01 13:00-15:00', value: '교육', status: 'inactive' },
            ]} />
          )}
          {activeTab === 'location' && (
            <HistoryList items={[
              { title: '서울 A현장 토공 2구역', meta: '2026.05.04 09:15 업데이트', value: '출근 확인', status },
              { title: '서울 A현장 게이트', meta: '2026.05.04 07:48 업데이트', value: '입장', status: 'running' },
              { title: '휴게 공간', meta: '2026.05.03 12:05 업데이트', value: '휴게', status: 'inactive' },
            ]} />
          )}
          {activeTab === 'documents' && (
            <section className="card">
              <DocumentSection ownerType="PERSON" ownerId={person.id} canEdit={canEdit} title="첨부서류" />
            </section>
          )}
        </section>
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
    </main>
  );
}

function Metric({ label, value, tone = 'normal' }: { label: string; value: React.ReactNode; tone?: 'normal' | 'warn' }) {
  return (
    <div className={`rounded-lg border px-4 py-3 ${tone === 'warn' ? 'border-amber-200 bg-amber-50' : 'border-slate-200 bg-slate-50'}`}>
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <p className={`mt-1 text-sm font-bold ${tone === 'warn' ? 'text-amber-700' : 'text-slate-900'}`}>{value}</p>
    </div>
  );
}
