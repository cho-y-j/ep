import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar } from '../../components/ui';
import MoneyInput from '../../components/MoneyInput';

type RateType = 'DAILY' | 'MONTHLY';

/** 백엔드 rows 는 JSONB 패스스루(snake_case 키). */
type TemplateRow = Record<string, unknown>;

type QuoteTemplate = {
  id: number;
  supplier_company_id: number;
  name: string;
  memo?: string | null;
  rows: TemplateRow[];
  created_at: string;
  updated_at: string;
};

const OT_FIELDS: Array<{ key: string; label: string }> = [
  { key: 'rate_early', label: '조출' },
  { key: 'rate_lunch', label: '점심' },
  { key: 'rate_evening', label: '연장' },
  { key: 'rate_night', label: '야간' },
  { key: 'rate_overnight', label: '철야' },
];

export default function QuoteTemplatesPage() {
  const [templates, setTemplates] = useState<QuoteTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<QuoteTemplate | 'new' | null>(null);
  const [q, setQ] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await api.get<QuoteTemplate[]>('/api/quote-templates');
      setTemplates(data ?? []);
    } catch {
      setTemplates([]);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => { void load(); }, []);

  const remove = async (t: QuoteTemplate) => {
    if (!window.confirm(`템플릿 "${t.name}" 을(를) 삭제할까요?`)) return;
    try {
      await api.delete(`/api/quote-templates/${t.id}`);
      toast.success('템플릿이 삭제되었습니다');
      void load();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '삭제에 실패했습니다');
    }
  };

  const qLower = q.trim().toLowerCase();
  const filtered = templates.filter((t) => !qLower || `${t.name} ${t.memo ?? ''}`.toLowerCase().includes(qLower));

  return (
    <AppShell breadcrumb={[{ label: '견적 템플릿' }]}>
      <div className="mx-auto max-w-6xl space-y-6">
        <PageHeader
          title="견적 템플릿 (단가표)"
          subtitle='장비 종류·규격별 5분류 단가표를 미리 등록해 두면, 견적 발송 화면에서 "템플릿 불러오기"로 한 번에 삽입할 수 있어요.'
          actions={<button onClick={() => setEditing('new')} className="btn-primary shrink-0">+ 새 템플릿</button>}
        />

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '템플릿 이름·메모 검색' }}
          activeFilterCount={q ? 1 : 0}
          onReset={() => setQ('')}
        />

        {loading ? (
          <div className="text-sm text-slate-400">불러오는 중…</div>
        ) : templates.length === 0 ? (
          <div className="card p-10 text-center">
            <div className="text-4xl mb-2">📋</div>
            <div className="font-semibold text-slate-700">아직 등록한 템플릿이 없습니다</div>
            <p className="mt-1 text-sm text-slate-400">
              우측 상단 "새 템플릿"으로 첫 단가표를 만드세요. 라인마다 장비 종류·규격 + 기본단가 + OT 5분류 단가를 담습니다.
            </p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-400">조건에 맞는 템플릿이 없습니다.</div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {filtered.map((t) => (
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

function money(n?: unknown): string {
  const v = typeof n === 'number' ? n : Number(n);
  return Number.isFinite(v) && v > 0 ? v.toLocaleString() + '원' : '-';
}

function TemplateCard({ t, onEdit, onDelete }: { t: QuoteTemplate; onEdit: () => void; onDelete: () => void }) {
  const rows = t.rows ?? [];
  return (
    <div className="card p-5 flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="font-bold text-slate-900 truncate">{t.name}</div>
          <div className="mt-0.5 text-xs text-slate-500">{rows.length}개 라인</div>
        </div>
      </div>

      {rows.length > 0 && (
        <div className="space-y-1.5">
          {rows.slice(0, 3).map((r, i) => (
            <div key={i} className="rounded-lg bg-slate-50 px-3 py-2 text-xs">
              <div className="flex items-center justify-between gap-2">
                <span className="font-medium text-slate-700 truncate">{String(r.equipment_desc ?? '(규격 미입력)')}</span>
                <span className="shrink-0 rounded-full bg-brand-50 text-brand-700 px-2 py-0.5 font-semibold">
                  {r.rate_type === 'MONTHLY' ? '월대' : '일대'} {money(r.base_rate)}
                </span>
              </div>
              <div className="mt-1 flex flex-wrap gap-1">
                {OT_FIELDS.map((f) => (
                  <span key={f.key} className="text-[10px] text-slate-500">
                    {f.label} {money(r[f.key])}
                  </span>
                ))}
              </div>
            </div>
          ))}
          {rows.length > 3 && <div className="text-xs text-slate-400">외 {rows.length - 3}개 라인…</div>}
        </div>
      )}

      {t.memo && <div className="text-xs text-slate-500 border-t border-slate-100 pt-2">{t.memo}</div>}

      <div className="flex items-center justify-end gap-3 text-xs">
        <button onClick={onDelete} className="text-rose-500 font-semibold hover:underline">삭제</button>
        <button onClick={onEdit} className="text-brand-600 font-semibold hover:underline">수정</button>
      </div>
    </div>
  );
}

type FormRow = {
  equipment_desc: string;
  rate_type: RateType;
  base_rate: number | '';
  rate_early: number | '';
  rate_lunch: number | '';
  rate_evening: number | '';
  rate_night: number | '';
  rate_overnight: number | '';
  note: string;
};

function emptyRow(): FormRow {
  return {
    equipment_desc: '', rate_type: 'DAILY', base_rate: '',
    rate_early: '', rate_lunch: '', rate_evening: '', rate_night: '', rate_overnight: '', note: '',
  };
}

function toFormRow(r: TemplateRow): FormRow {
  const num = (v: unknown): number | '' => (v == null || v === '' ? '' : Number(v));
  return {
    equipment_desc: String(r.equipment_desc ?? ''),
    rate_type: r.rate_type === 'MONTHLY' ? 'MONTHLY' : 'DAILY',
    base_rate: num(r.base_rate),
    rate_early: num(r.rate_early),
    rate_lunch: num(r.rate_lunch),
    rate_evening: num(r.rate_evening),
    rate_night: num(r.rate_night),
    rate_overnight: num(r.rate_overnight),
    note: String(r.note ?? ''),
  };
}

function TemplateForm({ template, onClose, onSaved }: {
  template: QuoteTemplate | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState(template?.name ?? '');
  const [memo, setMemo] = useState(template?.memo ?? '');
  const [rows, setRows] = useState<FormRow[]>(() =>
    template?.rows && template.rows.length > 0 ? template.rows.map(toFormRow) : [emptyRow()]);
  const [busy, setBusy] = useState(false);

  const setRow = (i: number, patch: Partial<FormRow>) =>
    setRows((prev) => prev.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  const addRow = () => setRows((prev) => [...prev, emptyRow()]);
  const removeRow = (i: number) => setRows((prev) => (prev.length <= 1 ? prev : prev.filter((_, idx) => idx !== i)));

  const save = async () => {
    if (!name.trim()) { toast.error('템플릿 이름을 입력하세요'); return; }
    const numOrNull = (v: number | '') => (v === '' ? null : v);
    const body = {
      name: name.trim(),
      memo: memo.trim() || null,
      rows: rows.map((r) => ({
        equipment_desc: r.equipment_desc.trim() || null,
        rate_type: r.rate_type,
        base_rate: numOrNull(r.base_rate),
        rate_early: numOrNull(r.rate_early),
        rate_lunch: numOrNull(r.rate_lunch),
        rate_evening: numOrNull(r.rate_evening),
        rate_night: numOrNull(r.rate_night),
        rate_overnight: numOrNull(r.rate_overnight),
        note: r.note.trim() || null,
      })),
    };
    setBusy(true);
    try {
      if (template) await api.put(`/api/quote-templates/${template.id}`, body);
      else await api.post('/api/quote-templates', body);
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
      <div className="bg-white rounded-xl shadow-xl w-full max-w-3xl max-h-[92vh] flex flex-col">
        <div className="px-5 py-3 border-b">
          <h3 className="font-bold text-slate-900">{template ? '템플릿 수정' : '새 견적 템플릿'}</h3>
        </div>
        <div className="px-5 py-4 space-y-4 text-sm overflow-y-auto">
          <label className="block">
            <span className="text-xs font-semibold text-slate-500">템플릿 이름 <span className="text-rose-500">*</span></span>
            <input value={name} onChange={(e) => setName(e.target.value)} className="input mt-1 w-full"
                   placeholder="예: 고소작업차 표준 단가표 2026" />
          </label>

          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-500">단가 라인 ({rows.length})</span>
              <button type="button" onClick={addRow} className="text-xs font-semibold text-brand-600 hover:underline">+ 행 추가</button>
            </div>
            {rows.map((r, i) => (
              <div key={i} className="rounded-lg border border-slate-200 p-3 space-y-2.5">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[11px] font-semibold text-slate-400">라인 {i + 1}</span>
                  {rows.length > 1 && (
                    <button type="button" onClick={() => removeRow(i)} className="text-[11px] text-rose-500 hover:underline">행 삭제</button>
                  )}
                </div>
                <div className="grid gap-2 sm:grid-cols-[1fr_auto]">
                  <input value={r.equipment_desc} onChange={(e) => setRow(i, { equipment_desc: e.target.value })}
                         className="input w-full" placeholder="장비 종류·규격 (예: 고소작업차 45m)" />
                  <div className="flex gap-1.5">
                    {(['DAILY', 'MONTHLY'] as RateType[]).map((rt) => (
                      <button key={rt} type="button" onClick={() => setRow(i, { rate_type: rt })}
                              className={`px-3 py-1.5 rounded-lg text-xs font-semibold border ${r.rate_type === rt ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-slate-600 border-slate-200'}`}>
                        {rt === 'DAILY' ? '일대' : '월대'}
                      </button>
                    ))}
                  </div>
                </div>
                <label className="block">
                  <span className="text-[11px] text-slate-500">기본단가 ({r.rate_type === 'DAILY' ? '일대' : '월대'}, 원)</span>
                  <MoneyInput className="input mt-0.5 w-full" value={r.base_rate}
                              onChange={(v) => setRow(i, { base_rate: v })} />
                </label>
                <div>
                  <div className="text-[11px] font-semibold text-slate-500 mb-1">OT 5분류 단가 (원)</div>
                  <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
                    {OT_FIELDS.map((of) => (
                      <label key={of.key} className="block">
                        <span className="text-[11px] text-slate-500">{of.label}</span>
                        <MoneyInput className="input mt-0.5 w-full text-sm" showKorean={false}
                                    value={r[of.key as keyof FormRow] as number | ''}
                                    onChange={(v) => setRow(i, { [of.key]: v } as Partial<FormRow>)} />
                      </label>
                    ))}
                  </div>
                </div>
                <input value={r.note} onChange={(e) => setRow(i, { note: e.target.value })}
                       className="input w-full text-xs" placeholder="비고 (선택)" />
              </div>
            ))}
          </div>

          <label className="block">
            <span className="text-xs font-semibold text-slate-500">메모</span>
            <textarea value={memo} onChange={(e) => setMemo(e.target.value)} className="input mt-1 w-full" rows={2} />
          </label>
        </div>
        <div className="px-5 py-3 border-t flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 text-sm hover:bg-slate-100 rounded">취소</button>
          <button onClick={save} disabled={busy} className="btn-primary disabled:opacity-50">
            {busy ? '저장 중…' : template ? '수정 저장' : '템플릿 등록'}
          </button>
        </div>
      </div>
    </div>
  );
}
