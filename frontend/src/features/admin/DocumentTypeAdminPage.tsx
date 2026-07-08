import { useEffect, useMemo, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
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

export default function DocumentTypeAdminPage() {
  const [types, setTypes] = useState<DocType[]>([]);
  const [loading, setLoading] = useState(true);
  // 신규 추가 폼
  const [newName, setNewName] = useState('');
  const [newAppliesTo, setNewAppliesTo] = useState<DocType['applies_to']>('PERSON');
  const [newRequired, setNewRequired] = useState(false);
  const [newHasExpiry, setNewHasExpiry] = useState(false);
  const [adding, setAdding] = useState(false);

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
    return out as Partial<DocType>;
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

  const groups = useMemo(() => ({
    PERSON: types.filter((t) => t.applies_to === 'PERSON'),
    EQUIPMENT: types.filter((t) => t.applies_to === 'EQUIPMENT'),
    COMPANY: types.filter((t) => t.applies_to === 'COMPANY'),
  }), [types]);

  return (
    <AppShell breadcrumb={[{ label: '서류종류 관리' }]}>
      <div className="max-w-5xl mx-auto px-6 py-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold">서류종류 관리</h1>
          <p className="text-sm text-slate-500 mt-1">
            자원별 필수/선택 서류와 역할·카테고리 적용 범위를 지정합니다.
            예: 조종원에게 신분증·보험증을 필수로 지정하면, 해당 인원 서류 등록 시 "필수"로 묶여 표시됩니다.
          </p>
        </div>

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
                      <label className="flex items-center gap-1.5 text-xs text-slate-500 ml-auto">
                        <input type="checkbox" checked={t.active}
                               onChange={(e) => patch(t.id, { active: e.target.checked })} />
                        활성
                      </label>
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
