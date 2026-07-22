import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { FilterSelect } from '../../components/ui';
import type { CompanyResponse } from '../../types/auth';
import type { OwnerType } from '../../types/document';
import type { EquipmentResponse } from '../../types/equipment';
import { equipmentCategoryLabel, EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory } from '../../types/equipment';
import { useHandledEquipmentTypes, handledCodeFilter } from '../equipment/useHandledEquipmentTypes';
import type { PersonResponse, PersonRole } from '../../types/person';
import { ALL_PERSON_ROLES, PERSON_ROLE_LABEL } from '../../types/person';
import { targetKey, type PickedTarget } from './target';

type Operator = { person_id: number; person_name?: string | null };
type BatchResponse = { results: Array<{ equipment_id: number; operators: Operator[] }> };

const equipLabel = (e: EquipmentResponse) =>
  e.vehicle_no || e.model || equipmentCategoryLabel(e.category) || `장비 #${e.id}`;

/**
 * 서류 수집 대상(장비·인력) 다중 선택.
 * 장비는 1대에 교대조 조종원 N명이 붙으므로, 선택 시 그 장비의 기본 조종원 전원을 함께 담는다
 * (POST /api/equipment/default-operators 배치 1회. 기본 조종원이 없으면 operator_person_id 폴백).
 */
