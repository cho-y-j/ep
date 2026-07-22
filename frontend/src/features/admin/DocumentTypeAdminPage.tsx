import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar } from '../../components/ui';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import { ALL_PERSON_ROLES, PERSON_ROLE_LABEL } from '../../types/person';
import { EQUIPMENT_CATEGORIES, EQUIPMENT_CATEGORY_LABEL } from '../../types/equipment';

/** /api/admin/document-types — DocumentType 엔티티 (Jackson SNAKE_CASE 직렬화). */
type DocType = {
  id: number;
  name: string;
  applies_to: 'PERSON' | 'EQUIPMENT' | 'COMPANY';
  has_expiry: boolean;
  required: boolean;
  active: boolean;
  blocks_assignment: boolean;
  sort_order: number;
  applies_to_person_roles: string | null;
  applies_to_categories: string | null;
  ocr_region_template: string | null;
  sample_image_key: string | null;
  sample_description: string | null;
};

const APPLIES_LABEL: Record<DocType['applies_to'], string> = {
  PERSON: '인원', EQUIPMENT: '장비', COMPANY: '회사',
};

function csvHas(csv: string | null, v: string): boolean {
  return (csv ?? '').split(',').map((s) => s.trim()).filter(Boolean).includes(v);
}
function csvToggle(csv: string | null, v: string): string {
  const arr = (csv ?? '').split(',').map((s) => s.trim()).filter(Boolean);
  return (arr.includes(v) ? arr.filter((x) => x !== v) : [...arr, v]).join(',');
}

/** 역할/카테고리 다중 선택 칩. value=CSV. */
function Chips({ value, options, labels, onChange }: {
  value: string | null;
  options: string[];
  labels: Record<string, string>;
  onChange: (csv: string) => void;
}) {
  return (
    <div className="flex flex-wrap gap-1">
      {options.map((opt) => {
        const on = csvHas(value, opt);
        return (
          <button key={opt} type="button" onClick={() => onChange(csvToggle(value, opt))}
            className={`px-2 py-0.5 rounded-full text-xs font-medium border transition ${
              on ? 'bg-brand-600 text-white border-brand-600'
                 : 'bg-white text-slate-500 border-slate-300 hover:border-slate-400'
            }`}>
            {labels[opt] ?? opt}
          </button>
        );
      })}
      {!value && <span className="text-xs text-slate-400 self-center">전체(미지정)</span>}
    </div>
  );
}

/** 서류종류별 마스킹된 예시 이미지 1장 — 업로드/미리보기/교체/삭제. */
function SampleControl({ typeId, sampleKey, busy, onUpload, onDelete }: {
  typeId: number; sampleKey: string | null; busy: boolean;
  onUpload: (file: File) => void; onDelete: () => void;
}) {
  const ref = useRef<HTMLInputElement | null>(null);
  // sampleKey 는 업로드마다 새 UUID → 쿼리에 넣어 교체 시 캐시 무효화.
  const src = sampleKey ? `/api/document-types/${typeId}/sample?v=${encodeURIComponent(sampleKey)}` : null;
  return (
    <div className="flex shrink-0 items-center gap-1.5">
      <input ref={ref} type="file" accept="image/*" className="hidden"
        onChange={(e) => { const f = e.target.files?.[0]; if (f) onUpload(f); e.target.value = ''; }} />
      {src ? (
        <>
          <a href={src} target="_blank" rel="noreferrer" title="샘플 이미지 크게 보기">
            <img src={src} alt="샘플" className="h-8 w-11 rounded border border-slate-200 object-cover" />
          </a>
          <button type="button" disabled={busy} onClick={() => ref.current?.click()}
            className="text-xs px-2 py-1 rounded border border-slate-300 text-slate-600 hover:bg-slate-50 disabled:opacity-50">교체</button>
          <button type="button" disabled={busy} onClick={onDelete}
            className="text-xs px-2 py-1 rounded border border-rose-200 text-rose-600 hover:bg-rose-50 disabled:opacity-50">삭제</button>
        </>
      ) : (
        <button type="button" disabled={busy} onClick={() => ref.current?.click()}
          className="text-xs px-2.5 py-1 rounded border border-slate-300 text-slate-600 hover:bg-slate-50 disabled:opacity-50">
          {busy ? '올리는 중…' : '＋ 샘플 등록'}
        </button>
      )}
    </div>
  );
}

