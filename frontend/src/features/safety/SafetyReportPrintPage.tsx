import { useEffect, useState, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import {
  formatElapsed,
  SEVERITY_LABEL,
  timeOnly,
  type EmergencyTimeline,
  type SafetyReport,
  type TimelineAlert,
} from '../../types/safetyReport';

/**
 * P3d 안전관리 이행 보고서 인쇄뷰 — HTML + print CSS(업체변경 신청서 패턴 재사용). AppShell 미사용.
 * 표지(현장·기간·발행일시·발행자) + ①요약 + ②타임라인 + ③미이행 + ④현행 기준.
 * 개인 서명 이미지는 출력하지 않고 유무·건수만 표기(개인정보 최소화).
 */
export default function SafetyReportPrintPage() {
  const [params] = useSearchParams();
  const siteId = params.get('siteId');
  const from = params.get('from');
  const to = params.get('to');
  const [r, setR] = useState<SafetyReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!siteId) { setError('현장이 지정되지 않았습니다'); return; }
    api.get<SafetyReport>('/api/safety-reports', { params: { siteId, from, to } })
      .then((res) => setR(res.data))
      .catch((e) => setError(e instanceof AxiosError ? (e.response?.data?.message ?? '불러오기 실패') : '불러오기 실패'));
  }, [siteId, from, to]);

  if (error) return <p className="p-8 text-rose-600">{error}</p>;
  if (!r) return <p className="p-8 text-slate-400">불러오는 중...</p>;

  const a = r.alert_summary;
  const ins = r.inspection_summary;
  const w = r.work_compliance_summary;
  const nc = r.noncompliance;
  const s = r.standard;

  return (
    <div className="min-h-screen bg-slate-100 print:bg-white">
      <style>{`
        @media print {
          .no-print { display: none !important; }
          .print-sheet { box-shadow: none !important; margin: 0 !important; max-width: none !important; }
        }
      `}</style>

      <div className="no-print sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
        <span className="text-sm font-semibold text-slate-700">안전관리 이행 보고서 미리보기</span>
        <div className="flex gap-2">
          <button type="button" onClick={() => window.close()} className="rounded border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50">닫기</button>
          <button type="button" onClick={() => window.print()} className="btn-primary">인쇄 / PDF 저장</button>
        </div>
      </div>

      <div className="print-sheet mx-auto my-6 max-w-[820px] bg-white p-10 text-[12px] leading-relaxed shadow">
        <header className="border-b-2 border-slate-900 pb-3 text-center">
          <h1 className="text-2xl font-bold tracking-[0.3em] text-slate-950">안전관리 이행 보고서</h1>
          <div className="mt-1 text-xs text-slate-500">중대재해처벌법·산업안전보건법 대비 · 고지·확인·조치 증거사슬</div>
        </header>

        {/* 표지 */}
        <table className="mt-5 w-full border-collapse text-[12px]">
          <tbody>
            <tr>
              <Th>현장</Th><Td>{r.site_name ?? '-'}{r.site_code ? ` (${r.site_code})` : ''}</Td>
              <Th>기간</Th><Td>{r.from} ~ {r.to}</Td>
            </tr>
            <tr>
              <Th>발주사(BP)</Th><Td>{r.bp_company_name ?? '-'}</Td>
              <Th>원청</Th><Td>{r.client_org_name ?? '-'}</Td>
            </tr>
            <tr>
              <Th>발행일시</Th><Td>{r.generated_at.slice(0, 16).replace('T', ' ')}</Td>
              <Th>발행자</Th><Td>{r.generated_by ?? '-'}</Td>
            </tr>
          </tbody>
        </table>

        {/* ① 요약 */}
        <H2>① 요약</H2>
        <table className="w-full border-collapse text-[12px]">
          <tbody>
            <tr>
              <Th>안전알림</Th>
              <Td colSpan={3}>총 {a.total}건 · 확인대상 {a.ack_needed}건 · 확인 {a.acknowledged}건 · 확인율 {a.ack_rate_pct ?? '-'}{a.ack_rate_pct == null ? '' : '%'} · 평균 확인 {a.avg_ack_minutes == null ? '-' : `${a.avg_ack_minutes}분`} · 에스컬레이션 {a.escalated_count}건</Td>
            </tr>
            <tr>
              <Th>법정점검(NFC)</Th>
              <Td colSpan={3}>실시 {ins.legal_days}일 · 총 {ins.legal_total}건 · NFC 검증율 {ins.nfc_rate_pct ?? '-'}{ins.nfc_rate_pct == null ? '' : '%'} · 대상 장비 {ins.target_equipment}대</Td>
            </tr>
            <tr>
              <Th>조종원 일일점검</Th>
              <Td colSpan={3}>실시 {ins.operator_days}일 · 총 {ins.operator_total}건</Td>
            </tr>
            <tr>
              <Th>작업계획서 서명</Th>
              <Td colSpan={3}>서명 완결 {w.plan_fully_signed} / {w.plan_total}건 (5인 전원)</Td>
            </tr>
            <tr>
              <Th>일일 확인서 서명</Th>
              <Td colSpan={3}>서명율 {w.log_sign_rate_pct ?? '-'}{w.log_sign_rate_pct == null ? '' : '%'} ({w.log_signed} / {w.log_total}건)</Td>
            </tr>
          </tbody>
        </table>

        {/* 긴급 대응 이력 (개인 응급 골든타임) */}
        {r.emergency_response.total > 0 && (
          <>
            <H2>긴급 대응 이력 (개인 응급 SOS·낙상·BLE 중계 · 골든타임)</H2>
            <table className="w-full border-collapse text-[12px]">
              <tbody>
                <tr>
                  <Th className="w-40">요약</Th>
                  <Td>긴급 {r.emergency_response.total}건 · 대응체인 발동 {r.emergency_response.chain_activated}건 · 동료 응답 {r.emergency_response.responded}건
                    · 평균 최초응답 {r.emergency_response.avg_first_response_seconds == null ? '-' : formatElapsed(Math.round(r.emergency_response.avg_first_response_seconds))}
                    · 60초 무응답 확대 {r.emergency_response.escalated_count}건 · BLE 대리중계 {r.emergency_response.relayed_count}건</Td>
                </tr>
              </tbody>
            </table>
            <table className="mt-1 w-full border-collapse text-[11px]">
              <thead>
                <tr>
                  <Th className="w-28">종류</Th>
                  <Th>감지 → 통보 → 응답 → 해제</Th>
                  <Th className="w-20">응답자</Th>
                </tr>
              </thead>
              <tbody>
                {r.emergency_response.timelines.map((t) => (
                  <tr key={t.alert_id}>
                    <Td className="align-top">{t.kind_label}{t.relayed ? ' (중계)' : ''}{t.escalated ? ' (확대)' : ''}</Td>
                    <Td className="align-top">{emergencyLine(t)}</Td>
                    <Td className="align-top">{t.responder_count}명</Td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}

        {/* ② 타임라인 */}
        <H2>② 일자별 타임라인 (발송 → 확인 사슬)</H2>
        {r.timeline.length === 0 ? (
          <EmptyRow>기간 내 안전 활동 기록이 없습니다.</EmptyRow>
        ) : (
          <table className="w-full border-collapse text-[11px]">
            <thead>
              <tr>
                <Th className="w-24">일자</Th>
                <Th>안전알림 (발송→확인)</Th>
                <Th className="w-28">점검</Th>
                <Th className="w-32">강풍</Th>
              </tr>
            </thead>
            <tbody>
              {r.timeline.map((d) => (
                <tr key={d.date}>
                  <Td className="align-top font-semibold">{d.date}</Td>
                  <Td className="align-top">
                    {d.alerts.length === 0 ? <span className="text-slate-400">-</span> : (
                      <ul className="space-y-0.5">
                        {d.alerts.map((al) => <li key={al.id}>{alertLine(al)}</li>)}
                      </ul>
                    )}
                  </Td>
                  <Td className="align-top">
                    {d.legal_inspections > 0 && <div>법정 {d.legal_inspections}</div>}
                    {d.operator_inspections > 0 && <div>조종원 {d.operator_inspections}</div>}
                    {d.legal_inspections === 0 && d.operator_inspections === 0 && <span className="text-slate-400">-</span>}
                  </Td>
                  <Td className="align-top">
                    {d.wind_event ? (
                      <span>{d.wind_event.wind_mps != null ? `${d.wind_event.wind_mps}m/s ` : ''}
                        {d.wind_event.entered_at ? `진입 ${timeOnly(d.wind_event.entered_at)}` : ''}
                        {d.wind_event.cleared_at ? ` 해제 ${timeOnly(d.wind_event.cleared_at)}` : ''}</span>
                    ) : <span className="text-slate-400">-</span>}
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {/* ③ 미이행 */}
        <H2>③ 미이행 목록</H2>
        <table className="w-full border-collapse text-[12px]">
          <tbody>
            <tr>
              <Th className="w-40">미확인 안전알림 ({nc.unacknowledged_alerts.length})</Th>
              <Td>{nc.unacknowledged_alerts.length === 0 ? '없음' :
                nc.unacknowledged_alerts.map((al) => `${al.kind_label} ${al.created_at.slice(5, 16).replace('T', ' ')}${al.escalated_at ? '(에스컬)' : ''}`).join(', ')}</Td>
            </tr>
            <tr>
              <Th>점검 없는 작업 예정일 ({nc.uninspected_work_days.length})</Th>
              <Td>{nc.uninspected_work_days.length === 0 ? '없음' : nc.uninspected_work_days.join(', ')}</Td>
            </tr>
            <tr>
              <Th>서명 미완결 계획서 ({nc.unsigned_plans.length})</Th>
              <Td>{nc.unsigned_plans.length === 0 ? '없음' :
                nc.unsigned_plans.map((p) => `${p.work_date} ${p.title ?? `#${p.work_plan_id}`}(미서명: ${p.pending_roles.join('·')})`).join(' / ')}</Td>
            </tr>
            <tr>
              <Th>미서명 일일 확인서 ({nc.unsigned_logs.length})</Th>
              <Td>{nc.unsigned_logs.length === 0 ? '없음' : nc.unsigned_logs.map((l) => `${l.work_date} ${l.label}`).join(', ')}</Td>
            </tr>
          </tbody>
        </table>

        {/* ④ 현행 기준 */}
        <H2>④ 현행 안전 기준 ({s.configured ? '현장 설정' : '법정 기본값'})</H2>
        <table className="w-full border-collapse text-[12px]">
          <tbody>
            <tr>
              <Th className="w-40">폭염 임계(주의/휴식/위험/중지)</Th>
              <Td>{s.temp_caution} / {s.temp_warning} / {s.temp_danger} / {s.temp_extreme}℃ <span className="text-slate-400">(법정 {s.legal_temp_caution}/{s.legal_temp_warning}/{s.legal_temp_danger}/{s.legal_temp_extreme})</span></Td>
            </tr>
            <tr>
              <Th>휴식(간격/시간)</Th>
              <Td>{s.rest_interval_min}분 간격 / {s.rest_duration_min}분 <span className="text-slate-400">(법정 {s.legal_rest_interval}분 이하 / {s.legal_rest_duration}분 이상)</span></Td>
            </tr>
            <tr>
              <Th>무더위 시간대</Th><Td>{s.midday_start_hour}~{s.midday_end_hour}시 옥외작업 중지 권고</Td>
            </tr>
            <tr>
              <Th>강풍 작업중지</Th><Td>{s.wind_stop_mps}m/s <span className="text-slate-400">(법정 {s.legal_wind_stop}m/s 이하)</span></Td>
            </tr>
            <tr>
              <Th>일일점검 게이트 / 정비 주기</Th>
              <Td>{s.enforce_daily_inspection_gate ? '작업시작 차단' : '경고'} / {s.maintenance_interval_hours == null ? '정비 알림 비활성' : `${s.maintenance_interval_hours}시간`}</Td>
            </tr>
          </tbody>
        </table>

        <p className="mt-6 text-[11px] text-slate-400">
          ※ 본 보고서는 시스템 기록(타임스탬프)만으로 자동 집계되었습니다. 개인 서명 이미지는 출력하지 않고 유무·건수만 표기합니다(개인정보 최소화).
          강풍 이벤트는 현장별 최근 전이 스냅샷 1건이 기록됩니다.
        </p>
        <div className="mt-2 text-right text-[11px] text-slate-400">발행: {r.generated_at.slice(0, 16).replace('T', ' ')} · {r.generated_by ?? '-'}</div>
      </div>
    </div>
  );
}

function emergencyLine(t: EmergencyTimeline): string {
  const steps: string[] = [`감지 ${timeOnly(t.detected_at)}`];
  if (t.peer_notified_at) steps.push(`통보 ${timeOnly(t.peer_notified_at)}`);
  if (t.first_response_at) {
    steps.push(`응답 ${timeOnly(t.first_response_at)}${t.response_elapsed_seconds != null ? ` (${formatElapsed(t.response_elapsed_seconds)})` : ''}`);
  } else if (t.peer_notified_at) {
    steps.push('응답 없음');
  }
  if (t.resolved_at) steps.push(`해제 ${timeOnly(t.resolved_at)}`);
  return steps.join(' → ');
}

function alertLine(al: TimelineAlert): string {
  const sev = SEVERITY_LABEL[al.severity ?? 'NORMAL'] ?? (al.severity ?? '');
  if (!al.needs_ack) return `[${sev}] ${al.kind_label} 발송 ${timeOnly(al.created_at)}`;
  if (al.acknowledged_at) {
    return `[${sev}] ${al.kind_label} 발송 ${timeOnly(al.created_at)} → 확인 ${timeOnly(al.acknowledged_at)} (${formatElapsed(al.ack_elapsed_seconds)})`;
  }
  return `[${sev}] ${al.kind_label} 발송 ${timeOnly(al.created_at)} → 미확인${al.escalated_at ? '(에스컬)' : ''}`;
}

function Th({ children, className = '' }: { children?: ReactNode; className?: string }) {
  return <th className={`border border-slate-300 bg-slate-50 px-3 py-1.5 text-left align-top font-semibold ${className}`}>{children}</th>;
}
function Td({ children, colSpan, className = '' }: { children?: ReactNode; colSpan?: number; className?: string }) {
  return <td colSpan={colSpan} className={`border border-slate-300 px-3 py-1.5 ${className}`}>{children}</td>;
}
function H2({ children }: { children: ReactNode }) {
  return <h2 className="mt-6 mb-2 text-sm font-bold text-slate-900">{children}</h2>;
}
function EmptyRow({ children }: { children: ReactNode }) {
  return <div className="border border-slate-300 px-3 py-3 text-center text-slate-400">{children}</div>;
}
