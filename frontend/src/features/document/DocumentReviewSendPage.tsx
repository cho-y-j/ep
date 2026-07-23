import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';
import { formatOwnerSubLabel } from '../../lib/format';
import { toast } from '../../lib/toast';
import { useAuth } from '../auth/AuthContext';

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
  const { user } = useAuth();
  // 프리필 — 자원 현황 세트 보드의 "심사 보내기"에서 ?equipment=<id>(장비묶음) 또는 ?person=<id>(zip)로 진입.
  const [searchParams] = useSearchParams();
  const prefillEquipment = searchParams.get('equipment');
  const prefillPerson = searchParams.get('person');
  const prefillApplied = useRef(false);
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
  // 출력형식 다중 선택: bundle(장비묶음 병합 PDF, 기본) + zip(자원별 압축) — 둘 다 켜면 한 메일에 함께 첨부.
  // 인원 프리필은 병합 PDF(장비 앵커) 대상이 아니라 zip 만.
  const [formats, setFormats] = useState<Set<'zip' | 'bundle'>>(
    () => new Set([prefillPerson ? 'zip' : 'bundle'] as const),
  );
  // 자원 선택 UI 모드 — 병합 PDF 가 켜져 있으면 장비묶음 피커(조종원 개별 선택 포함), zip 만이면 기존 자원 카드.
  const mode: 'zip' | 'bundle' = formats.has('bundle') ? 'bundle' : 'zip';
  const [operators, setOperators] = useState<Record<number, Array<{ person_id: number; person_name?: string | null }>>>({});
  const [opSel, setOpSel] = useState<Record<number, Set<number>>>({});
  const [separatorPage, setSeparatorPage] = useState(true);
  // 클라이언트 검색·필터 — 차량번호/이름 + 종류(owner_sub_label). SupplierInboxPage 패턴.
  const [q, setQ] = useState('');
  const [subFilter, setSubFilter] = useState('');

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

  // 장비묶음 모드에서 보이는 자원은 장비뿐(그 교대조 조종원은 카드 안에서 개별 선택).
  const visibleGroups = useMemo(
    () => (mode === 'bundle' ? groups.filter((g) => g.owner_type === 'EQUIPMENT') : groups),
    [groups, mode],
  );

  // 프리필 자원 자동 선택 — 목록 로드 후 1회만(해당 자원에 보낼 서류가 있을 때).
  useEffect(() => {
    if (prefillApplied.current || groups.length === 0) return;
    prefillApplied.current = true;
    const key = prefillEquipment ? `EQUIPMENT:${prefillEquipment}`
      : prefillPerson ? `PERSON:${prefillPerson}` : null;
    if (key && groups.some((g) => g.key === key)) setSelected(new Set([key]));
  }, [groups, prefillEquipment, prefillPerson]);

  // 종류 필터 옵션 — 현재 보이는 자원의 owner_sub_label(장비 카테고리/인력 역할)을 distinct.
  const subOptions = useMemo(() => {
    const m = new Map<string, string>();
    for (const g of visibleGroups) {
      const label = formatOwnerSubLabel(g.owner_type, g.owner_sub_label);
      if (g.owner_sub_label && label) m.set(g.owner_sub_label, label);
    }
    return Array.from(m.entries()).map(([value, label]) => ({ value, label }));
  }, [visibleGroups]);

  const qLower = q.trim().toLowerCase();
  const filteredGroups = useMemo(() => visibleGroups.filter((g) => {
    if (subFilter && g.owner_sub_label !== subFilter) return false;
    if (qLower) {
      const sub = formatOwnerSubLabel(g.owner_type, g.owner_sub_label) ?? '';
      if (!`${g.owner_name} ${sub}`.toLowerCase().includes(qLower)) return false;
    }
    return true;
  }), [visibleGroups, subFilter, qLower]);
  const activeFilterCount = [q, subFilter].filter(Boolean).length;

  // 장비묶음 모드 — 장비 그룹의 교대조 조종원을 배치 1회로 로드(개별 체크용).
  useEffect(() => {
    if (mode !== 'bundle') return;
    const eqIds = groups.filter((g) => g.owner_type === 'EQUIPMENT').map((g) => g.owner_id);
    if (eqIds.length === 0) { setOperators({}); return; }
    api.post<{ results: Array<{ equipment_id: number; operators: Array<{ person_id: number; person_name?: string | null }> }> }>(
      '/api/equipment/default-operators', { equipment_ids: eqIds })
      .then((r) => {
        const map: Record<number, Array<{ person_id: number; person_name?: string | null }>> = {};
        r.data.results.forEach((res) => { map[res.equipment_id] = res.operators; });
        setOperators(map);
      })
      .catch(() => setOperators({}));
  }, [mode, groups]);

  const selectedCount = selected.size;
  const selectedDocs = useMemo(
    () => visibleGroups.filter((g) => selected.has(g.key)).reduce((s, g) => s + g.docCount, 0),
    [visibleGroups, selected],
  );

  const opIds = (eqId: number) => (operators[eqId] ?? []).map((o) => o.person_id);
  const isOpOn = (eqId: number, pid: number) => { const s = opSel[eqId]; return s ? s.has(pid) : true; };
  function toggleOp(eqId: number, pid: number) {
    setOpSel((prev) => {
      const cur = prev[eqId] ?? new Set(opIds(eqId));
      const next = new Set(cur);
      if (next.has(pid)) next.delete(pid); else next.add(pid);
      return { ...prev, [eqId]: next };
    });
  }
  function toggleFormat(f: 'zip' | 'bundle') {
    const next = new Set(formats);
    if (next.has(f)) {
      if (next.size === 1) { toast.error('출력 형식을 최소 1개 선택하세요'); return; }
      next.delete(f);
    } else {
      next.add(f);
    }
    // 피커 모드(장비묶음 ↔ 자원 카드)가 바뀌면 선택 초기화 — 기존 switchMode 동작 유지.
    const after: 'zip' | 'bundle' = next.has('bundle') ? 'bundle' : 'zip';
    if (after !== mode) {
      setSelected(new Set());
      setOpSel({});
    }
    setFormats(next);
  }

  function toggle(key: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }
  // 필터 적용된 목록 기준 전체 선택/해제 — 필터 밖 기존 선택은 유지.
  const allFilteredSelected = filteredGroups.length > 0 && filteredGroups.every((g) => selected.has(g.key));
  function toggleAll() {
    setSelected((prev) => {
      const next = new Set(prev);
      filteredGroups.forEach((g) => { if (allFilteredSelected) next.delete(g.key); else next.add(g.key); });
      return next;
    });
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

  async function sendBundle() {
    const eqGroups = visibleGroups.filter((g) => selected.has(g.key));
    if (eqGroups.length === 0) { toast.error('보낼 장비를 선택하세요'); return; }
    const bundles = eqGroups.map((g) => ({
      equipment_id: g.owner_id,
      operator_person_ids: opSel[g.owner_id] ? [...opSel[g.owner_id]] : opIds(g.owner_id),
    }));
    setSending(true);
    try {
      const res = await api.post('/api/documents/review-bundle-pdf', {
        bundles,
        emails,
        message: message.trim() || undefined,
        bp_company_id: bpId ? Number(bpId) : undefined,
        separator_page: separatorPage,
        include_zip: formats.has('zip'), // 둘 다 선택 시 같은 메일에 자원별 ZIP 도 첨부
      }, { timeout: 120_000 }); // 서류 병합 PDF 생성 + 메일 첨부라 전역 10초로는 부족
      const d = res.data as { bundles: number; recipients: number; bp_delivered: boolean; total_docs: number; skipped_empty: number };
      const parts: string[] = [];
      if (d.recipients > 0) parts.push(`이메일 ${d.recipients}명`);
      if (d.bp_delivered) parts.push('BP사 계정');
      let msg = `발송 완료 (${parts.join(' + ')}) — 묶음 ${d.bundles}건 / 서류 ${d.total_docs}건`;
      if (d.skipped_empty > 0) msg += ` · 서류없음 ${d.skipped_empty}건 제외`;
      toast.success(msg);
      setSelected(new Set());
      setOpSel({});
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '발송 실패');
    } finally {
      setSending(false);
    }
  }

  async function send() {
    if (emails.length === 0 && !bpId) { toast.error('받는 사람 이메일 또는 BP사를 선택하세요'); return; }
    if (formats.has('bundle')) { await sendBundle(); return; }
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
        <PageHeader
          title="서류 심사 보내기"
          subtitle={mode === 'bundle'
            ? '장비를 고르면 그 교대조 조종원 서류까지 하나의 PDF로 병합해 발송합니다. 조종원은 개별 선택할 수 있습니다.'
            : '보낼 자원(장비·인원)을 고르면 각 자원의 서류를 자원별 압축파일(zip)로 묶어 입력한 이메일로 발송합니다. 받는 분이 시스템에 가입되어 있지 않아도 됩니다.'}
        />

        {/* 출력형식 — 다중 선택(둘 다 켜면 한 메일에 병합 PDF + ZIP 함께 첨부). 기본 = 장비묶음 병합 PDF. */}
        <section className="card p-4 space-y-3">
          <div className="text-sm font-semibold text-slate-700">출력 형식 <span className="font-normal text-xs text-slate-400">(복수 선택 가능)</span></div>
          <div className="inline-flex overflow-hidden rounded-lg border border-slate-300">
            {([['bundle', '장비묶음 병합 PDF'], ['zip', '자원별 압축(ZIP)']] as const).map(([m, label]) => (
              <button key={m} type="button" onClick={() => toggleFormat(m)}
                      className={`px-4 py-2 text-sm font-semibold ${formats.has(m) ? 'bg-brand-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>
                {formats.has(m) ? '✓ ' : ''}{label}
              </button>
            ))}
          </div>
          {formats.has('bundle') && formats.has('zip') && (
            <div className="text-xs text-slate-500">병합 PDF와 자원별 ZIP 을 한 메일에 함께 첨부해 발송합니다.</div>
          )}
          {mode === 'bundle' && (
            <label className="flex w-fit items-center gap-2 text-sm text-slate-600 cursor-pointer">
              <input type="checkbox" checked={separatorPage} onChange={(e) => setSeparatorPage(e.target.checked)}
                     className="h-4 w-4 accent-brand-600" />
              자원마다 구분면(이름) 페이지 넣기
            </label>
          )}
        </section>

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

          {/* Reply-To 안내 — 심사 메일은 발송자(로그인 이메일)로 답장이 오게 설정됨. */}
          <div className="pt-2 border-t border-slate-100 text-xs text-slate-500">
            <div>답장은 내 메일{user?.email ? <>(<b className="text-slate-700">{user.email}</b>)</> : null}로 와요.</div>
            <details className="mt-1">
              <summary className="cursor-pointer text-slate-400 hover:text-slate-600">답장 확인 방법 (네이버 메일 등)</summary>
              <div className="mt-1 space-y-0.5">
                <p>· 받는 분이 메일에서 '답장'을 누르면 회신이 내 로그인 이메일로 도착합니다.</p>
                <p>· 네이버 메일이면: 네이버 메일 → 받은메일함에서 회신을 확인하세요.</p>
                <p>· 시스템 발송 계정이 아니라 위 내 메일 주소로 회신됩니다.</p>
              </div>
            </details>
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
            <div className="text-sm font-semibold text-slate-700">{mode === 'bundle' ? '보낼 장비 선택' : '보낼 자원 선택'}</div>
            {filteredGroups.length > 0 && (
              <button type="button" onClick={toggleAll}
                      className="text-xs font-semibold text-slate-500 hover:text-slate-900">
                {allFilteredSelected ? '전체 해제' : '전체 선택'}
              </button>
            )}
          </div>
          {visibleGroups.length > 0 && (
            <div className="px-4 pt-3 -mb-3">
              <FilterBar
                search={{ value: q, onChange: setQ, placeholder: '차량번호·이름 검색' }}
                activeFilterCount={activeFilterCount}
                onReset={() => { setQ(''); setSubFilter(''); }}
              >
                <FilterSelect value={subFilter} onChange={setSubFilter} placeholder="종류 전체" options={subOptions} />
              </FilterBar>
            </div>
          )}
          {loading ? (
            <div className="p-8 text-center text-sm text-slate-400">불러오는 중…</div>
          ) : visibleGroups.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-400">
              {mode === 'bundle' ? '보낼 서류가 있는 장비가 없습니다.' : '보낼 서류가 있는 자원이 없습니다.'}
            </div>
          ) : filteredGroups.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-400">조건에 맞는 자원이 없습니다.</div>
          ) : mode === 'bundle' ? (
            <div className="space-y-2.5 p-4">
              {filteredGroups.map((g) => {
                const on = selected.has(g.key);
                const ops = operators[g.owner_id] ?? [];
                return (
                  <div key={g.key}
                       className={`rounded-xl border p-3 transition ${
                         on ? 'border-brand-500 ring-2 ring-brand-200 bg-brand-50/40' : 'border-slate-200 bg-white'
                       }`}>
                    <button type="button" onClick={() => toggle(g.key)}
                            className="flex w-full items-center justify-between gap-2 text-left">
                      <div className="min-w-0">
                        <div className="font-bold text-slate-900 truncate">{g.owner_name}</div>
                        <div className="mt-0.5 text-xs text-slate-500">
                          서류 {g.docCount}건{ops.length > 0 ? ` · 교대조 조종원 ${ops.length}명` : ''}
                        </div>
                      </div>
                      <span className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 ${
                        on ? 'border-brand-500 bg-brand-500 text-white' : 'border-slate-300 text-transparent'
                      }`}>
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
                      </span>
                    </button>
                    {on && ops.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1.5 border-t border-slate-100 pt-2">
                        {ops.map((op) => (
                          <label key={op.person_id} className="inline-flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer">
                            <input type="checkbox" checked={isOpOn(g.owner_id, op.person_id)}
                                   onChange={() => toggleOp(g.owner_id, op.person_id)}
                                   className="h-3.5 w-3.5 accent-brand-600" />
                            조종원 {op.person_name || `#${op.person_id}`}
                          </label>
                        ))}
                      </div>
                    )}
                    {on && ops.length === 0 && (
                      <div className="mt-2 border-t border-slate-100 pt-2 text-xs text-slate-400">
                        등록된 교대조 조종원이 없습니다 (장비 서류만 발송)
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 p-4">
              {filteredGroups.map((g) => {
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
            선택: <b className="text-slate-900">{selectedCount}</b>{mode === 'bundle' ? '개 장비' : '개 자원'} · 서류 <b className="text-slate-900">{selectedDocs}</b>건
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
