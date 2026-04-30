import { useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import CompanyFields from './CompanyFields';
import type { CompanyResponse, CompanyType } from '../../types/auth';

type Props = {
  onCreated: (c: CompanyResponse) => void;
  onCancel: () => void;
};

const EMPTY = { name: '', businessNumber: '', type: 'BP' as CompanyType };

export default function CompanyCreateForm({ onCreated, onCancel }: Props) {
  const [values, setValues] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await api.post<CompanyResponse>('/api/companies', {
        name: values.name,
        business_number: values.businessNumber,
        type: values.type,
      });
      setValues(EMPTY);
      onCreated(res.data);
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '생성 실패');
      } else {
        setError('생성 실패');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="card mb-6 space-y-4">
      <h2 className="text-base font-bold">새 회사 등록</h2>
      <CompanyFields
        values={values}
        onChange={(next) => setValues({
          name: next.name,
          businessNumber: next.businessNumber,
          type: next.type ?? 'BP',
        })}
        showType
        required
      />
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
