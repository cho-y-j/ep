import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { PageHeader } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { useEquipmentTypes } from '../equipment/useEquipmentTypes';

/**
 * ⚙ 설정 — 취급 장비종류. 장비공급사 master 가 자기가 다루는 장비종류를 미리 지정해 두면
 * 장비 등록·서류 수집요청의 종류 선택이 그 종류만 기본 표시된다. 아무것도 고르지 않으면 전체 표시(기존 동작).
 */
export default function HandledEquipmentTypesPage() {
  const { user } = useAuth();
  const isEquipMaster = user?.role === 'EQUIPMENT_SUPPLIER' && !!user?.is_company_admin;
  const { options } = useEquipmentTypes();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!isEquipMaster) return;
    let alive = true;
    api.get<string[]>('/api/companies/me/equipment-types')
      .then((r) => { if (alive) setSelected(new Set(r.data ?? [])); })
      .catch(() => { if (alive) setSelected(new Set()); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [isEquipMaster]);

  // 종류를 그룹(건설기계/차량/기타)별로 묶어 체크리스트로. options 는 정렬순.
  const groups = useMemo(() => {
    const m = new Map<string, { code: string; name: string }[]>();
    for (const o of options) {
      const g = o.grp || '기타';
      if (!m.has(g)) m.set(g, []);
      m.get(g)!.push({ code: o.code, name: o.name });
    }
    return [...m.entries()];
  }, [options]);

  function toggle(code: string) {
    setSaved(false);
    setSelected((s) => {
      const next = new Set(s);
      if (next.has(code)) next.delete(code); else next.add(code);
      return next;
    });
  }

  async function save() {
    setSaving(true);
    setErr(null);
    setSaved(false);
    try {
      await api.put('/api/companies/me/equipment-types', { codes: [...selected] });
      setSaved(true);
    } catch (e: any) {
      setErr(e?.response?.data?.message || e?.response?.data?.error || '저장 실패');
    } finally {
      setSaving(false);
    }
  }

  if (!isEquipMaster) {
    return (
      <AppShell breadcrumb={[{ label: '취급 장비종류' }]}>
        <div className="card border-amber-200 bg-amber-50 text-sm text-amber-900 max-w-xl mx-auto">
          이 설정은 장비공급사 관리자만 사용할 수 있습니다.
          <div className="mt-2"><Link to="/" className="underline">대시보드로</Link></div>
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell breadcrumb={[{ label: '취급 장비종류' }]}>
      <div className="space-y-4">
        <PageHeader
          title="취급 장비종류"
          subtitle="우리 회사가 다루는 장비종류를 골라 두면, 장비 등록·서류 수집요청의 종류 선택이 이 종류만 먼저 보입니다. 필요할 때 '전체 보기'로 나머지도 볼 수 있습니다. 아무것도 고르지 않으면 전체가 표시됩니다."
          actions={
            <div className="flex items-center gap-2">
              <button type="button" onClick={() => { setSaved(false); setSelected(new Set(options.map((o) => o.code))); }}
                className="btn-ghost" disabled={loading || options.length === 0}>전체 선택</button>
              <button type="button" onClick={() => { setSaved(false); setSelected(new Set()); }}
                className="btn-ghost" disabled={loading}>전체 해제</button>
              <button type="button" onClick={() => void save()} className="btn-primary" disabled={loading || saving}>
                {saving ? '저장 중…' : '저장'}
              </button>
            </div>
          }
        />

        {err && <div className="card border-rose-200 bg-rose-50 text-sm text-rose-800">{err}</div>}
        {saved && <div className="card border-emerald-200 bg-emerald-50 text-sm text-emerald-800">저장했습니다. 선택한 {selected.size}개 종류가 장비 등록·수집요청에서 먼저 표시됩니다.</div>}

        {loading ? (
          <div className="card text-sm text-slate-400">불러오는 중…</div>
        ) : options.length === 0 ? (
          <div className="card text-sm text-slate-400">등록된 장비종류가 없습니다.</div>
        ) : (
          <div className="space-y-4">
            {groups.map(([grp, items]) => (
              <div key={grp} className="card">
                <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">{grp}</div>
                <div className="grid grid-cols-2 gap-x-4 gap-y-1 sm:grid-cols-3">
                  {items.map((it) => (
                    <label key={it.code} className="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-slate-50">
                      <input type="checkbox" checked={selected.has(it.code)} onChange={() => toggle(it.code)}
                        className="h-4 w-4 shrink-0 accent-brand-600" />
                      <span className="truncate text-sm text-slate-700">{it.name}</span>
                    </label>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}
