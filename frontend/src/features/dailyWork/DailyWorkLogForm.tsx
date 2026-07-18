import { useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import { OT_COLS, type DailyWorkLog, type RateType } from './types';

export type FormOptions = {
  equipment: { id: number; label: string }[];
  persons: { id: number; name: string }[];
  contracts: { id: number; title: string; rate_type: RateType; bp_company_id: number | null; site_id: number | null; site_name: string | null }[];
  sites: { id: number; name: string }[];
  bps: { id: number; name: string }[];
};

type OtKey = (typeof OT_COLS)[number]['key'];

type FormState = {
  work_date: string;
  site_id: number | '';
  site_name: string;
  bp_company_id: number | '';
  contract_id: number | '';
  equipment_id: number | '';
  person_id: number | '';
  work_content: string;
  work_location: string;
  rate_type: RateType;
  start_time: string;
  end_time: string;
  memo: string;
  ot: Record<OtKey, number | ''>;
};

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function initState(existing: DailyWorkLog | null): FormState {
  const otOf = (k: OtKey): number | '' => {
    if (!existing) return '';
    const v = existing[`ot_${k}` as keyof DailyWorkLog] as number;
    return v ? v : '';
  };
  return {
    work_date: existing?.work_date ?? today(),
    site_id: existing?.site_id ?? '',
    site_name: existing?.site_name ?? '',
    bp_company_id: existing?.bp_company_id ?? '',
    contract_id: existing?.contract_id ?? '',
    equipment_id: existing?.equipment_id ?? '',
    person_id: existing?.person_id ?? '',
    work_content: existing?.work_content ?? '',
    work_location: existing?.work_location ?? '',
    rate_type: existing?.rate_type ?? 'DAILY',
    start_time: existing?.start_time?.slice(0, 5) ?? '',
    end_time: existing?.end_time?.slice(0, 5) ?? '',
    memo: existing?.memo ?? '',
    ot: { early: otOf('early'), lunch: otOf('lunch'), evening: otOf('evening'), night: otOf('night'), overnight: otOf('overnight') },
  };
}

/** 일일 확인서 작성·수정 폼. 계약 연결 시 구분·BP·현장 자동 상속. */
export default function DailyWorkLogForm({ existing, options, onSaved, onCancel }: {
  existing: DailyWorkLog | null;
  options: FormOptions;
  onSaved: () => void;
  onCancel?: () => void;
}) {
  const [f, setF] = useState<FormState>(() => initState(existing));
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setF((p) => ({ ...p, [k]: v }));

  const onContract = (idStr: string) => {
    const id = idStr ? Number(idStr) : '';
    const c = options.contracts.find((x) => x.id === id);
    setF((p) => ({
      ...p,
      contract_id: id,
      rate_type: c ? c.rate_type : p.rate_type,
      bp_company_id: c && c.bp_company_id != null ? c.bp_company_id : p.bp_company_id,
      site_id: c && c.site_id != null ? c.site_id : p.site_id,
    }));
  };

  const numOrNull = (v: number | '') => (v === '' ? null : v);

  const save = async () => {
    if (!f.work_date) { toast.error('작업일을 선택하세요'); return; }
    if (f.equipment_id === '' && f.person_id === '') { toast.error('장비 또는 작업자(운전원)를 하나 이상 선택하세요'); return; }
    setBusy(true);
    try {
      const body = {
        work_date: f.work_date,
        site_id: f.site_id === '' ? null : f.site_id,
        site_name: f.site_id === '' ? (f.site_name.trim() || null) : null,
        bp_company_id: numOrNull(f.bp_company_id),
        contract_id: numOrNull(f.contract_id),
        equipment_id: numOrNull(f.equipment_id),
        person_id: numOrNull(f.person_id),
        work_content: f.work_content.trim() || null,
        work_location: f.work_location.trim() || null,
        rate_type: f.rate_type,
        ot_early: f.ot.early === '' ? 0 : f.ot.early,
        ot_lunch: f.ot.lunch === '' ? 0 : f.ot.lunch,
        ot_evening: f.ot.evening === '' ? 0 : f.ot.evening,
        ot_night: f.ot.night === '' ? 0 : f.ot.night,
        ot_overnight: f.ot.overnight === '' ? 0 : f.ot.overnight,
        start_time: f.start_time || null,
        end_time: f.end_time || null,
        memo: f.memo.trim() || null,
      };
      if (existing) await api.put(`/api/daily-work-logs/${existing.id}`, body);
      else await api.post('/api/daily-work-logs', body);
      toast.success(existing ? '일일 확인서가 수정되었습니다' : '일일 확인서가 등록되었습니다');
      onSaved();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '저장에 실패했습니다');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4 text-sm">
      <div className="grid grid-cols-2 gap-3">
        <Field label="작업일" required>
          <input type="date" value={f.work_date} onChange={(e) => set('work_date', e.target.value)} className="input w-full" />
        </Field>
        <Field label="구분" required>
          <div className="flex gap-2">
            {(['DAILY', 'MONTHLY'] as RateType[]).map((rt) => (
              <button key={rt} type="button" onClick={() => set('rate_type', rt)}
                      className={`flex-1 px-3 py-1.5 rounded-lg text-sm font-semibold border ${f.rate_type === rt ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-slate-600 border-slate-200'}`}>
                {rt === 'DAILY' ? '일대' : '월대'}
              </button>
            ))}
          </div>
        </Field>
      </div>

      <Field label="계약 연결 (단가·구분·BP 자동 상속)">
        <select value={f.contract_id} onChange={(e) => onContract(e.target.value)} className="input w-full">
          <option value="">계약 연결 안 함</option>
          {options.contracts.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
        </select>
      </Field>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Field label="장비">
          <select value={f.equipment_id} onChange={(e) => set('equipment_id', e.target.value ? Number(e.target.value) : '')} className="input w-full">
            <option value="">장비 선택 안 함</option>
            {options.equipment.map((e) => <option key={e.id} value={e.id}>{e.label}</option>)}
          </select>
        </Field>
        <Field label="운전원 / 작업자">
          <select value={f.person_id} onChange={(e) => set('person_id', e.target.value ? Number(e.target.value) : '')} className="input w-full">
            <option value="">작업자 선택 안 함</option>
            {options.persons.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        </Field>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Field label="현장">
          <select value={f.site_id} onChange={(e) => set('site_id', e.target.value ? Number(e.target.value) : '')} className="input w-full">
            <option value="">현장 미지정 (직접 입력)</option>
            {options.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
          {f.site_id === '' && (
            <input value={f.site_name} onChange={(e) => set('site_name', e.target.value)} className="input mt-1.5 w-full" placeholder="현장명 직접 입력 (선택)" />
          )}
        </Field>
        <Field label="협력사 (BP)">
          <select value={f.bp_company_id} onChange={(e) => set('bp_company_id', e.target.value ? Number(e.target.value) : '')} className="input w-full">
            <option value="">BP사 선택 안 함 (단독모드)</option>
            {options.bps.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        </Field>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Field label="작업위치">
          <input value={f.work_location} onChange={(e) => set('work_location', e.target.value)} className="input w-full" placeholder="예: X6 / 9m" />
        </Field>
        <Field label="작업내용">
          <input value={f.work_content} onChange={(e) => set('work_content', e.target.value)} className="input w-full" placeholder="예: 제관" />
        </Field>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <Field label="작업 시작시간">
          <input type="time" value={f.start_time} onChange={(e) => set('start_time', e.target.value)} className="input w-full" />
        </Field>
        <Field label="작업 종료시간">
          <input type="time" value={f.end_time} onChange={(e) => set('end_time', e.target.value)} className="input w-full" />
        </Field>
      </div>

      <div>
        <div className="text-xs font-semibold text-slate-500 mb-1">OT 5분류 시간 (시간)</div>
        <div className="grid grid-cols-5 gap-2">
          {OT_COLS.map((c) => (
            <label key={c.key} className="block">
              <span className="text-[11px] text-slate-500">{c.label}</span>
              <span className="block text-[9px] text-slate-400 leading-tight">{c.time}</span>
              <input type="number" min={0} step={0.5} value={f.ot[c.key]}
                     onChange={(e) => setF((p) => ({ ...p, ot: { ...p.ot, [c.key]: e.target.value === '' ? '' : Number(e.target.value) } }))}
                     className="input mt-0.5 w-full text-sm" />
            </label>
          ))}
        </div>
      </div>

      <Field label="연장작업 내용 / 메모">
        <textarea value={f.memo} onChange={(e) => set('memo', e.target.value)} className="input w-full" rows={2} />
      </Field>

      <div className="flex justify-end gap-2 pt-1">
        {onCancel && <button onClick={onCancel} className="px-3 py-1.5 text-sm hover:bg-slate-100 rounded">취소</button>}
        <button onClick={save} disabled={busy} className="btn-primary disabled:opacity-50">
          {busy ? '저장 중…' : existing ? '수정 저장' : '일일 확인서 등록'}
        </button>
      </div>
    </div>
  );
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="text-xs font-semibold text-slate-500">{label}{required && <span className="text-rose-500"> *</span>}</span>
      <div className="mt-1">{children}</div>
    </label>
  );
}
