import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { api } from '../../../../lib/api';
import { useAuth } from '../../../auth/AuthContext';
import { SignaturePadDialog } from './SignaturePadDialog';

export interface SignaturePanelHandle {
  /** 입력된 이메일을 가진 4명에게 사인 요청 메일 일괄 발송. 이미 SIGNED 인 건 건너뜀.
   *  parent 가 save() 직후 호출하면 workPlanId state 가 아직 패널에 전파되지 않으므로
   *  override id 를 직접 넘길 수 있다. */
  sendAllPending: (overrideWorkPlanId?: number) => Promise<{ sent: number; skipped: number }>;
  /** 패널이 들고 있는 입력값을 그대로 반환 (parent 가 검증용으로 읽음). */
  getInputs: () => Record<string, { name: string; email: string }>;
}

export type SignatureRole = 'AUTHOR' | 'SUPERVISOR' | 'CONFIRMER' | 'REVIEWER' | 'APPROVER';
export type SignatureStatus = 'PENDING' | 'SIGNED' | 'EXPIRED' | 'INVALIDATED';

export interface SignatureItem {
  id: number;
  work_plan_id: number;
  role: SignatureRole;
  role_label: string;
  signer_name?: string | null;
  signer_email?: string | null;
  status: SignatureStatus;
  signed_at?: string | null;
  token_expires_at?: string | null;
  has_signature: boolean;
  signature_png_base64?: string | null;
}

interface SignaturePanelProps {
  workPlanId: number | null;
  /** 5개 사인 모두 완료 여부 — WorkPlanCreatePage 가 submit 게이트로 사용. */
  onAllSignedChange?: (allSigned: boolean) => void;
  /** 1명 이상 SIGNED 여부 — 워크시트 변경 시 무효화 다이얼로그 트리거에 사용. */
  onAnySignedChange?: (anySigned: boolean) => void;
  /** 사인 데이터 변화 콜백 (PNG 포함) — DOCX 임베드용 캐시. PNG 캐싱 위해 별도 호출. */
  onSignaturesChange?: (items: SignatureItem[]) => void;
}

const ROLE_DEFS: { role: SignatureRole; label: string; sub: string; isAuthor: boolean }[] = [
  { role: 'AUTHOR',     label: '작성자',   sub: 'Biz.P 현장소장',    isAuthor: true },
  { role: 'SUPERVISOR', label: '담당자',   sub: 'SKEP 관리감독자',    isAuthor: false },
  { role: 'CONFIRMER',  label: '확인자',   sub: 'SKEP HYPER',         isAuthor: false },
  { role: 'REVIEWER',   label: '검토자',   sub: 'SKEP 안전관리자',    isAuthor: false },
  { role: 'APPROVER',   label: '승인자',   sub: 'SKEP 현장총괄',      isAuthor: false },
];

const STATUS_CHIP: Record<SignatureStatus, { label: string; cls: string }> = {
  PENDING:      { label: '요청 보냄',  cls: 'bg-amber-100 text-amber-800' },
  SIGNED:       { label: '사인 완료',  cls: 'bg-emerald-100 text-emerald-800' },
  EXPIRED:      { label: '만료',       cls: 'bg-rose-100 text-rose-800' },
  INVALIDATED:  { label: '무효',       cls: 'bg-slate-200 text-slate-600' },
};

