import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';

/** 점검 항목 — 백엔드 items JSONB 패스스루. no 는 저장 시 행 순서로 재부여. */
type Item = { no: number; text: string; required: boolean };

type Template = {
  id: number;
  name: string;
  target: string;
  items: Item[];
  active: boolean;
  created_at: string;
  updated_at: string;
};

/**
 * S2′ 법정점검 템플릿 관리 — ADMIN 전용(시스템 관리).
 * 어드민이 항목을 편집(추가/삭제/순서)하는 템플릿. 사용자 실템플릿 수령 시 입력만 하면 됨.
 */
export default function SafetyCheckTemplatesPage() {
  const [templates, setTemplates] = useState<Template[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<Template | 'new' | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await api.get<Template[]>('/api/safety-check-templates');
      setTemplates(data ?? []);
    } catch {
      setTemplates([]);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => { void load(); }, []);

  const remove = async (t: Template) => {
    if (!window.confirm(`템플릿 "${t.name}" 을(를) 삭제할까요?`)) return;
    try {
      await api.delete(`/api/safety-check-templates/${t.id}`);
      toast.success('템플릿이 삭제되었습니다');
      void load();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '삭제에 실패했습니다');
    }
  };

  return (
    <AppShell breadcrumb={[{ label: '점검 템플릿' }]}>
      <div className="mx-auto max-w-5xl space-y-6">
        <header className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-slate-950">법정점검 템플릿 (S2′)</h1>
            <p className="mt-1 text-sm text-slate-500">
              안전점검원이 NFC 태그 후 수행하는 법정점검 체크리스트입니다. 항목을 자유롭게 추가·삭제·정렬하세요.
            </p>
          </div>
          <button onClick={() => setEditing('new')} className="btn-primary shrink-0">+ 새 템플릿</button>
        </header>

        {loading ? (
          <div className="text-sm text-slate-400">불러오는 중…</div>
        ) : templates.length === 0 ? (
          <div className="card p-10 text-center">
            <div className="mb-2 text-4xl">🛡️</div>
            <div className="font-semibold text-slate-700">등록된 점검 템플릿이 없습니다</div>
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {templates.map((t) => (
              <TemplateCard key={t.id} t={t} onEdit={() => setEditing(t)} onDelete={() => void remove(t)} />
            ))}
          </div>
        )}
      </div>

      {editing && (
        <TemplateForm
          template={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); void load(); }}
        />
      )}
    </AppShell>
  );
}

