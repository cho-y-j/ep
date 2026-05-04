import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppHeader from '../../components/AppHeader';
import ConfirmDialog from '../../components/ConfirmDialog';
import EquipmentFields, { type EquipmentFieldValues } from './EquipmentFields';
import DocumentSection from '../document/DocumentSection';
import { EQUIPMENT_CATEGORY_LABEL, type EquipmentResponse } from '../../types/equipment';
import type { CompanyResponse } from '../../types/auth';
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

function toFieldValues(e: EquipmentResponse): EquipmentFieldValues {
  return {
    supplierId: e.supplier_id,
    vehicleNo: e.vehicle_no ?? '',
    category: e.category,
    model: e.model ?? '',
    manufacturer: e.manufacturer ?? '',
    year: e.year != null ? String(e.year) : '',
  };
}

function equipmentStatus(e: EquipmentResponse): HealthStatus {
  if (e.expiring_count > 0) return 'attention';
  if (!e.model && !e.vehicle_no) return 'broken';
  return 'running';
}

function operationRate(e: EquipmentResponse): number {
  if (equipmentStatus(e) === 'broken') return 0;
  const base = 72 + (e.id % 5) * 4;
  return e.expiring_count > 0 ? Math.max(48, base - 18) : Math.min(96, base);
}

