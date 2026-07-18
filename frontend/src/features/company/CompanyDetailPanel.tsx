import { useState } from 'react';
import { AxiosError } from 'axios';
import { Link } from 'react-router-dom';
import SidePanel from '../../components/SidePanel';
import { api } from '../../lib/api';
import { COMPANY_TYPE_LABEL, type CompanyResponse, type CompanyType } from '../../types/auth';

type Props = {
  company: CompanyResponse | null;
  onClose: () => void;
  onChange: (updated: CompanyResponse) => void;
};

export default function CompanyDetailPanel({ company, onClose, onChange }: Props) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(company?.name ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!company) {
    return <SidePanel open={false} onClose={onClose} title="">{null}</SidePanel>;
  }

  function startEdit() {
    if (!company) return;
    setName(company.name);
    setError(null);
    setEditing(true);
  }

  async function save() {
    if (!company) return;
    setBusy(true);
    setError(null);
    try {
      const res = await api.patch<CompanyResponse>(`/api/companies/${company.id}`, { name });
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

  return (
    <SidePanel
      open={!!company}
      onClose={onClose}
      title="회사 상세"
      footer={
        editing ? (
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setEditing(false)} className="px-4 py-2 rounded-lg text-slate-700 hover:bg-slate-100">
              취소
            </button>
            <button type="button" onClick={save} disabled={busy} className="btn-primary disabled:opacity-50">
              {busy ? '저장 중...' : '저장'}
            </button>
          </div>
        ) : (
          <div className="flex justify-end">
            <button type="button" onClick={startEdit} className="btn-primary">회사명 수정</button>
          </div>
        )
      }
    >
      {editing ? (
        <div className="space-y-4">
          <label className="block">
            <span className="text-sm font-medium text-slate-700">회사명</span>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="input mt-1"
            />
          </label>
          <p className="text-xs text-slate-500">사업자번호와 유형은 변경할 수 없습니다.</p>
          {error && (
            <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>
          )}
        </div>
      ) : (
        <dl className="space-y-4 text-sm">
          <Row label="회사명" value={company.name} />
          <Row label="사업자번호" value={company.business_number} />
          <Row label="유형" value={COMPANY_TYPE_LABEL[company.type]} />
          <Row label="등록일" value={new Date(company.created_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} />
        </dl>
      )}

      <CompanyShortcuts companyId={company.id} companyType={company.type} companyName={company.name} />
    </SidePanel>
  );
}

/** 회사 컨텍스트로 기존 페이지를 여는 바로가기 — 기존 디자인 그대로 활용. */
function CompanyShortcuts({ companyId, companyType, companyName }:
  { companyId: number; companyType: CompanyType; companyName: string }) {
  const showEquipment = companyType === 'EQUIPMENT';
  const showPersons = true; // 모든 회사 type 가 직속 인원 가질 수 있음
  return (
    <div className="mt-6 pt-5 border-t border-slate-200 space-y-2">
      <div className="text-xs text-slate-500 mb-2">
        <strong className="text-slate-700">{companyName}</strong> 화면 바로가기
      </div>
      {showEquipment && (
        <Link to={`/equipment?supplierId=${companyId}`}
              className="block px-3 py-2 rounded-lg border border-slate-200 hover:bg-slate-50 text-sm">
          <span className="font-medium text-slate-900">이 공급사의 장비</span>
          <span className="block text-xs text-slate-500">자원 목록 페이지를 이 회사로 필터해서 봅니다</span>
        </Link>
      )}
      {showPersons && (
        <Link to={`/persons?supplierId=${companyId}`}
              className="block px-3 py-2 rounded-lg border border-slate-200 hover:bg-slate-50 text-sm">
          <span className="font-medium text-slate-900">
            {companyType === 'BP' ? '이 BP의 직속 인원' : '이 공급사의 인원'}
          </span>
          <span className="block text-xs text-slate-500">인원 목록 페이지를 이 회사로 필터해서 봅니다</span>
        </Link>
      )}
      {companyType === 'BP' && (
        <Link to={`/sites?bpCompanyId=${companyId}`}
              className="block px-3 py-2 rounded-lg border border-slate-200 hover:bg-slate-50 text-sm">
          <span className="font-medium text-slate-900">이 BP의 현장</span>
          <span className="block text-xs text-slate-500">현장 목록을 이 BP로 필터해서 봅니다</span>
        </Link>
      )}
    </div>
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
