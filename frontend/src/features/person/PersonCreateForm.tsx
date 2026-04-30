import { useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import PersonFields, { EMPTY_PERSON_FIELDS, type PersonFieldValues } from './PersonFields';
import type { CompanyResponse, CompanyType } from '../../types/auth';
import type { PersonResponse } from '../../types/person';

type Props = {
  /** ADMIN 컨텍스트일 때 회사 목록 (EQUIPMENT + MANPOWER) */
  suppliers?: CompanyResponse[];
  /** 셀프 등록 시 본인 회사 type (역할 선택지 필터링용) */
  selfSupplierType?: CompanyType;
  requireSupplierId?: boolean;
  onCreated: (p: PersonResponse) => void;
  onCancel: () => void;
};

export default function PersonCreateForm({ suppliers, selfSupplierType, requireSupplierId, onCreated, onCancel }: Props) {
  const [values, setValues] = useState<PersonFieldValues>(EMPTY_PERSON_FIELDS);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (requireSupplierId && !values.supplierId) {
      setError('소속 공급사를 선택하세요');
      return;
    }
    if (values.roles.length === 0) {
      setError('역할을 1개 이상 선택하세요');
      return;
    }

    setBusy(true);
    try {
      const body: Record<string, unknown> = {
        name: values.name,
        birth: values.birth || null,
        phone: values.phone || null,
        roles: values.roles,
      };
      if (values.supplierId) body.supplier_id = values.supplierId;
      const res = await api.post<PersonResponse>('/api/persons', body);
      setValues(EMPTY_PERSON_FIELDS);
      onCreated(res.data);
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '등록 실패');
      } else {
        setError('등록 실패');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="card mb-6 space-y-4">
      <h2 className="text-base font-bold">새 인원 등록</h2>
      <PersonFields
        values={values}
        onChange={setValues}
        suppliers={suppliers}
        supplierType={selfSupplierType}
        required
      />
      {error && (
        <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>
      )}
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="px-4 py-2 rounded-lg text-slate-700 hover:bg-slate-100">취소</button>
        <button type="submit" disabled={busy} className="btn-primary disabled:opacity-50">
          {busy ? '등록 중...' : '등록'}
        </button>
      </div>
    </form>
  );
}
