import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import type { EquipmentResponse } from '../../types/equipment';

/** 차량 관리 — 정기검사/오일/등록 만료 due(편집) + 조종원 일상점검 이력. */
export type DailyInsp = {
  id: number;
  inspect_date: string;
  inspector_name?: string | null;
  overall?: string | null;
  notes?: string | null;
  items?: string | null;
  // R3: 조종원 보고 가동시간(아워미터)·운행거리(km). 선택 — 표시 전용.
  hour_meter?: number | null;
  odometer_km?: number | null;
};

type CheckItem = { key?: string; label?: string; result?: string; note?: string };

function parseItems(s?: string | null): CheckItem[] {
  if (!s) return [];
  try {
    const v = JSON.parse(s);
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
}

function itemBadge(result?: string): { txt: string; cls: string } {
  const r = (result ?? '').toUpperCase();
  if (r === 'OK' || r === 'PASS' || r === '양호') return { txt: '양호', cls: 'bg-emerald-100 text-emerald-700' };
  if (r === 'FAIL' || r === '불량') return { txt: '불량', cls: 'bg-rose-100 text-rose-700' };
  if (r === 'CHECK' || r === 'ATTENTION' || r === '점검필요') return { txt: '점검필요', cls: 'bg-amber-100 text-amber-700' };
  return { txt: result || '-', cls: 'bg-slate-100 text-slate-600' };
}

function dday(date?: string | null): { txt: string; cls: string } | null {
  if (!date) return null;
  const today = new Date().setHours(0, 0, 0, 0);
  const d = Math.round((new Date(date + 'T00:00:00').getTime() - today) / 86400000);
  if (d < 0) return { txt: `D+${-d} 만료`, cls: 'bg-rose-100 text-rose-700' };
  if (d === 0) return { txt: '오늘', cls: 'bg-rose-100 text-rose-700' };
  if (d <= 14) return { txt: `D-${d}`, cls: 'bg-amber-100 text-amber-700' };
  return { txt: `D-${d}`, cls: 'bg-slate-100 text-slate-500' };
}

export default function EquipmentDuePanel({ equipment, canEdit, onSaved, history }: {
  equipment: EquipmentResponse; canEdit: boolean; onSaved: () => void; history: DailyInsp[];
}) {
  const [editing, setEditing] = useState(false);
  const [insp, setInsp] = useState('');
  const [oil, setOil] = useState('');
  const [reg, setReg] = useState('');
  const [saving, setSaving] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);

  useEffect(() => {
    setInsp(equipment.inspection_due_date ?? '');
    setOil(equipment.oil_change_due_date ?? '');
    setReg(equipment.registration_expiry ?? '');
  }, [equipment.inspection_due_date, equipment.oil_change_due_date, equipment.registration_expiry]);

  const save = async () => {
    setSaving(true);
    try {
      await api.patch(`/api/equipment/${equipment.id}/due-dates`, {
        inspection_due_date: insp || null,
        oil_change_due_date: oil || null,
        registration_expiry: reg || null,
      });
      toast.success('차량 관리 일정 저장됨');
      setEditing(false);
      onSaved();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '저장 실패');
    } finally { setSaving(false); }
  };

  const rows: Array<[string, string | null | undefined]> = [
    ['정기검사', equipment.inspection_due_date],
    ['오일교체', equipment.oil_change_due_date],
    ['차량등록 만료', equipment.registration_expiry],
  ];

  // R3: 조종원이 보고한 가동시간/운행거리가 있는 점검만 추려 일자별 추이 표시(표시 전용, 정본 아님).
  const reported = history.filter((h) => h.hour_meter != null || h.odometer_km != null);

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-base font-bold">차량 관리 (검사·정비)</h3>
        {canEdit && !editing && (
          <button onClick={() => setEditing(true)} className="text-sm font-semibold text-brand-700 hover:text-brand-800">일정 수정</button>
        )}
      </div>

      {editing ? (
        <div className="space-y-3">
          <DateField label="정기검사일" value={insp} onChange={setInsp} />
          <DateField label="오일교체일" value={oil} onChange={setOil} />
          <DateField label="차량등록 만료일" value={reg} onChange={setReg} />
          <div className="flex justify-end gap-2 pt-1">
            <button onClick={() => setEditing(false)} className="rounded px-3 py-1.5 text-sm hover:bg-slate-100">취소</button>
            <button onClick={save} disabled={saving} className="btn-primary disabled:opacity-50">{saving ? '저장…' : '저장'}</button>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          {rows.map(([label, date]) => {
            const dd = dday(date);
            return (
              <div key={label} className="rounded-lg border border-slate-100 p-3">
                <div className="text-xs text-slate-500">{label}</div>
                <div className="mt-1 flex items-center gap-2">
                  <span className="font-semibold text-slate-900">{date ?? '미설정'}</span>
                  {dd && <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold ${dd.cls}`}>{dd.txt}</span>}
                </div>
              </div>
            );
          })}
          {/* S4'(P3a): 가동시간 기반 정비 — oil_change(날짜)와 병존. */}
          <div className={`rounded-lg border p-3 ${equipment.maintenance_due ? 'border-rose-200 bg-rose-50' : 'border-slate-100'}`}>
            <div className="text-xs text-slate-500">가동시간 정비</div>
            <div className="mt-1 flex flex-wrap items-center gap-2">
              <span className="font-semibold text-slate-900">누적 {equipment.cumulative_work_hours}h</span>
              {equipment.maintenance_interval_hours != null ? (
                <span className="text-xs text-slate-500">/ 주기 {equipment.maintenance_interval_hours}h</span>
              ) : (
                <span className="text-xs text-slate-400">주기 미설정</span>
              )}
              {equipment.maintenance_due && (
                <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-rose-100 text-rose-700">정비 도래</span>
              )}
            </div>
          </div>
        </div>
      )}

      <div className="mt-5 border-t border-slate-100 pt-4">
        <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700">
          가동시간 추이
          <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-normal text-slate-500">조종원 보고 · 표시용</span>
        </div>
        {reported.length === 0 ? (
          <p className="py-3 text-center text-sm text-slate-400">조종원이 보고한 가동시간/운행거리가 아직 없습니다</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-slate-400">
                  <th className="py-1.5 pr-3 font-medium">일자</th>
                  <th className="py-1.5 pr-3 font-medium text-right">아워미터 (h)</th>
                  <th className="py-1.5 font-medium text-right">운행거리 (km)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {reported.slice(0, 12).map((h) => (
                  <tr key={h.id}>
                    <td className="py-1.5 pr-3 text-slate-700">{h.inspect_date}</td>
                    <td className="py-1.5 pr-3 text-right font-medium text-slate-900">{h.hour_meter != null ? Number(h.hour_meter).toLocaleString() : '-'}</td>
                    <td className="py-1.5 text-right font-medium text-slate-900">{h.odometer_km != null ? Number(h.odometer_km).toLocaleString() : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="mt-5 border-t border-slate-100 pt-4">
        <div className="mb-2 text-sm font-semibold text-slate-700">일상점검 이력 ({history.length})</div>
        {history.length === 0 ? (
          <p className="py-4 text-center text-sm text-slate-400">아직 일상점검 기록이 없습니다 (조종원이 앱에서 매일 제출)</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {history.slice(0, 10).map((h) => {
              const items = parseItems(h.items);
              const open = openId === h.id;
              return (
                <li key={h.id} className="py-2.5 text-sm">
                  <button
                    type="button"
                    onClick={() => items.length > 0 && setOpenId(open ? null : h.id)}
                    className={`flex w-full items-center justify-between gap-3 text-left ${items.length > 0 ? '' : 'cursor-default'}`}
                  >
                    <div className="min-w-0">
                      <span className="font-medium text-slate-800">{h.inspect_date}</span>
                      {h.inspector_name && <span className="text-slate-500"> · {h.inspector_name}</span>}
                      {h.notes && <div className="truncate text-xs text-slate-500">{h.notes}</div>}
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                        h.overall === 'PASS' ? 'bg-emerald-100 text-emerald-700'
                          : h.overall === 'FAIL' ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-700'
                      }`}>{h.overall === 'PASS' ? '정상' : h.overall === 'FAIL' ? '이상' : '주의'}</span>
                      {items.length > 0 && <span className="text-[10px] text-slate-400">{open ? '▲' : `▼ ${items.length}`}</span>}
                    </div>
                  </button>
                  {open && items.length > 0 && (
                    <ul className="mt-2 space-y-1 rounded-lg bg-slate-50 p-2.5">
                      {items.map((it, i) => {
                        const b = itemBadge(it.result);
                        return (
                          <li key={i} className="flex items-center justify-between gap-2 text-xs">
                            <span className="min-w-0 truncate text-slate-700">{it.label || it.key || `항목 ${i + 1}`}</span>
                            <div className="flex shrink-0 items-center gap-2">
                              {it.note && <span className="max-w-[8rem] truncate text-slate-400">{it.note}</span>}
                              <span className={`rounded px-1.5 py-0.5 font-semibold ${b.cls}`}>{b.txt}</span>
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

function DateField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="flex items-center justify-between gap-3 text-sm">
      <span className="text-slate-600">{label}</span>
      <input type="date" value={value} onChange={(e) => onChange(e.target.value)} className="input h-9 w-44" />
    </label>
  );
}
