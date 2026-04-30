import { useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import SidePanel from '../../components/SidePanel';
import ConfirmDialog from '../../components/ConfirmDialog';
import EquipmentFields, { type EquipmentFieldValues } from './EquipmentFields';
import { api } from '../../lib/api';
import { EQUIPMENT_CATEGORY_LABEL, type EquipmentResponse } from '../../types/equipment';
import type { CompanyResponse } from '../../types/auth';

type Props = {
  equipment: EquipmentResponse | null;
  supplier?: CompanyResponse | null;
  onClose: () => void;
  onChange: (updated: EquipmentResponse) => void;
  onDelete: (id: number) => void;
  canEdit: boolean;
};

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

export default function EquipmentDetailPanel({ equipment, supplier, onClose, onChange, onDelete, canEdit }: Props) {
  const [editing, setEditing] = useState(false);
  const [values, setValues] = useState<EquipmentFieldValues>(() =>
    equipment ? toFieldValues(equipment) : toFieldValues({ id: 0, supplier_id: 0, category: 'EXCAVATOR', created_at: '' } as EquipmentResponse)
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (!equipment) {
    return <SidePanel open={false} onClose={onClose} title="">{null}</SidePanel>;
  }

  function startEdit() {
    if (!equipment) return;
    setValues(toFieldValues(equipment));
    setError(null);
    setEditing(true);
  }

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!equipment || !values) return;
    setBusy(true);
    setError(null);
    try {
      const res = await api.patch<EquipmentResponse>(`/api/equipment/${equipment.id}`, {
        vehicle_no: values.vehicleNo || null,
        category: values.category,
        model: values.model || null,
        manufacturer: values.manufacturer || null,
        year: values.year ? Number(values.year) : null,
      });
      onChange(res.data);
      setEditing(false);
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '저장 실패');
      } else {
        setError('저장 실패');
      }
    } finally {
      setBusy(false);
    }
  }

  async function doDelete() {
    if (!equipment) return;
    setBusy(true);
    try {
      await api.delete(`/api/equipment/${equipment.id}`);
      onDelete(equipment.id);
      setConfirmDelete(false);
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '삭제 실패');
      } else {
        setError('삭제 실패');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <SidePanel
        open={!!equipment}
        onClose={onClose}
        title={editing ? '장비 수정' : '장비 상세'}
        footer={
          editing ? (
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setEditing(false)} className="px-4 py-2 rounded-lg text-slate-700 hover:bg-slate-100">취소</button>
              <button type="submit" form="equipment-edit-form" disabled={busy} className="btn-primary disabled:opacity-50">
                {busy ? '저장 중...' : '저장'}
              </button>
            </div>
          ) : canEdit ? (
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setConfirmDelete(true)} className="px-4 py-2 rounded-lg bg-red-600 text-white font-medium hover:bg-red-700">
                삭제
              </button>
              <button type="button" onClick={startEdit} className="btn-primary">수정</button>
            </div>
          ) : null
        }
      >
        {editing ? (
          <form id="equipment-edit-form" onSubmit={save} className="space-y-4">
            <EquipmentFields values={values} onChange={setValues} required />
            {error && <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>}
          </form>
        ) : (
          <dl className="space-y-4 text-sm">
            <Row label="장비 종류" value={EQUIPMENT_CATEGORY_LABEL[equipment.category]} />
            <Row label="차량번호" value={equipment.vehicle_no || <span className="text-slate-400">없음</span>} />
            <Row label="제조사" value={equipment.manufacturer || <span className="text-slate-400">—</span>} />
            <Row label="모델" value={equipment.model || <span className="text-slate-400">—</span>} />
            <Row label="제조년도" value={equipment.year ?? <span className="text-slate-400">—</span>} />
            <Row label="공급사" value={supplier ? `${supplier.name} (${supplier.business_number})` : equipment.supplier_id} />
            <Row label="등록일" value={new Date(equipment.created_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} />
            {error && <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>}
          </dl>
        )}
      </SidePanel>

      <ConfirmDialog
        open={confirmDelete}
        title="장비 삭제"
        message={`${EQUIPMENT_CATEGORY_LABEL[equipment.category]} ${equipment.vehicle_no ?? equipment.model ?? ''} 를 삭제합니다.\n복구할 수 없습니다.`}
        confirmLabel="삭제"
        variant="danger"
        busy={busy}
        onConfirm={doDelete}
        onCancel={() => setConfirmDelete(false)}
      />
    </>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-start gap-4">
      <dt className="w-24 shrink-0 text-slate-500">{label}</dt>
      <dd className="flex-1 text-slate-900">{value}</dd>
    </div>
  );
}
