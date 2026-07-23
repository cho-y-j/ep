import { useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import type { CompanyResponse } from '../../types/auth';
import type { OwnerType, DocumentTypeResponse } from '../../types/document';
import { PERSON_ROLE_LABEL, rolesAllowedFor, type PersonRole } from '../../types/person';
import { useEquipmentTypes } from '../equipment/useEquipmentTypes';
import { fetchSuggestedSelByType, type Sel } from './suggest';

/** 등록형 행 1건 — 무엇을(장비종류/역할) 몇 대·명, 어떤 서류를 받을지. */
type RegRow = {
  key: string;
  owner_type: OwnerType;
  planned_type: string;
  quantity: number;
  sel: Sel;
};

const MODES = ['none', 'required', 'optional'] as const;
const MODE_LABEL = { none: '제외', required: '필수', optional: '선택' } as const;
const pickIds = (s: Sel, mode: 'required' | 'optional') =>
  Object.entries(s).filter(([, v]) => v === mode).map(([k]) => Number(k));
const countOf = (s: Sel, mode: 'required' | 'optional') =>
  Object.values(s).filter((v) => v === mode).length;

let rowSeq = 0;
const nextKey = () => `r${rowSeq++}`;

/**
 * 신규 자원 등록형 요청 폼 — 협력업체 1곳을 정하고 [장비종류/역할 × 수량] 행을 쌓는다.
 * 종류를 고르면 그 종류의 필수/선택 서류를 suggest-by-type 로 프리필(TargetDocEditor 미러).
 * 생성 시 각 행은 owner 없는 슬롯 quantity 개가 되고, 공개 링크에서 값 입력 순간 자원이 만들어진다.
 */
export default function RegisterTargetForm({ typesEq, typesPe, onDone }: {
  typesEq: DocumentTypeResponse[];
  typesPe: DocumentTypeResponse[];
  onDone: () => void;
}) {
  const { company: myCompany } = useAuth();
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [companyId, setCompanyId] = useState('');
  const [rows, setRows] = useState<RegRow[]>([]);
  const [open, setOpen] = useState<Record<string, boolean>>({});
  const [recipientName, setRecipientName] = useState('');
  const [recipientPhone, setRecipientPhone] = useState('');
  const [title, setTitle] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { options: eqOptions } = useEquipmentTypes();

  useEffect(() => {
    api.get<CompanyResponse[]>('/api/companies/children')
      .then((r) => setCompanies(r.data ?? []))
      .catch(() => setCompanies([]));
  }, []);

  // 본인 회사도 선택 가능 — 자기 명의 신규 등록형 링크(백엔드 selfAndChildren 검증이 본인 허용).
  const companyOptions = useMemo(() => {
    if (!myCompany || companies.some((c) => c.id === myCompany.id)) return companies;
    return [myCompany, ...companies];
  }, [myCompany, companies]);

  const company = companyOptions.find((c) => String(c.id) === companyId) ?? null;
  const companyType = company?.type ?? null;
  // 장비 등록은 장비공급사 협력업체만. 역할은 회사유형이 허용하는 것만(인력사는 조종원 제외).
  const canEquipment = companyType === 'EQUIPMENT';
  const roleOptions: PersonRole[] = companyType ? rolesAllowedFor(companyType) : [];

  function addRow() {
    const owner_type: OwnerType = canEquipment ? 'EQUIPMENT' : 'PERSON';
    const key = nextKey();
    setRows((rs) => [...rs, { key, owner_type, planned_type: '', quantity: 1, sel: {} }]);
    setOpen((o) => ({ ...o, [key]: true }));
  }
  function removeRow(key: string) {
    setRows((rs) => rs.filter((r) => r.key !== key));
  }
  function patchRow(key: string, patch: Partial<RegRow>) {
    setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  }

  /** 종류 확정 시 그 종류의 필수/선택 서류를 프리필. */
  async function changeType(key: string, owner_type: OwnerType, planned_type: string) {
    patchRow(key, { owner_type, planned_type, sel: {} });
    if (!planned_type) return;
    const sel = await fetchSuggestedSelByType(owner_type, planned_type);
    setRows((rs) => rs.map((r) => (r.key === key ? { ...r, sel } : r)));
  }

  // 협력업체를 바꾸면 종류 옵션이 달라지므로 행을 비운다(장비→인력사 전환 시 잘못된 조합 방지).
  useEffect(() => { setRows([]); }, [companyId]);

  const slotTotal = useMemo(() => rows.reduce((n, r) => n + (r.quantity || 0), 0), [rows]);

  async function create() {
    if (!companyId) { setError('협력업체를 선택하세요'); return; }
    if (rows.length === 0) { setError('등록할 종류를 1개 이상 추가하세요'); return; }
    for (const r of rows) {
      if (!r.planned_type) { setError('각 행의 종류를 선택하세요'); return; }
      if (r.quantity < 1) { setError('수량은 1 이상이어야 합니다'); return; }
      if (countOf(r.sel, 'required') + countOf(r.sel, 'optional') === 0) {
        setError('수집할 서류가 0건인 행이 있습니다'); return;
      }
    }
    setBusy(true); setError(null);
    try {
      await api.post('/api/document-collections', {
        title: title.trim() || null,
        recipient_name: recipientName.trim() || null,
        recipient_phone: recipientPhone.trim() || null,
        target_company_id: Number(companyId),
        targets: rows.map((r) => ({
          owner_type: r.owner_type,
          planned_type: r.planned_type,
          quantity: r.quantity,
          required_type_ids: pickIds(r.sel, 'required'),
          optional_type_ids: pickIds(r.sel, 'optional'),
        })),
      });
      onDone();
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '생성 실패') : '생성 실패');
    } finally { setBusy(false); }
  }

  return (
    <div className="space-y-4">
      <p className="rounded-lg border border-brand-200 bg-brand-50 px-3 py-2 text-xs text-brand-800">
        협력업체 직원에게 링크를 보내면, 직원이 <strong>로그인 없이</strong> 차량번호·이름을 입력하는 순간 장비·인력이 신규 등록되고 이어서 서류를 올립니다.
      </p>

      {error && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}

      <div className="space-y-1">
        <div className="text-sm font-medium text-slate-700">협력업체 <span className="text-xs font-normal text-slate-400">— 신규 자원을 이 회사 명의로 등록</span></div>
        <select value={companyId} onChange={(e) => setCompanyId(e.target.value)} className="input w-full">
          <option value="">협력업체 선택</option>
          {companyOptions.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name} ({c.type === 'EQUIPMENT' ? '장비' : c.type === 'MANPOWER' ? '인력' : c.type})
              {c.id === myCompany?.id ? ' — 본인 회사' : ''}
            </option>
          ))}
        </select>
      </div>

      {company && (
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <div className="text-sm font-medium text-slate-700">등록할 종류 · 수량</div>
            <button type="button" onClick={addRow} className="rounded-md border border-brand-300 bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700 hover:bg-brand-100">
              + 행 추가
            </button>
          </div>
          {rows.length === 0 && <p className="rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-400">‘행 추가’로 등록할 장비종류/역할과 수량을 지정하세요.</p>}

          {rows.map((r) => {
            const types = r.owner_type === 'EQUIPMENT' ? typesEq : typesPe;
            const req = countOf(r.sel, 'required');
            const opt = countOf(r.sel, 'optional');
            const empty = req + opt === 0;
            return (
              <div key={r.key} className={`rounded-lg border ${empty ? 'border-amber-300 bg-amber-50/40' : 'border-slate-200'}`}>
                <div className="flex flex-wrap items-center gap-2 px-3 py-2">
                  {canEquipment && (
                    <select value={r.owner_type}
                      onChange={(e) => changeType(r.key, e.target.value as OwnerType, '')}
                      className="input w-24">
                      <option value="EQUIPMENT">장비</option>
                      <option value="PERSON">인력</option>
                    </select>
                  )}
                  <select value={r.planned_type}
                    onChange={(e) => changeType(r.key, r.owner_type, e.target.value)}
                    className="input flex-1 min-w-[8rem]">
                    <option value="">{r.owner_type === 'EQUIPMENT' ? '장비종류 선택' : '역할 선택'}</option>
                    {r.owner_type === 'EQUIPMENT'
                      ? eqOptions.map((o) => <option key={o.code} value={o.code}>{o.name}</option>)
                      : roleOptions.map((role) => <option key={role} value={role}>{PERSON_ROLE_LABEL[role]}</option>)}
                  </select>
                  <div className="flex items-center gap-1">
                    <input type="number" min={1} value={r.quantity}
                      onChange={(e) => patchRow(r.key, { quantity: Math.max(1, Number(e.target.value) || 1) })}
                      className="input w-16 text-center" />
                    <span className="text-xs text-slate-500">{r.owner_type === 'EQUIPMENT' ? '대' : '명'}</span>
                  </div>
                  <button type="button" onClick={() => setOpen((o) => ({ ...o, [r.key]: !o[r.key] }))}
                    className="text-xs font-semibold text-slate-500 hover:underline">
                    서류 {empty ? <span className="text-amber-700">없음</span> : `필수 ${req}·선택 ${opt}`} {open[r.key] ? '▲' : '▼'}
                  </button>
                  <button type="button" onClick={() => removeRow(r.key)} className="rounded p-1 text-slate-400 hover:bg-slate-100" aria-label="행 삭제">✕</button>
                </div>
                {open[r.key] && (
                  <div className="divide-y divide-slate-100 border-t border-slate-200">
                    {!r.planned_type ? <p className="p-3 text-xs text-slate-400">종류를 먼저 선택하세요.</p> :
                     types.length === 0 ? <p className="p-3 text-xs text-slate-400">서류 종류가 없습니다.</p> :
                      types.map((ty) => (
                        <div key={ty.id} className="flex items-center justify-between px-3 py-2">
                          <span className="text-sm text-slate-700">{ty.name}</span>
                          <div className="inline-flex overflow-hidden rounded-md border border-slate-300 text-xs">
                            {MODES.map((v) => (
                              <button key={v} type="button" onClick={() => patchRow(r.key, { sel: { ...r.sel, [ty.id]: v } })}
                                className={`px-2.5 py-1 font-semibold border-r border-slate-200 last:border-r-0 ${
                                  (r.sel[ty.id] ?? 'none') === v
                                    ? v === 'required' ? 'bg-rose-600 text-white' : v === 'optional' ? 'bg-amber-500 text-white' : 'bg-slate-500 text-white'
                                    : 'bg-white text-slate-600 hover:bg-slate-50'}`}>
                                {MODE_LABEL[v]}
                              </button>
                            ))}
                          </div>
                        </div>
                      ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {company && rows.length > 0 && (
        <div className="space-y-2">
          <div className="text-sm font-medium text-slate-700">받는사람 <span className="text-xs font-normal text-slate-400">— 비워도 됩니다</span></div>
          <div className="grid grid-cols-2 gap-2">
            <input value={recipientName} onChange={(e) => setRecipientName(e.target.value)} placeholder="이름 (선택)" className="input" />
            <input value={recipientPhone} onChange={(e) => setRecipientPhone(e.target.value)} placeholder="010-1234-5678 (선택)" className="input" />
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목 (선택)" className="input" />
          </div>
        </div>
      )}

      <button onClick={create} disabled={busy || !companyId || rows.length === 0}
        className="btn-primary w-full disabled:opacity-50">
        {busy ? '처리 중…' : `링크 생성 — 슬롯 ${slotTotal}개`}
      </button>
    </div>
  );
}