function TemplateCard({ t, onEdit, onDelete }: { t: Template; onEdit: () => void; onDelete: () => void }) {
  const items = t.items ?? [];
  return (
    <div className="card flex flex-col gap-3 p-5">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="truncate font-bold text-slate-900">{t.name}</div>
          <div className="mt-0.5 flex items-center gap-2 text-xs text-slate-500">
            <span>{items.length}개 항목</span>
            <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${t.active ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-400'}`}>
              {t.active ? '활성' : '비활성'}
            </span>
          </div>
        </div>
      </div>
      {items.length > 0 && (
        <ol className="space-y-1 text-xs text-slate-600">
          {items.slice(0, 4).map((it, i) => (
            <li key={i} className="flex gap-1.5">
              <span className="shrink-0 font-semibold text-slate-400">{i + 1}.</span>
              <span className="truncate">{it.text}</span>
            </li>
          ))}
          {items.length > 4 && <li className="text-slate-400">외 {items.length - 4}개…</li>}
        </ol>
      )}
      <div className="flex items-center justify-end gap-3 text-xs">
        <button onClick={onDelete} className="font-semibold text-rose-500 hover:underline">삭제</button>
        <button onClick={onEdit} className="font-semibold text-brand-600 hover:underline">수정</button>
      </div>
    </div>
  );
}

function TemplateForm({ template, onClose, onSaved }: {
  template: Template | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState(template?.name ?? '');
  const [active, setActive] = useState(template?.active ?? true);
  const [items, setItems] = useState<Item[]>(() =>
    template?.items && template.items.length > 0
      ? template.items.map((it, i) => ({ no: i + 1, text: it.text ?? '', required: it.required !== false }))
      : [{ no: 1, text: '', required: true }]);
  const [busy, setBusy] = useState(false);

  const setItem = (i: number, patch: Partial<Item>) =>
    setItems((prev) => prev.map((it, idx) => (idx === i ? { ...it, ...patch } : it)));
  const addItem = () => setItems((prev) => [...prev, { no: prev.length + 1, text: '', required: true }]);
  const removeItem = (i: number) => setItems((prev) => (prev.length <= 1 ? prev : prev.filter((_, idx) => idx !== i)));
  const move = (i: number, dir: -1 | 1) => setItems((prev) => {
    const j = i + dir;
    if (j < 0 || j >= prev.length) return prev;
    const next = [...prev];
    [next[i], next[j]] = [next[j], next[i]];
    return next;
  });

  const save = async () => {
    if (!name.trim()) { toast.error('템플릿 이름을 입력하세요'); return; }
    const cleaned = items.filter((it) => it.text.trim());
    if (cleaned.length === 0) { toast.error('점검 항목을 1개 이상 입력하세요'); return; }
    const body = {
      name: name.trim(),
      target: 'EQUIPMENT',
      active,
      items: cleaned.map((it, i) => ({ no: i + 1, text: it.text.trim(), required: it.required })),
    };
    setBusy(true);
    try {
      if (template) await api.put(`/api/safety-check-templates/${template.id}`, body);
      else await api.post('/api/safety-check-templates', body);
      toast.success(template ? '템플릿이 수정되었습니다' : '템플릿이 등록되었습니다');
      onSaved();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '저장에 실패했습니다');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="flex max-h-[92vh] w-full max-w-2xl flex-col rounded-xl bg-white shadow-xl">
        <div className="border-b px-5 py-3">
          <h3 className="font-bold text-slate-900">{template ? '점검 템플릿 수정' : '새 점검 템플릿'}</h3>
        </div>
        <div className="space-y-4 overflow-y-auto px-5 py-4 text-sm">
          <div className="flex flex-wrap items-end gap-3">
            <label className="block flex-1 min-w-[200px]">
              <span className="text-xs font-semibold text-slate-500">템플릿 이름 <span className="text-rose-500">*</span></span>
              <input value={name} onChange={(e) => setName(e.target.value)} className="input mt-1 w-full"
                     placeholder="예: 고소작업차 법정 점검표" />
            </label>
            <label className="flex items-center gap-2 pb-2">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              <span className="text-xs font-medium text-slate-600">활성</span>
            </label>
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-500">점검 항목 ({items.length})</span>
              <button type="button" onClick={addItem} className="text-xs font-semibold text-brand-600 hover:underline">+ 항목 추가</button>
            </div>
            {items.map((it, i) => (
              <div key={i} className="flex items-start gap-2 rounded-lg border border-slate-200 p-2">
                <span className="mt-2 w-5 shrink-0 text-center text-[11px] font-semibold text-slate-400">{i + 1}</span>
                <div className="flex-1 space-y-1.5">
                  <textarea value={it.text} onChange={(e) => setItem(i, { text: e.target.value })}
                            className="input w-full text-sm" rows={2} placeholder="점검 항목 문구" />
                  <label className="flex items-center gap-1.5 text-[11px] text-slate-500">
                    <input type="checkbox" checked={it.required} onChange={(e) => setItem(i, { required: e.target.checked })} />
                    필수 항목
                  </label>
                </div>
                <div className="flex shrink-0 flex-col items-center gap-1">
                  <button type="button" onClick={() => move(i, -1)} disabled={i === 0}
                          className="rounded px-1.5 text-slate-400 hover:bg-slate-100 disabled:opacity-30" title="위로">▲</button>
                  <button type="button" onClick={() => move(i, 1)} disabled={i === items.length - 1}
                          className="rounded px-1.5 text-slate-400 hover:bg-slate-100 disabled:opacity-30" title="아래로">▼</button>
                  {items.length > 1 && (
                    <button type="button" onClick={() => removeItem(i)}
                            className="rounded px-1.5 text-rose-400 hover:bg-rose-50" title="삭제">✕</button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
        <div className="flex justify-end gap-2 border-t px-5 py-3">
          <button onClick={onClose} className="rounded px-3 py-1.5 text-sm hover:bg-slate-100">취소</button>
          <button onClick={save} disabled={busy} className="btn-primary disabled:opacity-50">
            {busy ? '저장 중…' : template ? '수정 저장' : '템플릿 등록'}
          </button>
        </div>
      </div>
    </div>
  );
}
