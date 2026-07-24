import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import SearchInput from '../../components/ui/SearchInput';
import { PERSON_ROLE_LABEL, type PersonRole } from '../../types/person';
import { type ComboOperatorCheck } from '../readiness/DeployCheckCard';

interface OperatorItem { id: number; person_id: number; person_name?: string | null; priority: number; }
interface PersonOption { id: number; name: string; supplier_id?: number; roles?: string[]; team?: string; }

interface Props {
  equipmentId: number;
  /** 장비공급사 id — 자기 회사 OPERATOR 인원 목록 fetch 용. */
  supplierId: number;
  /** true 면 편집 가능. false 면 readonly 표시. */
  canEdit: boolean;
  /** R1 조합 판정(deploy-check-combo) 조종원별 결과 — 서류 상태 배지 산출용(부모가 1회 fetch 재사용). */
  operatorChecks?: ComboOperatorCheck[];
}

/** V36: 장비의 기본 조종원 목록 (우선순위 N명). 견적/작업계획서 자동 prefill 대상. */
export default function EquipmentDefaultOperators({ equipmentId, supplierId, canEdit, operatorChecks }: Props) {
  const { user } = useAuth();
  const [items, setItems] = useState<OperatorItem[]>([]);
  const [candidates, setCandidates] = useState<PersonOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState(''); // 추가 후보 이름 필터(즉시, 클라이언트)

  useEffect(() => {
    load();
    if (canEdit) {
      api.get<any>('/api/persons?size=200').then((r) => {
        const list = Array.isArray(r.data) ? r.data : (r.data.content ?? []);
        // 백엔드가 이미 actor 권한별로 인원을 필터링 (BP 는 자기 회사 + 자기 사이트 ACTIVE 참여 공급사).
        // 그 안에서 OPERATOR 역할 가진 인원만 후보로. 직종을 바꾸려면 인원 목록에서 직접 수정.
        setCandidates(list.filter((p: PersonOption) => (p.roles ?? []).includes('OPERATOR')));
      }).catch(() => setCandidates([]));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [equipmentId, supplierId, canEdit, user?.company_id]);

  const load = () => {
    setLoading(true);
    api.get<OperatorItem[]>(`/api/equipment/${equipmentId}/default-operators`)
      .then((r) => setItems(r.data))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  };

  const save = async (newIds: number[]) => {
    setSaving(true); setError(null);
    try {
      const r = await api.put<OperatorItem[]>(
        `/api/equipment/${equipmentId}/default-operators`,
        { person_ids: newIds }
      );
      setItems(r.data);
    } catch (e: any) {
      setError(e?.response?.data?.message || '저장 실패');
    } finally {
      setSaving(false);
    }
  };

  // 응답의 person_name 우선 — 조회 전용(BP 등, candidates 미로드)에서도 이름이 보이게. 폴백은 기존 그대로.
  const personLabel = (it: OperatorItem) =>
    it.person_name ?? candidates.find((c) => c.id === it.person_id)?.name ?? `인원 #${it.person_id}`;

  // 조종원별 서류 상태 — combo 판정의 DOCUMENT 게이트(그 인원의 미검증/만료 필수서류 종류 수) 재사용.
  // combo 미로드/판정 밖이면 null → 배지 숨김(과장 표시 방지).
  const docMissingOf = (personId: number): number | null => {
    const check = operatorChecks?.find((o) => o.person_id === personId);
    if (!check) return null;
    return check.blocks.filter((b) => b.kind === 'DOCUMENT').length;
  };

  const handleRemove = (pid: number) => {
    save(items.filter((it) => it.person_id !== pid).map((it) => it.person_id));
  };

  return (
    <section className="card p-4">
      <div className="flex items-center justify-between mb-2">
        <div>
          <h3 className="text-sm font-bold text-slate-900">조합(교대조) 조종원</h3>
          <p className="text-[11px] text-slate-500 mt-0.5">
            이 조합으로 검사·투입·정산이 이어집니다. 견적/작업계획서 작성 시 자동 매칭 — 1순위가 가장 먼저.
          </p>
        </div>
        {loading && <span className="text-xs text-slate-400">로딩...</span>}
      </div>

      {items.length === 0 ? (
        <div className="text-xs text-slate-400 italic py-3 text-center border border-dashed border-slate-200 rounded">
          등록된 조합(교대조) 조종원이 없습니다.
        </div>
      ) : (
        <ul className="space-y-1">
          {items.map((it) => {
            const cand = candidates.find((c) => c.id === it.person_id);
            const docMissing = docMissingOf(it.person_id);
            return (
              <li key={it.id} className="flex flex-wrap items-center gap-2 px-2 py-1.5 rounded border border-slate-200 bg-slate-50">
                <span className="w-1.5 h-1.5 rounded-full bg-brand-500 shrink-0" />
                <span className="text-sm text-slate-900 flex-1 min-w-0 truncate">{personLabel(it)}</span>
                {cand?.team && (
                  <span className="text-[11px] px-1.5 py-0.5 rounded-full bg-slate-200 text-slate-700 shrink-0">{cand.team}</span>
                )}
                {docMissing != null && (
                  <span className={`text-[11px] px-1.5 py-0.5 rounded-full font-semibold shrink-0 ${
                    docMissing === 0 ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-800'}`}>
                    {docMissing === 0 ? '서류 완비' : `서류 ${docMissing}건 미비`}
                  </span>
                )}
                <Link to={`/persons/${it.person_id}`}
                  className="text-xs px-2 py-0.5 rounded border border-slate-300 text-slate-700 hover:bg-slate-50 shrink-0">
                  서류 보기
                </Link>
                {canEdit && (
                  <button type="button" onClick={() => handleRemove(it.person_id)} disabled={saving}
                    className="text-xs px-2 py-0.5 rounded border border-rose-300 text-rose-700 hover:bg-rose-50 disabled:opacity-30">제거</button>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {canEdit && (() => {
        const notAdded = candidates.filter((c) => !items.some((it) => it.person_id === c.id));
        const q = query.trim().toLowerCase();
        const available = q ? notAdded.filter((c) => (c.name ?? '').toLowerCase().includes(q)) : notAdded;
        return (
          <div className="mt-3 pt-3 border-t border-slate-100">
            <div className="flex items-center justify-between gap-2 mb-2">
              <div className="text-xs font-medium text-slate-600">
                추가할 수 있는 인원 ({available.length}명)
              </div>
              {notAdded.length > 0 && (
                <SearchInput value={query} onChange={setQuery} placeholder="이름 검색" className="w-40 shrink-0" />
              )}
            </div>
            <ul className="space-y-1">
              {notAdded.length === 0 ? (
                <li className="text-xs text-slate-400 italic py-3 text-center border border-dashed border-slate-200 rounded">
                  추가할 인원이 없습니다
                </li>
              ) : available.length === 0 ? (
                <li className="text-xs text-slate-400 italic py-3 text-center border border-dashed border-slate-200 rounded">
                  '{query.trim()}' 검색 결과 없음
                </li>
              ) : available.map((c) => (
                <li key={c.id} className="flex items-center gap-2 px-2 py-1.5 rounded border border-slate-200 bg-white hover:bg-slate-50">
                  <span className="text-sm text-slate-900 flex-1 truncate">{c.name}</span>
                  {c.roles && c.roles.length > 0 && (
                    <span className="text-[10px] text-slate-500">
                      {c.roles.map((r) => PERSON_ROLE_LABEL[r as PersonRole] ?? r).join(', ')}
                    </span>
                  )}
                  <button type="button"
                    onClick={() => {
                      // optimistic update — 다음 클릭이 빨라도 최신 items 반영. 응답 받으면 setItems(r.data) 로 정확한 priority 덮어쓰기.
                      const newIds = [...items.map((it) => it.person_id), c.id];
                      setItems(newIds.map((pid, i) => ({ id: -1 - i, person_id: pid, priority: i + 1 })));
                      save(newIds);
                    }}
                    disabled={saving}
                    className="text-xs px-2 py-0.5 rounded bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50">
                    + 추가
                  </button>
                </li>
              ))}
            </ul>
          </div>
        );
      })()}

      {error && <p className="text-xs text-rose-600 mt-2">{error}</p>}
    </section>
  );
}
