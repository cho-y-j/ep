import { useEffect, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, StatusBadge } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';

type TargetType = 'VEHICLE' | 'PERSON';
type OrderType = 'SAFETY_INSPECTION' | 'HEALTH_CHECK' | 'OTHER';
type OrderStatus = 'REQUESTED' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';

interface ComplianceOrder {
  id: number;
  bp_company_id: number;
  bp_company_name: string;
  supplier_company_id: number;
  supplier_company_name: string;
  target_type: TargetType;
  target_id: number;
  target_label: string;
  order_type: OrderType;
  order_subtype?: string | null;
  due_date: string;
  request_notes?: string | null;
  status: OrderStatus;
  overdue: boolean;
  submitted_at?: string | null;
  submission_notes?: string | null;
  proof_filename?: string | null;
  proof_content_type?: string | null;
  reviewed_at?: string | null;
  reviewed_by?: number | null;
  rejection_reason?: string | null;
  created_at: string;
}

const ORDER_TYPE_LABEL: Record<OrderType, string> = {
  SAFETY_INSPECTION: '안전점검',
  HEALTH_CHECK: '건강검진',
  OTHER: '기타',
};
const STATUS_TONE: Record<OrderStatus, 'neutral' | 'brand' | 'success' | 'warning' | 'danger'> = {
  REQUESTED: 'warning',
  SUBMITTED: 'brand',
  APPROVED: 'success',
  REJECTED: 'danger',
};
const STATUS_LABEL: Record<OrderStatus, string> = {
  REQUESTED: '대기',
  SUBMITTED: '검토 대기',
  APPROVED: '승인',
  REJECTED: '반려',
};

