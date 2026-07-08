import { useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import EquipmentFields, { EMPTY_EQUIPMENT_FIELDS, type EquipmentFieldValues } from './EquipmentFields';
import type { CompanyResponse } from '../../types/auth';
import type { EquipmentResponse } from '../../types/equipment';

type Props = {
  /** ADMIN 등 supplier_id 직접 선택 가능한 컨텍스트일 때 회사 목록 전달 */
  equipmentSuppliers?: CompanyResponse[];
  /** ADMIN 컨텍스트일 때 supplier_id 미지정이면 클라이언트에서 막음 */
  requireSupplierId?: boolean;
  onCreated: (e: EquipmentResponse) => void;
  onCancel: () => void;
};

export default function EquipmentCreateForm({ equipmentSuppliers, requireSupplierId, onCreated, onCancel }: Props) {
  const [values, setValues] = useState<EquipmentFieldValues>(EMPTY_EQUIPMENT_FIELDS);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Phase4: 외부 장비 기사(조종원) 로그인 계정 — 외부일 때만, 셋 다 채우면 등록과 동시에 발급.
  const [operator, setOperator] = useState({ name: '', phone: '', username: '', password: '' });

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (requireSupplierId && !values.supplierId) {
      setError('장비공급사를 선택하세요');
      return;
    }

    setBusy(true);
    try {
      const body: Record<string, unknown> = {
        category: values.category,
        vehicle_no: values.vehicleNo || null,
        model: values.model || null,
        manufacturer: values.manufacturer || null,
        year: values.year ? Number(values.year) : null,
        is_external: values.isExternal,
        vehicle_owner_name: values.isExternal ? (values.vehicleOwnerName || null) : null,
        vehicle_owner_business_no: values.isExternal ? (values.vehicleOwnerBusinessNo || null) : null,
        operator_name: values.isExternal ? (operator.name.trim() || null) : null,
        operator_phone: values.isExternal ? (operator.phone.trim() || null) : null,
        operator_username: values.isExternal ? (operator.username.trim() || null) : null,
        operator_password: values.isExternal ? (operator.password || null) : null,
      };
      if (values.supplierId) {
        body.supplier_id = values.supplierId;
      }
      const res = await api.post<EquipmentResponse>('/api/equipment', body);
      setValues(EMPTY_EQUIPMENT_FIELDS);
      setOperator({ name: '', phone: '', username: '', password: '' });
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
      <h2 className="text-base font-bold">새 장비 등록</h2>
      <EquipmentFields
        values={values}
        onChange={setValues}
        equipmentSuppliers={equipmentSuppliers}
        required
      />
      {values.isExternal && (
        <div className="space-y-3 rounded-lg border border-blue-200 bg-blue-50/40 p-3">
          <p className="text-xs text-blue-700">외부 장비 <strong>기사(조종원) 로그인 계정</strong> (선택) — 이름·아이디·비밀번호를 모두 채우면 등록과 동시에 계정이 발급되어 앱에서 로그인할 수 있습니다.</p>
          <div className="grid grid-cols-2 gap-3">
            <label className="block">
              <span className="text-sm font-medium text-slate-700">기사 이름</span>
              <input type="text" value={operator.name} onChange={(e) => setOperator({ ...operator, name: e.target.value })} placeholder="홍길동" className="input mt-1" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-700">기사 전화</span>
              <input type="text" value={operator.phone} onChange={(e) => setOperator({ ...operator, phone: e.target.value })} placeholder="010-0000-0000" className="input mt-1" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-700">로그인 아이디</span>
              <input type="text" autoComplete="off" value={operator.username} onChange={(e) => setOperator({ ...operator, username: e.target.value })} placeholder="아이디" className="input mt-1" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-700">비밀번호</span>
              <input type="password" autoComplete="new-password" value={operator.password} onChange={(e) => setOperator({ ...operator, password: e.target.value })} placeholder="비밀번호" className="input mt-1" />
            </label>
          </div>
        </div>
      )}
      {error && (
        <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>
      )}
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="px-4 py-2 rounded-lg text-slate-700 hover:bg-slate-100">
          취소
        </button>
        <button type="submit" disabled={busy} className="btn-primary disabled:opacity-50">
          {busy ? '등록 중...' : '등록'}
        </button>
      </div>
    </form>
  );
}
