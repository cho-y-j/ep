import { useState } from 'react';
import { AxiosError } from 'axios';
import SidePanel from '../../components/SidePanel';
import ConfirmDialog from '../../components/ConfirmDialog';
import PersonFields, { type PersonFieldValues } from './PersonFields';
import { api } from '../../lib/api';
import { PERSON_ROLE_LABEL, type PersonResponse } from '../../types/person';
import type { CompanyResponse, CompanyType } from '../../types/auth';

type Props = {
  person: PersonResponse | null;
  supplier?: CompanyResponse | null;
  supplierType?: CompanyType;
  onClose: () => void;
  onChange: (updated: PersonResponse) => void;
  onDelete: (id: number) => void;
  canEdit: boolean;
};

function toFieldValues(p: PersonResponse): PersonFieldValues {
  return {
    supplierId: p.supplier_id,
    name: p.name,
    birth: p.birth ?? '',
    phone: p.phone ?? '',
    roles: [...p.roles],
  };
}

export default function PersonDetailPanel({ person, supplier, supplierType, onClose, onChange, onDelete, canEdit }: Props) {
  const [editing, setEditing] = useState(false);
  const [values, setValues] = useState<PersonFieldValues>(() =>
    person ? toFieldValues(person) : { supplierId: '', name: '', birth: '', phone: '', roles: [] }
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (!person) {
    return <SidePanel open={false} onClose={onClose} title="">{null}</SidePanel>;
  }

  function startEdit() {
    if (!person) return;
    setValues(toFieldValues(person));
    setError(null);
    setEditing(true);
  }

  async function save() {
    if (!person) return;
    if (values.roles.length === 0) {
      setError('역할을 1개 이상 선택하세요');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await api.patch<PersonResponse>(`/api/persons/${person.id}`, {
        name: values.name,
        birth: values.birth || null,
        phone: values.phone || null,
        roles: values.roles,
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
    if (!person) return;
    setBusy(true);
    try {
      await api.delete(`/api/persons/${person.id}`);
      onDelete(person.id);
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
        open={!!person}
        onClose={onClose}
        title={editing ? '인원 수정' : '인원 상세'}
        footer={
          editing ? (
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setEditing(false)} className="px-4 py-2 rounded-lg text-slate-700 hover:bg-slate-100">취소</button>
              <button type="button" onClick={save} disabled={busy} className="btn-primary disabled:opacity-50">
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
          <div className="space-y-4">
            <PersonFields values={values} onChange={setValues} supplierType={supplierType} required />
            {error && <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>}
          </div>
        ) : (
          <dl className="space-y-4 text-sm">
            <Row label="이름" value={person.name} />
            <Row label="생년월일" value={person.birth || <span className="text-slate-400">—</span>} />
            <Row label="휴대폰" value={person.phone || <span className="text-slate-400">—</span>} />
            <Row
              label="역할"
              value={
                <div className="flex flex-wrap gap-1">
                  {person.roles.map((r) => (
                    <span key={r} className="inline-flex px-2 py-0.5 rounded bg-blue-50 text-blue-700 text-xs">
                      {PERSON_ROLE_LABEL[r]}
                    </span>
                  ))}
                </div>
              }
            />
            <Row label="공급사" value={supplier ? `${supplier.name} (${supplier.business_number})` : person.supplier_id} />
            <Row label="등록일" value={new Date(person.created_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} />
            {error && <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>}
          </dl>
        )}
      </SidePanel>

      <ConfirmDialog
        open={confirmDelete}
        title="인원 삭제"
        message={`${person.name} 을(를) 삭제합니다.\n복구할 수 없습니다.`}
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