export default function EquipmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [equipment, setEquipment] = useState<EquipmentResponse | null>(null);
  const [supplier, setSupplier] = useState<CompanyResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<DetailTabKey>('overview');

  const [editing, setEditing] = useState(false);
  const [values, setValues] = useState<EquipmentFieldValues | null>(null);
  const [saveBusy, setSaveBusy] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [photoNonce, setPhotoNonce] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const isAdmin = user?.role === 'ADMIN';
  const canEdit = useMemo(() => {
    if (!equipment || !user) return false;
    if (user.role === 'ADMIN') return true;
    if (user.role === 'EQUIPMENT_SUPPLIER') return equipment.supplier_id === user.company_id;
    return false;
  }, [equipment, user]);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setLoadError(null);
    try {
      const eqRes = await api.get<EquipmentResponse>(`/api/equipment/${id}`);
      setEquipment(eqRes.data);
      setValues(toFieldValues(eqRes.data));
      if (isAdmin) {
        try {
          const compRes = await api.get<CompanyResponse>(`/api/companies/${eqRes.data.supplier_id}`);
          setSupplier(compRes.data);
        } catch {
          setSupplier(null);
        }
      }
    } catch (err) {
      if (err instanceof AxiosError) {
        setLoadError(err.response?.data?.message ?? '장비 정보 불러오기 실패');
      } else {
        setLoadError('장비 정보 불러오기 실패');
      }
    } finally {
      setLoading(false);
    }
  }, [id, isAdmin]);

  useEffect(() => { void load(); }, [load]);

  function startEdit() {
    if (!equipment) return;
    setValues(toFieldValues(equipment));
    setSaveError(null);
    setEditing(true);
  }

  async function save() {
    if (!equipment || !values) return;
    setSaveBusy(true);
    setSaveError(null);
    try {
      const res = await api.patch<EquipmentResponse>(`/api/equipment/${equipment.id}`, {
        vehicle_no: values.vehicleNo || null,
        category: values.category,
        model: values.model || null,
        manufacturer: values.manufacturer || null,
        year: values.year ? Number(values.year) : null,
      });
      setEquipment(res.data);
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
    if (!equipment) return;
    setDeleteBusy(true);
    try {
      await api.delete(`/api/equipment/${equipment.id}`);
      navigate('/equipment', { replace: true });
    } catch (err) {
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '삭제 실패');
      }
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
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '사진 업로드 실패');
      }
    } finally {
      setPhotoBusy(false);
    }
  }

  async function deletePhoto() {
    if (!equipment) return;
    setPhotoBusy(true);
    try {
      const res = await api.delete<EquipmentResponse>(`/api/equipment/${equipment.id}/photo`);
      setEquipment(res.data);
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

  if (loadError || !equipment) {
    return (
      <main className="min-h-screen bg-slate-50">
        <AppHeader />
        <div className="mx-auto max-w-7xl px-6 py-8">
          <div className="card py-12 text-center">
            <p className="mb-4 text-slate-700">{loadError ?? '장비를 찾을 수 없습니다'}</p>
            <Link to="/equipment" className="btn-primary inline-flex">목록으로</Link>
          </div>
        </div>
      </main>
    );
  }

  const status = equipmentStatus(equipment);
  const rate = operationRate(equipment);
  const title = equipment.vehicle_no || equipment.model || EQUIPMENT_CATEGORY_LABEL[equipment.category];
  const registeredAt = new Date(equipment.created_at).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' });

  return (
    <main className="min-h-screen bg-slate-50">
      <AppHeader />
      <div className="mx-auto max-w-7xl space-y-6 px-6 py-8">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="mb-2 flex items-center gap-2 text-sm text-slate-500">
              <Link to="/equipment" className="font-medium text-brand-700 hover:text-brand-800">장비 관리</Link>
              <span>/</span>
              <span>상세 정보</span>
            </div>
            <h1 className="text-2xl font-bold text-slate-950">장비 상세</h1>
          </div>
          {canEdit && !editing && (
            <div className="flex gap-2">
              <button
                type="button"
                onClick={startEdit}
                className="btn-primary text-sm"
              >
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
            <EquipmentFields values={values} onChange={setValues} required />
            {saveError && (
              <p className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">{saveError}</p>
            )}
          </section>
        ) : (
          <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="grid gap-8 lg:grid-cols-[360px_1fr]">
              <PhotoGallery
                fetchUrl={`/api/equipment/${equipment.id}/photo`}
                hasPhoto={equipment.has_photo}
                photoNonce={photoNonce}
                fallbackText={title}
                alt={title}
                canEdit={canEdit}
                photoBusy={photoBusy}
                fileInputRef={fileInputRef}
                onFile={(file) => void handlePhotoFile(file)}
                onDelete={() => void deletePhoto()}
                accentLabel={EQUIPMENT_CATEGORY_LABEL[equipment.category]}
              />

              <div className="min-w-0 space-y-6">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <div className="mb-3 flex flex-wrap items-center gap-2">
                      <h2 className="text-3xl font-bold text-slate-950">{title}</h2>
                      <StatusBadge status={status} />
                    </div>
                    <p className="text-sm font-medium text-slate-500">
                      EQ-{String(equipment.id).padStart(4, '0')} · {EQUIPMENT_CATEGORY_LABEL[equipment.category]}
                    </p>
                  </div>
                  <div className="w-full max-w-[260px]">
                    <ProgressBar value={rate} label="가동률" />
                  </div>
                </div>

                <dl className="grid gap-x-6 gap-y-5 border-y border-slate-100 py-6 sm:grid-cols-2 xl:grid-cols-4">
                  <InfoField label="모델명" value={equipment.model ?? '-'} />
                  <InfoField label="제조사" value={equipment.manufacturer ?? '-'} />
                  <InfoField label="연식" value={equipment.year ?? '-'} />
                  <InfoField label="담당자" value={user?.name ?? '-'} />
                  <InfoField label="차량번호" value={equipment.vehicle_no ?? '-'} />
                  <InfoField label="공급사" value={supplier?.name ?? `id=${equipment.supplier_id}`} />
                  <InfoField label="등록일" value={registeredAt} />
                  <InfoField label="첨부 만료 예정" value={`${equipment.expiring_count}건`} />
                </dl>

                <div className="grid gap-3 sm:grid-cols-3">
                  <Metric label="이번 달 가동시간" value={`${Math.round(rate * 12.4)} 시간`} />
                  <Metric label="다음 점검일" value={equipment.expiring_count > 0 ? '확인 필요' : '2026.06.15'} tone={equipment.expiring_count > 0 ? 'warn' : 'normal'} />
                  <Metric label="현재 위치" value="현장 내 장비 주차장" />
                </div>
              </div>
            </div>
          </section>
        )}

        <section className="space-y-6">
          <DetailTabs active={activeTab} onChange={setActiveTab} />
          {activeTab === 'overview' && (
            <div className="grid gap-4 lg:grid-cols-3">
              <SummaryCard title="가동 현황">
                <div className="space-y-5">
                  <ProgressBar value={rate} label="최근 30일 가동률" />
                  <div className="grid grid-cols-3 gap-3 text-center">
                    <Metric label="가동" value={`${Math.round(rate * 10)}h`} />
                    <Metric label="대기" value={`${100 - rate}h`} />
                    <Metric label="비가동" value={status === 'broken' ? '진행중' : '0h'} />
                  </div>
                </div>
              </SummaryCard>
              <SummaryCard title="최근 점검 정보">
                <HistoryList items={[
                  { title: '정기 안전 점검', meta: '최근 점검일 2026.04.10', value: '3개월 주기', status },
                  { title: '소모품 상태 확인', meta: '유압유, 필터, 브레이크', value: equipment.expiring_count > 0 ? '확인 필요' : '정상', status: equipment.expiring_count > 0 ? 'attention' : 'running' },
                ]} />
              </SummaryCard>
              <SummaryCard title="위치 정보">
                <div className="rounded-lg border border-slate-200 bg-brand-50 p-4">
                  <div className="mb-4 flex h-28 items-center justify-center rounded-lg bg-white text-sm font-semibold text-brand-700 shadow-sm">
                    GPS 위치
                  </div>
                  <p className="font-semibold text-slate-900">서울 A현장</p>
                  <p className="mt-1 text-sm text-slate-500">현장 내 장비 주차장</p>
                </div>
              </SummaryCard>
            </div>
          )}
          {activeTab === 'inspection' && (
            <HistoryList items={[
              { title: '정기 점검 완료', meta: '2026.04.10 · 김민수 기사', value: '이상 없음', status: 'running' },
              { title: '보험 증권 확인', meta: '2026.03.01 · 관리자', value: equipment.expiring_count > 0 ? '만료 임박' : '정상', status: equipment.expiring_count > 0 ? 'attention' : 'running' },
              { title: '입고 전 안전 확인', meta: '2026.02.18 · 현장관리팀', value: '완료', status: 'running' },
            ]} />
          )}
          {activeTab === 'operation' && (
            <HistoryList items={[
              { title: '토공 구간 작업', meta: '2026.05.03 08:00-17:00', value: `${rate}%`, status },
              { title: '상차 지원', meta: '2026.05.02 09:00-15:30', value: '86%', status: 'running' },
              { title: '대기', meta: '2026.05.01 13:00-17:00', value: '배차 대기', status: 'inactive' },
            ]} />
          )}
          {activeTab === 'location' && (
            <HistoryList items={[
              { title: '서울 A현장 장비 주차장', meta: '2026.05.04 09:20 업데이트', value: 'GPS 정상', status: 'running' },
              { title: '서울 A현장 토공 2구역', meta: '2026.05.03 17:10 업데이트', value: '작업 종료', status: 'running' },
              { title: '반입 게이트', meta: '2026.05.01 07:45 업데이트', value: '반입 완료', status: 'running' },
            ]} />
          )}
          {activeTab === 'documents' && (
            <section className="card">
              <DocumentSection ownerType="EQUIPMENT" ownerId={equipment.id} canEdit={canEdit} title="첨부서류" />
            </section>
          )}
        </section>
      </div>

      <ConfirmDialog
        open={confirmDelete}
        title="장비 삭제"
        message={`${EQUIPMENT_CATEGORY_LABEL[equipment.category]} ${equipment.vehicle_no ?? equipment.model ?? ''} 를 삭제합니다.\n복구할 수 없습니다.`}
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
