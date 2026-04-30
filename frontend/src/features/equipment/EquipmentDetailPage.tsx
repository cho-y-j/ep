import { useCallback, useEffect, useMemo, useState } from 'react';
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

export default function EquipmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [equipment, setEquipment] = useState<EquipmentResponse | null>(null);
  const [supplier, setSupplier] = useState<CompanyResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [editing, setEditing] = useState(false);
  const [values, setValues] = useState<EquipmentFieldValues | null>(null);
  const [saveBusy, setSaveBusy] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);

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

  if (loading) {
    return (
      <main className="min-h-screen bg-slate-50">
        <AppHeader />
        <div className="max-w-6xl mx-auto px-6 py-8 text-slate-400">불러오는 중...</div>
      </main>
    );
  }

  if (loadError || !equipment) {
    return (
      <main className="min-h-screen bg-slate-50">
        <AppHeader />
        <div className="max-w-6xl mx-auto px-6 py-8">
          <div className="card text-center py-12">
            <p className="text-slate-700 mb-4">{loadError ?? '장비를 찾을 수 없습니다'}</p>
            <Link to="/equipment" className="btn-primary inline-flex">목록으로</Link>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-slate-50">
      <AppHeader />
      <div className="max-w-6xl mx-auto px-6 py-8 space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link to="/equipment" className="text-sm text-slate-500 hover:text-slate-900">← 목록</Link>
            <h1 className="text-2xl font-bold">장비 상세</h1>
          </div>
          {canEdit && !editing && (
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setConfirmDelete(true)}
                className="px-3 py-1.5 rounded-lg bg-red-600 text-white text-sm font-medium hover:bg-red-700"
              >
                삭제
              </button>
              <button type="button" onClick={startEdit} className="btn-primary text-sm py-1.5">수정</button>
            </div>
          )}
          {editing && (
            <div className="flex gap-2">
              <button type="button" onClick={() => setEditing(false)} className="px-3 py-1.5 rounded-lg text-slate-700 text-sm hover:bg-slate-100">
                취소
              </button>
              <button type="button" onClick={save} disabled={saveBusy} className="btn-primary text-sm py-1.5 disabled:opacity-50">
                {saveBusy ? '저장 중...' : '저장'}
              </button>
            </div>
          )}
        </div>

        {/* 정보 카드 */}
        <section className="card">
          <h2 className="text-base font-bold mb-4">정보</h2>

          {editing && values ? (
            <div className="space-y-4">
              <EquipmentFields values={values} onChange={setValues} required />
              {saveError && (
                <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{saveError}</p>
              )}
            </div>
          ) : (
            <div className="flex flex-col md:flex-row gap-6">
              <div className="shrink-0 w-[140px] aspect-square rounded-lg bg-slate-100 flex items-center justify-center">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4" className="text-slate-400">
                  <rect x="2" y="10" width="20" height="8" rx="1.5" />
                  <circle cx="7" cy="19" r="2" />
                  <circle cx="17" cy="19" r="2" />
                  <path d="M5 10V6h6l3 4" />
                </svg>
              </div>

              <div className="flex-1 min-w-0">
                <div className="mb-4">
                  <h3 className="text-2xl font-bold">
                    {equipment.vehicle_no || equipment.model || EQUIPMENT_CATEGORY_LABEL[equipment.category]}
                  </h3>
                  <div className="mt-1">
                    <span className="inline-flex px-2 py-0.5 rounded bg-blue-50 text-blue-700 text-xs font-medium">
                      {EQUIPMENT_CATEGORY_LABEL[equipment.category]}
                    </span>
                  </div>
                </div>

                <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-3 text-sm">
                  <Field label="차량번호" value={equipment.vehicle_no ?? '—'} />
                  <Field label="제조사" value={equipment.manufacturer ?? '—'} />
                  <Field label="모델" value={equipment.model ?? '—'} />
                  <Field label="제조년도" value={equipment.year ?? '—'} />
                  <Field label="공급사" value={supplier?.name ?? `id=${equipment.supplier_id}`} />
                  {supplier && <Field label="사업자번호" value={supplier.business_number} />}
                  <Field label="등록일" value={new Date(equipment.created_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} />
                </dl>
              </div>
            </div>
          )}
        </section>

        {/* 첨부 서류 카드 */}
        <section className="card">
          <DocumentSection ownerType="EQUIPMENT" ownerId={equipment.id} canEdit={canEdit} />
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

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="text-slate-900 mt-0.5">{value}</dd>
    </div>
  );
}
