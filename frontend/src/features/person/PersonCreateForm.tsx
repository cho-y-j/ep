import { useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { useSubSuppliers } from '../company/useSubSuppliers';
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
  const { company } = useAuth();
  // V77 대행 등록: 회사 관리자면 직속 자식(협력사) 소속으로도 등록 가능. 없으면 기존과 동일.
  const subSuppliers = useSubSuppliers();
  const [values, setValues] = useState<PersonFieldValues>(EMPTY_PERSON_FIELDS);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (requireSupplierId && !values.supplierId) {
      setError('소속 공급사를 선택하세요');
      return;
    }
    // 간소 등록 — 역할은 등록 후 상세에서 추가 (필수 아님).

    setBusy(true);
    try {
      const body: Record<string, unknown> = {
        name: values.name,
        birth: values.birth || null,
        phone: values.phone || null,
        roles: values.roles,
      };
      if (values.supplierId) body.supplier_id = values.supplierId;
      if (username.trim()) { body.username = username.trim(); body.password = password; }
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
      <div>
        <h2 className="text-base font-bold">새 인원 등록</h2>
        <p className="text-xs text-slate-500 mt-0.5">이름·전화만 입력하면 됩니다. 역할·생년월일·서류는 등록 후 상세에서 추가하세요.</p>
      </div>
      {!requireSupplierId && subSuppliers.length > 0 && (
        <label className="block">
          <span className="text-sm font-medium text-slate-700">소속 공급사</span>
          <select
            value={values.supplierId}
            onChange={(e) => setValues({ ...values, supplierId: e.target.value === '' ? '' : Number(e.target.value) })}
            className="input mt-1 bg-white"
          >
            <option value="">우리 회사{company ? ` — ${company.name}` : ''}</option>
            {subSuppliers.map((c) => (
              <option key={c.id} value={c.id}>{c.name} (협력사)</option>
            ))}
          </select>
          <span className="mt-1 block text-xs text-slate-400">협력사(하위공급사)를 선택하면 그 회사 소속으로 대신 등록됩니다.</span>
        </label>
      )}
      <PersonFields
        values={values}
        onChange={setValues}
        suppliers={suppliers}
        supplierType={selfSupplierType}
        required
        minimal
      />
      <div className="border-t border-slate-100 pt-3">
        <p className="text-sm font-semibold text-slate-700">앱 로그인 계정 <span className="font-normal text-slate-400">(선택 — 작업자 앱 아이디/비번)</span></p>
        <div className="mt-2 grid grid-cols-1 sm:grid-cols-2 gap-3">
          <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="아이디" className="input" autoComplete="off" />
          <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="비밀번호" className="input" autoComplete="new-password" />
        </div>
      </div>
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
