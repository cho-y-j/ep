import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import {
  CHANGE_KIND_LABEL,
  type L3Snapshot,
  type ResourceChangeRequestResponse,
} from '../../types/resourceChange';

/**
 * 업체변경 신청서 v0 — §7 임의양식 인쇄용 뷰(HTML + print CSS). AppShell 미사용.
 * 현장/적용일/변경구분/변경 전후 표(자동 채움)/사유/신규자원 확인 3체크(L3 자동)/신청·확인 서명란(공란).
 */

/** l3_snapshot 판정 → 카테고리별(서류/반입검사·검진·교육/안전점검) 확인 체크. */
function l3CategoryOk(snap: L3Snapshot | null | undefined, kind: string): boolean | null {
  if (!snap) return null;
  return !snap.blocks.some((b) => b.kind === kind);
}

export default function ResourceChangeDetailPage() {
  const { id } = useParams();
  const reqId = id ? Number(id) : NaN;
  const [r, setR] = useState<ResourceChangeRequestResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (Number.isNaN(reqId)) return;
    api.get<ResourceChangeRequestResponse>(`/api/resource-change-requests/${reqId}`)
      .then((res) => setR(res.data))
      .catch((e) => setError(e instanceof AxiosError ? (e.response?.data?.message ?? '불러오기 실패') : '불러오기 실패'));
  }, [reqId]);

  if (error) return <p className="p-8 text-rose-600">{error}</p>;
  if (!r) return <p className="p-8 text-slate-400">불러오는 중...</p>;

  const snap = r.l3_snapshot;
  const cats: Array<{ label: string; kind: string }> = [
    { label: '서류 (필수서류 검증)', kind: 'DOCUMENT' },
    { label: '반입검사 · 건강검진 · 안전교육', kind: 'CHECK' },
    { label: '현장 안전점검', kind: 'SAFETY' },
    { label: '이행지시', kind: 'COMPLIANCE' },
  ];

  const isOperator = r.change_kind === 'OPERATOR';

  return (
    <div className="min-h-screen bg-slate-100 print:bg-white">
      <style>{`
        @media print {
          .no-print { display: none !important; }
          .print-sheet { box-shadow: none !important; margin: 0 !important; max-width: none !important; }
        }
      `}</style>

      <div className="no-print sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
        <span className="text-sm font-semibold text-slate-700">업체변경 신청서 미리보기</span>
        <div className="flex gap-2">
          <button type="button" onClick={() => window.close()} className="rounded border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50">닫기</button>
          <button type="button" onClick={() => window.print()} className="btn-primary">인쇄 / PDF 저장</button>
        </div>
      </div>

      <div className="print-sheet mx-auto my-6 max-w-[800px] bg-white p-10 text-[12px] leading-relaxed shadow">
        <header className="border-b-2 border-slate-900 pb-3 text-center">
          <h1 className="text-2xl font-bold tracking-[0.3em] text-slate-950">업 체 변 경 신 청 서</h1>
          <div className="mt-1 text-xs text-slate-500">신청서 #{r.id} · 임의양식 v0</div>
        </header>

        {/* 기본 정보 */}
        <table className="mt-5 w-full border-collapse text-[12px]">
          <tbody>
            <tr>
              <th className="w-28 border border-slate-300 bg-slate-50 px-3 py-2 text-left font-semibold">현장</th>
              <td className="border border-slate-300 px-3 py-2">{r.site_name ?? '-'}</td>
              <th className="w-28 border border-slate-300 bg-slate-50 px-3 py-2 text-left font-semibold">적용일</th>
              <td className="border border-slate-300 px-3 py-2">{r.apply_date ?? '-'}</td>
            </tr>
            <tr>
              <th className="border border-slate-300 bg-slate-50 px-3 py-2 text-left font-semibold">변경구분</th>
              <td className="border border-slate-300 px-3 py-2 font-semibold text-slate-800">{CHANGE_KIND_LABEL[r.change_kind]}</td>
              <th className="border border-slate-300 bg-slate-50 px-3 py-2 text-left font-semibold">공급사(신청)</th>
              <td className="border border-slate-300 px-3 py-2">{r.supplier_name ?? '-'}</td>
            </tr>
            <tr>
              <th className="border border-slate-300 bg-slate-50 px-3 py-2 text-left font-semibold">발주사(BP)</th>
              <td className="border border-slate-300 px-3 py-2" colSpan={3}>{r.bp_name ?? '-'}</td>
            </tr>
          </tbody>
        </table>

        {/* 변경 전후 표 */}
        <h2 className="mt-6 mb-2 text-sm font-bold text-slate-900">변경 전 · 후</h2>
        <table className="w-full border-collapse text-[12px]">
          <thead>
            <tr>
              <th className="w-28 border border-slate-300 bg-slate-50 px-3 py-2 font-semibold"> </th>
              <th className="border border-slate-300 bg-slate-50 px-3 py-2 font-semibold">변경 전</th>
              <th className="border border-slate-300 bg-slate-50 px-3 py-2 font-semibold">변경 후</th>
            </tr>
          </thead>
          <tbody>
            {isOperator ? (
              <>
                <tr>
                  <th className="border border-slate-300 bg-slate-50 px-3 py-2 text-left font-semibold">조종원명</th>
                  <td className="border border-slate-300 px-3 py-2">{r.old_operator_name ?? r.old_label ?? '-'}</td>
                  <td className="border border-slate-300 px-3 py-2 font-semibold">{r.new_operator_name ?? r.new_label ?? '-'}</td>
                </tr>
                <tr>
                  <th className="border border-slate-300 bg-slate-50 px-3 py-2 text-left font-semibold">연락처</th>
                  <td className="border border-slate-300 px-3 py-2">{r.old_contact ?? '-'}</td>
                  <td className="border border-slate-300 px-3 py-2">{r.new_contact ?? '-'}</td>
                </tr>
              </>
            ) : (
              <>
                <tr>
                  <th className="border border-slate-300 bg-slate-50 px-3 py-2 text-left font-semibold">장비 / 업체</th>
                  <td className="border border-slate-300 px-3 py-2">{r.old_label ?? '-'}</td>
                  <td className="border border-slate-300 px-3 py-2 font-semibold">{r.new_label ?? '-'}</td>
                </tr>
                <tr>
                  <th className="border border-slate-300 bg-slate-50 px-3 py-2 text-left font-semibold">차량번호</th>
                  <td className="border border-slate-300 px-3 py-2">{r.old_vehicle_no ?? '-'}</td>
                  <td className="border border-slate-300 px-3 py-2">{r.new_vehicle_no ?? '-'}</td>
                </tr>
              </>
            )}
          </tbody>
        </table>

        {/* 사유 */}
        <h2 className="mt-6 mb-2 text-sm font-bold text-slate-900">변경 사유</h2>
        <div className="min-h-[48px] whitespace-pre-line border border-slate-300 px-3 py-2">{r.reason ?? ''}</div>

        {/* 신규자원 확인 3체크 (L3 판정 자동) */}
        <h2 className="mt-6 mb-2 text-sm font-bold text-slate-900">신규 자원 확인 (투입 사전판정)</h2>
        <div className="rounded border border-slate-300 px-3 py-2">
          <div className="mb-2 text-[12px]">
            판정 결과:{' '}
            {snap == null ? (
              <span className="text-slate-500">판정 없음</span>
            ) : snap.ready ? (
              <span className="font-bold text-emerald-700">✓ 투입 가능</span>
            ) : (
              <span className="font-bold text-amber-700">! 부족 {snap.blocks.length}건 (아래 해결 필요)</span>
            )}
            {snap?.checkedAt && <span className="ml-2 text-slate-400">({snap.checkedAt} 기준)</span>}
          </div>
          <ul className="space-y-1">
            {cats.map((c) => {
              const ok = l3CategoryOk(snap, c.kind);
              return (
                <li key={c.kind} className="flex items-center gap-2">
                  <span className={`inline-flex h-4 w-4 items-center justify-center border ${ok ? 'border-emerald-500 text-emerald-600' : 'border-slate-400 text-slate-300'}`}>{ok ? '✓' : ''}</span>
                  <span className={ok ? 'text-slate-800' : 'text-slate-500'}>{c.label}</span>
                </li>
              );
            })}
          </ul>
          {snap && !snap.ready && snap.blocks.length > 0 && (
            <ul className="mt-2 space-y-0.5 border-t border-slate-200 pt-2 text-[11px] text-amber-700">
              {snap.blocks.map((b, i) => (
                <li key={i}>· {b.label}{b.detail ? ` — ${b.detail}` : ''}</li>
              ))}
            </ul>
          )}
        </div>

        {/* 서명란 (공란 — 서명 수집은 후속) */}
        <div className="mt-8 grid grid-cols-2 gap-6">
          <div className="text-center">
            <div className="mb-1 text-xs font-semibold text-slate-600">신청 (공급사)</div>
            <div className="flex h-20 items-end justify-end border border-slate-300 px-3 pb-2 text-xs text-slate-400">(서명)</div>
          </div>
          <div className="text-center">
            <div className="mb-1 text-xs font-semibold text-slate-600">확인 (BP 소장)</div>
            <div className="flex h-20 items-end justify-end border border-slate-300 px-3 pb-2 text-xs text-slate-400">(서명)</div>
          </div>
        </div>

        <div className="mt-6 text-right text-[11px] text-slate-400">작성일: {r.created_at.slice(0, 10)}</div>
      </div>
    </div>
  );
}