export default function ComplianceOrdersPage() {
  const { user } = useAuth();
  const isBp = user?.role === 'BP' || user?.role === 'ADMIN';
  const isSupplier = user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';
  const [items, setItems] = useState<ComplianceOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [issueOpen, setIssueOpen] = useState(false);
  const [submitTarget, setSubmitTarget] = useState<ComplianceOrder | null>(null);
  const [reviewTarget, setReviewTarget] = useState<ComplianceOrder | null>(null);

  const scope = isBp ? 'bp' : 'supplier';
  const refresh = async () => {
    setLoading(true); setError(null);
    try {
      const res = await api.get<ComplianceOrder[]>(`/api/compliance-orders?scope=${scope}`);
      setItems(res.data ?? []);
    } catch (e: any) {
      setError(e?.response?.data?.message || '조회 실패');
    } finally { setLoading(false); }
  };
  useEffect(() => { void refresh(); }, [scope]);

  const openProof = async (id: number) => {
    try {
      const res = await api.get(`/api/compliance-orders/${id}/proof`, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data as Blob);
      window.open(url, '_blank');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e: any) {
      alert(e?.response?.data?.message ?? '증빙 조회 실패');
    }
  };

  return (
    <AppShell>
      <div className="space-y-4">
        <PageHeader
          title="이행지시"
          subtitle={isBp ? '발행한 지시 · 증빙 검토 · 승인/반려'
            : '받은 지시 · 증빙 업로드 · 이행 완료 보고'}
          actions={isBp ? (
            <button type="button" onClick={() => setIssueOpen(true)} className="btn-primary">+ 새 지시 발행</button>
          ) : null}
        />
        {error && <div className="card border-rose-200 bg-rose-50 text-sm text-rose-800">{error}</div>}
        {loading ? (
          <div className="card text-sm text-slate-400">로딩…</div>
        ) : items.length === 0 ? (
          <div className="card text-sm text-slate-500">
            {isBp ? '아직 발행한 지시가 없습니다. 우상단 "새 지시 발행"으로 시작하세요.' : '받은 지시가 없습니다.'}
          </div>
        ) : (
          <div className="space-y-2">
            {items.map((o) => (
              <div key={o.id} className={`card ${o.overdue ? 'border-rose-300' : ''}`}>
                <div className="flex items-start justify-between gap-3 flex-wrap">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-bold text-slate-900">
                        {ORDER_TYPE_LABEL[o.order_type]}
                        {o.order_subtype && ` · ${o.order_subtype}`}
                      </span>
                      <StatusBadge tone={STATUS_TONE[o.status]}>{STATUS_LABEL[o.status]}</StatusBadge>
                      {o.overdue && <StatusBadge tone="danger">기한 초과</StatusBadge>}
                    </div>
                    <div className="mt-1 text-xs text-slate-600">
                      <strong>대상</strong>: {o.target_type === 'VEHICLE' ? '차량' : '인원'} · {o.target_label}
                      {' · '}<strong>마감</strong>: {o.due_date}
                    </div>
                    <div className="mt-0.5 text-xs text-slate-500">
                      {isBp ? `공급사: ${o.supplier_company_name}` : `발주사: ${o.bp_company_name}`}
                    </div>
                    {o.request_notes && (
                      <div className="mt-1 text-xs text-slate-700 whitespace-pre-wrap"><strong>요청:</strong> {o.request_notes}</div>
                    )}
                    {o.submission_notes && (
                      <div className="mt-1 text-xs text-slate-700 whitespace-pre-wrap"><strong>제출 메모:</strong> {o.submission_notes}</div>
                    )}
                    {o.status === 'REJECTED' && o.rejection_reason && (
                      <div className="mt-1 text-xs text-rose-700 whitespace-pre-wrap"><strong>반려 사유:</strong> {o.rejection_reason}</div>
                    )}
                  </div>
                  <div className="flex flex-col gap-1.5 items-end">
                    {o.proof_filename && (
                      <button type="button" onClick={() => openProof(o.id)}
                              className="text-xs px-2.5 py-1 rounded border border-brand-300 text-brand-700 hover:bg-brand-50">
                        증빙 보기
                      </button>
                    )}
                    {isSupplier && (o.status === 'REQUESTED' || o.status === 'REJECTED') && (
                      <button type="button" onClick={() => setSubmitTarget(o)}
                              className="text-xs px-2.5 py-1 rounded bg-brand-600 text-white hover:bg-brand-700">
                        이행 완료 + 증빙
                      </button>
                    )}
                    {isBp && o.status === 'SUBMITTED' && (
                      <button type="button" onClick={() => setReviewTarget(o)}
                              className="text-xs px-2.5 py-1 rounded bg-brand-600 text-white hover:bg-brand-700">
                        검토
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
        {issueOpen && (
          <IssueDialog onClose={() => setIssueOpen(false)} onIssued={() => { setIssueOpen(false); void refresh(); }} />
        )}
        {submitTarget && (
          <SubmitDialog target={submitTarget} onClose={() => setSubmitTarget(null)} onDone={() => { setSubmitTarget(null); void refresh(); }} />
        )}
        {reviewTarget && (
          <ReviewDialog target={reviewTarget} onClose={() => setReviewTarget(null)} onDone={() => { setReviewTarget(null); void refresh(); }} />
        )}
      </div>
    </AppShell>
  );
}

function IssueDialog({ onClose, onIssued }: { onClose: () => void; onIssued: () => void }) {
  const [suppliers, setSuppliers] = useState<Array<{ id: number; name: string }>>([]);
  const [supplierId, setSupplierId] = useState<number | null>(null);
  const [targetType, setTargetType] = useState<TargetType>('VEHICLE');
  const [resources, setResources] = useState<Array<{ id: number; label: string }>>([]);
  const [targetId, setTargetId] = useState<number | null>(null);
  const [orderType, setOrderType] = useState<OrderType>('SAFETY_INSPECTION');
  const [orderSubtype, setOrderSubtype] = useState('');
  const [dueDate, setDueDate] = useState(() => {
    const d = new Date(); d.setDate(d.getDate() + 7); return d.toISOString().slice(0, 10);
  });
  const [requestNotes, setRequestNotes] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    // 연동된 공급사 list (BP의 거래 공급사)
    const supplierType = targetType === 'VEHICLE' ? 'EQUIPMENT' : 'MANPOWER';
    api.get<Array<{ id: number; name: string }>>(`/api/companies/connected-suppliers?type=${supplierType}`)
      .then((res) => {
        setSuppliers(res.data ?? []);
        setSupplierId(res.data?.[0]?.id ?? null);
      })
      .catch(() => setSuppliers([]));
  }, [targetType]);

  useEffect(() => {
    // 공급사의 자원 (차량/인원) — connected-resources 로 좁힘
    if (!supplierId) { setResources([]); return; }
    api.get<{ equipmentIds: number[]; personIds: number[] }>(`/api/companies/connected-resources?supplierId=${supplierId}`)
      .then(async (res) => {
        const ids = targetType === 'VEHICLE' ? res.data.equipmentIds : res.data.personIds;
        if (!ids?.length) {
          // fallback: 전체 자원
          const path = targetType === 'VEHICLE' ? '/api/equipment' : '/api/persons';
          const all = await api.get(path);
          const list: any[] = Array.isArray(all.data) ? all.data : (all.data.content ?? []);
          setResources(list.filter((r: any) => r.supplier_id === supplierId).map((r: any) => ({
            id: r.id,
            label: targetType === 'VEHICLE'
              ? (r.vehicle_no ?? r.model ?? `#${r.id}`)
              : r.name,
          })));
        } else {
          // ids → 자원 라벨 매핑
          const path = targetType === 'VEHICLE' ? '/api/equipment' : '/api/persons';
          const all = await api.get(path);
          const list: any[] = Array.isArray(all.data) ? all.data : (all.data.content ?? []);
          const map = new Map(list.map((r: any) => [r.id, r]));
          setResources(ids.map((id: number) => {
            const r: any = map.get(id);
            return {
              id,
              label: r ? (targetType === 'VEHICLE'
                ? (r.vehicle_no ?? r.model ?? `#${id}`)
                : r.name) : `#${id}`,
            };
          }));
        }
        setTargetId(null);
      })
      .catch(() => setResources([]));
  }, [supplierId, targetType]);

  const submit = async () => {
    setErr(null);
    if (!supplierId || !targetId || !dueDate) { setErr('필수 필드를 입력하세요'); return; }
    setBusy(true);
    try {
      await api.post('/api/compliance-orders', {
        supplier_company_id: supplierId,
        target_type: targetType,
        target_id: targetId,
        order_type: orderType,
        order_subtype: orderSubtype || null,
        due_date: dueDate,
        request_notes: requestNotes || null,
      });
      onIssued();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '발행 실패');
    } finally { setBusy(false); }
  };

  return (
    <Modal title="새 이행지시 발행" onClose={onClose}>
      {err && <ErrBox text={err} />}
      <div className="space-y-2">
        <Field label="공급사 *">
          <select value={supplierId ?? ''} onChange={(e) => setSupplierId(Number(e.target.value))} className="input">
            <option value="">선택…</option>
            {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </Field>
        <Field label="대상 종류 *">
          <select value={targetType} onChange={(e) => setTargetType(e.target.value as TargetType)} className="input">
            <option value="VEHICLE">차량</option>
            <option value="PERSON">인원</option>
          </select>
        </Field>
        <Field label="대상 자원 *">
          <select value={targetId ?? ''} onChange={(e) => setTargetId(Number(e.target.value))} className="input">
            <option value="">선택…</option>
            {resources.map((r) => <option key={r.id} value={r.id}>{r.label}</option>)}
          </select>
        </Field>
        <Field label="지시 유형 *">
          <select value={orderType} onChange={(e) => setOrderType(e.target.value as OrderType)} className="input">
            <option value="SAFETY_INSPECTION">안전점검</option>
            <option value="HEALTH_CHECK">건강검진</option>
            <option value="OTHER">기타</option>
          </select>
        </Field>
        <Field label="세부 (선택)">
          <input type="text" value={orderSubtype} onChange={(e) => setOrderSubtype(e.target.value)}
                 placeholder="예: 정기 안전점검 / 2026 상반기 건강검진" className="input" />
        </Field>
        <Field label="마감일 *">
          <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} className="input" />
        </Field>
        <Field label="요청 메모 (선택)">
          <textarea rows={3} value={requestNotes} onChange={(e) => setRequestNotes(e.target.value)} className="input" />
        </Field>
      </div>
      <Actions onClose={onClose} onSubmit={submit} busy={busy} submitLabel="발행" busyLabel="발행 중…" />
    </Modal>
  );
}

function SubmitDialog({ target, onClose, onDone }: { target: ComplianceOrder; onClose: () => void; onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = async () => {
    setErr(null);
    if (!file) { setErr('증빙 파일을 선택하세요 (PDF/이미지/Office)'); return; }
    setBusy(true);
    try {
      const fd = new FormData(); fd.append('file', file);
      await api.post(`/api/compliance-orders/${target.id}/proof`, fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      await api.post(`/api/compliance-orders/${target.id}/submit`, { submission_notes: notes || null });
      onDone();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '제출 실패');
    } finally { setBusy(false); }
  };

  return (
    <Modal title="이행 완료 + 증빙 업로드" onClose={onClose}>
      <p className="text-xs text-slate-500">
        {target.target_type === 'VEHICLE' ? '차량' : '인원'} · {target.target_label} · {ORDER_TYPE_LABEL[target.order_type]}
      </p>
      {err && <ErrBox text={err} />}
      <div className="space-y-2">
        <Field label="증빙 파일 * (PDF / JPG / PNG / DOCX / XLSX)">
          <input type="file" accept=".pdf,.jpg,.jpeg,.png,.webp,.docx,.xlsx"
                 onChange={(e) => setFile(e.target.files?.[0] ?? null)} className="input" />
        </Field>
        <Field label="제출 메모 (선택)">
          <textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} className="input" />
        </Field>
      </div>
      <Actions onClose={onClose} onSubmit={submit} busy={busy} submitLabel="제출" busyLabel="제출 중…" />
    </Modal>
  );
}

function ReviewDialog({ target, onClose, onDone }: { target: ComplianceOrder; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const review = async (approve: boolean) => {
    setErr(null);
    if (!approve && !reason.trim()) { setErr('반려 사유를 입력하세요'); return; }
    setBusy(true);
    try {
      await api.post(`/api/compliance-orders/${target.id}/review`, {
        approve, rejection_reason: approve ? null : reason,
      });
      onDone();
    } catch (e: any) {
      setErr(e?.response?.data?.message ?? '검토 실패');
    } finally { setBusy(false); }
  };

  return (
    <Modal title="이행 증빙 검토" onClose={onClose}>
      <p className="text-xs text-slate-500">
        {target.target_type === 'VEHICLE' ? '차량' : '인원'} · {target.target_label} · {ORDER_TYPE_LABEL[target.order_type]}
      </p>
      {target.submission_notes && (
        <div className="rounded border border-slate-200 bg-slate-50 p-2 text-xs text-slate-700 whitespace-pre-wrap">
          <strong>제출 메모:</strong> {target.submission_notes}
        </div>
      )}
      {err && <ErrBox text={err} />}
      <Field label="반려 시 사유 (승인 시 비워둠)">
        <textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} className="input" />
      </Field>
      <div className="flex items-center justify-end gap-2 pt-2">
        <button type="button" onClick={onClose} className="btn-ghost">취소</button>
        <button type="button" onClick={() => review(false)} disabled={busy}
                className="px-3 py-1.5 rounded border border-rose-300 text-rose-700 hover:bg-rose-50 text-sm">반려</button>
        <button type="button" onClick={() => review(true)} disabled={busy} className="btn-primary">
          {busy ? '처리 중…' : '승인'}
        </button>
      </div>
    </Modal>
  );
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-5 space-y-3 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between">
          <h3 className="text-base font-bold text-slate-900">{title}</h3>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600">✕</button>
        </div>
        {children}
      </div>
    </div>
  );
}
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label className="text-xs text-slate-600 block mb-0.5">{label}</label>{children}</div>;
}
function ErrBox({ text }: { text: string }) {
  return <div className="rounded-md border border-rose-200 bg-rose-50 p-2 text-xs text-rose-800">{text}</div>;
}
function Actions({ onClose, onSubmit, busy, submitLabel, busyLabel }: {
  onClose: () => void; onSubmit: () => void; busy: boolean; submitLabel: string; busyLabel: string;
}) {
  return (
    <div className="flex items-center justify-end gap-2 pt-2">
      <button type="button" onClick={onClose} className="btn-ghost">취소</button>
      <button type="button" onClick={onSubmit} disabled={busy} className="btn-primary">
        {busy ? busyLabel : submitLabel}
      </button>
    </div>
  );
}
