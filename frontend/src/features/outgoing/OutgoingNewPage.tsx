import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory } from '../../types/equipment';
import { PERSON_ROLE_LABEL, type PersonRole } from '../../types/person';
import MoneyInput from '../../components/MoneyInput';

type Mode = 'REGISTERED_BP' | 'EMAIL';

interface Equipment { id: number; vehicle_no?: string; model?: string; category: string; }
interface Person { id: number; name: string; roles: string[]; }
interface User { id: number; name: string; email: string; company_id?: number; company_name?: string; role: string; }
interface BpCompany { id: number; name: string; }

export default function OutgoingNewPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const fromProposal = params.get('fromProposal');
  const [mode, setMode] = useState<Mode>('REGISTERED_BP');
  const [resourceKind, setResourceKind] = useState<'equipment' | 'person'>('equipment');
  const [resourceId, setResourceId] = useState('');
  const [equipments, setEquipments] = useState<Equipment[]>([]);
  const [persons, setPersons] = useState<Person[]>([]);
  const [bpCompanies, setBpCompanies] = useState<BpCompany[]>([]);
  const [recipientBpCompanyId, setRecipientBpCompanyId] = useState('');
  const [bpUsers, setBpUsers] = useState<User[]>([]);
  const [recipientUserId, setRecipientUserId] = useState('');
  const [recipientEmail, setRecipientEmail] = useState('');
  const [ccEmails, setCcEmails] = useState<string[]>([]);
  const [ccInput, setCcInput] = useState('');
  /** fromProposal prefill 시 카테고리/역할 필터 — dropdown 후보를 그것만 표시. */
  const [filterCategory, setFilterCategory] = useState<string | null>(null);
  const [filterRole, setFilterRole] = useState<string | null>(null);
  const [dailyRate, setDailyRate] = useState('');
  const [monthlyRate, setMonthlyRate] = useState('');
  const [periodStart, setPeriodStart] = useState('');
  const [periodEnd, setPeriodEnd] = useState('');
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(null);

  useEffect(() => {
    if (!user?.company_id) return;
    api.get<Equipment[]>('/api/equipment').then((r) =>
      setEquipments(r.data.filter((e: any) => e.supplier_id === user.company_id)));
    api.get<any>('/api/persons?size=200').then((r) => {
      const list = Array.isArray(r.data) ? r.data : (r.data.content ?? []);
      setPersons(list.filter((p: any) => p.supplier_id === user.company_id));
    });
    // 공급사도 BP 회사 목록 조회 가능 (공개 엔드포인트)
    api.get<BpCompany[]>('/api/companies/bp-list')
      .then((r) => setBpCompanies(r.data))
      .catch(() => setBpCompanies([]));
  }, [user]);

  // 선택된 BP 회사의 담당자 목록 fetch
  useEffect(() => {
    if (!recipientBpCompanyId) { setBpUsers([]); return; }
    api.get<User[]>(`/api/companies/${recipientBpCompanyId}/bp-users`)
      .then((r) => setBpUsers(r.data))
      .catch(() => setBpUsers([]));
  }, [recipientBpCompanyId]);

  // 선정 제안에서 정식 견적서 발송 — proposal 정보로 prefill
  useEffect(() => {
    if (!fromProposal) return;
    api.get<any[]>('/api/quotations/proposals/mine').then((r) => {
      const p = r.data.find((it) => String(it.id) === fromProposal);
      if (!p) return;
      if (p.equipment_id) {
        setResourceKind('equipment');
        setResourceId(String(p.equipment_id));
        if (p.request_equipment_category) setFilterCategory(p.request_equipment_category);
      } else if (p.person_id) {
        setResourceKind('person');
        setResourceId(String(p.person_id));
        if (p.request_manpower_role) setFilterRole(p.request_manpower_role);
      }
      if (p.daily_rate) setDailyRate(String(p.daily_rate));
      if (p.monthly_rate) setMonthlyRate(String(p.monthly_rate));
      if (p.note) setNote(p.note);
      if (p.request_work_period_start) setPeriodStart(p.request_work_period_start);
      if (p.request_work_period_end) setPeriodEnd(p.request_work_period_end);
      if (p.request_bp_company_id) {
        setMode('REGISTERED_BP');
        setRecipientBpCompanyId(String(p.request_bp_company_id));
      }
      if (p.request_requested_by_user_id) {
        setRecipientUserId(String(p.request_requested_by_user_id));
      }
    }).catch(() => {});
  }, [fromProposal]);

  function addCc() {
    const v = ccInput.trim().replace(/,$/, '');
    if (!v) return;
    if (!v.includes('@')) { setError('참조 이메일 형식이 올바르지 않습니다'); return; }
    if (ccEmails.includes(v)) { setCcInput(''); return; }
    setCcEmails((prev) => [...prev, v]);
    setCcInput('');
  }

  async function submit() {
    setError(null);
    setResult(null);
    if (!resourceId) { setError('자원 선택 필수'); return; }
    if (mode === 'REGISTERED_BP' && !recipientUserId) { setError('수신 BP 사용자 필수'); return; }
    if (mode === 'EMAIL' && !recipientEmail.trim()) { setError('이메일 필수'); return; }
    setSubmitting(true);
    try {
      const res = await api.post('/api/outgoing-quotations', {
        equipment_id: resourceKind === 'equipment' ? Number(resourceId) : null,
        person_id: resourceKind === 'person' ? Number(resourceId) : null,
        daily_rate: dailyRate ? Number(dailyRate) : null,
        monthly_rate: monthlyRate ? Number(monthlyRate) : null,
        note: note || null,
        period_start: periodStart || null,
        period_end: periodEnd || null,
        mode,
        recipient_user_id: mode === 'REGISTERED_BP' ? Number(recipientUserId) : null,
        recipient_email: mode === 'EMAIL' ? recipientEmail.trim() : null,
        cc_emails: mode === 'EMAIL' && ccEmails.length > 0 ? ccEmails : null,
      });
      setResult(res.data?.mail_sent
        ? '발송 완료 — 메일이 전송되었습니다.'
        : `기록은 저장됐으나 메일 발송 실패: ${res.data?.mail_error ?? '알 수 없는 오류'}`);
    } catch (e: any) {
      setError(e?.response?.data?.message || '발송 실패');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AppShell breadcrumb={[{ label: '내 견적', to: '/outgoing-quotations' }, { label: '새 견적 발송' }]}>
      <div className="max-w-2xl mx-auto px-6 py-8 space-y-5">
        <div>
          <h1 className="text-2xl font-bold">공급사 영업 견적 발송</h1>
          <p className="text-sm text-slate-500 mt-1">내 보유 자원 + 단가로 BP 회사에 견적서 (PDF) 발송.</p>
        </div>

        <div className="card p-6 space-y-4">
          <div>
            <span className="text-xs text-slate-600 font-medium">자원 종류</span>
            <div className="flex gap-2 mt-1">
              <button onClick={() => { setResourceKind('equipment'); setResourceId(''); }}
                      className={`px-3 py-1.5 rounded ${resourceKind === 'equipment' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'}`}>
                장비
              </button>
              <button onClick={() => { setResourceKind('person'); setResourceId(''); }}
                      className={`px-3 py-1.5 rounded ${resourceKind === 'person' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'}`}>
                인원
              </button>
            </div>
          </div>

          <label className="block">
            <span className="text-xs text-slate-600 font-medium">
              {resourceKind === 'equipment' ? '장비 선택' : '인원 선택'}
              {filterCategory && (
                <span className="ml-2 text-[10px] text-slate-500">
                  카테고리 고정: {EQUIPMENT_CATEGORY_LABEL[filterCategory as EquipmentCategory] ?? filterCategory}
                </span>
              )}
              {filterRole && (
                <span className="ml-2 text-[10px] text-slate-500">
                  역할 고정: {PERSON_ROLE_LABEL[filterRole as PersonRole] ?? filterRole}
                </span>
              )}
            </span>
            <select className="input mt-1" value={resourceId} onChange={(e) => setResourceId(e.target.value)}>
              <option value="">선택</option>
              {resourceKind === 'equipment'
                ? equipments
                    .filter((e) => !filterCategory || e.category === filterCategory)
                    .map((e) => (
                      <option key={e.id} value={e.id}>
                        {e.vehicle_no || e.model || `장비 #${e.id}`} ({EQUIPMENT_CATEGORY_LABEL[e.category as EquipmentCategory] ?? e.category})
                      </option>
                    ))
                : persons
                    .filter((p) => !filterRole || (p.roles ?? []).includes(filterRole))
                    .map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} ({(p.roles ?? []).map((r) => PERSON_ROLE_LABEL[r as PersonRole] ?? r).join(', ')})
                      </option>
                    ))}
            </select>
          </label>

          <div className="grid grid-cols-2 gap-3">
            <label className="block">
              <span className="text-xs text-slate-600 font-medium">일대 (원)</span>
              <MoneyInput className="input mt-1" value={dailyRate}
                onChange={(v) => setDailyRate(v === '' ? '' : String(v))} />
            </label>
            <label className="block">
              <span className="text-xs text-slate-600 font-medium">월대 (원)</span>
              <MoneyInput className="input mt-1" value={monthlyRate}
                onChange={(v) => setMonthlyRate(v === '' ? '' : String(v))} />
            </label>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <label className="block">
              <span className="text-xs text-slate-600 font-medium">가용 시작일 (선택)</span>
              <input type="date" className="input mt-1" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)} />
            </label>
            <label className="block">
              <span className="text-xs text-slate-600 font-medium">가용 종료일 (선택)</span>
              <input type="date" className="input mt-1" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)} />
            </label>
          </div>

          <label className="block">
            <span className="text-xs text-slate-600 font-medium">메모</span>
            <textarea rows={2} className="input mt-1" value={note} onChange={(e) => setNote(e.target.value)} />
          </label>

          <div className="pt-3 border-t border-slate-200">
            <span className="text-xs text-slate-600 font-medium">수신 방식</span>
            <div className="flex gap-2 mt-1">
              <button onClick={() => setMode('REGISTERED_BP')}
                      className={`px-3 py-1.5 rounded ${mode === 'REGISTERED_BP' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'}`}>
                등록된 BP
              </button>
              <button onClick={() => setMode('EMAIL')}
                      className={`px-3 py-1.5 rounded ${mode === 'EMAIL' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-700'}`}>
                외부 이메일
              </button>
            </div>
          </div>

          {mode === 'REGISTERED_BP' ? (
            <div className={fromProposal ? 'block' : 'grid grid-cols-2 gap-3'}>
              <label className="block">
                <span className="text-xs text-slate-600 font-medium">BP 회사</span>
                <select className="input mt-1" value={recipientBpCompanyId}
                  onChange={(e) => { setRecipientBpCompanyId(e.target.value); setRecipientUserId(''); }}
                  disabled={!!fromProposal}>
                  <option value="">선택</option>
                  {bpCompanies.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </label>
              {!fromProposal && (
                <label className="block">
                  <span className="text-xs text-slate-600 font-medium">BP 담당자</span>
                  <select className="input mt-1" value={recipientUserId}
                    onChange={(e) => setRecipientUserId(e.target.value)}
                    disabled={!recipientBpCompanyId}>
                    <option value="">{recipientBpCompanyId ? '선택' : '먼저 회사 선택'}</option>
                    {bpUsers.map((u) => (
                      <option key={u.id} value={u.id}>{u.name} ({u.email})</option>
                    ))}
                  </select>
                </label>
              )}
            </div>
          ) : (
            <div className="space-y-3">
              <label className="block">
                <span className="text-xs text-slate-600 font-medium">받는 이메일</span>
                <input type="email" className="input mt-1" value={recipientEmail}
                       onChange={(e) => setRecipientEmail(e.target.value)} placeholder="bp@example.com" />
              </label>
              <div>
                <span className="text-xs text-slate-600 font-medium">참조(CC) — 선택</span>
                <div className="flex gap-2 mt-1">
                  <input type="email" className="input flex-1" value={ccInput}
                         onChange={(e) => setCcInput(e.target.value)}
                         onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); addCc(); } }}
                         placeholder="참조 이메일 (Enter 로 추가)" />
                  <button type="button" onClick={addCc}
                          className="px-3 rounded-lg bg-slate-100 hover:bg-slate-200 text-sm font-semibold text-slate-700">추가</button>
                </div>
                {ccEmails.length > 0 && (
                  <div className="flex flex-wrap gap-1.5 pt-1.5">
                    {ccEmails.map((e) => (
                      <span key={e} className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-slate-100 text-slate-700 text-xs font-medium">
                        {e}
                        <button type="button" onClick={() => setCcEmails((prev) => prev.filter((x) => x !== e))}
                                className="text-slate-400 hover:text-slate-700">✕</button>
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {error && <div className="text-sm text-rose-600">{error}</div>}
          {result && <div className="text-sm text-emerald-600">{result}</div>}
          <div className="flex justify-end gap-2 pt-2">
            <button onClick={() => navigate('/outgoing-quotations')} className="btn-ghost">취소</button>
            <button onClick={submit} disabled={submitting} className="btn-primary">
              {submitting ? '발송 중…' : '견적 발송'}
            </button>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
