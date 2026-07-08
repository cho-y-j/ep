import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { EQUIPMENT_CATEGORY_LABEL } from '../../types/equipment';
import {
  COMPLIANCE_STATUS_LABEL,
  WORK_PLAN_STATUS_LABEL,
  type WorkPlanResponse,
} from '../../types/workPlan';

/**
 * 인쇄 전용 페이지. AppShell 미사용 — A4 1매 기준 레이아웃, @media print 로 브라우저 인쇄/PDF 저장.
 */
export default function WorkPlanPrintPage() {
  const { id } = useParams();
  const [wp, setWp] = useState<WorkPlanResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    api.get<WorkPlanResponse>(`/api/work-plans/${id}`)
      .then((res) => setWp(res.data))
      .catch((e) => setError(e?.response?.data?.message ?? '불러오기 실패'));
  }, [id]);

  if (error) return <p className="p-8 text-rose-600">{error}</p>;
  if (!wp) return <p className="p-8 text-slate-400">불러오는 중...</p>;

  return (
    <div className="bg-slate-100 min-h-screen print:bg-white">
      <style>{`
        @page { size: A4; margin: 14mm; }
        @media print {
          .no-print { display: none !important; }
          body { background: white !important; }
          .print-sheet { box-shadow: none !important; margin: 0 !important; padding: 0 !important; max-width: none !important; }
        }
      `}</style>

      <div className="no-print sticky top-0 z-10 flex items-center justify-between bg-white border-b border-slate-200 px-6 py-3">
        <span className="text-sm font-semibold text-slate-700">작업계획서 인쇄 미리보기</span>
        <div className="flex gap-2">
          <button type="button" onClick={() => window.close()} className="rounded border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50">닫기</button>
          <button type="button" onClick={() => window.print()} className="btn-primary">인쇄 / PDF 저장</button>
        </div>
      </div>

      <div className="print-sheet mx-auto my-6 max-w-[800px] bg-white shadow p-10 text-[12px] leading-relaxed">
        <header className="border-b-2 border-slate-900 pb-4">
          <h1 className="text-2xl font-bold text-slate-950">작 업 계 획 서</h1>
          <div className="mt-2 flex justify-between text-sm">
            <span className="text-slate-500">작업계획서 #{wp.id}</span>
            <span className="text-slate-700 font-semibold">상태: {WORK_PLAN_STATUS_LABEL[wp.status]}</span>
          </div>
        </header>

        <section className="mt-5">
          <table className="w-full border border-slate-300">
            <tbody>
              <Row label="제목" value={wp.title} />
              <Row label="현장" value={`${wp.site_name ?? '-'} (${wp.bp_company_name ?? '-'})`} />
              <Row label="작업일" value={wp.work_date} />
              <Row label="작업시간" value={
                wp.start_time
                  ? `${wp.start_time.slice(0, 5)} ~ ${wp.end_time?.slice(0, 5) ?? '-'}`
                  : '-'
              } />
              <Row label="작업위치" value={wp.work_location ?? '-'} />
              <Row label="상세 내용" value={wp.description ?? '-'} multiline />
            </tbody>
          </table>
        </section>

        <section className="mt-6">
          <h2 className="text-base font-bold text-slate-900 mb-2">투입 장비 ({wp.equipment?.length ?? 0})</h2>
          {(wp.equipment?.length ?? 0) === 0 ? (
            <p className="text-slate-400 text-xs">없음</p>
          ) : (
            <table className="w-full border border-slate-300">
              <thead>
                <tr className="bg-slate-50 text-left">
                  <Th>장비</Th><Th>분류</Th><Th>공급사</Th><Th>용도</Th><Th>비고</Th>
                </tr>
              </thead>
              <tbody>
                {wp.equipment!.map((e) => (
                  <tr key={e.id}>
                    <Td>{e.equipment_name ?? `장비#${e.equipment_id}`}</Td>
                    <Td>{e.category ? EQUIPMENT_CATEGORY_LABEL[e.category] : '-'}</Td>
                    <Td>{e.supplier_company_name ?? '-'}</Td>
                    <Td>{e.purpose ?? '-'}</Td>
                    <Td>{e.note ?? '-'}</Td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="mt-6">
          <h2 className="text-base font-bold text-slate-900 mb-2">투입 인원 ({wp.persons?.length ?? 0})</h2>
          {(wp.persons?.length ?? 0) === 0 ? (
            <p className="text-slate-400 text-xs">없음</p>
          ) : (
            <table className="w-full border border-slate-300">
              <thead>
                <tr className="bg-slate-50 text-left">
                  <Th>이름</Th><Th>역할</Th><Th>공급사</Th><Th>매칭 장비</Th><Th>비고</Th>
                </tr>
              </thead>
              <tbody>
                {wp.persons!.map((p) => (
                  <tr key={p.id}>
                    <Td>{p.person_name ?? `인원#${p.person_id}`}</Td>
                    <Td>{p.role ?? '-'}</Td>
                    <Td>{p.supplier_company_name ?? '-'}</Td>
                    <Td>{p.equipment_id ? `장비#${p.equipment_id}` : '-'}</Td>
                    <Td>{p.note ?? '-'}</Td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        {(wp.compliance_checks?.length ?? 0) > 0 && (
          <section className="mt-6">
            <h2 className="text-base font-bold text-slate-900 mb-2">서류 컴플라이언스 이력</h2>
            <table className="w-full border border-slate-300">
              <thead>
                <tr className="bg-slate-50 text-left">
                  <Th>일시</Th><Th>대상</Th><Th>상태</Th><Th>사유</Th>
                </tr>
              </thead>
              <tbody>
                {wp.compliance_checks!.map((c) => (
                  <tr key={c.id}>
                    <Td>{c.checked_at.replace('T', ' ').slice(0, 16)}</Td>
                    <Td>{c.target_type === 'EQUIPMENT' ? '장비' : '인원'}#{c.target_id}</Td>
                    <Td>{COMPLIANCE_STATUS_LABEL[c.status]}</Td>
                    <Td>{c.reason ?? '-'}{c.override_reason ? ` (강제: ${c.override_reason})` : ''}</Td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        )}

        <footer className="mt-10 grid grid-cols-2 gap-6 text-xs text-slate-700">
          <div className="border-t-2 border-slate-300 pt-2">
            <div className="text-slate-500">작성</div>
            <div className="mt-8 text-right">서명/인</div>
          </div>
          <div className="border-t-2 border-slate-300 pt-2">
            <div className="text-slate-500">승인</div>
            <div className="mt-8 text-right">서명/인</div>
          </div>
        </footer>

        <div className="mt-4 text-[10px] text-slate-400 text-right">
          출력일: {new Date().toLocaleString('ko-KR')}
        </div>
      </div>
    </div>
  );
}

function Row({ label, value, multiline }: { label: string; value: string; multiline?: boolean }) {
  return (
    <tr>
      <th className="border border-slate-300 bg-slate-50 px-3 py-2 text-left w-[110px] text-slate-700 font-semibold">{label}</th>
      <td className={`border border-slate-300 px-3 py-2 text-slate-900 ${multiline ? 'whitespace-pre-line' : ''}`}>{value}</td>
    </tr>
  );
}

function Th({ children }: { children: React.ReactNode }) {
  return <th className="border border-slate-300 px-2 py-1.5 text-slate-700 font-semibold">{children}</th>;
}

function Td({ children }: { children: React.ReactNode }) {
  return <td className="border border-slate-300 px-2 py-1.5 text-slate-900">{children}</td>;
}
