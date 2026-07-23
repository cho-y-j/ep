import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AlimTalkSendBox from '../../components/AlimTalkSendBox';
import {
  CHECK_TYPE_LABEL,
  type ResourceCheckType,
  type ResourceOwnerType,
  type ResourceCheckResponse,
} from '../../types/resourceCheck';

type Props = {
  open: boolean;
  onClose: () => void;
  onIssued: () => void;
  workPlanId?: number | null;
  ownerType: ResourceOwnerType;
  ownerId: number;
  ownerLabel: string;
  supplierCompanyId: number;
  supplierCompanyName?: string | null;
  personPhone?: string | null;
  /** 재검사 통보 등 프리필 — 열 때 이 종류만 선택(미지정이면 자원별 추천 선택). */
  initialTypes?: ResourceCheckType[] | null;
};

const TYPES_FOR_EQUIPMENT: ResourceCheckType[] = ['VEHICLE_SAFETY', 'OTHER'];
const TYPES_FOR_PERSON: ResourceCheckType[] = ['HEALTH_CHECK', 'SAFETY_TRAINING', 'OTHER'];
// F4: 자원 종류별 자동추천(다이얼로그 열 때 프리셀렉트). 나머지(OTHER 등)는 수동 선택.
const RECOMMENDED_FOR_EQUIPMENT: ResourceCheckType[] = ['VEHICLE_SAFETY'];
const RECOMMENDED_FOR_PERSON: ResourceCheckType[] = ['HEALTH_CHECK', 'SAFETY_TRAINING'];
// 알림톡 템플릿이 있는 점검(건강검진=person, 차량안전점검=equipment).
const ALIMTALK_TYPES: ResourceCheckType[] = ['HEALTH_CHECK', 'VEHICLE_SAFETY'];

