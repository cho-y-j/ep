import { useEffect, useState } from 'react';
import { api } from '../../../../lib/api';
import { toast } from '../../../../lib/toast';

type MissingDoc = { document_type_id: number; document_type_name: string; supplement_status?: 'NONE' | 'OPEN' };
type MissingResource = {
  owner_type: 'EQUIPMENT' | 'PERSON';
  owner_id: number;
  owner_label: string;
  supplier_company_id: number;
  missing: MissingDoc[];
};

type OtherDocTypeIdMap = { EQUIPMENT: number; PERSON: number };

type Props = {
  open: boolean;
  workPlanId: number | null;
  onClose: () => void;
};

/** 작업계획서 제출 실패 시 누락 자원+서류 표시 + 체크 선택 + 기타 자유 입력. */
export default function MissingDocsDialog({ open, workPlanId, onClose }: Props) {
  const [items, setItems] = useState<MissingResource[]>([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  /** 체크된 (자원-서류) 키 set. key = `${owner_type}:${owner_id}:${doc_type_id}` */
  const [selected, setSelected] = useState<Set<string>>(new Set());
  /** 자원별 "기타" 입력 — key = `${owner_type}:${owner_id}` */
  const [otherText, setOtherText] = useState<Record<string, string>>({});
  const [otherTypeIds, setOtherTypeIds] = useState<OtherDocTypeIdMap | null>(null);

  // "기타" 발송용 document_type_id 한번만 조회
  useEffect(() => {
    if (!open) return;
    api.get<any[]>('/api/document-types').then((r) => {
      const eqType = r.data.find((t) => t.name === '기타 서류 요청 (장비)' && t.applies_to === 'EQUIPMENT');
      const pType = r.data.find((t) => t.name === '기타 서류 요청 (인원)' && t.applies_to === 'PERSON');
      if (eqType && pType) setOtherTypeIds({ EQUIPMENT: eqType.id, PERSON: pType.id });
    }).catch(() => {});
  }, [open]);

  useEffect(() => {
    if (!open || !workPlanId) return;
    setLoading(true);
    api.get<MissingResource[]>(`/api/work-plans/${workPlanId}/missing-docs`)
      .then((r) => {
        setItems(r.data);
        // 기본 체크 — 아직 발송 안 된 항목만. OPEN(발송 대기) 은 체크 안 함.
        const init = new Set<string>();
        r.data.forEach((res) => res.missing.forEach((m) => {
          if (m.supplement_status !== 'OPEN') {
            init.add(`${res.owner_type}:${res.owner_id}:${m.document_type_id}`);
          }
        }));
        setSelected(init);
        setOtherText({});
      })
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [open, workPlanId]);

  const toggle = (key: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  const sendAll = async () => {
    const payload: Array<Record<string, unknown>> = [];
    for (const r of items) {
      // 체크된 누락 서류
      for (const m of r.missing) {
        const key = `${r.owner_type}:${r.owner_id}:${m.document_type_id}`;
        if (selected.has(key)) {
          payload.push({
            target_owner_type: r.owner_type,
            target_owner_id: r.owner_id,
            document_type_id: m.document_type_id,
            context_work_plan_id: workPlanId,
            reason: '작업계획서 제출 시 필수 서류 누락 — 보완 부탁드립니다.',
          });
        }
      }
      // 기타 자유 입력
      const otherKey = `${r.owner_type}:${r.owner_id}`;
      const text = (otherText[otherKey] ?? '').trim();
      if (text) {
        if (!otherTypeIds) {
          toast.error('"기타" 서류 타입이 시드되지 않음');
          continue;
        }
        payload.push({
          target_owner_type: r.owner_type,
          target_owner_id: r.owner_id,
          document_type_id: otherTypeIds[r.owner_type],
          context_work_plan_id: workPlanId,
          reason: `[${r.owner_label}] 요청 서류: ${text}`,
        });
      }
    }

    if (payload.length === 0) {
      toast.error('보낼 항목이 없습니다 — 체크하거나 기타 입력하세요');
      return;
    }

    setBusy(true);
    try {
      await api.post('/api/document-supplements/batch', payload);
      toast.success(`보완요청 ${payload.length}건 발송 — 공급사 회신 후 다시 제출하세요. (이미 OPEN 상태인 동일 요청은 중복 발송 skip)`);
      onClose();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '보완요청 발송 실패');
    } finally { setBusy(false); }
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[85vh] flex flex-col">
        <div className="px-5 py-3 border-b">
          <h3 className="font-bold text-slate-900">필수 서류 누락 — 보완요청 발송</h3>
          <p className="text-xs text-slate-500 mt-0.5">
            보낼 서류를 체크하세요. 추가로 필요한 서류는 "기타"에 직접 입력 가능합니다.
            동일 요청은 중복 발송되지 않습니다.
          </p>
        </div>
        <div className="px-5 py-4 overflow-y-auto flex-1 space-y-3">
          {loading ? (
            <div className="text-sm text-slate-400">불러오는 중…</div>
          ) : items.length === 0 ? (
            <div className="text-sm text-emerald-700">누락 서류 없음 — 제출 가능 상태입니다.</div>
          ) : (
            items.map((r) => {
              const otherKey = `${r.owner_type}:${r.owner_id}`;
              return (
                <div key={otherKey} className="rounded-lg border border-amber-200 bg-amber-50/40 p-3">
                  <div className="text-sm font-semibold text-slate-900 mb-2">
                    {r.owner_type === 'EQUIPMENT' ? '🚛 ' : '👤 '}{r.owner_label}
                  </div>
                  <div className="space-y-1">
                    {r.missing.map((m) => {
                      const key = `${r.owner_type}:${r.owner_id}:${m.document_type_id}`;
                      const isOpen = m.supplement_status === 'OPEN';
                      return (
                        <label key={m.document_type_id} className={`flex items-center gap-2 text-sm ${isOpen ? 'opacity-70' : ''}`}>
                          <input type="checkbox" checked={selected.has(key)}
                                 disabled={isOpen}
                                 onChange={() => toggle(key)} />
                          <span className={isOpen ? 'text-slate-500 line-through' : 'text-slate-800'}>{m.document_type_name}</span>
                          {isOpen && (
                            <span className="ml-1 px-1.5 py-0.5 rounded-full text-[10px] font-semibold bg-amber-100 text-amber-800">
                              발송 대기 (공급사 회신 중)
                            </span>
                          )}
                        </label>
                      );
                    })}
                  </div>
                  <div className="mt-2">
                    <label className="block text-[11px] font-medium text-slate-500 mb-0.5">기타 서류 요청 (직접 입력)</label>
                    <input type="text" value={otherText[otherKey] ?? ''}
                           onChange={(e) => setOtherText((p) => ({ ...p, [otherKey]: e.target.value }))}
                           placeholder="예: 차량 정기검사 결과표"
                           className="w-full px-2 py-1 text-xs border border-slate-300 rounded" />
                  </div>
                </div>
              );
            })
          )}
        </div>
        <div className="px-5 py-3 border-t flex items-center justify-between">
          <span className="text-xs text-slate-500">
            체크 {selected.size}건 + 기타 {Object.values(otherText).filter((v) => v.trim()).length}건
          </span>
          <div className="flex gap-2">
            <button onClick={onClose} className="px-3 py-1.5 rounded text-sm text-slate-700 hover:bg-slate-100">
              닫기
            </button>
            <button onClick={sendAll} disabled={busy}
                    className="px-4 py-1.5 rounded bg-blue-600 text-white text-sm font-semibold hover:bg-blue-700 disabled:opacity-50">
              {busy ? '발송 중…' : '선택한 항목 보완요청 발송'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
