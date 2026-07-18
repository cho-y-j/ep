import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import PageContainer from '../../components/ui/PageContainer';

type CompanyRow = { id: number; name: string; type?: string | null };
type PersonRow = { id: number; name: string; supplierName?: string | null };
type SiteRow = { id: number; name: string };

export default function AdminAnnouncementsPage() {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [siteId, setSiteId] = useState<string>('');
  const [supplierId, setSupplierId] = useState<string>('');
  const [selectedPersonIds, setSelectedPersonIds] = useState<number[]>([]);
  const [target, setTarget] = useState<'phone' | 'all'>('phone');
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<{ attempted: number; targets: number; phone_sent?: number; watch_sent?: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [companies, setCompanies] = useState<CompanyRow[]>([]);
  const [persons, setPersons] = useState<PersonRow[]>([]);
  const [sites, setSites] = useState<SiteRow[]>([]);
  const [loadingMeta, setLoadingMeta] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.get<CompanyRow[]>('/api/companies').catch(() => ({ data: [] as CompanyRow[] })),
      api.get<any>('/api/persons?size=500').catch(() => ({ data: { content: [] } })),
      api.get<SiteRow[]>('/api/sites').catch(() => ({ data: [] as SiteRow[] })),
    ]).then(([cs, ps, ss]) => {
      if (cancelled) return;
      const list = Array.isArray(ps.data) ? ps.data : (ps.data?.content ?? []);
      setCompanies(cs.data ?? []);
      setPersons(list.map((p: any) => ({ id: p.id, name: p.name, supplierName: p.supplierName ?? p.supplier_name })));
      setSites(Array.isArray(ss.data) ? ss.data : []);
    }).finally(() => { if (!cancelled) setLoadingMeta(false); });
    return () => { cancelled = true; };
  }, []);

  // 공급사 선택 시 그 공급사 소속 인원만 필터해서 보여줌. 없으면 전체.
  const personsFiltered = supplierId
    ? persons.filter((p) => {
        const co = companies.find((c) => String(c.id) === supplierId);
        return co && p.supplierName === co.name;
      })
    : persons;

  const togglePerson = (id: number) => {
    setSelectedPersonIds((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);
  };

  const send = async () => {
    if (!title.trim() || !body.trim()) {
      setError('제목과 내용은 필수입니다');
      return;
    }
    setSending(true);
    setError(null);
    setResult(null);
    try {
      const payload: Record<string, unknown> = { title: title.trim(), body: body.trim(), target };
      if (siteId) payload.site_id = Number(siteId);
      if (supplierId) payload.supplier_id = Number(supplierId);
      if (selectedPersonIds.length > 0) payload.person_ids = selectedPersonIds;
      const res = await api.post('/api/announcements/broadcast', payload);
      setResult(res.data);
    } catch (e) {
      const err = e as AxiosError<{ message?: string }>;
      setError(err.response?.data?.message ?? err.message ?? '발송 실패');
    } finally {
      setSending(false);
    }
  };

  return (
    <AppShell>
      <PageContainer>
        <h1 className="text-xl font-semibold text-slate-900 mb-1">공지사항 발송</h1>
        <p className="text-sm text-slate-500 mb-6">현장 작업자 폰/워치로 푸시 알림 발송</p>
        <div className="space-y-4 max-w-2xl">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">제목</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={64}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="예: 안전점검 일정 변경"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">내용</label>
            <textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              maxLength={500}
              rows={4}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              placeholder="자세한 내용을 입력하세요"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">현장 (선택 — 안전 상황판 확인율 집계)</label>
            <select
              value={siteId}
              onChange={(e) => setSiteId(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm bg-white"
              disabled={loadingMeta}
            >
              <option value="">현장 미지정</option>
              {sites.map((st) => (
                <option key={st.id} value={st.id}>{st.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">공급사 (선택)</label>
            <select
              value={supplierId}
              onChange={(e) => { setSupplierId(e.target.value); setSelectedPersonIds([]); }}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm bg-white"
              disabled={loadingMeta}
            >
              <option value="">전체 (선택 없음)</option>
              {companies.map((c) => (
                <option key={c.id} value={c.id}>{c.name}{c.type ? ` (${c.type})` : ''}</option>
              ))}
            </select>
          </div>

          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="block text-xs font-semibold text-slate-700">
                인원 선택 (선택 — {selectedPersonIds.length}명)
              </label>
              <div className="text-xs text-slate-500">
                <button type="button" onClick={() => setSelectedPersonIds(personsFiltered.map((p) => p.id))}
                  className="underline hover:text-slate-700 mr-2">전체 선택</button>
                <button type="button" onClick={() => setSelectedPersonIds([])}
                  className="underline hover:text-slate-700">선택 해제</button>
              </div>
            </div>
            <div className="rounded-md border border-slate-300 bg-white max-h-48 overflow-y-auto p-2">
              {loadingMeta && <div className="text-sm text-slate-500 px-2 py-1">불러오는 중…</div>}
              {!loadingMeta && personsFiltered.length === 0 && (
                <div className="text-sm text-slate-500 px-2 py-1">표시할 인원이 없습니다.</div>
              )}
              {personsFiltered.map((p) => (
                <label key={p.id} className="flex items-center gap-2 px-2 py-1 hover:bg-slate-50 rounded cursor-pointer text-sm">
                  <input
                    type="checkbox"
                    checked={selectedPersonIds.includes(p.id)}
                    onChange={() => togglePerson(p.id)}
                  />
                  <span className="text-slate-900">{p.name}</span>
                  {p.supplierName && <span className="text-xs text-slate-500">· {p.supplierName}</span>}
                </label>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">발송 대상 기기</label>
            <div className="flex gap-4 text-sm">
              <label className="inline-flex items-center gap-2">
                <input type="radio" name="target" value="phone"
                  checked={target === 'phone'} onChange={() => setTarget('phone')} />
                폰만
              </label>
              <label className="inline-flex items-center gap-2">
                <input type="radio" name="target" value="all"
                  checked={target === 'all'} onChange={() => setTarget('all')} />
                폰 + 워치
              </label>
            </div>
          </div>

          <div className="rounded-md bg-blue-50 border border-blue-200 p-3 text-xs text-blue-800">
            · 인원을 선택하면 선택한 사람들만, 공급사만 선택하면 그 공급사 전체, 둘 다 비우면 fcm_token 등록된 전원에게 발송됩니다.
            <br />
            · Firebase admin key가 서버에 설치되어 있어야 실제 푸시가 도착합니다.
          </div>
          <button
            onClick={send}
            disabled={sending}
            className="rounded-md bg-blue-600 px-6 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {sending ? '발송 중...' : '공지 발송'}
          </button>
          {result && (
            <div className="rounded-md bg-emerald-50 border border-emerald-200 p-3 text-xs text-emerald-800">
              발송 시도 {result.attempted}건 / 대상 {result.targets}명
              {result.phone_sent != null && ` (폰 ${result.phone_sent}, 워치 ${result.watch_sent ?? 0})`}
            </div>
          )}
          {error && (
            <div className="rounded-md bg-red-50 border border-red-200 p-3 text-xs text-red-800">
              {error}
            </div>
          )}
        </div>
      </PageContainer>
    </AppShell>
  );
}
