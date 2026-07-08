import { useEffect, useMemo, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { formatOwnerSubLabel } from '../../lib/format';
import { toast } from '../../lib/toast';

type DocRow = {
  id: number;
  owner_type: 'EQUIPMENT' | 'PERSON' | 'COMPANY';
  owner_id: number;
  owner_name?: string | null;
  owner_sub_label?: string | null;
};

type ResourceGroup = {
  key: string;
  owner_type: 'EQUIPMENT' | 'PERSON';
  owner_id: number;
  owner_name: string;
  owner_sub_label: string | null;
  docCount: number;
};

export default function DocumentReviewSendPage() {
  const [rows, setRows] = useState<DocRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [emails, setEmails] = useState<string[]>([]);
  const [emailInput, setEmailInput] = useState('');
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  // BP사 담당자에서 수신 이메일 추가
  const [bpCompanies, setBpCompanies] = useState<Array<{ id: number; name: string }>>([]);
  const [bpId, setBpId] = useState('');
  const [bpUsers, setBpUsers] = useState<Array<{ id: number; name: string; email: string }>>([]);

  useEffect(() => {
    api.get<DocRow[]>('/api/documents/my-supplier')
      .then((r) => setRows(r.data))
      .catch(() => setRows([]))
      .finally(() => setLoading(false));
    api.get<Array<{ id: number; name: string }>>('/api/companies/bp-list')
      .then((r) => setBpCompanies(r.data))
      .catch(() => setBpCompanies([]));
  }, []);

  useEffect(() => {
    if (!bpId) { setBpUsers([]); return; }
    api.get<Array<{ id: number; name: string; email: string }>>(`/api/companies/${bpId}/bp-users`)
      .then((r) => setBpUsers(r.data))
      .catch(() => setBpUsers([]));
  }, [bpId]);

  // 장비/인원만 자원 단위로 그룹핑 (회사 서류는 제외). my-supplier 는 서류 있는 자원만 반환.
  const groups: ResourceGroup[] = useMemo(() => {
    const m = new Map<string, ResourceGroup>();
    for (const r of rows) {
      if (r.owner_type !== 'EQUIPMENT' && r.owner_type !== 'PERSON') continue;
      const key = `${r.owner_type}:${r.owner_id}`;
      const g = m.get(key);
      if (g) g.docCount++;
      else m.set(key, {
        key, owner_type: r.owner_type, owner_id: r.owner_id,
        owner_name: r.owner_name ?? `#${r.owner_id}`,
        owner_sub_label: r.owner_sub_label ?? null, docCount: 1,
      });
    }
    return Array.from(m.values());
  }, [rows]);

  const selectedCount = selected.size;
  const selectedDocs = useMemo(
    () => groups.filter((g) => selected.has(g.key)).reduce((s, g) => s + g.docCount, 0),
    [groups, selected],
  );

  function toggle(key: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }
  function toggleAll() {
    setSelected((prev) => prev.size === groups.length ? new Set() : new Set(groups.map((g) => g.key)));
  }

  function pushEmail(raw: string) {
    const v = raw.trim().replace(/,$/, '');
    if (!v) return;
    if (!v.includes('@')) { toast.error('올바른 이메일 형식이 아닙니다'); return; }
    setEmails((prev) => (prev.includes(v) ? prev : [...prev, v]));
  }
  function addEmail() {
    pushEmail(emailInput);
    setEmailInput('');
  }
  function removeEmail(e: string) {
    setEmails((prev) => prev.filter((x) => x !== e));
  }

  async function send() {
    if (emails.length === 0 && !bpId) { toast.error('받는 사람 이메일 또는 BP사를 선택하세요'); return; }
    const eqIds: number[] = [], pIds: number[] = [];
    for (const g of groups) {
      if (!selected.has(g.key)) continue;
      if (g.owner_type === 'EQUIPMENT') eqIds.push(g.owner_id); else pIds.push(g.owner_id);
    }
    if (eqIds.length + pIds.length === 0) { toast.error('보낼 자원을 선택하세요'); return; }
    setSending(true);
    try {
      const res = await api.post('/api/documents/review-mail', {
        emails,
        equipment_ids: eqIds,
        person_ids: pIds,
        message: message.trim() || undefined,
        bp_company_id: bpId ? Number(bpId) : undefined,
      }, { timeout: 120_000 }); // 서류 zip 생성 + 메일 첨부 발송이라 전역 10초로는 부족
      const d = res.data as { recipients: number; resources: number; total_docs: number; bp_delivered: boolean };
      const parts: string[] = [];
      if (d.recipients > 0) parts.push(`이메일 ${d.recipients}명`);
      if (d.bp_delivered) parts.push('BP사 계정');
      toast.success(`발송 완료 (${parts.join(' + ')}) — 자원 ${d.resources}건 / 서류 ${d.total_docs}건`);
      setSelected(new Set());
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '발송 실패');
    } finally {
      setSending(false);
    }
  }

  return (
    <AppShell breadcrumb={[{ label: '서류 심사 보내기' }]}>
      <div className="max-w-4xl mx-auto px-6 py-8 space-y-5">
        <div>
          <h1 className="text-2xl font-bold">서류 심사 보내기</h1>
          <p className="text-sm text-slate-500 mt-1">
            보낼 자원(장비·인원)을 고르면 각 자원의 서류를 자원별 압축파일(zip)로 묶어 입력한 이메일로 발송합니다.
            받는 분이 시스템에 가입되어 있지 않아도 됩니다.
          </p>
        </div>

        {/* 받는 사람 */}
        <section className="card p-4 space-y-2">
          <div className="text-sm font-semibold text-slate-700">받는 사람 이메일</div>
          <div className="flex gap-2">
            <input
              type="email"
              value={emailInput}
              onChange={(e) => setEmailInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); addEmail(); } }}
              placeholder="example@company.com  (Enter 로 추가)"
              className="input flex-1"
            />
            <button type="button" onClick={addEmail}
                    className="px-3 py-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-sm font-semibold text-slate-700">
              추가
            </button>
          </div>
          {emails.length > 0 && (
            <div className="flex flex-wrap gap-1.5 pt-1">
              {emails.map((e) => (
                <span key={e} className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-brand-50 text-brand-700 text-xs font-medium">
                  {e}
                  <button type="button" onClick={() => removeEmail(e)} className="text-brand-400 hover:text-brand-700">✕</button>
                </span>
              ))}
            </div>
          )}

          {/* BP사 계정으로 전송 + 담당자 이메일 추가 */}
          <div className="pt-2 border-t border-slate-100">
            <div className="text-xs font-medium text-slate-500 mb-1">BP사 선택 (계정 수신함으로 전송)</div>
            <div className="flex gap-2">
              <select className="input flex-1" value={bpId} onChange={(e) => setBpId(e.target.value)}>
                <option value="">BP사 선택 안 함</option>
                {bpCompanies.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
              {bpUsers.length > 0 && (
                <button type="button" onClick={() => bpUsers.forEach((u) => pushEmail(u.email))}
                        className="px-3 rounded-lg bg-slate-100 hover:bg-slate-200 text-sm font-semibold text-slate-700 whitespace-nowrap">
                  이메일도 추가
                </button>
              )}
            </div>
            {bpId && (
              <div className="text-xs text-emerald-600 pt-1.5">
                선택한 BP사 계정의 "받은 서류 심사"에 등록됩니다. (이메일은 위 목록에 추가된 경우에만 별도 발송)
              </div>
            )}
            {bpId && bpUsers.length === 0 && (
              <div className="text-xs text-slate-400 pt-1.5">이 BP사에 등록된 담당자가 없습니다.</div>
            )}
            {bpUsers.length > 0 && (
              <div className="flex flex-wrap gap-1.5 pt-1.5">
                {bpUsers.map((u) => (
                  <button type="button" key={u.id} onClick={() => pushEmail(u.email)}
                          className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md border border-slate-200 hover:border-brand-400 text-xs text-slate-700">
                    + {u.name} <span className="text-slate-400">({u.email})</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </section>

        {/* 메모 */}
        <section className="card p-4 space-y-2">
          <div className="text-sm font-semibold text-slate-700">메모 (선택)</div>
          <textarea
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            rows={3}
            placeholder="예: 요청하신 장비 서류입니다. 검토 후 회신 부탁드립니다."
            className="input resize-y w-full"
          />
        </section>

        {/* 자원 선택 */}
        <section className="card p-0 overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200 bg-slate-50">
            <div className="text-sm font-semibold text-slate-700">보낼 자원 선택</div>
            {groups.length > 0 && (
              <button type="button" onClick={toggleAll}
                      className="text-xs font-semibold text-slate-500 hover:text-slate-900">
                {selected.size === groups.length ? '전체 해제' : '전체 선택'}
              </button>
            )}
          </div>
          {loading ? (
            <div className="p-8 text-center text-sm text-slate-400">불러오는 중…</div>
          ) : groups.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-400">보낼 서류가 있는 자원이 없습니다.</div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 p-4">
              {groups.map((g) => {
                const sub = formatOwnerSubLabel(g.owner_type, g.owner_sub_label);
                const on = selected.has(g.key);
                const typeCls = g.owner_type === 'EQUIPMENT' ? 'bg-emerald-100 text-emerald-700' : 'bg-blue-100 text-blue-700';
                return (
                  <button
                    type="button"
                    key={g.key}
                    onClick={() => toggle(g.key)}
                    className={`text-left rounded-xl border p-4 shadow-sm transition ${
                      on ? 'border-brand-500 ring-2 ring-brand-200 bg-brand-50/40'
                         : 'border-slate-200 bg-white hover:shadow-md hover:border-slate-300'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${typeCls}`}>
                        {sub ?? (g.owner_type === 'EQUIPMENT' ? '장비' : '인원')}
                      </span>
                      <span className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 ${
                        on ? 'border-brand-500 bg-brand-500 text-white' : 'border-slate-300 text-transparent'
                      }`}>
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
                      </span>
                    </div>
                    <div className="mt-2 font-bold text-slate-900 truncate">{g.owner_name}</div>
                    <div className="mt-1 text-xs text-slate-500">서류 {g.docCount}건</div>
                  </button>
                );
              })}
            </div>
          )}
        </section>

        {/* 발송 */}
        <div className="flex items-center justify-between gap-3">
          <div className="text-sm text-slate-500">
            선택: <b className="text-slate-900">{selectedCount}</b>개 자원 · 서류 <b className="text-slate-900">{selectedDocs}</b>건
          </div>
          <button type="button" onClick={send} disabled={sending || selectedCount === 0 || (emails.length === 0 && !bpId)}
                  className="px-5 py-2.5 rounded-lg bg-brand-600 text-white font-semibold hover:bg-brand-700 disabled:opacity-50">
            {sending ? '발송 중…' : (emails.length === 0 && bpId ? 'BP사 계정으로 보내기' : '보내기')}
          </button>
        </div>
      </div>
    </AppShell>
  );
}
