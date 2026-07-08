import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AlimTalkSendBox from '../../components/AlimTalkSendBox';
import {
  CHECK_TYPE_LABEL,
  type ResourceCheckType,
  type ResourceOwnerType,
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
};

const TYPES_FOR_EQUIPMENT: ResourceCheckType[] = ['VEHICLE_SAFETY', 'OTHER'];
const TYPES_FOR_PERSON: ResourceCheckType[] = ['HEALTH_CHECK', 'SAFETY_TRAINING', 'OTHER'];

export default function IssueResourceCheckDialog({
  open, onClose, onIssued, workPlanId, ownerType, ownerId, ownerLabel,
  supplierCompanyId, supplierCompanyName, personPhone,
}: Props) {
  const allowed = ownerType === 'EQUIPMENT' ? TYPES_FOR_EQUIPMENT : TYPES_FOR_PERSON;
  const [checkType, setCheckType] = useState<ResourceCheckType>(allowed[0]);
  const [dueDate, setDueDate] = useState('');
  const [notes, setNotes] = useState('');
  const [alimtalkPhones, setAlimtalkPhones] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  // 알림톡 템플릿이 있는 점검만 발송칸 노출 (건강검진=person, 차량안전점검=equipment)
  const isAlimtalkType = checkType === 'HEALTH_CHECK' || checkType === 'VEHICLE_SAFETY';
  const [autoPhone, setAutoPhone] = useState<string | null>(null);

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

  const submit = async () => {
    setBusy(true);
    try {
      await api.post('/api/resource-checks', {
        work_plan_id: workPlanId,
        owner_type: ownerType,
        owner_id: ownerId,
        supplier_company_id: supplierCompanyId,
        check_type: checkType,
        due_date: dueDate || null,
        notes: notes || null,
        alimtalk_phones: isAlimtalkType ? alimtalkPhones : [],
      });
      toast.success(`${CHECK_TYPE_LABEL[checkType]} 요청 발송 — ${supplierCompanyName ?? '공급사'}`);
      onIssued();
      onClose();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '발송 실패');
    } finally {
      setBusy(false);
    }
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
          <label className="block">
            <span className="text-xs font-semibold text-slate-500">점검 종류</span>
            <select value={checkType} onChange={(e) => setCheckType(e.target.value as ResourceCheckType)}
                    className="mt-1 w-full px-2.5 py-1.5 text-sm border border-slate-300 rounded">
              {allowed.map((t) => (
                <option key={t} value={t}>{CHECK_TYPE_LABEL[t]}</option>
              ))}
            </select>
          </label>
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
          {isAlimtalkType && (
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
          <button onClick={submit} disabled={busy} className="btn-primary disabled:opacity-50 text-sm">
            {busy ? '발송 중…' : '발송'}
          </button>
        </div>
      </div>
    </div>
  );
}
