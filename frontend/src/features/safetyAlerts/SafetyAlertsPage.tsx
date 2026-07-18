import { useEffect, useMemo, useRef, useState } from 'react';
import { AxiosError } from 'axios';
import { Client as StompClient } from '@stomp/stompjs';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import KakaoMap, { type MapMarker, type MapCircle, type KakaoMapHandle } from '../../components/KakaoMap';
import { useAuth } from '../auth/AuthContext';
import { toast } from '../../lib/toast';
import {
  KIND_LABEL,
  LEVEL_BADGE,
  LEVEL_LABEL,
  ackState,
  type SafetyAlertResponse,
} from '../../types/safetyAlert';
import { PersonAvatar, SiteWeatherCard, TabButton, type SiteWeatherRow } from './atoms';
import WorkTimeSection from './WorkTimeSection';

const ROW_CAP = 200;

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!));
}

/** S5' 확인응답 상태 배지 — 확인됨(시각)/미확인/에스컬레이션됨. ack 대상 아니면 표시 안 함. */
function AckBadge({ r }: { r: SafetyAlertResponse }) {
  const st = ackState(r);
  if (st === 'na') return <span className="text-xs text-slate-400">—</span>;
  if (st === 'acknowledged') {
    const t = r.acknowledged_at
      ? new Date(r.acknowledged_at).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
      : '';
    return <span className="inline-block rounded bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">확인됨 {t}</span>;
  }
  if (st === 'escalated') {
    return <span className="inline-block rounded bg-rose-100 px-2 py-0.5 text-xs font-medium text-rose-700 ring-1 ring-rose-300">에스컬레이션됨</span>;
  }
  return <span className="inline-block rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">미확인</span>;
}
type SiteRow = { id: number; name: string; latitude?: number | null; longitude?: number | null; geofenceRadiusM?: number | null };

