import { useEffect, useState } from 'react';
import { api } from '../../../lib/api';
import DispatchSendDialog from './DispatchSendDialog';
import type { DispatchedEquipmentResponse, DispatchedPersonResponse } from '../../../types/dispatch';

type Props = {
  quotationRequestId: number;
  requestedCategory?: string | null;
  /** 인력 견적이면 요청 역할 — 인원 선택 prefilter. */
  requestedManpowerRole?: string | null;
  /** 공급사 본인이 선정된 경우. NOT_SENT 일 때 보내기 다이얼로그 노출. */
  canSend: boolean;
  /** BP / ADMIN / 공급사 모두 PDF 보기/다운로드 가능. */
  canViewPdf: boolean;
  /** BP/ADMIN 만 — 다중 공급사 비교표 노출. */
  canCompare?: boolean;
};

export default function DispatchSection({ quotationRequestId, requestedCategory, requestedManpowerRole, canSend, canViewPdf, canCompare }: Props) {
  const [sent, setSent] = useState<DispatchedEquipmentResponse[]>([]);
  const [sentPersons, setSentPersons] = useState<DispatchedPersonResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const [eqRes, pRes] = await Promise.all([
        api.get<DispatchedEquipmentResponse[]>(`/api/quotations/${quotationRequestId}/dispatched`),
        api.get<DispatchedPersonResponse[]>(`/api/quotations/${quotationRequestId}/dispatched-persons`).catch(() => ({ data: [] })),
      ]);
      setSent(eqRes.data);
      setSentPersons(pRes.data);
    } catch {
      setSent([]);
      setSentPersons([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, [quotationRequestId]);

  const pdfBase = `/api/quotations/${quotationRequestId}/pdf`;
  // axios 로 blob 받아 새 탭 열기 — window.open 직접 호출은 Authorization 헤더 못 전달.
  const openPreview = async (mode: 'single' | 'full') => {
    try {
      const res = await api.get(`${pdfBase}?mode=${mode}`, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data as Blob);
      window.open(url, '_blank');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err: any) {
      const msg = err?.response?.data?.message ?? 'PDF 조회 실패';
      alert(msg);
    }
  };
  const download = async (mode: 'single' | 'full') => {
    try {
      const res = await api.get(`${pdfBase}?mode=${mode}&disposition=attachment`, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data as Blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `quotation-${quotationRequestId}${mode === 'full' ? '-full' : ''}.pdf`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 5_000);
    } catch (err: any) {
      alert(err?.response?.data?.message ?? '다운로드 실패');
    }
  };

  // 엑셀 양식 (공급사별 견적서.xlsx + PDF 미리보기, 다중 공급사 비교표)
  const blobDownload = async (path: string, filename: string) => {
    try {
      const res = await api.get(path, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data as Blob);
      const a = document.createElement('a');
      a.href = url; a.download = filename; a.click();
      setTimeout(() => URL.revokeObjectURL(url), 5_000);
    } catch (err: any) {
      alert(err?.response?.data?.message ?? '다운로드 실패');
    }
  };
  const blobPreview = async (path: string) => {
    try {
      const res = await api.get(path, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data as Blob);
      window.open(url, '_blank');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err: any) {
      alert(err?.response?.data?.message ?? '미리보기 실패');
    }
  };
  // 공급사별 unique list (BP/ADMIN 시점 — 공급사 여러 곳이면 각각 버튼)
  const suppliers = Array.from(
    new Map(sent.map((d) => [d.supplier_company_id, d.supplier_company_name ?? `공급사#${d.supplier_company_id}`])).entries(),
  );

  if (loading) return <div className="card text-sm text-slate-400">배차 정보 로딩...</div>;

  const isSent = sent.length > 0 || sentPersons.length > 0;

  return (
    <section className="card space-y-3">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-bold text-slate-900">배차 차량·인원 / 견적서</h2>
          <p className="mt-1 text-xs text-slate-500">
            {isSent
              ? `보낸 차량 ${sent.length}대 · 인원 ${sentPersons.length}명 — 견적서 보기/다운로드 가능`
              : (canSend ? '선정 완료. 보낼 차량·인원을 선택해주세요.' : '아직 발송되지 않았습니다.')}
          </p>
        </div>
        {canSend && !isSent && (
          <button onClick={() => setDialogOpen(true)} className="btn-primary">차량·인원 선택 후 보내기</button>
        )}
      </div>

      {isSent && (
        <>
          <div className="rounded-lg border border-slate-200 overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200 text-left text-slate-500">
                <tr>
                  <th className="px-3 py-2 font-medium">#</th>
                  <th className="px-3 py-2 font-medium">차량</th>
                  <th className="px-3 py-2 font-medium">카테고리</th>
                  <th className="px-3 py-2 font-medium text-right">일대(원)</th>
                  <th className="px-3 py-2 font-medium text-right">월대(원)</th>
                  <th className="px-3 py-2 font-medium">비고</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {sent.map((d, i) => (
                  <tr key={d.id}>
                    <td className="px-3 py-2 text-slate-500">{i + 1}</td>
                    <td className="px-3 py-2 font-medium text-slate-900">{d.equipment_label}</td>
                    <td className="px-3 py-2 text-slate-600">{d.equipment_category ?? '-'}</td>
                    <td className="px-3 py-2 text-right font-mono">{d.daily_price != null ? d.daily_price.toLocaleString() : '-'}</td>
                    <td className="px-3 py-2 text-right font-mono">{d.monthly_price != null ? d.monthly_price.toLocaleString() : '-'}</td>
                    <td className="px-3 py-2 text-slate-600">{d.notes ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {sentPersons.length > 0 && (
            <div className="rounded-lg border border-slate-200 overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 border-b border-slate-200 text-left text-slate-500">
                  <tr>
                    <th className="px-3 py-2 font-medium">#</th>
                    <th className="px-3 py-2 font-medium">인원</th>
                    <th className="px-3 py-2 font-medium">직책</th>
                    <th className="px-3 py-2 font-medium text-right">일대(원)</th>
                    <th className="px-3 py-2 font-medium text-right">월대(원)</th>
                    <th className="px-3 py-2 font-medium">비고</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {sentPersons.map((d, i) => (
                    <tr key={d.id}>
                      <td className="px-3 py-2 text-slate-500">{i + 1}</td>
                      <td className="px-3 py-2 font-medium text-slate-900">{d.person_label}</td>
                      <td className="px-3 py-2 text-slate-600">{d.job_title ?? '-'}</td>
                      <td className="px-3 py-2 text-right font-mono">{d.daily_price != null ? d.daily_price.toLocaleString() : '-'}</td>
                      <td className="px-3 py-2 text-right font-mono">{d.monthly_price != null ? d.monthly_price.toLocaleString() : '-'}</td>
                      <td className="px-3 py-2 text-slate-600">{d.notes ?? '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {canViewPdf && (
            <div className="space-y-2 pt-2">
              <div className="flex flex-wrap gap-2">
                <button onClick={() => openPreview('single')} className="px-3 py-1.5 rounded-md border border-brand-300 text-brand-700 hover:bg-brand-50 text-sm">
                  견적서 보기 (단건)
                </button>
                <button onClick={() => openPreview('full')} className="px-3 py-1.5 rounded-md border border-brand-300 text-brand-700 hover:bg-brand-50 text-sm">
                  견적서 보기 (전체)
                </button>
                <button onClick={() => download('single')} className="px-3 py-1.5 rounded-md border border-slate-300 text-slate-700 hover:bg-slate-50 text-sm">
                  다운로드 (단건)
                </button>
                <button onClick={() => download('full')} className="px-3 py-1.5 rounded-md border border-slate-300 text-slate-700 hover:bg-slate-50 text-sm">
                  다운로드 (전체)
                </button>
              </div>
              {/* 견적서 양식 (엑셀/PDF) — 공급사별 */}
              <div className="rounded-lg border border-amber-200 bg-amber-50/50 p-3 space-y-2">
                <div className="text-xs font-semibold text-amber-900">견적서 양식 (회사 정보 자동 채움)</div>
                {suppliers.map(([sid, sname]) => (
                  <div key={sid} className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium text-slate-900 mr-1">{sname}</span>
                    <button
                      onClick={() => blobPreview(`/api/quotations/${quotationRequestId}/quote.pdf?supplier=${sid}`)}
                      className="px-2.5 py-1 rounded border border-brand-300 text-brand-700 hover:bg-brand-50 text-xs"
                    >미리보기 (PDF)</button>
                    <button
                      onClick={() => blobDownload(`/api/quotations/${quotationRequestId}/quote.xlsx?supplier=${sid}`, `견적서-${quotationRequestId}-${sname}.xlsx`)}
                      className="px-2.5 py-1 rounded border border-slate-300 text-slate-700 hover:bg-slate-50 text-xs"
                    >엑셀 다운로드</button>
                  </div>
                ))}
                {canCompare && suppliers.length >= 2 && (
                  <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-amber-200">
                    <span className="text-sm font-medium text-slate-900 mr-1">비교표 ({suppliers.length}개 공급사)</span>
                    <button
                      onClick={() => blobPreview(`/api/quotations/${quotationRequestId}/compare.pdf`)}
                      className="px-2.5 py-1 rounded border border-brand-300 text-brand-700 hover:bg-brand-50 text-xs"
                    >미리보기 (PDF)</button>
                    <button
                      onClick={() => blobDownload(`/api/quotations/${quotationRequestId}/compare.xlsx`, `비교표-${quotationRequestId}.xlsx`)}
                      className="px-2.5 py-1 rounded border border-slate-300 text-slate-700 hover:bg-slate-50 text-xs"
                    >엑셀 다운로드</button>
                  </div>
                )}
              </div>
            </div>
          )}
        </>
      )}

      <DispatchSendDialog
        open={dialogOpen}
        quotationRequestId={quotationRequestId}
        requestedCategory={requestedCategory}
        requestedManpowerRole={requestedManpowerRole}
        onClose={() => setDialogOpen(false)}
        onSent={() => { void load(); }}
      />
    </section>
  );
}
