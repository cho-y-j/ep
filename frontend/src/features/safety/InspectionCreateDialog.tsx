import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import type { CreateInspectionPayload, InspectionKind, InspectionTarget } from '../../types/safety';

type ResourceOption = { id: number; label: string };

type Props = {
  siteId: number;
  onClose: () => void;
  onCreated: () => void;
};

export default function InspectionCreateDialog({ siteId, onClose, onCreated }: Props) {
  const [kind, setKind] = useState<InspectionKind>('VEHICLE_INSPECTION');
  const [targetType, setTargetType] = useState<InspectionTarget>('VEHICLE');
  const [targetId, setTargetId] = useState<number | null>(null);
  const [scheduledAt, setScheduledAt] = useState('');
  const [duration, setDuration] = useState('60');
  const [options, setOptions] = useState<ResourceOption[]>([]);
  const [busy, setBusy] = useState(false);

  // VEHICLE / PERSON 별 옵션 로드 — 시연용 단순화: 전체 list
  useEffect(() => {
    const path = targetType === 'VEHICLE' ? '/api/equipment' : '/api/persons?size=200';
    api.get(path).then((res: any) => {
      const list = Array.isArray(res.data) ? res.data : (res.data.content ?? []);
      setOptions(list.map((r: any) => ({
        id: r.id,
        label: targetType === 'VEHICLE'
          ? (r.vehicle_no ?? r.model ?? `장비 #${r.id}`)
          : r.name,
      })));
    }).catch(() => setOptions([]));
  }, [targetType]);

  async function submit() {
    if (!targetId || !scheduledAt) {
      toast.error('대상 자원과 일정을 선택하세요');
      return;
    }
    setBusy(true);
    try {
      const payload: CreateInspectionPayload = {
        site_id: siteId,
        target_type: targetType,
        target_id: targetId,
        kind,
        scheduled_at: scheduledAt.length === 16 ? scheduledAt + ':00' : scheduledAt,
        duration_minutes: duration ? Number(duration) : null,
      };
      await api.post('/api/safety-inspections', payload);
      toast.success('일정 등록됨');
      onCreated();
    } catch (err) {
      toast.error(err instanceof AxiosError ? (err.response?.data?.message ?? '실패') : '실패');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-5 py-3 border-b">
          <h2 className="text-base font-bold">안전점검 일정 등록</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700 text-xl">×</button>
        </div>
        <div className="p-5 space-y-3">
          <label className="block">
            <span className="text-xs font-semibold text-slate-500">검사 종류</span>
            <select value={kind} onChange={(e) => setKind(e.target.value as InspectionKind)}
                    className="input mt-1 bg-white w-full">
              <option value="VEHICLE_INSPECTION">차량검사 (사전 — 며칠 전)</option>
              <option value="ENTRY_CHECK">입소검사 (당일 — 시간)</option>
            </select>
          </label>

          <label className="block">
            <span className="text-xs font-semibold text-slate-500">대상 유형</span>
            <div className="flex gap-2 mt-1">
              <button type="button" onClick={() => { setTargetType('VEHICLE'); setTargetId(null); }}
                      className={`flex-1 px-3 py-1.5 rounded border text-sm ${targetType === 'VEHICLE' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-300'}`}>
                장비
              </button>
              <button type="button" onClick={() => { setTargetType('PERSON'); setTargetId(null); }}
                      className={`flex-1 px-3 py-1.5 rounded border text-sm ${targetType === 'PERSON' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-300'}`}>
                인원
              </button>
            </div>
          </label>

          <label className="block">
            <span className="text-xs font-semibold text-slate-500">대상</span>
            <select value={targetId ?? ''} onChange={(e) => setTargetId(e.target.value ? Number(e.target.value) : null)}
                    className="input mt-1 bg-white w-full">
              <option value="">선택…</option>
              {options.map((o) => <option key={o.id} value={o.id}>{o.label}</option>)}
            </select>
          </label>

          <label className="block">
            <span className="text-xs font-semibold text-slate-500">일정 (날짜 + 시간)</span>
            <input type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)}
                   className="input mt-1 w-full" />
          </label>

          <label className="block">
            <span className="text-xs font-semibold text-slate-500">소요 시간 (분)</span>
            <input type="number" value={duration} onChange={(e) => setDuration(e.target.value)}
                   className="input mt-1 w-full" />
          </label>
        </div>
        <div className="flex justify-end gap-2 px-5 py-3 border-t bg-slate-50">
          <button onClick={onClose} className="px-4 py-2 rounded-lg text-slate-700 hover:bg-slate-100">취소</button>
          <button onClick={submit} disabled={busy} className="btn-primary disabled:opacity-50">
            {busy ? '등록 중…' : '등록'}
          </button>
        </div>
      </div>
    </div>
  );
}