/** 작업계획서 5개 사인 카드 — 작성자 본인 + 4명 이메일 요청. */
export const SignaturePanel = forwardRef<SignaturePanelHandle, SignaturePanelProps>(function SignaturePanel(
  { workPlanId, onAllSignedChange, onAnySignedChange, onSignaturesChange },
  ref
) {
  const { user } = useAuth();
  const storageKey = user ? `skep.signRecipients.${user.id}` : null;

  const [items, setItems] = useState<SignatureItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [inputs, setInputs] = useState<Record<string, { name: string; email: string }>>(() => {
    // 초기값 — localStorage 에서 사용자별 최근 입력 복원
    const fallback = {
      SUPERVISOR: { name: '', email: '' },
      CONFIRMER:  { name: '', email: '' },
      REVIEWER:   { name: '', email: '' },
      APPROVER:   { name: '', email: '' },
    };
    if (typeof window === 'undefined' || !user) return fallback;
    try {
      const raw = localStorage.getItem(`skep.signRecipients.${user.id}`);
      if (!raw) return fallback;
      const saved = JSON.parse(raw);
      return { ...fallback, ...saved };
    } catch {
      return fallback;
    }
  });

  // inputs 변경 시 localStorage 자동 저장 — 다음 작업계획서에서도 prefill
  useEffect(() => {
    if (!storageKey) return;
    try { localStorage.setItem(storageKey, JSON.stringify(inputs)); } catch {}
  }, [inputs, storageKey]);
  const [padOpen, setPadOpen] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    if (!workPlanId) {
      setItems([]);
      onAllSignedChange?.(false);
      return;
    }
    void refresh();
    // 외부 사인 완료 자동 감지 — 15초 간격 폴링 + 탭 포커스 복귀 시 즉시 갱신
    const interval = setInterval(() => { void refresh(); }, 15000);
    const onFocus = () => { void refresh(); };
    window.addEventListener('focus', onFocus);
    return () => {
      clearInterval(interval);
      window.removeEventListener('focus', onFocus);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workPlanId]);

  const refresh = async () => {
    if (!workPlanId) return;
    setLoading(true);
    try {
      // withPng=1 — DOCX 임베드용. 부모가 캐시해서 buildDocxBlob 호출마다 재fetch 안 함.
      const res = await api.get<SignatureItem[]>(`/api/work-plans/${workPlanId}/signatures?withPng=1`);
      setItems(res.data ?? []);
      onSignaturesChange?.(res.data ?? []);
      const signedCount = (res.data ?? []).filter((s) => s.status === 'SIGNED').length;
      onAllSignedChange?.(signedCount >= 5);
      onAnySignedChange?.(signedCount > 0);
      // 이메일 입력란에 기존 값 채우기 — 비어있는 칸만 서버값으로 prefill.
      // (15초 폴링이 사용자가 입력 중인 값을 덮어쓰지 않도록 빈 칸만 채움)
      setInputs((prev) => {
        const next = { ...prev };
        for (const s of res.data ?? []) {
          if (s.role === 'AUTHOR') continue;
          const cur = prev[s.role] ?? { name: '', email: '' };
          next[s.role] = {
            name: cur.name?.trim() ? cur.name : (s.signer_name ?? ''),
            email: cur.email?.trim() ? cur.email : (s.signer_email ?? ''),
          };
        }
        return next;
      });
    } catch (e) {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  const find = (role: SignatureRole) => items.find((s) => s.role === role);

  // 최신 inputs/items 를 ref 로 보관 — useImperativeHandle 내부 함수가 stale 참조 안 하도록.
  const inputsRef = useRef(inputs);
  inputsRef.current = inputs;
  const itemsRef = useRef(items);
  itemsRef.current = items;

  useImperativeHandle(ref, () => ({
    getInputs: () => inputsRef.current,
    sendAllPending: async (overrideWorkPlanId?: number) => {
      let sent = 0;
      let skipped = 0;
      const useId = overrideWorkPlanId ?? workPlanId ?? null;
      if (!useId) return { sent, skipped };
      const roles: SignatureRole[] = ['SUPERVISOR', 'CONFIRMER', 'REVIEWER', 'APPROVER'];
      const body: Record<string, { name: string; email: string }> = {};
      for (const r of roles) {
        const i = inputsRef.current[r];
        const existing = itemsRef.current.find((s) => s.role === r);
        if (existing?.status === 'SIGNED') { skipped++; continue; }
        if (!i?.email?.trim()) { skipped++; continue; }
        body[r] = { name: (i.name ?? '').trim(), email: i.email.trim() };
        sent++;
      }
      if (sent > 0) {
        // catch 하지 않음 — 호출자(submitWorkPlan)가 alert 띄울 수 있도록.
        await api.post(`/api/work-plans/${useId}/signatures/request`, body);
        if (workPlanId) await refresh();
      }
      return { sent, skipped };
    },
  }), [workPlanId]);

  const submitAuthorSign = async (pngBase64: string) => {
    if (!workPlanId) return;
    setBusy('AUTHOR');
    try {
      await api.post(`/api/work-plans/${workPlanId}/signatures/author`, { pngBase64 });
      setPadOpen(false);
      await refresh();
    } catch (e: any) {
      alert('작성자 사인 저장 실패: ' + (e?.response?.data?.error || e?.message || e));
    } finally {
      setBusy(null);
    }
  };

  const requestOne = async (role: SignatureRole) => {
    if (!workPlanId) return;
    const i = inputs[role];
    if (!i?.email?.trim()) {
      alert('이메일을 입력하세요');
      return;
    }
    setBusy(role);
    try {
      await api.post(`/api/work-plans/${workPlanId}/signatures/request`, {
        [role]: { name: i.name?.trim() ?? '', email: i.email.trim() },
      });
      await refresh();
    } catch (e: any) {
      alert('사인 요청 메일 발송 실패: ' + (e?.response?.data?.error || e?.message || e));
    } finally {
      setBusy(null);
    }
  };

  const sendAllBatch = async () => {
    if (!workPlanId) {
      alert('작업계획서 [제출하기] 를 먼저 누르면 메일이 자동 발송됩니다.');
      return;
    }
    setLoading(true);
    try {
      const result = await (async () => {
        const roles: SignatureRole[] = ['SUPERVISOR', 'CONFIRMER', 'REVIEWER', 'APPROVER'];
        const body: Record<string, { name: string; email: string }> = {};
        let sent = 0;
        for (const r of roles) {
          const i = inputs[r];
          const existing = items.find((s) => s.role === r);
          if (existing?.status === 'SIGNED') continue;
          if (!i?.email?.trim()) continue;
          body[r] = { name: (i.name ?? '').trim(), email: i.email.trim() };
          sent++;
        }
        if (sent > 0) {
          await api.post(`/api/work-plans/${workPlanId}/signatures/request`, body);
          await refresh();
        }
        return sent;
      })();
      if (result === 0) alert('입력된 이메일이 없습니다.');
    } catch (e: any) {
      alert('일괄 발송 실패: ' + (e?.response?.data?.error || e?.message || e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card p-4 bg-white border-slate-200 space-y-3">
      <div className="flex items-center justify-between">
        <div className="text-sm font-bold text-slate-900">
          전자서명 (5명)
          {loading && <span className="ml-2 text-xs text-slate-500">(로드 중...)</span>}
        </div>
        <div className="flex items-center gap-2">
          {workPlanId && (
            <button
              type="button"
              onClick={() => void sendAllBatch()}
              disabled={loading}
              title="입력된 이메일에 한 번에 재발송"
              className="text-[11px] px-2 py-1 rounded-md bg-blue-600 text-white font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              다같이 재발송
            </button>
          )}
          <div className="text-[11px] text-slate-500">
            완료 <span className="font-bold text-slate-900">{items.filter((s) => s.status === 'SIGNED').length}</span> / 5
          </div>
        </div>
      </div>
      {!workPlanId && (
        <div className="text-[11px] text-slate-500 italic bg-slate-50 border border-slate-200 rounded px-3 py-2">
          아래 4명 이름/이메일을 입력하고 우측 상단 <strong>[제출하기]</strong> 를 누르면 자동으로 메일이 일괄 발송됩니다.
          작성자 본인 사인은 [제출하기] 클릭 후 상세 페이지에서 가능합니다.
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-3">
        {ROLE_DEFS.map(({ role, label, sub, isAuthor }) => {
          const sig = find(role);
          const chip = sig ? STATUS_CHIP[sig.status] : null;
          const png = sig?.signature_png_base64
            ? `data:image/png;base64,${sig.signature_png_base64}`
            : null;

          return (
            <div
              key={role}
              className={`rounded-xl border bg-white p-3 ${sig?.status === 'SIGNED' ? 'border-emerald-300' : 'border-slate-200'}`}
            >
              <div className="flex items-start justify-between mb-1.5">
                <div>
                  <div className="text-sm font-bold text-slate-900">{label}</div>
                  <div className="text-[10px] text-slate-500">{sub}</div>
                </div>
                {chip && (
                  <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${chip.cls}`}>{chip.label}</span>
                )}
              </div>

              <div className="h-20 border border-dashed border-slate-200 rounded-md bg-slate-50 flex items-center justify-center mb-2 overflow-hidden">
                {png ? (
                  <img src={png} alt="사인" className="max-h-full max-w-full object-contain" />
                ) : (
                  <span className="text-[10px] text-slate-400">사인 없음</span>
                )}
              </div>

              {sig?.signer_name && (
                <div className="text-[11px] text-slate-700 truncate" title={sig.signer_name}>
                  {sig.signer_name}
                </div>
              )}
              {sig?.signer_email && !isAuthor && (
                <div className="text-[10px] text-slate-400 truncate" title={sig.signer_email}>
                  {sig.signer_email}
                </div>
              )}

              {isAuthor ? (
                workPlanId ? (
                  <button
                    type="button"
                    onClick={() => setPadOpen(true)}
                    disabled={busy === 'AUTHOR'}
                    className="mt-2 w-full text-xs px-2 py-1.5 rounded-md bg-blue-600 text-white font-medium hover:bg-blue-700 disabled:opacity-50"
                  >
                    {sig?.status === 'SIGNED' ? '다시 사인하기' : '본인 사인'}
                  </button>
                ) : (
                  <div className="mt-2 text-[10px] text-slate-400 italic text-center">
                    제출 후 상세 페이지에서 사인
                  </div>
                )
              ) : (
                <div className="mt-2 space-y-1.5">
                  {!sig || sig.status !== 'SIGNED' ? (
                    <>
                      <input
                        type="text"
                        value={inputs[role]?.name ?? ''}
                        onChange={(e) => setInputs((p) => ({ ...p, [role]: { ...p[role], name: e.target.value } }))}
                        placeholder="이름"
                        className="w-full text-xs border border-slate-300 rounded px-2 py-1"
                      />
                      <input
                        type="email"
                        value={inputs[role]?.email ?? ''}
                        onChange={(e) => setInputs((p) => ({ ...p, [role]: { ...p[role], email: e.target.value } }))}
                        placeholder="이메일"
                        className="w-full text-xs border border-slate-300 rounded px-2 py-1"
                      />
                      {workPlanId && (
                        <button
                          type="button"
                          onClick={() => requestOne(role)}
                          disabled={busy === role}
                          className="w-full text-xs px-2 py-1.5 rounded-md border border-blue-500 text-blue-700 bg-white hover:bg-blue-50 disabled:opacity-50"
                        >
                          {busy === role ? '발송 중…' : sig?.status === 'PENDING' ? '재발송' : '재발송'}
                        </button>
                      )}
                    </>
                  ) : (
                    <div className="text-[10px] text-emerald-600">
                      ✓ {sig.signed_at?.slice(0, 10) ?? '완료'}
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <SignaturePadDialog
        open={padOpen}
        title="작성자 본인 사인"
        signerName="Biz.P 현장소장 본인 사인"
        onClose={() => setPadOpen(false)}
        onConfirm={submitAuthorSign}
      />
    </div>
  );
});
