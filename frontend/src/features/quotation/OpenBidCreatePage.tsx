import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { EQUIPMENT_CATEGORIES, EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory } from '../../types/equipment';
import { useEquipmentTypes } from '../equipment/useEquipmentTypes';
import MoneyInput from '../../components/MoneyInput';
import type { CompanyResponse } from '../../types/auth';

interface ClientOrg { id: number; name: string; }

interface Draft {
  clientOrgId: string;
  bpCompanyId: string;
  workLocationText: string;
  equipmentCategory: EquipmentCategory | '';
  specText: string;
  count: number;
  workPeriodStart: string;
  workPeriodEnd: string;
  proposedDailyRate: string;
  proposedMonthlyRate: string;
  notes: string;
  emailRecipients: string;
}

const EMPTY: Draft = {
  clientOrgId: '',
  bpCompanyId: '',
  workLocationText: '',
  equipmentCategory: '',
  specText: '',
  count: 1,
  workPeriodStart: '',
  workPeriodEnd: '',
  proposedDailyRate: '',
  proposedMonthlyRate: '',
  notes: '',
  emailRecipients: '',
};

export default function OpenBidCreatePage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const { options: typeOptions } = useEquipmentTypes();
  const categoryOptions = typeOptions.length
    ? typeOptions
    : EQUIPMENT_CATEGORIES.map((c) => ({ code: c, name: EQUIPMENT_CATEGORY_LABEL[c], grp: '' }));
  const [orgs, setOrgs] = useState<ClientOrg[]>([]);
  const [bpCompanies, setBpCompanies] = useState<CompanyResponse[]>([]);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    api.get<ClientOrg[]>('/api/client-orgs').then((r) => setOrgs(r.data)).catch(() => {});
    if (isAdmin) {
      api.get<CompanyResponse[]>('/api/companies', { params: { type: 'BP' } })
        .then((r) => setBpCompanies(r.data)).catch(() => {});
    }
  }, [isAdmin]);

  function update<K extends keyof Draft>(k: K, v: Draft[K]) {
    setDraft({ ...draft, [k]: v });
    if (fieldErrors[k as string]) {
      const next = { ...fieldErrors };
      delete next[k as string];
      setFieldErrors(next);
    }
  }

  async function submit() {
    setError(null);
    const errs: Record<string, string> = {};
    if (isAdmin && !draft.bpCompanyId) errs.bpCompanyId = 'BP 회사를 선택해주세요';
    if (!draft.equipmentCategory) errs.equipmentCategory = '장비 종류를 선택해주세요';
    if (!draft.workPeriodStart) errs.workPeriodStart = '시작일은 필수입니다';
    if (!draft.workPeriodEnd) errs.workPeriodEnd = '종료일은 필수입니다';
    if (draft.count < 1) errs.count = '1 이상 입력해주세요';
    if (draft.workPeriodStart && draft.workPeriodEnd && draft.workPeriodStart > draft.workPeriodEnd) {
      errs.workPeriodEnd = '종료일이 시작일보다 빠를 수 없습니다';
    }
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs);
      setError('필수 항목을 확인해주세요');
      return;
    }
    setFieldErrors({});
    setSubmitting(true);
    try {
      // backend Jackson 이 snake_case 로 받음 (CreateQuotationPayload 와 동일 convention).
      await api.post('/api/quotations/open-bid', {
        request_type: 'EQUIPMENT',
        equipment_category: draft.equipmentCategory,
        client_org_id: draft.clientOrgId ? Number(draft.clientOrgId) : null,
        work_location_text: draft.workLocationText || null,
        spec_text: draft.specText || null,
        count: draft.count,
        work_period_start: draft.workPeriodStart,
        work_period_end: draft.workPeriodEnd,
        proposed_daily_rate: draft.proposedDailyRate ? Number(draft.proposedDailyRate) : null,
        proposed_monthly_rate: draft.proposedMonthlyRate ? Number(draft.proposedMonthlyRate) : null,
        notes: draft.notes || null,
        on_behalf_of_bp_company_id: isAdmin && draft.bpCompanyId ? Number(draft.bpCompanyId) : null,
        email_recipients: draft.emailRecipients || null,
      });
      navigate('/quotations');
    } catch (e: any) {
      setError(e?.response?.data?.message || '발송 실패');
      setSubmitting(false);
    }
  }

  return (
    <AppShell breadcrumb={[{ label: '견적 관리', to: '/quotations' }, { label: '공개입찰 발송' }]}>
      <div className="max-w-3xl mx-auto px-6 py-8 space-y-5">
        <div>
          <h1 className="text-lg font-bold">공개입찰 견적 발송</h1>
          <p className="text-sm text-slate-500 mt-1">
            spec 만 적어 게시하면 플랫폼 전체 장비공급사가 자기 장비 + 단가로 제안할 수 있습니다.
            받은 제안들 중 BP 가 선정합니다.
          </p>
        </div>

        <div className="card p-6 space-y-4">
          {isAdmin && (
            <Field label="BP 회사 (대행 발송) *" error={fieldErrors.bpCompanyId}>
              <select className={inputCls(fieldErrors.bpCompanyId)} value={draft.bpCompanyId}
                      onChange={(e) => update('bpCompanyId', e.target.value)}>
                <option value="">— BP 선택 —</option>
                {bpCompanies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
          )}

          <Field label="원청기관 (선택)">
            <select className="input" value={draft.clientOrgId} onChange={(e) => update('clientOrgId', e.target.value)}>
              <option value="">선택 안 함</option>
              {orgs.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
            </select>
          </Field>

          <Field label="현장 이름/주소">
            <input className="input" value={draft.workLocationText}
                   onChange={(e) => update('workLocationText', e.target.value)}
                   placeholder="예: 화성 1공장 증축 / 경기 화성시…" />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="장비 종류 *" error={fieldErrors.equipmentCategory}>
              <select className={inputCls(fieldErrors.equipmentCategory)} value={draft.equipmentCategory}
                      onChange={(e) => update('equipmentCategory', e.target.value as EquipmentCategory)}>
                <option value="">선택</option>
                {categoryOptions.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
              </select>
            </Field>
            <Field label="수량 *" error={fieldErrors.count}>
              <input type="number" min={1} className={inputCls(fieldErrors.count)} value={draft.count}
                     onChange={(e) => update('count', Math.max(1, Number(e.target.value) || 1))} />
            </Field>
          </div>

          <Field label="세부 사양">
            <input className="input" value={draft.specText}
                   onChange={(e) => update('specText', e.target.value)}
                   placeholder="예: 3.5t 미니, 짧은 붐, 무한궤도" />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="작업 시작일 *" error={fieldErrors.workPeriodStart}>
              <input type="date" className={inputCls(fieldErrors.workPeriodStart)} value={draft.workPeriodStart}
                     onChange={(e) => update('workPeriodStart', e.target.value)} />
            </Field>
            <Field label="작업 종료일 *" error={fieldErrors.workPeriodEnd}>
              <input type="date" className={inputCls(fieldErrors.workPeriodEnd)} value={draft.workPeriodEnd}
                     onChange={(e) => update('workPeriodEnd', e.target.value)} />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Field label="희망 일대 (원, 선택)">
              <MoneyInput value={draft.proposedDailyRate}
                          onChange={(v) => update('proposedDailyRate', v === '' ? '' : String(v))}
                          placeholder="비우면 협의" />
            </Field>
            <Field label="희망 월대 (원, 선택)">
              <MoneyInput value={draft.proposedMonthlyRate}
                          onChange={(v) => update('proposedMonthlyRate', v === '' ? '' : String(v))}
                          placeholder="비우면 협의" />
            </Field>
          </div>

          <Field label="추가 요구사항">
            <textarea className="input" rows={3} value={draft.notes}
                      onChange={(e) => update('notes', e.target.value)} />
          </Field>

          <Field label="외부 이메일 안내 발송 (선택, 쉼표/줄바꿈 구분)">
            <textarea className="input" rows={2} value={draft.emailRecipients}
                      onChange={(e) => update('emailRecipients', e.target.value)}
                      placeholder="example1@company.com, example2@company.com" />
            <div className="text-[11px] text-slate-500 mt-1">
              입력한 이메일 주소로 공개입찰 안내 메일 발송. skep 계정 없는 공급사에게 확장 발송용.
            </div>
          </Field>

          {error && <div className="text-sm text-rose-600">{error}</div>}
          <div className="flex justify-end gap-2 pt-2">
            <button onClick={() => navigate('/quotations')} className="btn-ghost">취소</button>
            <button onClick={submit} disabled={submitting} className="btn-primary disabled:opacity-50">
              {submitting ? '발송 중…' : '공개입찰 발송'}
            </button>
          </div>
        </div>
      </div>
    </AppShell>
  );
}

function Field({ label, children, error }: { label: string; children: React.ReactNode; error?: string }) {
  return (
    <label className="block">
      <span className="text-xs text-slate-600 font-medium">{label}</span>
      <div className="mt-1">{children}</div>
      {error && <div className="text-xs text-rose-600 mt-1">{error}</div>}
    </label>
  );
}

function inputCls(error?: string) {
  return error ? 'input border-rose-400 focus:border-rose-500 focus:ring-rose-200' : 'input';
}