/** 작업자 안전알림 — ADMIN/BP. 현장 탭으로 분리 + 미해결 알람 지도 + 표(사진/전화 포함). */
export default function SafetyAlertsPage() {
  const { user } = useAuth();
  const allowed = user?.role === 'ADMIN' || user?.role === 'BP';

  const [unresolvedOnly, setUnresolvedOnly] = useState(true);
  const [unackedOnly, setUnackedOnly] = useState(false);
  const [rows, setRows] = useState<SafetyAlertResponse[]>([]);
  const [liveCount, setLiveCount] = useState(0);
  const [sites, setSites] = useState<SiteRow[]>([]);
  const [siteWeather, setSiteWeather] = useState<SiteWeatherRow[]>([]);
  const mapRef = useRef<KakaoMapHandle>(null);
  const [focusedAlertId, setFocusedAlertId] = useState<number | null>(null);

  function focusOnAlert(r: SafetyAlertResponse) {
    if (r.lat == null || r.lng == null) {
      toast.error('좌표 정보가 없는 알림입니다');
      return;
    }
    setFocusedAlertId(r.id);
    mapRef.current?.panTo({ lat: r.lat as number, lng: r.lng as number }, 2);
    // 페이지 상단(지도) 으로 스크롤.
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  // 선택된 탭: null = 전체 / number = 특정 site_id / 'none' = site 미지정.
  const [activeTab, setActiveTab] = useState<number | null | 'none'>(null);

  async function load() {
    if (!allowed) return;
    try {
      const r = await api.get<SafetyAlertResponse[]>('/api/safety-alerts', {
        params: { unresolvedOnly },
      });
      setRows(r.data.slice(0, ROW_CAP));
    } catch (e) {
      const err = e as AxiosError<{ message?: string }>;
      toast.error(err.response?.data?.message ?? '안전알림을 불러올 수 없습니다');
    }
  }

  useEffect(() => { load(); }, [unresolvedOnly]);

  useEffect(() => {
    if (!allowed) return;
    api.get<SiteRow[]>('/api/sites').then((r) => setSites(r.data)).catch(() => setSites([]));
    // 출근 중인 현장별 현재 체감온도/폭염단계 (실시간 KMA 조회라 다소 느릴 수 있음).
    api.get<SiteWeatherRow[]>('/api/safety-alerts/site-weather')
      .then((r) => setSiteWeather(r.data)).catch(() => setSiteWeather([]));
  }, [allowed]);

  useEffect(() => {
    if (!allowed) return;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const client = new StompClient({
      brokerURL: `${protocol}//${window.location.host}/ws-raw/websocket`,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/safety-alerts/all', (msg) => {
          try {
            const payload = JSON.parse(msg.body) as SafetyAlertResponse & { event?: string };
            if (payload.event === 'resolved') {
              setRows((prev) =>
                prev.map((r) => (r.id === payload.id ? { ...r, resolved: true } : r)),
              );
              return;
            }
            if (payload.event === 'acked') {
              setRows((prev) =>
                prev.map((r) =>
                  r.id === payload.id
                    ? { ...r, acknowledged_at: payload.acknowledged_at, ack_person_id: payload.ack_person_id }
                    : r,
                ),
              );
              return;
            }
            setRows((prev) => [payload, ...prev.filter((r) => r.id !== payload.id)].slice(0, ROW_CAP));
            setLiveCount((n) => n + 1);
          } catch {
            // ignore malformed
          }
        });
      },
    });
    client.activate();
    return () => { client.deactivate(); };
  }, [allowed]);

  // 탭 + 미확인 필터된 alert.
  const filteredRows = useMemo(() => {
    let list = rows;
    if (activeTab === 'none') list = list.filter((r) => r.site_id == null);
    else if (activeTab !== null) list = list.filter((r) => r.site_id === activeTab);
    // 미확인만 = ack 대상인데 아직 확인 안 됨(pending·escalated).
    if (unackedOnly) list = list.filter((r) => ackState(r) === 'pending' || ackState(r) === 'escalated');
    return list;
  }, [rows, activeTab, unackedOnly]);

  // 미해결 + 좌표 있는 알람 마커. tooltipHtml 로 hover/클릭 시 상세 표시.
  const mapMarkers = useMemo<MapMarker[]>(() => {
    return filteredRows
      .filter((r) => !r.resolved && r.lat != null && r.lng != null)
      .map((r) => {
        const name = r.person_name ?? `#${r.person_id}`;
        const kind = KIND_LABEL[r.kind] ?? r.kind;
        const level = LEVEL_LABEL[r.level] ?? r.level;
        const when = new Date(r.created_at).toLocaleString('ko-KR');
        const phone = r.person_phone
          ? `<div style="margin-top:4px"><a href="tel:${r.person_phone}" style="color:#2563eb">${r.person_phone}</a></div>`
          : '';
        const vitals: string[] = [];
        if (r.hr != null) vitals.push(`심박 ${r.hr}`);
        if (r.spo2 != null) vitals.push(`SpO₂ ${r.spo2}`);
        if (r.body_temp != null) vitals.push(`${r.body_temp}℃`);
        const vitalsHtml = vitals.length
          ? `<div style="margin-top:4px;font-size:11px;color:#64748b">${vitals.join(' · ')}</div>`
          : '';
        const tooltipHtml = `
          <div style="padding:8px 10px;min-width:160px;font-size:12px;color:#0f172a">
            <div style="font-weight:600;font-size:13px">${escapeHtml(name)}</div>
            <div style="margin-top:2px;color:#475569">${escapeHtml(kind)} · ${escapeHtml(level)}</div>
            <div style="margin-top:4px;font-size:11px;color:#64748b">${when}</div>
            ${vitalsHtml}
            ${phone}
          </div>`;
        return {
          id: `alert-${r.id}`,
          position: { lat: r.lat as number, lng: r.lng as number },
          label: name,
          color: r.level === 'danger' ? 'rose' : r.level === 'warning' ? 'amber' : 'slate',
          tooltipHtml,
        };
      });
  }, [filteredRows]);
  const mapCenter = mapMarkers[0]?.position ?? null;

  // 현장 범위 — 활성 alert가 있는 site 중 좌표 보유한 곳만. radius 있으면 원, 없으면 기본 300m.
  const activeSiteIds = useMemo(() => {
    const ids = new Set<number>();
    for (const r of filteredRows) if (!r.resolved && r.site_id != null) ids.add(r.site_id);
    return ids;
  }, [filteredRows]);
  const siteCircles = useMemo<MapCircle[]>(() => {
    return sites
      .filter((s) => activeSiteIds.has(s.id) && s.latitude != null && s.longitude != null)
      .map((s) => ({
        id: `site-${s.id}`,
        center: { lat: s.latitude!, lng: s.longitude! },
        radiusM: s.geofenceRadiusM ?? 300,
        color: 'amber' as const,
      }));
  }, [sites, activeSiteIds]);
  // 현장 중심 마커도 같이 표시 (별도 노란색).
  const siteMarkers = useMemo<MapMarker[]>(() => {
    return sites
      .filter((s) => activeSiteIds.has(s.id) && s.latitude != null && s.longitude != null)
      .map((s) => ({
        id: `site-marker-${s.id}`,
        position: { lat: s.latitude!, lng: s.longitude! },
        label: s.name,
        color: 'amber' as const,
        tooltipHtml: `<div style="padding:6px 10px;font-size:12px"><b>${escapeHtml(s.name)}</b><div style="margin-top:2px;color:#64748b">현장 범위${s.geofenceRadiusM ? ` ${s.geofenceRadiusM}m` : ''}</div></div>`,
      }));
  }, [sites, activeSiteIds]);
  const combinedMarkers = useMemo(() => [...siteMarkers, ...mapMarkers], [siteMarkers, mapMarkers]);

  // 탭 옆 카운트 — 미해결 기준.
  const countBySite = useMemo(() => {
    const map = new Map<number | 'none' | 'all', number>();
    map.set('all', 0);
    for (const r of rows) {
      if (r.resolved) continue;
      map.set('all', (map.get('all') ?? 0) + 1);
      const key: number | 'none' = r.site_id ?? 'none';
      map.set(key, (map.get(key) ?? 0) + 1);
    }
    return map;
  }, [rows]);

  async function dispatchHelp(id: number, name: string) {
    if (!window.confirm(`같은 현장의 다른 작업자들에게 "${name} 긴급" 알림을 보낼까요?`)) return;
    try {
      const res = await api.post<{ targets: number; phone_sent: number; watch_sent: number }>(
        `/api/safety-alerts/${id}/dispatch-help`,
      );
      toast.success(`${res.data.targets}명에게 발송 (폰 ${res.data.phone_sent}, 워치 ${res.data.watch_sent})`);
    } catch (e) {
      const err = e as AxiosError<{ message?: string }>;
      toast.error(err.response?.data?.message ?? '발송 실패');
    }
  }

  async function resolve(id: number) {
    if (!window.confirm('이 알림을 처리완료로 표시하시겠습니까?')) return;
    try {
      await api.post(`/api/safety-alerts/${id}/resolve`);
      setRows((prev) => prev.map((r) => (r.id === id ? { ...r, resolved: true } : r)));
      toast.success('처리완료 되었습니다');
    } catch (e) {
      const err = e as AxiosError<{ message?: string }>;
      toast.error(err.response?.data?.message ?? '처리에 실패했습니다');
    }
  }

  if (!allowed) {
    return (
      <AppShell>
        <div className="p-6 text-slate-600">접근 권한이 없습니다.</div>
      </AppShell>
    );
  }

  // 현장 탭에 표시할 site 후보 — alert 가 한 건이라도 있는 site 우선, 빈 site 는 끝.
  const siteIdsWithAlerts = new Set<number>(rows.map((r) => r.site_id).filter((x): x is number => x != null));
  const sortedSites = [...sites].sort((a, b) => {
    const aH = siteIdsWithAlerts.has(a.id) ? 0 : 1;
    const bH = siteIdsWithAlerts.has(b.id) ? 0 : 1;
    return aH - bH;
  });
  const hasNoneBucket = rows.some((r) => r.site_id == null);

  return (
    <AppShell>
      <div className="p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-semibold text-slate-900">
            작업자 안전알림
            {liveCount > 0 && (
              <span className="ml-2 inline-block rounded-full bg-rose-100 px-2 py-0.5 text-xs font-medium text-rose-700">
                실시간 +{liveCount}
              </span>
            )}
          </h1>
          <div className="flex items-center gap-2">
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={unresolvedOnly}
                onChange={(e) => setUnresolvedOnly(e.target.checked)}
              />
              미처리만
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={unackedOnly}
                onChange={(e) => setUnackedOnly(e.target.checked)}
              />
              미확인만
            </label>
            <button
              onClick={load}
              className="rounded border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50"
            >
              새로고침
            </button>
          </div>
        </div>

        {/* 현장 탭 */}
        <div className="flex flex-wrap gap-1 border-b border-slate-200">
          <TabButton active={activeTab === null} count={countBySite.get('all') ?? 0}
            onClick={() => setActiveTab(null)}>전체</TabButton>
          {sortedSites.map((s) => (
            <TabButton key={s.id} active={activeTab === s.id} count={countBySite.get(s.id) ?? 0}
              onClick={() => setActiveTab(s.id)}>{s.name}</TabButton>
          ))}
          {hasNoneBucket && (
            <TabButton active={activeTab === 'none'} count={countBySite.get('none') ?? 0}
              onClick={() => setActiveTab('none')}>현장 미지정</TabButton>
          )}
        </div>

        {/* 현장별 폭염 단계 — 출근 중인 현장의 현재 체감온도/단계 */}
        {siteWeather.length > 0 && (
          <div>
            <div className="text-sm font-semibold text-slate-800 mb-2">현장별 폭염 단계 (현재)</div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {siteWeather.map((w) => (
                <SiteWeatherCard key={w.site_id} w={w} />
              ))}
            </div>
          </div>
        )}

        {/* 출근 중 작업자 작업시간 지정 — 휴식 알림 기준 */}
        <WorkTimeSection />

        {mapMarkers.length > 0 && (
          <div className="rounded border border-slate-200 bg-white overflow-hidden">
            <div className="flex items-center justify-between px-3 py-2 border-b border-slate-100">
              <div className="text-sm font-semibold text-slate-800">
                미해결 알람 위치 ({mapMarkers.length})
              </div>
              <div className="flex items-center gap-3 text-xs text-slate-500">
                <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-rose-500" />위험</span>
                <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-amber-500" />경고</span>
                <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-slate-400" />기타</span>
              </div>
            </div>
            <KakaoMap ref={mapRef} center={mapCenter} zoom={3} markers={combinedMarkers} circles={siteCircles} height="360px" />
          </div>
        )}

        {filteredRows.length === 0 ? (
          <div className="rounded border border-dashed border-slate-300 p-10 text-center text-slate-500">
            표시할 알림이 없습니다.
          </div>
        ) : (
          <div className="overflow-x-auto rounded border border-slate-200 bg-white">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left text-slate-600">
                <tr>
                  <th className="px-3 py-2">시각</th>
                  <th className="px-3 py-2">작업자</th>
                  <th className="px-3 py-2">연락처</th>
                  <th className="px-3 py-2">종류</th>
                  <th className="px-3 py-2">수준</th>
                  <th className="px-3 py-2">확인</th>
                  <th className="px-3 py-2">바이탈</th>
                  <th className="px-3 py-2">위치</th>
                  <th className="px-3 py-2">처리</th>
                </tr>
              </thead>
              <tbody>
                {filteredRows.map((r) => (
                  <tr key={r.id}
                      onClick={() => focusOnAlert(r)}
                      className={`border-t border-slate-100 cursor-pointer transition-colors ${
                        focusedAlertId === r.id ? 'bg-rose-50' : 'hover:bg-slate-50'
                      }`}>
                    <td className="px-3 py-2 text-slate-700 whitespace-nowrap">
                      {new Date(r.created_at).toLocaleString('ko-KR')}
                    </td>
                    <td className="px-3 py-2">
                      <div className="flex items-center gap-2">
                        <PersonAvatar personId={r.person_id} hasPhoto={!!r.person_has_photo} />
                        <span className="font-medium text-slate-900">
                          {r.person_name ?? `#${r.person_id}`}
                        </span>
                      </div>
                    </td>
                    <td className="px-3 py-2 text-slate-700">
                      {r.person_phone ? (
                        <a href={`tel:${r.person_phone}`} onClick={(e) => e.stopPropagation()}
                           className="text-blue-600 hover:underline">
                          {r.person_phone}
                        </a>
                      ) : '—'}
                    </td>
                    <td className="px-3 py-2 text-slate-700">{KIND_LABEL[r.kind] ?? r.kind}</td>
                    <td className="px-3 py-2">
                      <span
                        className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${
                          LEVEL_BADGE[r.level] ?? 'bg-slate-100 text-slate-700'
                        }`}
                      >
                        {LEVEL_LABEL[r.level] ?? r.level}
                      </span>
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      <AckBadge r={r} />
                    </td>
                    <td className="px-3 py-2 text-slate-700 whitespace-nowrap">
                      {r.hr != null && <span className="mr-2">심박 {r.hr}</span>}
                      {r.spo2 != null && <span className="mr-2">SpO₂ {r.spo2}</span>}
                      {r.body_temp != null && <span>{r.body_temp}℃</span>}
                    </td>
                    <td className="px-3 py-2 text-slate-700 whitespace-nowrap">
                      {r.lat != null && r.lng != null ? (
                        <a
                          href={`https://map.kakao.com/link/map/${encodeURIComponent(r.person_name ?? `#${r.person_id}`)},${r.lat},${r.lng}`}
                          target="_blank" rel="noreferrer"
                          onClick={(e) => e.stopPropagation()}
                          className="text-blue-600 hover:underline text-xs"
                        >
                          {(r.lat as number).toFixed(5)}, {(r.lng as number).toFixed(5)}
                        </a>
                      ) : '—'}
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      {r.resolved ? (
                        <span className="text-xs text-slate-500">완료</span>
                      ) : (
                        <div className="flex flex-col gap-1" onClick={(e) => e.stopPropagation()}>
                          <button
                            onClick={() => dispatchHelp(r.id, r.person_name ?? `#${r.person_id}`)}
                            className="rounded bg-rose-600 px-2 py-1 text-xs font-medium text-white hover:bg-rose-700"
                          >
                            주변 호출
                          </button>
                          <button
                            onClick={() => resolve(r.id)}
                            className="rounded bg-emerald-600 px-2 py-1 text-xs font-medium text-white hover:bg-emerald-700"
                          >
                            처리완료
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </AppShell>
  );
}