/** 서류종류별 샘플 설명글 — 사진과 독립(글만/사진만/둘다). 포커스 아웃 시 변경분만 저장. */
function SampleDescription({ value, onSave }: {
  value: string | null; onSave: (text: string) => void;
}) {
  const [text, setText] = useState(value ?? '');
  useEffect(() => { setText(value ?? ''); }, [value]);
  return (
    <label className="w-full block">
      <span className="text-xs text-slate-500">샘플 설명글</span>
      <textarea rows={2} value={text} onChange={(e) => setText(e.target.value)}
        onBlur={() => { const t = text.trim(); if (t !== (value ?? '')) onSave(t); }}
        placeholder="예: 앞면 전체가 나오게 밝은 곳에서 촬영해 주세요 (사진 없이 글만 등록도 가능)"
        className="input mt-0.5 w-full text-xs resize-y" />
    </label>
  );
}

export default function DocumentTypeAdminPage() {
  const navigate = useNavigate();
  const [types, setTypes] = useState<DocType[]>([]);
  const [loading, setLoading] = useState(true);
  // 신규 추가 폼
  const [newName, setNewName] = useState('');
  const [newAppliesTo, setNewAppliesTo] = useState<DocType['applies_to']>('PERSON');
  const [newRequired, setNewRequired] = useState(false);
  const [newHasExpiry, setNewHasExpiry] = useState(false);
  const [adding, setAdding] = useState(false);
  const [q, setQ] = useState('');
  const [sampleBusyId, setSampleBusyId] = useState<number | null>(null);

  async function load() {
    setLoading(true);
    try {
      const r = await api.get<DocType[]>('/api/admin/document-types');
      setTypes(r.data);
    } catch { toast.error('서류종류를 불러올 수 없습니다'); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  /** 단일 필드 변경 즉시 저장 + 로컬 반영. */
  async function patch(id: number, body: Record<string, unknown>) {
    try {
      await api.patch(`/api/admin/document-types/${id}`, body);
      setTypes((prev) => prev.map((t) => (t.id === id ? { ...t, ...remap(body) } : t)));
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '저장 실패');
    }
  }
  // PATCH body(snake) → 로컬 state(snake) 키 매핑
  function remap(body: Record<string, unknown>): Partial<DocType> {
    const out: Record<string, unknown> = {};
    if ('required' in body) out.required = body.required;
    if ('active' in body) out.active = body.active;
    if ('applies_to_person_roles' in body) out.applies_to_person_roles = (body.applies_to_person_roles as string) || null;
    if ('applies_to_categories' in body) out.applies_to_categories = (body.applies_to_categories as string) || null;
    if ('sample_description' in body) out.sample_description = (body.sample_description as string) || null;
    return out as Partial<DocType>;
  }

  /** 마스킹된 예시 이미지 업로드(교체). */
  async function uploadSample(id: number, file: File) {
    setSampleBusyId(id);
    try {
      const fd = new FormData();
      fd.append('file', file);
      await api.post(`/api/document-types/${id}/sample`, fd, { headers: { 'Content-Type': 'multipart/form-data' } });
      toast.success('샘플 이미지를 등록했습니다');
      await load();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '샘플 등록 실패');
    } finally { setSampleBusyId(null); }
  }

  async function deleteSample(id: number) {
    setSampleBusyId(id);
    try {
      await api.delete(`/api/document-types/${id}/sample`);
      toast.success('샘플 이미지를 삭제했습니다');
      await load();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '샘플 삭제 실패');
    } finally { setSampleBusyId(null); }
  }

  async function addType() {
    if (!newName.trim()) { toast.error('이름을 입력하세요'); return; }
    setAdding(true);
    try {
      await api.post('/api/admin/document-types', {
        name: newName.trim(),
        applies_to: newAppliesTo,
        has_expiry: newHasExpiry,
        requires_verification: false,
        sort_order: 100,
        required: newRequired,
        blocks_assignment: false,
      });
      setNewName(''); setNewRequired(false); setNewHasExpiry(false);
      toast.success('추가되었습니다');
      void load();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '추가 실패');
    } finally { setAdding(false); }
  }

  const qLower = q.trim().toLowerCase();
  const groups = useMemo(() => {
    const match = (t: DocType) => !qLower || t.name.toLowerCase().includes(qLower);
    return {
      PERSON: types.filter((t) => t.applies_to === 'PERSON' && match(t)),
      EQUIPMENT: types.filter((t) => t.applies_to === 'EQUIPMENT' && match(t)),
      COMPANY: types.filter((t) => t.applies_to === 'COMPANY' && match(t)),
    };
  }, [types, qLower]);

  return (
    <AppShell breadcrumb={[{ label: '서류종류 관리' }]}>
      <div className="max-w-5xl mx-auto px-6 py-8 space-y-6">
        <PageHeader
          title="서류종류 관리"
          subtitle="자원별 필수/선택 서류와 역할·카테고리 적용 범위를 지정합니다."
        />

        {/* 신규 추가 */}
        <section className="card p-4 flex flex-wrap items-end gap-3">
          <label className="block">
            <span className="text-xs font-medium text-slate-600">서류 이름</span>
            <input className="input mt-1 w-48" value={newName} onChange={(e) => setNewName(e.target.value)}
                   placeholder="예: 신분증" />
          </label>
          <label className="block">
            <span className="text-xs font-medium text-slate-600">적용 대상</span>
            <select className="input mt-1" value={newAppliesTo}
                    onChange={(e) => setNewAppliesTo(e.target.value as DocType['applies_to'])}>
              <option value="PERSON">인원</option>
              <option value="EQUIPMENT">장비</option>
              <option value="COMPANY">회사</option>
            </select>
          </label>
          <label className="flex items-center gap-1.5 text-sm text-slate-700 pb-2">
            <input type="checkbox" checked={newRequired} onChange={(e) => setNewRequired(e.target.checked)} />
            필수
          </label>
          <label className="flex items-center gap-1.5 text-sm text-slate-700 pb-2">
            <input type="checkbox" checked={newHasExpiry} onChange={(e) => setNewHasExpiry(e.target.checked)} />
            만료일 있음
          </label>
          <button onClick={addType} disabled={adding}
                  className="px-4 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700 disabled:opacity-50">
            {adding ? '추가 중…' : '+ 추가'}
          </button>
        </section>

        <FilterBar search={{ value: q, onChange: setQ, placeholder: '서류 이름 검색' }} />

        {loading ? (
          <div className="card p-8 text-center text-slate-400">불러오는 중…</div>
        ) : (
          (['PERSON', 'EQUIPMENT', 'COMPANY'] as const).map((grp) => (
            <section key={grp} className="card p-0 overflow-hidden">
              <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-200 text-sm font-semibold text-slate-700">
                {APPLIES_LABEL[grp]} 서류 ({groups[grp].length})
              </div>
              {groups[grp].length === 0 ? (
                <div className="p-6 text-center text-sm text-slate-400">없음</div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {groups[grp].map((t) => (
                    <div key={t.id} className={`px-4 py-3 flex flex-wrap items-center gap-x-4 gap-y-2 ${t.active ? '' : 'opacity-50'}`}>
                      <span className="font-semibold text-slate-900 w-40 truncate">{t.name}</span>
                      {t.ocr_region_template && (
                        <span className="text-[11px] px-1.5 py-0.5 rounded bg-brand-50 text-brand-700 border border-brand-200">영역맵</span>
                      )}
                      <label className="flex items-center gap-1.5 text-sm text-slate-700">
                        <input type="checkbox" checked={t.required}
                               onChange={(e) => patch(t.id, { required: e.target.checked })} />
                        필수
                      </label>
                      {grp === 'PERSON' && (
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-slate-500">역할:</span>
                          <Chips value={t.applies_to_person_roles} options={ALL_PERSON_ROLES}
                                 labels={PERSON_ROLE_LABEL as Record<string, string>}
                                 onChange={(csv) => patch(t.id, { applies_to_person_roles: csv })} />
                        </div>
                      )}
                      {grp === 'EQUIPMENT' && (
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-slate-500">카테고리:</span>
                          <Chips value={t.applies_to_categories} options={EQUIPMENT_CATEGORIES}
                                 labels={EQUIPMENT_CATEGORY_LABEL as Record<string, string>}
                                 onChange={(csv) => patch(t.id, { applies_to_categories: csv })} />
                        </div>
                      )}
                      <button type="button" onClick={() => navigate(`/admin/document-types/${t.id}/regions`)}
                        className="ml-auto text-xs px-2.5 py-1 rounded border border-slate-300 text-slate-600 hover:bg-slate-50">
                        영역 편집
                      </button>
                      <label className="flex items-center gap-1.5 text-xs text-slate-500">
                        <input type="checkbox" checked={t.active}
                               onChange={(e) => patch(t.id, { active: e.target.checked })} />
                        활성
                      </label>
                      {/* 협력업체(담당자)에게 '샘플 보기'로 보여줄 예시 — 이미지·설명 자유 조합 */}
                      <div className="mt-1 flex w-full flex-col gap-2 border-t border-slate-100 pt-2 sm:flex-row sm:items-start">
                        <span className="shrink-0 pt-1 text-xs font-semibold text-slate-500">예시(샘플)</span>
                        <SampleControl typeId={t.id} sampleKey={t.sample_image_key}
                          busy={sampleBusyId === t.id}
                          onUpload={(f) => uploadSample(t.id, f)} onDelete={() => deleteSample(t.id)} />
                        <div className="flex-1">
                          <SampleDescription value={t.sample_description}
                            onSave={(text) => patch(t.id, { sample_description: text })} />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          ))
        )}
      </div>
    </AppShell>
  );
}