export default function IssueResourceCheckDialog({
  open, onClose, onIssued, workPlanId, ownerType, ownerId, ownerLabel,
  supplierCompanyId, supplierCompanyName, personPhone, initialTypes,
}: Props) {
  const allowed = ownerType === 'EQUIPMENT' ? TYPES_FOR_EQUIPMENT : TYPES_FOR_PERSON;
  const [selected, setSelected] = useState<Set<ResourceCheckType>>(new Set());
  const [dueDate, setDueDate] = useState('');
  const [notes, setNotes] = useState('');
  const [alimtalkPhones, setAlimtalkPhones] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  // 같은 자원에 이미 회신 대기(REQUESTED) 중인 점검 종류 — 중복 발송 경고용.
  const [existingRequested, setExistingRequested] = useState<Set<ResourceCheckType>>(new Set());
  const [autoPhone, setAutoPhone] = useState<string | null>(null);
  // R2 조합 모드(장비 대상 한정): 교대조 조종원 자동 로드 → issue-combo 1회 호출. 끄면 단건 발행 기존 그대로.
  const [comboMode, setComboMode] = useState(false);
  const [comboOperators, setComboOperators] = useState<{ person_id: number; person_name?: string | null }[]>([]);
  const [comboChecked, setComboChecked] = useState<Set<number>>(new Set());
  const [operatorTypes, setOperatorTypes] = useState<Set<ResourceCheckType>>(new Set(RECOMMENDED_FOR_PERSON));

  const showAlimtalk = !comboMode && [...selected].some((t) => ALIMTALK_TYPES.includes(t));
  const dupTypes = [...selected].filter((t) => existingRequested.has(t));

  // F4: 열 때 자원 종류별 추천 프리셀렉트. 재검사 통보는 initialTypes(해당 종류만)로 프리필.
  useEffect(() => {
    if (!open) { setComboMode(false); return; }
    setOperatorTypes(new Set(RECOMMENDED_FOR_PERSON));
    if (initialTypes && initialTypes.length > 0) { setSelected(new Set(initialTypes)); return; }
    setSelected(new Set(ownerType === 'EQUIPMENT' ? RECOMMENDED_FOR_EQUIPMENT : RECOMMENDED_FOR_PERSON));
  }, [open, ownerType, initialTypes]);

  // R2: 조합 모드 켜면 그 장비의 교대조 조종원 로드(기존 배치 API) — 전원 기본 선택, 개별 해제 가능.
  useEffect(() => {
    if (!open || !comboMode || ownerType !== 'EQUIPMENT') return;
    let alive = true;
    api.post<{ results: Array<{ equipment_id: number; operators: Array<{ person_id: number; person_name?: string | null }> }> }>(
      '/api/equipment/default-operators', { equipment_ids: [ownerId] })
      .then((res) => {
        if (!alive) return;
        const ops = res.data.results.find((x) => x.equipment_id === ownerId)?.operators ?? [];
        setComboOperators(ops);
        setComboChecked(new Set(ops.map((o) => o.person_id)));
      })
      .catch(() => { if (alive) { setComboOperators([]); setComboChecked(new Set()); } });
    return () => { alive = false; };
  }, [open, comboMode, ownerType, ownerId]);

  // F4: 기존 동일 자원 REQUESTED 요청을 bp-list 에서 클라 필터해 중복 경고.
  useEffect(() => {
    if (!open) { setExistingRequested(new Set()); return; }
    let alive = true;
    api.get<ResourceCheckResponse[]>('/api/resource-checks/bp-list')
      .then((res) => {
        if (!alive) return;
        setExistingRequested(new Set(
          (res.data ?? [])
            .filter((r) => r.owner_type === ownerType && r.owner_id === ownerId && r.status === 'REQUESTED')
            .map((r) => r.check_type),
        ));
      })
      .catch(() => { if (alive) setExistingRequested(new Set()); });
    return () => { alive = false; };
  }, [open, ownerType, ownerId]);

  // 사람 대상이면 person.phone 자동후보 (등록번호 추가 버튼용). 실패해도 수동입력 가능.
  useEffect(() => {
    if (!open || ownerType !== 'PERSON') { setAutoPhone(null); return; }
    let alive = true;
    api.get(`/api/persons/${ownerId}`)
      .then((res) => { if (alive) setAutoPhone(res.data?.phone ?? null); })
      .catch(() => { if (alive) setAutoPhone(null); });
    return () => { alive = false; };
  }, [open, ownerType, ownerId]);

  if (!open) return null;

  const toggle = (t: ResourceCheckType) => setSelected((prev) => {
    const next = new Set(prev);
    if (next.has(t)) next.delete(t); else next.add(t);
    return next;
  });

  const toggleOperatorType = (t: ResourceCheckType) => setOperatorTypes((prev) => {
    const next = new Set(prev);
    if (next.has(t)) next.delete(t); else next.add(t);
    return next;
  });

  const toggleOperator = (personId: number) => setComboChecked((prev) => {
    const next = new Set(prev);
    if (next.has(personId)) next.delete(personId); else next.add(personId);
    return next;
  });

  // R2 조합 발행 — issue-combo 1회 호출(단일 트랜잭션·전 행 combo_equipment_id 스냅샷).
  const submitCombo = async () => {
    const equipmentTypes = [...selected];
    const opTypes = [...operatorTypes];
    const opIds = comboOperators.filter((o) => comboChecked.has(o.person_id)).map((o) => o.person_id);
    if (equipmentTypes.length === 0 && (opIds.length === 0 || opTypes.length === 0)) {
      toast.error('발행할 점검이 없습니다'); return;
    }
    setBusy(true);
    try {
      const res = await api.post<ResourceCheckResponse[]>('/api/resource-checks/issue-combo', {
        equipment_id: ownerId,
        operator_person_ids: opIds,
        supplier_company_id: supplierCompanyId,
        work_plan_id: workPlanId,
        due_date: dueDate || null,
        notes: notes || null,
        checks: { equipment: equipmentTypes, operator: opTypes },
      });
      toast.success(`조합 점검 ${res.data.length}건 발송 — ${supplierCompanyName ?? '공급사'}`);
      onIssued();
      onClose();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '발송 실패');
    } finally { setBusy(false); }
  };

  const submit = async () => {
    const types = [...selected];
    if (types.length === 0) { toast.error('점검 종류를 1개 이상 선택하세요'); return; }
    setBusy(true);
    // 발행 흐름 불변: 종류별로 기존과 동일한 POST /api/resource-checks 를 각각 호출.
    const results = await Promise.allSettled(types.map((t) =>
      api.post('/api/resource-checks', {
        work_plan_id: workPlanId,
        owner_type: ownerType,
        owner_id: ownerId,
        supplier_company_id: supplierCompanyId,
        check_type: t,
        due_date: dueDate || null,
        notes: notes || null,
        alimtalk_phones: ALIMTALK_TYPES.includes(t) ? alimtalkPhones : [],
      }),
    ));
    setBusy(false);
    const okCount = results.filter((r) => r.status === 'fulfilled').length;
    const failCount = results.length - okCount;
    const supplier = supplierCompanyName ?? '공급사';
    if (okCount > 0) {
      toast.success(types.length === 1
        ? `${CHECK_TYPE_LABEL[types[0]]} 요청 발송 — ${supplier}`
        : `점검 요청 ${okCount}건 발송 — ${supplier}`);
      onIssued();
    }
    if (failCount > 0) {
      const firstRej = results.find((r) => r.status === 'rejected') as PromiseRejectedResult | undefined;
      const msg = firstRej?.reason?.response?.data?.message ?? '발송 실패';
      toast.error(okCount === 0 ? msg : `${failCount}건 발송 실패: ${msg}`);
    }
    if (okCount > 0) onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        <div className="px-5 py-3 border-b">
          <h3 className="font-bold text-slate-900">점검 요청 발송</h3>
          <p className="text-xs text-slate-500 mt-0.5">
            {ownerLabel} → {supplierCompanyName ?? `공급사 #${supplierCompanyId}`}
          </p>
        </div>
        <div className="px-5 py-4 space-y-3">
          {ownerType === 'EQUIPMENT' && (
            <label className="flex items-center gap-2 text-sm font-semibold text-slate-800 cursor-pointer">
              <input type="checkbox" checked={comboMode} onChange={(e) => setComboMode(e.target.checked)} />
              <span>조합으로 발행</span>
              <span className="font-normal text-xs text-slate-500">장비+교대조 조종원 일괄</span>
            </label>
          )}
          <div>
            <span className="text-xs font-semibold text-slate-500">
              {comboMode ? '장비 점검 종류' : '점검 종류'}{' '}
              <span className="font-normal text-slate-400">(추천 자동 선택 — 수정 가능)</span>
            </span>
            <div className="mt-1 space-y-1.5">
              {allowed.map((t) => (
                <label key={t} className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                  <input type="checkbox" checked={selected.has(t)} onChange={() => toggle(t)} />
                  <span>{CHECK_TYPE_LABEL[t]}</span>
                  {existingRequested.has(t) && (
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-100 text-amber-800">회신 대기 중</span>
                  )}
                </label>
              ))}
            </div>
          </div>
          {comboMode && (
            <div className="rounded-lg border border-indigo-200 bg-indigo-50/40 px-3 py-2 space-y-2">
              <div>
                <span className="text-xs font-semibold text-slate-500">조합(교대조) 조종원 — 발행 대상 선택</span>
                {comboOperators.length === 0 ? (
                  <div className="mt-1 text-xs text-slate-400">이 장비에 등록된 조종원이 없습니다 (장비 점검만 발행).</div>
                ) : (
                  <div className="mt-1 space-y-1.5">
                    {comboOperators.map((o) => (
                      <label key={o.person_id} className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                        <input type="checkbox" checked={comboChecked.has(o.person_id)}
                               onChange={() => toggleOperator(o.person_id)} />
                        <span>{o.person_name ?? `인원 #${o.person_id}`}</span>
                      </label>
                    ))}
                  </div>
                )}
              </div>
              <div>
                <span className="text-xs font-semibold text-slate-500">조종원 점검 종류</span>
                <div className="mt-1 space-y-1.5">
                  {TYPES_FOR_PERSON.map((t) => (
                    <label key={t} className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                      <input type="checkbox" checked={operatorTypes.has(t)} onChange={() => toggleOperatorType(t)} />
                      <span>{CHECK_TYPE_LABEL[t]}</span>
                    </label>
                  ))}
                </div>
              </div>
            </div>
          )}
          {dupTypes.length > 0 && (
            <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
              이미 회신 대기 중인 요청이 있습니다: {dupTypes.map((t) => CHECK_TYPE_LABEL[t]).join(', ')}. 중복 발송에 주의하세요.
            </div>
          )}
          <label className="block">
            <span className="text-xs font-semibold text-slate-500">마감일 (점검 받아야 하는 날짜)</span>
            <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)}
                   className="mt-1 w-full px-2.5 py-1.5 text-sm border border-slate-300 rounded" />
          </label>
          <label className="block">
            <span className="text-xs font-semibold text-slate-500">메모 (선택)</span>
            <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={3}
                      placeholder="추가 안내 사항"
                      className="mt-1 w-full px-2.5 py-1.5 text-sm border border-slate-300 rounded" />
          </label>
          {showAlimtalk && (
            <AlimTalkSendBox
              personPhone={ownerType === 'PERSON' ? (personPhone ?? autoPhone) : null}
              value={alimtalkPhones}
              onChange={setAlimtalkPhones}
            />
          )}
        </div>
        <div className="px-5 py-3 border-t flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 rounded text-sm text-slate-700 hover:bg-slate-100">
            취소
          </button>
          <button onClick={comboMode ? submitCombo : submit} disabled={busy}
                  className="btn-primary disabled:opacity-50 text-sm">
            {busy ? '발송 중…' : comboMode ? '조합 발송' : '발송'}
          </button>
        </div>
      </div>
    </div>
  );
}