export default function TargetPicker({ value, onChange }: {
  value: PickedTarget[];
  onChange: (v: PickedTarget[]) => void;
}) {
  const [tab, setTab] = useState<OwnerType>('EQUIPMENT');
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [equipment, setEquipment] = useState<EquipmentResponse[]>([]);
  const [persons, setPersons] = useState<PersonResponse[]>([]);
  const [personTotal, setPersonTotal] = useState(0);
  const [operators, setOperators] = useState<Record<number, Operator[]>>({});
  const [loading, setLoading] = useState(false);

  // 필터
  const [supplierId, setSupplierId] = useState('');
  const [category, setCategory] = useState('');
  const [role, setRole] = useState('');
  const [q, setQ] = useState('');

  // 취급 장비종류 설정이 있으면 종류 필터를 그 종류만 기본 표시 + '전체 보기' 토글. 없으면 전체(기존 동작).
  const handledTypes = useHandledEquipmentTypes();
  const [showAllTypes, setShowAllTypes] = useState(false);
  const typePass = handledCodeFilter(handledTypes, showAllTypes);
  const shownCatKeys = (Object.keys(EQUIPMENT_CATEGORY_LABEL) as EquipmentCategory[])
    .filter((c) => !typePass || typePass(c));

  // 장비별 "조종원 함께" — 기본 ON(장비 고르면 교대조가 따라오는 게 기본 기대).
  const [withOperators, setWithOperators] = useState<Record<number, boolean>>({});
  const opsOn = (id: number) => withOperators[id] ?? true;

  const picked = useMemo(() => new Set(value.map(targetKey)), [value]);

  // '취급만'으로 좁혀졌는데 선택된 종류가 목록 밖이면 종류 필터를 초기화(전체).
  useEffect(() => {
    if (typePass && category && !typePass(category)) setCategory('');
  }, [handledTypes, showAllTypes]); // eslint-disable-line react-hooks/exhaustive-deps

  // 협력업체 목록 = 내가 등록한 하위공급사(/children)만. 탭(장비/인력) 유형에 맞게 필터.
  useEffect(() => {
    let alive = true;
    const want = tab === 'EQUIPMENT' ? 'EQUIPMENT' : 'MANPOWER';
    api.get<CompanyResponse[]>('/api/companies/children')
      .then((r) => { if (alive) setCompanies((r.data ?? []).filter((c) => c.type === want)); })
      .catch(() => { if (alive) setCompanies([]); });
    return () => { alive = false; };
  }, [tab]);

  // 장비 목록 + 그 장비들의 기본 조종원(배치 1회).
  useEffect(() => {
    if (tab !== 'EQUIPMENT') return;
    let alive = true;
    setLoading(true);
    api.get<EquipmentResponse[]>('/api/equipment', {
      params: { supplierId: supplierId || undefined, category: category || undefined },
    })
      .then(async (r) => {
        if (!alive) return;
        setEquipment(r.data);
        const ids = r.data.map((e) => e.id);
        if (ids.length === 0) { setOperators({}); return; }
        const b = await api.post<BatchResponse>('/api/equipment/default-operators', { equipment_ids: ids });
        if (!alive) return;
        const map: Record<number, Operator[]> = {};
        b.data.results.forEach((res) => { map[res.equipment_id] = res.operators; });
        setOperators(map);
      })
      .catch(() => { if (alive) { setEquipment([]); setOperators({}); } })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [tab, supplierId, category]);

  // 인원 목록 — 검색은 서버 필터(q). size 는 백엔드 상한 100.
  useEffect(() => {
    if (tab !== 'PERSON') return;
    let alive = true;
    setLoading(true);
    const t = setTimeout(() => {
      api.get<{ content: PersonResponse[]; total_elements: number }>('/api/persons', {
        params: { supplierId: supplierId || undefined, role: role || undefined, q: q.trim() || undefined, size: 100 },
      })
        .then((r) => { if (alive) { setPersons(r.data.content); setPersonTotal(r.data.total_elements); } })
        .catch(() => { if (alive) { setPersons([]); setPersonTotal(0); } })
        .finally(() => { if (alive) setLoading(false); });
    }, 250);
    return () => { alive = false; clearTimeout(t); };
  }, [tab, supplierId, role, q]);

  const qLower = q.trim().toLowerCase();
  const shownEquipment = useMemo(
    () => (qLower ? equipment.filter((e) => equipLabel(e).toLowerCase().includes(qLower)) : equipment),
    [equipment, qLower],
  );

  /** 장비 1대가 담을 대상들 — 장비 + (토글 ON 이면) 그 장비 조종원 전원. */
  function equipmentTargets(e: EquipmentResponse): PickedTarget[] {
    const label = equipLabel(e);
    const out: PickedTarget[] = [{ owner_type: 'EQUIPMENT', owner_id: e.id, label }];
    if (!opsOn(e.id)) return out;
    (operators[e.id] ?? []).forEach((op) => {
      out.push({
        owner_type: 'PERSON', owner_id: op.person_id, label: op.person_name || `인원 #${op.person_id}`,
        via_equipment_id: e.id, via_equipment_label: label,
      });
    });
    return out;
  }

  function add(list: PickedTarget[]) {
    const next = [...value];
    const seen = new Set(next.map(targetKey));
    list.forEach((t) => { if (seen.add(targetKey(t))) next.push(t); });
    onChange(next);
  }

  /** 장비 해제 = 그 장비 + 그 장비 때문에 담긴 조종원까지 제거. */
  function removeEquipment(id: number) {
    onChange(value.filter((t) => !(t.owner_type === 'EQUIPMENT' && t.owner_id === id) && t.via_equipment_id !== id));
  }

  function toggleEquipment(e: EquipmentResponse) {
    if (picked.has(targetKey({ owner_type: 'EQUIPMENT', owner_id: e.id }))) removeEquipment(e.id);
    else add(equipmentTargets(e));
  }

  /** 조종원 토글 — 이미 고른 장비면 즉시 조종원을 넣거나 뺀다. */
  function toggleWithOperators(e: EquipmentResponse) {
    const on = !opsOn(e.id);
    setWithOperators((s) => ({ ...s, [e.id]: on }));
    if (!picked.has(targetKey({ owner_type: 'EQUIPMENT', owner_id: e.id }))) return;
    if (on) {
      const label = equipLabel(e);
      add((operators[e.id] ?? []).map((op) => ({
        owner_type: 'PERSON' as OwnerType, owner_id: op.person_id, label: op.person_name || `인원 #${op.person_id}`,
        via_equipment_id: e.id, via_equipment_label: label,
      })));
    } else {
      onChange(value.filter((t) => t.via_equipment_id !== e.id));
    }
  }

  function togglePerson(p: PersonResponse) {
    const key = targetKey({ owner_type: 'PERSON', owner_id: p.id });
    if (picked.has(key)) onChange(value.filter((t) => targetKey(t) !== key));
    else add([{ owner_type: 'PERSON', owner_id: p.id, label: p.name }]);
  }

  function selectAllShown() {
    if (tab === 'EQUIPMENT') add(shownEquipment.flatMap(equipmentTargets));
    else add(persons.map((p) => ({ owner_type: 'PERSON' as OwnerType, owner_id: p.id, label: p.name })));
  }

  function clearAllShown() {
    if (tab === 'EQUIPMENT') {
      const ids = new Set(shownEquipment.map((e) => e.id));
      onChange(value.filter((t) => !(t.owner_type === 'EQUIPMENT' && ids.has(t.owner_id))
        && (t.via_equipment_id === undefined || !ids.has(t.via_equipment_id))));
    } else {
      const ids = new Set(persons.map((p) => p.id));
      onChange(value.filter((t) => !(t.owner_type === 'PERSON' && ids.has(t.owner_id))));
    }
  }

  const supplierOptions = companies.map((c) => ({ value: String(c.id), label: c.name }));

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <div className="inline-flex overflow-hidden rounded-lg border border-slate-300">
          {(['EQUIPMENT', 'PERSON'] as const).map((t) => (
            <button key={t} type="button" onClick={() => { setTab(t); setQ(''); setSupplierId(''); }}
              className={`px-4 py-2 text-sm font-semibold ${tab === t ? 'bg-brand-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>
              {t === 'EQUIPMENT' ? '장비(차량)' : '인력'}
            </button>
          ))}
        </div>
        <div className="flex gap-2">
          <button type="button" onClick={selectAllShown}
            className="rounded-md border border-brand-300 bg-brand-50 px-2.5 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-100">
            현재 목록 전체 선택
          </button>
          <button type="button" onClick={clearAllShown}
            className="rounded-md border border-slate-300 px-2.5 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50">
            전체 해제
          </button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        <FilterSelect value={supplierId} onChange={setSupplierId} placeholder="협력업체 전체" options={supplierOptions} />
        {tab === 'EQUIPMENT' ? (
          <div className="flex items-center gap-2">
            <FilterSelect value={category} onChange={setCategory} placeholder="종류 전체"
              options={shownCatKeys.map((c) => ({ value: c, label: EQUIPMENT_CATEGORY_LABEL[c] }))} />
            {handledTypes && handledTypes.length > 0 && (
              <button type="button" onClick={() => setShowAllTypes((v) => !v)}
                className="shrink-0 text-xs font-medium text-brand-600 hover:underline">
                {showAllTypes ? '취급만' : '전체 보기'}
              </button>
            )}
          </div>
        ) : (
          <FilterSelect value={role} onChange={setRole} placeholder="역할 전체"
            options={ALL_PERSON_ROLES.map((r: PersonRole) => ({ value: r, label: PERSON_ROLE_LABEL[r] }))} />
        )}
        <input value={q} onChange={(e) => setQ(e.target.value)}
          placeholder={tab === 'EQUIPMENT' ? '차량번호·모델 검색' : '이름 검색'}
          className="input flex-1 min-w-[160px]" />
      </div>

      <div className="max-h-72 divide-y divide-slate-100 overflow-y-auto rounded-lg border border-slate-200">
        {loading ? <p className="p-3 text-xs text-slate-400">불러오는 중…</p>
          : tab === 'EQUIPMENT' ? (
            shownEquipment.length === 0 ? <p className="p-3 text-xs text-slate-400">조건에 맞는 장비가 없습니다.</p> :
              shownEquipment.map((e) => {
                const ops = operators[e.id] ?? [];
                const checked = picked.has(targetKey({ owner_type: 'EQUIPMENT', owner_id: e.id }));
                return (
                  <div key={e.id} className="flex items-center justify-between gap-3 px-3 py-2">
                    <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-2">
                      <input type="checkbox" checked={checked} onChange={() => toggleEquipment(e)}
                        className="h-4 w-4 shrink-0 accent-brand-600" />
                      <span className="truncate text-sm text-slate-700">{equipLabel(e)}</span>
                      <span className="shrink-0 text-xs text-slate-400">{equipmentCategoryLabel(e.category)}</span>
                    </label>
                    <label className={`flex shrink-0 items-center gap-1.5 text-xs font-semibold ${ops.length === 0 ? 'cursor-not-allowed text-slate-300' : 'cursor-pointer text-slate-600'}`}
                      title={ops.length === 0 ? '등록된 기본 조종원이 없습니다' : ops.map((o) => o.person_name).join(', ')}>
                      <input type="checkbox" disabled={ops.length === 0} checked={ops.length > 0 && opsOn(e.id)}
                        onChange={() => toggleWithOperators(e)} className="h-3.5 w-3.5 accent-brand-600" />
                      조종원 함께 추가{ops.length > 0 ? ` (${ops.length}명)` : ''}
                    </label>
                  </div>
                );
              })
          ) : (
            persons.length === 0 ? <p className="p-3 text-xs text-slate-400">조건에 맞는 인원이 없습니다.</p> :
              persons.map((p) => (
                <label key={p.id} className="flex cursor-pointer items-center gap-2 px-3 py-2">
                  <input type="checkbox" checked={picked.has(targetKey({ owner_type: 'PERSON', owner_id: p.id }))}
                    onChange={() => togglePerson(p)} className="h-4 w-4 shrink-0 accent-brand-600" />
                  <span className="truncate text-sm text-slate-700">{p.name}</span>
                  <span className="shrink-0 text-xs text-slate-400">
                    {p.roles.map((r) => PERSON_ROLE_LABEL[r]).join('·')}
                  </span>
                </label>
              ))
          )}
      </div>
      {tab === 'PERSON' && personTotal > persons.length && (
        <p className="text-xs text-slate-400">전체 {personTotal}명 중 {persons.length}명 표시 — 검색·필터로 좁혀주세요.</p>
      )}

      <div>
        <div className="mb-1 text-xs font-semibold text-slate-500">선택한 대상 {value.length}건</div>
        {value.length === 0 ? <p className="text-xs text-slate-400">아직 선택한 대상이 없습니다.</p> :
          <div className="flex flex-wrap gap-1.5">
            {value.map((t) => (
              <span key={targetKey(t)}
                className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold ${
                  t.owner_type === 'EQUIPMENT' ? 'bg-brand-50 text-brand-700' : 'bg-slate-100 text-slate-600'}`}>
                {t.label}
                {t.via_equipment_label && <span className="font-normal text-slate-400">· {t.via_equipment_label} 조종원</span>}
                <button type="button" aria-label={`${t.label} 해제`}
                  onClick={() => (t.owner_type === 'EQUIPMENT'
                    ? removeEquipment(t.owner_id)
                    : onChange(value.filter((x) => targetKey(x) !== targetKey(t))))}
                  className="text-slate-400 hover:text-slate-700">✕</button>
              </span>
            ))}
          </div>}
      </div>
    </div>
  );
}
