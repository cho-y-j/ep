import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import KakaoMap, { type MapMarker, type MapCircle, type PolygonGeoJson, type KakaoMapHandle } from '../../components/KakaoMap';
import { useAuth } from '../auth/AuthContext';
import { toast } from '../../lib/toast';
import { ackState, KIND_LABEL, LEVEL_LABEL, SEVERITY_LABEL } from '../../types/safetyAlert';
import type { BoardSite, SiteBoard, AlertMarker, RecipientStatus, WatchWorker, PersonVitals } from '../../types/safetyBoard';
import LegalInspectionStatusCard from '../safety/LegalInspectionStatusCard';
import FilterBar from '../../components/ui/FilterBar';
import FilterSelect from '../../components/ui/FilterSelect';
import { PERSON_ROLE_LABEL, type PersonRole } from '../../types/person';

const HAS_KAKAO_KEY = !!import.meta.env.VITE_KAKAO_JS_KEY;
const POLL_MS = 10_000;
const WATCH_OFFLINE_SEC = 30 * 60;   // P5-W0 30분 무수신 = 두절.

type TabKey = 'alerts' | 'watch' | 'inspection' | 'announcement' | 'report' | 'settings';

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!));
}

const STAGE_LEVEL_BADGE: Record<string, string> = {
  info: 'bg-emerald-100 text-emerald-700',
  caution: 'bg-amber-100 text-amber-700',
  warning: 'bg-orange-100 text-orange-700',
  danger: 'bg-rose-100 text-rose-700',
};

/** 자동 팝업·마커 강조 대상 — 긴급(EMERGENCY) 또는 미확인 주의(CAUTION). */
function isCriticalAlert(a: AlertMarker): boolean {
  return a.severity === 'EMERGENCY' || (a.severity === 'CAUTION' && a.acknowledged_at == null);
}

// 경보음(선택 토글) — Web Audio 짧은 비프. 자산 없음, 실패 시 무음 폴백.
let audioCtx: AudioContext | null = null;
function playBeep() {
  try {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return;
    audioCtx = audioCtx ?? new Ctor();
    const ctx = audioCtx;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.value = 880;
    gain.gain.value = 0.15;
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.25);
  } catch { /* 무음 폴백 */ }
}

/**
 * P4a 안전 상황판 — 맵 중심 단일 관제. BP·ADMIN(자기/전체 현장)·CLIENT(자기 원청, 읽기).
 * 상단 지도(주인공) + 요약 스트립 + 탭(알림/점검/공지/보고서/설정).
 */
export default function SafetyBoardPage() {
  const { user } = useAuth();
  const role = user?.role;
  const isClient = role === 'CLIENT';
  const canManage = role === 'ADMIN' || role === 'BP';

  const [sites, setSites] = useState<BoardSite[]>([]);
  const [siteId, setSiteId] = useState<number | null>(null);
  const [board, setBoard] = useState<SiteBoard | null>(null);
  const [loading, setLoading] = useState(false);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [tab, setTab] = useState<TabKey>('alerts');
  const [expandedAnn, setExpandedAnn] = useState<number | null>(null);
  const [recipients, setRecipients] = useState<Record<number, RecipientStatus[]>>({});
  const mapRef = useRef<KakaoMapHandle>(null);

  // C. 자동 팝업 — 폴링 diff 로 새 위험 경보 감지. seen 으로 재팝업 방지, 큐로 다건(최신 우선) 처리.
  const seenAlertIds = useRef<Set<number>>(new Set());
  const primedRef = useRef(false);
  const [alertQueue, setAlertQueue] = useState<AlertMarker[]>([]);
  const [soundOn, setSoundOn] = useState(false);
  const [mapFailed, setMapFailed] = useState(false);   // 카카오 SDK 로드 실패 시 폴백 목록으로.
  // 안전 지도 필터 — 검색(차량번호·이름)·차량 가동상태·건강·공급사·BP. 클라 필터(마커·목록·요약 좁힘).
  const [q, setQ] = useState('');
  const [fVehicle, setFVehicle] = useState('');
  const [fHealth, setFHealth] = useState('');
  const [fSupplier, setFSupplier] = useState('');
  const [fBp, setFBp] = useState('');
  const soundOnRef = useRef(false);
  useEffect(() => { soundOnRef.current = soundOn; }, [soundOn]);

  // 현장 목록 로드 → 첫 현장 자동 선택.
  useEffect(() => {
    api.get<BoardSite[]>('/api/safety-board/sites')
      .then((r) => {
        setSites(r.data);
        if (r.data.length > 0) setSiteId((prev) => prev ?? r.data[0].id);
      })
      .catch((e) => {
        const err = e as AxiosError<{ message?: string }>;
        toast.error(err.response?.data?.message ?? '현장 목록을 불러올 수 없습니다');
      });
  }, []);

  async function loadBoard(id: number, silent = false) {
    if (!silent) setLoading(true);
    try {
      const r = await api.get<SiteBoard>(`/api/safety-board/sites/${id}`);
      setBoard(r.data);
      setUpdatedAt(new Date());
    } catch (e) {
      const err = e as AxiosError<{ message?: string }>;
      if (!silent) toast.error(err.response?.data?.message ?? '상황판을 불러올 수 없습니다');
    } finally {
      if (!silent) setLoading(false);
    }
  }

  // 현장 선택 시 로드 + 10초 폴링.
  useEffect(() => {
    if (siteId == null) return;
    loadBoard(siteId);
    const t = setInterval(() => loadBoard(siteId, true), POLL_MS);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [siteId]);

  // 현장 전환 시 팝업 상태 초기화 — 새 현장은 다시 prime(기존 경보는 조용히 seen 처리).
  useEffect(() => {
    seenAlertIds.current = new Set();
    primedRef.current = false;
    setAlertQueue([]);
    setQ(''); setFVehicle(''); setFHealth(''); setFSupplier(''); setFBp('');
  }, [siteId]);

  const geo = board && board.latitude != null && board.longitude != null;
  const mapCenter = geo ? { lat: board!.latitude!, lng: board!.longitude! } : null;

  const polygon = useMemo<PolygonGeoJson | null>(() => {
    if (!board?.polygon_geojson) return null;
    try { return JSON.parse(board.polygon_geojson) as PolygonGeoJson; } catch { return null; }
  }, [board?.polygon_geojson]);

  const circles = useMemo<MapCircle[]>(() => {
    if (!geo) return [];
    return [{ id: `site-${board!.site_id}`, center: mapCenter!, radiusM: board!.geofence_radius_m ?? 300, color: 'amber' }];
  }, [geo, board, mapCenter]);

  // 필터 적용 대상 = 워치 작업자(안전 지도 주체 — 차량번호·조종사·안전원·건강). 클라 필터로 좁힘.
  const allWatch = board?.watch_workers ?? [];
  const filtersActive = !!(q || fVehicle || fHealth || fSupplier || fBp);
  const activeFilterCount = [fVehicle, fHealth, fSupplier, fBp].filter(Boolean).length;
  const filteredWatch = useMemo(
    () => allWatch.filter((w) => matchWatch(w, { q, vehicle: fVehicle, health: fHealth, supplier: fSupplier, bp: fBp })),
    [allWatch, q, fVehicle, fHealth, fSupplier, fBp],
  );
  const supplierOptions = useMemo(() => uniqueCompanyOptions(allWatch, 'supplier'), [allWatch]);
  const bpOptions = useMemo(() => uniqueCompanyOptions(allWatch, 'bp'), [allWatch]);

  const markers = useMemo<MapMarker[]>(() => {
    if (!board) return [];
    const out: MapMarker[] = [];
    // 현장 중심.
    if (geo) {
      out.push({
        id: `sitecenter-${board.site_id}`, position: mapCenter!, label: board.site_name, color: 'amber',
        tooltipHtml: `<div style="padding:6px 10px;font-size:12px"><b>${escapeHtml(board.site_name)}</b><div style="margin-top:2px;color:#64748b">현장 범위${board.geofence_radius_m ? ` ${board.geofence_radius_m}m` : ''}</div></div>`,
      });
    }
    // 워치 마커 — 필터 통과분만. 최근 수신 위치에 바이탈 팝오버(맥박·체온·배터리·상태·혈압·차량·역할·소속). 위험은 펄스.
    const watchAllIds = new Set(board.watch_workers.map((w) => w.person_id));
    for (const w of filteredWatch) {
      if (w.lat == null || w.lng == null) continue;
      const d = watchDisplay(w);
      out.push({
        id: `watch-${w.person_id}`, position: { lat: w.lat, lng: w.lng },
        label: w.name, color: watchColor(w), pulse: d.pulse,
        tooltipHtml: watchTooltip(w, d.label),
      });
    }
    // 작업자 마커 — 워치 대상은 제외(중복 방지). 필터 활성 시엔 속성 없는 출근-only 마커 숨김. 체크인=파랑 / 체크아웃=회색.
    for (const w of board.workers) {
      if (w.lat == null || w.lng == null) continue;
      if (watchAllIds.has(w.person_id)) continue;
      if (filtersActive) continue;
      const when = new Date(w.check_in_at).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
      out.push({
        id: `worker-${w.person_id}`, position: { lat: w.lat, lng: w.lng },
        label: w.name, color: w.checked_in ? 'blue' : 'slate',
        tooltipHtml: `<div style="padding:8px 10px;font-size:12px"><b>${escapeHtml(w.name)}</b><div style="margin-top:2px;color:#475569">${w.checked_in ? '체크인 중' : '퇴근'}</div><div style="margin-top:2px;color:#64748b">${when} 출근</div></div>`,
      });
    }
    // 경보 마커 — 심각도 색 + 위험(긴급/미확인 주의) 펄스.
    for (const a of board.alerts) {
      if (a.lat == null || a.lng == null) continue;
      const kind = KIND_LABEL[a.kind] ?? a.kind;
      const lvl = LEVEL_LABEL[a.level] ?? a.level;
      const ackTxt = a.unacked ? '미확인' : a.acknowledged_at ? '확인됨' : '';
      out.push({
        id: `alert-${a.id}`, position: { lat: a.lat, lng: a.lng },
        label: a.person_name ?? `#${a.id}`,
        color: a.level === 'danger' ? 'rose' : a.level === 'warning' ? 'amber' : 'slate',
        pulse: isCriticalAlert(a),
        tooltipHtml: `<div style="padding:8px 10px;font-size:12px;min-width:150px"><b>${escapeHtml(a.person_name ?? kind)}</b><div style="margin-top:2px;color:#475569">${escapeHtml(kind)} · ${escapeHtml(lvl)}</div>${a.message ? `<div style="margin-top:2px;color:#64748b">${escapeHtml(a.message)}</div>` : ''}${ackTxt ? `<div style="margin-top:3px;font-weight:600;color:${a.unacked ? '#e11d48' : '#059669'}">${ackTxt}</div>` : ''}</div>`,
      });
    }
    return out;
  }, [board, geo, mapCenter, filteredWatch, filtersActive]);

  // 보드 갱신(최초/폴링)마다 위험 경보 diff. 최초 로드는 기존 경보를 조용히 prime(스톰 방지) — "새로 생긴" 것만 팝업.
  useEffect(() => {
    if (!board) return;
    const criticals = board.alerts.filter(isCriticalAlert);
    if (!primedRef.current) {
      for (const a of criticals) seenAlertIds.current.add(a.id);
      primedRef.current = true;
      return;
    }
    const fresh = criticals.filter((a) => !seenAlertIds.current.has(a.id));
    if (fresh.length === 0) return;
    for (const a of fresh) seenAlertIds.current.add(a.id);
    fresh.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());   // 최신 우선.
    setAlertQueue((q) => [...fresh, ...q]);
    if (soundOnRef.current) playBeep();
    const top = fresh[0];
    if (top.lat != null && top.lng != null) mapRef.current?.panTo({ lat: top.lat, lng: top.lng }, 2);   // 지도 자동 이동.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [board]);

  function focusOnAlert(a: AlertMarker) {
    if (a.lat == null || a.lng == null) { toast.error('좌표 정보가 없는 알림입니다'); return; }
    mapRef.current?.panTo({ lat: a.lat, lng: a.lng }, 2);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function dequeueAlert() { setAlertQueue((q) => q.slice(1)); }

  // [확인] — 관제(ADMIN/BP) 경보 처리. 기존 resolve 엔드포인트(CLIENT 불가) 재사용. seen 이 재팝업을 이미 막음.
  async function ackAlert(id: number) {
    try {
      await api.post(`/api/safety-alerts/${id}/resolve`);
      toast.success('경보를 확인 처리했습니다');
      if (siteId != null) loadBoard(siteId, true);
    } catch (e) {
      const err = e as AxiosError<{ message?: string }>;
      toast.error(err.response?.data?.message ?? '확인 처리에 실패했습니다');
    }
    dequeueAlert();
  }

  async function toggleRecipients(annId: number) {
    if (expandedAnn === annId) { setExpandedAnn(null); return; }
    setExpandedAnn(annId);
    if (!recipients[annId]) {
      try {
        const r = await api.get<RecipientStatus[]>(`/api/safety-board/announcements/${annId}/recipients`);
        setRecipients((prev) => ({ ...prev, [annId]: r.data }));
      } catch (e) {
        const err = e as AxiosError<{ message?: string }>;
        toast.error(err.response?.data?.message ?? '수신자를 불러올 수 없습니다');
      }
    }
  }

  const s = board?.summary;

  return (
    <AppShell>
      <div className="p-4 sm:p-6 space-y-4">
        {/* 헤더 + 현장 선택 */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="h1-page">안전 상황판</h1>
            <p className="text-sm text-slate-500">
              현장 안전을 지도 한 곳에서 — 출근 위치 · 경보 · 기상 · 점검 · 공지 확인
              {isClient && <span className="ml-1 text-slate-400">(읽기 전용)</span>}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <select
              value={siteId ?? ''}
              onChange={(e) => setSiteId(Number(e.target.value))}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm bg-white"
            >
              {sites.length === 0 && <option value="">현장 없음</option>}
              {sites.map((st) => (
                <option key={st.id} value={st.id}>
                  {st.name}{st.unresolved_alerts > 0 ? ` (경보 ${st.unresolved_alerts})` : ''}
                </option>
              ))}
            </select>
            <button onClick={() => setSoundOn((v) => !v)}
              className={`rounded border px-3 py-1.5 text-sm ${soundOn ? 'border-brand-300 bg-brand-50 text-brand-700' : 'border-slate-300 text-slate-500 hover:bg-slate-50'}`}
              title="새 위험 경보 발생 시 소리 알림">
              {soundOn ? '🔔 소리 켜짐' : '🔕 소리 꺼짐'}
            </button>
            <button onClick={() => siteId != null && loadBoard(siteId)}
              className="rounded border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50">
              새로고침
            </button>
          </div>
        </div>
        {updatedAt && (
          <div className="text-xs text-slate-400 -mt-2">
            {updatedAt.toLocaleTimeString('ko-KR')} 기준 · 10초마다 자동 갱신
          </div>
        )}

        {/* 안전 지도 필터 — 검색(차량번호·이름)·차량 가동상태·건강·공급사·BP. 지도 마커·워치 목록·요약을 좁힘. */}
        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '차량번호·이름 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={() => { setFVehicle(''); setFHealth(''); setFSupplier(''); setFBp(''); }}
        >
          <FilterSelect value={fVehicle} onChange={setFVehicle} placeholder="차량 전체"
            options={[{ value: 'ASSIGNED', label: '가동중' }, { value: 'AVAILABLE', label: '대기' }, { value: 'BROKEN', label: '정비' }]} />
          <FilterSelect value={fHealth} onChange={setFHealth} placeholder="건강 전체"
            options={[{ value: 'normal', label: '정상' }, { value: 'caution', label: '주의' }, { value: 'danger', label: '경보' }]} />
          <FilterSelect value={fSupplier} onChange={setFSupplier} placeholder="공급사 전체" options={supplierOptions} />
          <FilterSelect value={fBp} onChange={setFBp} placeholder="원청(BP) 전체" options={bpOptions} />
        </FilterBar>
        {filtersActive && (
          <div className="text-xs text-slate-500 -mt-2">
            워치 {filteredWatch.length}/{allWatch.length}명 표시 중 (필터 적용)
          </div>
        )}

        {/* 상단 지도 — 화면의 주인공 */}
        <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 border-b border-slate-100">
            <div className="text-sm font-semibold text-slate-800">
              현장 지도 {board ? `· 작업자 ${board.workers.length} · 경보 ${board.alerts.length}` : ''}
            </div>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
              <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-emerald-500" />정상</span>
              <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-amber-500" />주의</span>
              <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-rose-500" />경보</span>
              <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-blue-500" />출근</span>
              <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-slate-400" />미착용·두절</span>
              <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-rose-500 animate-pulse" />위험</span>
            </div>
          </div>
          {HAS_KAKAO_KEY && geo && !mapFailed ? (
            <KakaoMap ref={mapRef} center={mapCenter} zoom={board?.map_zoom ?? 3}
              markers={markers} circles={circles} polygon={polygon} height="56vh"
              onLoadError={() => setMapFailed(true)} />
          ) : (
            <MapFallback board={board} watchWorkers={filteredWatch} hasKey={HAS_KAKAO_KEY} hasGeo={!!geo} mapFailed={mapFailed} />
          )}
        </div>

        {/* 요약 스트립 */}
        <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2">
          <StatCard label="체감온도">
            {s?.weather.available ? (
              <div className="flex items-center gap-1.5">
                <span className="text-lg font-bold text-slate-800 tabular-nums">{s.weather.feels_like}℃</span>
                {s.weather.stage_label && (
                  <span className={`rounded px-1.5 py-0.5 text-[11px] font-semibold ${STAGE_LEVEL_BADGE[s.weather.level ?? ''] ?? 'bg-slate-100 text-slate-600'}`}>
                    {s.weather.stage_label}
                  </span>
                )}
              </div>
            ) : <span className="text-sm text-slate-400">—</span>}
          </StatCard>
          <StatCard label="풍속">
            {s?.weather.available && s.weather.wind_mps != null ? (
              <div className="flex items-center gap-1.5">
                <span className="text-lg font-bold text-slate-800 tabular-nums">{s.weather.wind_mps}<span className="text-xs font-medium text-slate-400"> m/s</span></span>
                {s.weather.wind_stop_active && <span className="rounded bg-rose-100 px-1.5 py-0.5 text-[11px] font-semibold text-rose-700">작업중지</span>}
              </div>
            ) : <span className="text-sm text-slate-400">—</span>}
          </StatCard>
          <StatCard label="출근">
            <span className="text-lg font-bold text-slate-800 tabular-nums">{s?.attended ?? 0}</span>
            <span className="text-xs text-slate-400"> 명 (체크인 {s?.checked_in ?? 0})</span>
          </StatCard>
          <StatCard label="미확인 알림">
            <span className={`text-lg font-bold tabular-nums ${(s?.unacked_alerts ?? 0) > 0 ? 'text-rose-600' : 'text-slate-800'}`}>
              {s?.unacked_alerts ?? 0}
            </span>
          </StatCard>
          <StatCard label="법정점검">
            <span className="text-lg font-bold text-slate-800 tabular-nums">{s?.legal_done ?? 0}</span>
            <span className="text-xs text-slate-400"> / {s?.legal_target ?? 0}</span>
          </StatCard>
          <StatCard label="조종원 점검">
            <span className="text-lg font-bold text-slate-800 tabular-nums">{s?.operator_done ?? 0}</span>
            <span className="text-xs text-slate-400"> / {s?.operator_target ?? 0}</span>
          </StatCard>
          <StatCard label="공지 확인">
            <span className="text-lg font-bold text-slate-800 tabular-nums">{s?.announcement_read ?? 0}</span>
            <span className="text-xs text-slate-400"> / {s?.announcement_total ?? 0}</span>
          </StatCard>
        </div>

        {/* 탭 */}
        <div className="flex flex-wrap gap-1 border-b border-slate-200">
          <TabBtn active={tab === 'alerts'} onClick={() => setTab('alerts')}>알림·확인</TabBtn>
          <TabBtn active={tab === 'watch'} onClick={() => setTab('watch')}>워치</TabBtn>
          <TabBtn active={tab === 'inspection'} onClick={() => setTab('inspection')}>점검</TabBtn>
          <TabBtn active={tab === 'announcement'} onClick={() => setTab('announcement')}>공지</TabBtn>
          <TabBtn active={tab === 'report'} onClick={() => setTab('report')}>보고서</TabBtn>
          {canManage && <TabBtn active={tab === 'settings'} onClick={() => setTab('settings')}>설정</TabBtn>}
        </div>

        <div className="tab-fade-in">
          {tab === 'alerts' && <AlertsTab board={board} onFocus={focusOnAlert} canManage={canManage} />}
          {tab === 'watch' && <WatchTab workers={filteredWatch} canManage={canManage} />}
          {tab === 'inspection' && <InspectionTab board={board} canManage={canManage} siteId={siteId} />}
          {tab === 'announcement' && (
            <AnnouncementTab board={board} canManage={canManage} expanded={expandedAnn}
              recipients={recipients} onToggle={toggleRecipients} />
          )}
          {tab === 'report' && (
            <LinkCard to="/safety-reports" title="안전관리 이행 보고서"
              desc="현장·기간별 고지·확인·조치 이력을 조회·인쇄(감사·사고 조사 증빙)." />
          )}
          {tab === 'settings' && canManage && (
            <LinkCard to="/safety-settings" title="안전 설정"
              desc="현장별 온도 단계·휴식 간격·강풍 임계·점검 게이트(법정 기준 완화 금지)." />
          )}
        </div>

        {loading && !board && <div className="text-sm text-slate-400">불러오는 중…</div>}
      </div>

      {/* C. 문제 발생 시 즉시 자동 팝업 — 지도에서 보기(panTo)·확인(ack, CLIENT 비노출). */}
      {alertQueue.length > 0 && (
        <AlertPopup
          alert={alertQueue[0]}
          siteName={board?.site_name ?? ''}
          queueCount={alertQueue.length}
          canManage={canManage}
          onFocus={() => { focusOnAlert(alertQueue[0]); dequeueAlert(); }}
          onAck={() => ackAlert(alertQueue[0].id)}
          onClose={dequeueAlert}
        />
      )}
    </AppShell>
  );
}

// ── 하위 컴포넌트 ──────────────────────────────────────────

function StatCard({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
      <div className="text-[11px] font-medium text-slate-500 mb-0.5">{label}</div>
      <div className="flex items-baseline gap-1">{children}</div>
    </div>
  );
}

function TabBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick}
      className={`px-3 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
        active ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-800'
      }`}>
      {children}
    </button>
  );
}

/** 지도 우아한 폴백 — 카카오 키 미설정 또는 현장 좌표 미설정. 마커를 목록으로 표시(워치는 필터 반영). */
function MapFallback({ board, watchWorkers, hasKey, hasGeo, mapFailed }: { board: SiteBoard | null; watchWorkers: WatchWorker[]; hasKey: boolean; hasGeo: boolean; mapFailed?: boolean }) {
  const reason = mapFailed ? '지도 SDK를 불러오지 못해(카카오 콘솔에 도메인 등록 필요)' : !hasKey ? '지도 키가 설정되지 않아' : !hasGeo ? '현장 좌표가 설정되지 않아' : '';
  return (
    <div className="p-4" style={{ minHeight: '52vh' }}>
      <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 mb-3">
        {reason} 지도 대신 목록으로 표시합니다. 상황판의 나머지 기능(요약·탭)은 정상 동작합니다.
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <div className="text-sm font-semibold text-slate-800 mb-1">작업자 위치 ({board?.workers.length ?? 0})</div>
          {(board?.workers.length ?? 0) === 0 ? (
            <div className="text-sm text-slate-400">오늘 출근 기록 없음</div>
          ) : (
            <ul className="space-y-1">
              {board!.workers.map((w) => (
                <li key={w.person_id} className="flex items-center gap-2 text-sm">
                  <span className={`w-2 h-2 rounded-full ${w.checked_in ? 'bg-blue-500' : 'bg-slate-400'}`} />
                  <span className="font-medium text-slate-800">{w.name}</span>
                  <span className="text-xs text-slate-400">{w.checked_in ? '체크인' : '퇴근'}</span>
                  {w.lat != null && w.lng != null && (
                    <span className="text-xs text-slate-400">· {w.lat.toFixed(4)}, {w.lng.toFixed(4)}</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
        <div>
          <div className="text-sm font-semibold text-slate-800 mb-1">경보 ({board?.alerts.length ?? 0})</div>
          {(board?.alerts.length ?? 0) === 0 ? (
            <div className="text-sm text-slate-400">미해결 경보 없음</div>
          ) : (
            <ul className="space-y-1">
              {board!.alerts.map((a) => (
                <li key={a.id} className="flex items-center gap-2 text-sm">
                  <span className={`w-2 h-2 rounded-full ${a.unacked ? 'bg-rose-500 animate-pulse' : a.level === 'danger' ? 'bg-rose-500' : a.level === 'warning' ? 'bg-amber-500' : 'bg-slate-400'}`} />
                  <span className="font-medium text-slate-800">{a.person_name ?? `#${a.id}`}</span>
                  <span className="text-xs text-slate-500">{KIND_LABEL[a.kind] ?? a.kind}</span>
                  {a.unacked && <span className="rounded bg-rose-100 px-1.5 py-0.5 text-[10px] font-semibold text-rose-700">미확인</span>}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
      <div className="mt-4">
        <div className="text-sm font-semibold text-slate-800 mb-1">워치 바이탈 ({watchWorkers.length})</div>
        {watchWorkers.length === 0 ? (
          <div className="text-sm text-slate-400">표시할 워치 작업자가 없습니다</div>
        ) : (
          <ul className="space-y-1">
            {watchWorkers.map((w) => {
              const d = watchDisplay(w);
              return (
                <li key={w.person_id} className="flex flex-wrap items-center gap-2 text-sm">
                  <span className={`w-2 h-2 rounded-full ${d.dot} ${d.pulse ? 'animate-pulse' : ''}`} />
                  <span className="font-medium text-slate-800">{w.name}</span>
                  <span className={`text-xs ${d.tone}`}>{d.label}</span>
                  {w.role && <span className="text-xs text-slate-500">{roleLabel(w.role)}</span>}
                  {w.vehicle_no && <span className="text-xs font-medium text-slate-700">🚜 {w.vehicle_no}{w.vehicle_status ? ` (${VEHICLE_STATUS_LABEL[w.vehicle_status] ?? w.vehicle_status})` : ''}</span>}
                  {w.supplier_name && <span className="text-xs text-slate-400">{w.supplier_name}</span>}
                  {w.hr != null && w.hr > 0 && <span className="text-xs text-slate-500">심박 {w.hr}</span>}
                  {w.body_temp != null && <span className="text-xs text-slate-500">체온 {w.body_temp}℃</span>}
                  {w.battery != null && <span className="text-xs text-slate-500">배터리 {w.battery}%</span>}
                  {w.bp_verdict && <span className="text-xs text-slate-500">{BP_VERDICT_LABEL[w.bp_verdict] ?? w.bp_verdict}</span>}
                  {w.lat != null && w.lng != null && <span className="text-xs text-slate-400">· {w.lat.toFixed(4)}, {w.lng.toFixed(4)}</span>}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

function AlertsTab({ board, onFocus, canManage }: { board: SiteBoard | null; onFocus: (a: AlertMarker) => void; canManage: boolean }) {
  const alerts = board?.alerts ?? [];
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="text-sm text-slate-600">미해결 경보 {alerts.length}건 · 행 클릭 시 지도 이동</div>
        {canManage && <Link to="/safety-alerts" className="text-sm text-brand-600 hover:underline">전체 알림 관리 →</Link>}
      </div>
      {alerts.length === 0 ? (
        <div className="rounded border border-dashed border-slate-300 p-8 text-center text-slate-500">표시할 경보가 없습니다.</div>
      ) : (
        <div className="overflow-x-auto rounded border border-slate-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-600">
              <tr>
                <th className="px-3 py-2">시각</th><th className="px-3 py-2">작업자</th>
                <th className="px-3 py-2">종류</th><th className="px-3 py-2">수준</th><th className="px-3 py-2">확인</th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((a) => {
                const st = ackState(a);
                return (
                  <tr key={a.id} onClick={() => onFocus(a)}
                    className="border-t border-slate-100 cursor-pointer hover:bg-slate-50">
                    <td className="px-3 py-2 text-slate-600 whitespace-nowrap">{new Date(a.created_at).toLocaleString('ko-KR')}</td>
                    <td className="px-3 py-2 font-medium text-slate-800">{a.person_name ?? `#${a.id}`}</td>
                    <td className="px-3 py-2 text-slate-700">{KIND_LABEL[a.kind] ?? a.kind}</td>
                    <td className="px-3 py-2 text-slate-700">{LEVEL_LABEL[a.level] ?? a.level}</td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      {st === 'na' ? <span className="text-slate-400">—</span>
                        : st === 'acknowledged' ? <span className="rounded bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">확인됨</span>
                        : st === 'escalated' ? <span className="rounded bg-rose-100 px-2 py-0.5 text-xs font-medium text-rose-700 ring-1 ring-rose-300">에스컬레이션</span>
                        : <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">미확인</span>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/** C. 자동 팝업 모달 — 새 위험 경보. CLIENT 는 [확인] 비노출(읽기 격리). */
function AlertPopup({ alert, siteName, queueCount, canManage, onFocus, onAck, onClose }: {
  alert: AlertMarker; siteName: string; queueCount: number; canManage: boolean;
  onFocus: () => void; onAck: () => void; onClose: () => void;
}) {
  const kind = KIND_LABEL[alert.kind] ?? alert.kind;
  const sev = SEVERITY_LABEL[alert.severity ?? ''] ?? alert.severity ?? '';
  const isEmergency = alert.severity === 'EMERGENCY';
  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/40 p-4" role="alertdialog" aria-modal="true" aria-label="안전 경보">
      <div className="w-full max-w-md overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className={`flex items-center gap-2 px-4 py-3 text-white ${isEmergency ? 'bg-rose-600' : 'bg-amber-500'}`}>
          <span className="text-lg">{isEmergency ? '🚨' : '⚠️'}</span>
          <div className="font-bold">{isEmergency ? '긴급 경보' : '안전 경보'}</div>
          {queueCount > 1 && <span className="ml-auto rounded bg-white/25 px-2 py-0.5 text-xs">+{queueCount - 1} 대기</span>}
        </div>
        <div className="space-y-2 p-4">
          <div className="text-lg font-semibold text-slate-900">{alert.person_name ?? `작업자 #${alert.id}`}</div>
          <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <div><span className="text-slate-400">종류 </span><span className="font-medium text-slate-800">{kind}</span></div>
            <div><span className="text-slate-400">수준 </span><span className="font-medium text-slate-800">{sev}</span></div>
            <div className="col-span-2"><span className="text-slate-400">현장 </span><span className="text-slate-800">{siteName}</span></div>
            <div className="col-span-2"><span className="text-slate-400">시각 </span><span className="text-slate-800">{new Date(alert.created_at).toLocaleString('ko-KR')}</span></div>
          </div>
          {alert.message && <div className="rounded bg-slate-50 px-3 py-2 text-sm text-slate-700">{alert.message}</div>}
        </div>
        <div className="flex gap-2 px-4 pb-4">
          <button onClick={onFocus} className="flex-1 rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700">지도에서 보기</button>
          {canManage && <button onClick={onAck} className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">확인</button>}
          <button onClick={onClose} className="rounded-md px-3 py-2 text-sm text-slate-400 hover:text-slate-600">닫기</button>
        </div>
      </div>
    </div>
  );
}

// P5-W0 워치 타일 — 상태등(정상/주의/경보·회색=미착용/두절)·마지막 수신·배터리·착용.
function minsAgo(sec: number | null): string {
  if (sec == null) return '수신 없음';
  const m = Math.floor(sec / 60);
  if (m < 1) return '방금 전';
  if (m < 60) return `${m}분 전`;
  return `${Math.floor(m / 60)}시간 ${m % 60}분 전`;
}

function watchDisplay(w: WatchWorker): { dot: string; label: string; tone: string; pulse: boolean } {
  const offline = w.last_seen_at == null || (w.seconds_since_seen != null && w.seconds_since_seen > WATCH_OFFLINE_SEC);
  if (offline) return { dot: 'bg-slate-400', label: '신호 두절', tone: 'text-slate-500', pulse: false };
  if (w.worn === false) return { dot: 'bg-slate-400', label: '미착용', tone: 'text-slate-500', pulse: false };
  if (w.state === 'RED') return { dot: 'bg-rose-500', label: '경보', tone: 'text-rose-600', pulse: true };
  if (w.state === 'YELLOW') return { dot: 'bg-amber-500', label: '주의', tone: 'text-amber-600', pulse: false };
  return { dot: 'bg-emerald-500', label: '정상', tone: 'text-emerald-600', pulse: false };
}

const BP_VERDICT_LABEL: Record<string, string> = { OK: '혈압 정상', CAUTION: '혈압 주의', BLOCK: '혈압 위험' };
const VEHICLE_STATUS_LABEL: Record<string, string> = { ASSIGNED: '가동중', AVAILABLE: '대기', BROKEN: '정비' };

/** 건강 필터 분류 — 워치 상태등(RED/YELLOW) + 오늘 혈압 판정(BLOCK/CAUTION) 통합. */
function healthCat(w: WatchWorker): 'normal' | 'caution' | 'danger' {
  if (w.state === 'RED' || w.bp_verdict === 'BLOCK') return 'danger';
  if (w.state === 'YELLOW' || w.bp_verdict === 'CAUTION') return 'caution';
  return 'normal';
}

/** 투입 역할 한글 라벨 — PersonRole enum 명 매핑(미상은 원문). */
function roleLabel(role: string | null): string {
  if (!role) return '';
  return PERSON_ROLE_LABEL[role as PersonRole] ?? role;
}

type WatchFilters = { q: string; vehicle: string; health: string; supplier: string; bp: string };

/** 클라 필터 — 검색(차량번호·이름) + 차량 가동상태 + 건강 + 공급사 + BP. */
function matchWatch(w: WatchWorker, f: WatchFilters): boolean {
  if (f.q) {
    const q = f.q.trim().toLowerCase();
    if (!w.name.toLowerCase().includes(q) && !(w.vehicle_no?.toLowerCase().includes(q) ?? false)) return false;
  }
  if (f.vehicle && w.vehicle_status !== f.vehicle) return false;
  if (f.health && healthCat(w) !== f.health) return false;
  if (f.supplier && String(w.supplier_company_id ?? '') !== f.supplier) return false;
  if (f.bp && String(w.bp_company_id ?? '') !== f.bp) return false;
  return true;
}

/** 공급사·BP 필터 옵션 — 워치 목록에서 등장하는 회사만(중복 제거). */
function uniqueCompanyOptions(list: WatchWorker[], kind: 'supplier' | 'bp'): Array<{ value: string; label: string }> {
  const seen = new Map<string, string>();
  for (const w of list) {
    const id = kind === 'supplier' ? w.supplier_company_id : w.bp_company_id;
    if (id == null) continue;
    const name = kind === 'supplier' ? w.supplier_name : w.bp_name;
    seen.set(String(id), name ?? `#${id}`);
  }
  return [...seen].map(([value, label]) => ({ value, label }));
}

/** 워치 지도 마커 색 — 상태등 팔레트(정상 emerald·주의 amber·경보 rose·미착용/두절 slate). watchDisplay 와 동일 기준. */
function watchColor(w: WatchWorker): NonNullable<MapMarker['color']> {
  const offline = w.last_seen_at == null || (w.seconds_since_seen != null && w.seconds_since_seen > WATCH_OFFLINE_SEC);
  if (offline || w.worn === false) return 'slate';
  if (w.state === 'RED') return 'rose';
  if (w.state === 'YELLOW') return 'amber';
  return 'emerald';
}

/** 워치 마커 바이탈 팝오버 — 이름·역할·차량번호·소속·상태·심박·체온·배터리·마지막 수신·고위험·혈압. */
function watchTooltip(w: WatchWorker, stateLabel: string): string {
  const hr = w.hr != null && w.hr > 0 ? `${w.hr} bpm` : '—';
  const temp = w.body_temp != null ? `${w.body_temp}℃` : '—';
  const bat = w.battery != null ? `${w.battery}%` : '—';
  const highRisk = w.health_risk_level === 'HIGH'
    ? '<span style="margin-left:4px;padding:1px 5px;border-radius:4px;background:#ffe4e6;color:#e11d48;font-weight:700;font-size:10px">고위험</span>' : '';
  const roleTxt = roleLabel(w.role);
  const affil = [roleTxt, w.supplier_name].filter(Boolean).map((t) => escapeHtml(t as string)).join(' · ');
  const affilLine = affil ? `<div style="margin-top:2px;color:#64748b">${affil}${w.bp_name ? ` <span style="color:#94a3b8">· 원청 ${escapeHtml(w.bp_name)}</span>` : ''}</div>` : '';
  const vehicle = w.vehicle_no
    ? `<div style="margin-top:2px;color:#334155;font-weight:600">🚜 ${escapeHtml(w.vehicle_no)}${w.vehicle_status ? ` <span style="color:#94a3b8;font-weight:400">${escapeHtml(VEHICLE_STATUS_LABEL[w.vehicle_status] ?? w.vehicle_status)}</span>` : ''}</div>` : '';
  const bp = w.bp_verdict
    ? `<div style="margin-top:3px;font-weight:600;color:${w.bp_verdict === 'OK' ? '#059669' : w.bp_verdict === 'BLOCK' ? '#e11d48' : '#d97706'}">${BP_VERDICT_LABEL[w.bp_verdict] ?? w.bp_verdict}</div>` : '';
  return `<div style="padding:8px 10px;font-size:12px;min-width:170px">`
    + `<b>${escapeHtml(w.name)}</b> <span style="color:#64748b">${escapeHtml(stateLabel)}</span>${highRisk}`
    + `${affilLine}${vehicle}`
    + `<div style="margin-top:3px;color:#475569">심박 ${hr} · 체온 ${temp}</div>`
    + `<div style="margin-top:2px;color:#64748b">배터리 ${bat} · ${minsAgo(w.seconds_since_seen)} 수신</div>`
    + `${bp}</div>`;
}

/**
 * P5-W1 경량 심박 스파크라인(SVG, 라이브러리 없음) — 개인 정상범위 밴드(연녹) + 심박 선.
 * low/high = 개인 대역(정상범위). 미학습이면 밴드 없이 선만.
 */
function Sparkline({ series, low, high, width = 132, height = 34 }:
  { series: number[]; low: number | null; high: number | null; width?: number; height?: number }) {
  if (series.length < 2) {
    return <div className="text-[11px] text-slate-300" style={{ height }}>심박 데이터 수집 중</div>;
  }
  const pad = 3;
  const vals = series.concat(low != null ? [low] : [], high != null ? [high] : []);
  const min = Math.min(...vals);
  const max = Math.max(...vals);
  const range = Math.max(1, max - min);
  const x = (i: number) => pad + (i / (series.length - 1)) * (width - 2 * pad);
  const y = (v: number) => pad + (1 - (v - min) / range) * (height - 2 * pad);
  const path = series.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const bandTop = high != null ? y(high) : null;
  const bandBot = low != null ? y(low) : null;
  const lastV = series[series.length - 1];
  const outOfBand = (low != null && lastV < low) || (high != null && lastV > high);
  return (
    <svg width={width} height={height} className="block" role="img" aria-label="심박 스파크라인">
      {bandTop != null && bandBot != null && (
        <rect x={pad} y={bandTop} width={width - 2 * pad} height={Math.max(1, bandBot - bandTop)}
          fill="#10b981" fillOpacity={0.13} />
      )}
      <path d={path} fill="none" stroke="#0ea5e9" strokeWidth={1.5} strokeLinejoin="round" />
      <circle cx={x(series.length - 1)} cy={y(lastV)} r={2.4} fill={outOfBand ? '#e11d48' : '#0ea5e9'} />
    </svg>
  );
}

function WatchTab({ workers, canManage }: { workers: WatchWorker[]; canManage: boolean }) {
  const [openId, setOpenId] = useState<number | null>(null);
  // 관리자 그룹 요약 — 공급사별 인원·경보(건강 경보=워치 RED 또는 혈압 BLOCK) 수.
  const groups = useMemo(() => {
    const m = new Map<string, { name: string; count: number; alerts: number }>();
    for (const w of workers) {
      const key = String(w.supplier_company_id ?? '0');
      const g = m.get(key) ?? { name: w.supplier_name ?? '소속 미지정', count: 0, alerts: 0 };
      g.count += 1;
      if (healthCat(w) === 'danger') g.alerts += 1;
      m.set(key, g);
    }
    return [...m.values()].sort((a, b) => b.alerts - a.alerts || b.count - a.count);
  }, [workers]);
  return (
    <div className="space-y-3">
      {canManage && groups.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {groups.map((g) => (
            <div key={g.name} className="rounded-lg border border-slate-200 bg-white px-3 py-1.5">
              <span className="text-sm font-medium text-slate-800">{g.name}</span>
              <span className="ml-2 text-xs text-slate-500">인원 {g.count}</span>
              {g.alerts > 0 && <span className="ml-1.5 rounded bg-rose-100 px-1.5 py-0.5 text-[11px] font-semibold text-rose-700">경보 {g.alerts}</span>}
            </div>
          ))}
        </div>
      )}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="text-sm text-slate-600">워치 보고 작업자 {workers.length}명 · 상태등·최근 심박·배터리·착용</div>
        <div className="flex items-center gap-3 text-xs text-slate-500">
          <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-emerald-500" />정상</span>
          <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-amber-500" />주의</span>
          <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-rose-500" />경보</span>
          <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-slate-400" />미착용·두절</span>
        </div>
      </div>
      {workers.length === 0 ? (
        <div className="rounded border border-dashed border-slate-300 p-8 text-center text-slate-500">
          워치 상태를 보고한 작업자가 없습니다. 워치 앱이 연결되면 여기에 상태가 표시됩니다.
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          {workers.map((w) => {
            const d = watchDisplay(w);
            const lowBat = w.battery != null && w.battery <= 20;
            return (
              <div key={w.person_id} className="rounded-lg border border-slate-200 bg-white">
                <button type="button" onClick={() => setOpenId(openId === w.person_id ? null : w.person_id)}
                  className="w-full px-3 py-2.5 flex items-center gap-3 text-left hover:bg-slate-50 rounded-lg">
                  <span className={`w-3 h-3 rounded-full shrink-0 ${d.dot} ${d.pulse ? 'animate-pulse' : ''}`} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-slate-800 truncate">{w.name}</span>
                      <span className={`text-xs font-medium ${d.tone}`}>{d.label}</span>
                      {w.health_risk_level === 'HIGH' && (
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-rose-100 text-rose-700 font-semibold shrink-0">🔴 고위험</span>
                      )}
                      {!w.baseline_learned && (
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-500 shrink-0">학습 전</span>
                      )}
                    </div>
                    <div className="text-xs text-slate-400">
                      {minsAgo(w.seconds_since_seen)} 수신{w.hr != null && w.hr > 0 ? ` · 심박 ${w.hr}` : ''}
                    </div>
                    {(w.role || w.vehicle_no || w.supplier_name) && (
                      <div className="text-[11px] text-slate-500 mt-0.5 flex flex-wrap items-center gap-x-1.5">
                        {w.role && <span>{roleLabel(w.role)}</span>}
                        {w.vehicle_no && <span className="font-medium text-slate-700">🚜 {w.vehicle_no}{w.vehicle_status ? ` (${VEHICLE_STATUS_LABEL[w.vehicle_status] ?? w.vehicle_status})` : ''}</span>}
                        {w.supplier_name && <span className="text-slate-400 truncate">{w.supplier_name}</span>}
                      </div>
                    )}
                  </div>
                  <Sparkline series={w.hr_series ?? []} low={w.rest_hr_low} high={w.work_hr_high} />
                  <div className="text-right shrink-0">
                    {w.battery != null ? (
                      <div className={`text-sm font-semibold tabular-nums ${lowBat ? 'text-rose-600' : 'text-slate-700'}`}>
                        {w.battery}%{lowBat ? ' ⚠' : ''}
                      </div>
                    ) : <div className="text-xs text-slate-300">배터리 —</div>}
                    <div className="text-[11px] text-slate-400">{w.worn === false ? '미착용' : w.worn ? '착용' : '—'}</div>
                  </div>
                </button>
                {openId === w.person_id && <WatchDetail personId={w.person_id} canManage={canManage} />}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

/** 타일 클릭 상세 — 최근 심박 미니 차트(개인 대역) + 학습 상태 + 경보 이력(+실제/오탐 피드백). */
function WatchDetail({ personId, canManage }: { personId: number; canManage: boolean }) {
  const [data, setData] = useState<PersonVitals | null>(null);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    api.get<PersonVitals>(`/api/safety-alerts/person/${personId}/vitals`)
      .then((r) => setData(r.data))
      .catch(() => toast.error('상세를 불러올 수 없습니다'))
      .finally(() => setLoading(false));
  };
  useEffect(load, [personId]);

  async function resolve(id: number, realEvent: boolean) {
    try {
      await api.post(`/api/safety-alerts/${id}/resolve?realEvent=${realEvent}`);
      toast.success(realEvent ? '실제 상황으로 처리 — 개인 임계 강화' : '오탐 처리 완료');
      load();
    } catch {
      toast.error('처리에 실패했습니다');
    }
  }

  if (loading) return <div className="px-3 pb-3 text-xs text-slate-400">불러오는 중…</div>;
  if (!data) return null;
  const series = [...data.readings].reverse().map((r) => r.hr).filter((h): h is number => h != null && h > 0);
  const band = data.band;
  return (
    <div className="px-3 pb-3 border-t border-slate-100 pt-2 space-y-2">
      <div className="flex items-center justify-between">
        <div className="text-xs text-slate-500">최근 심박 {series.length}포인트</div>
        {band?.learned ? (
          <div className="text-[11px] text-slate-500 tabular-nums">
            정상범위 {band.rest_hr_low ?? '—'}~{band.work_hr_high ?? '—'} · 보정 {Number(band.adjust_pct ?? 0)}% · 오탐 {band.fp_count ?? 0}/실제 {band.tp_count ?? 0}
          </div>
        ) : <div className="text-[11px] text-slate-400">베이스라인 학습 전</div>}
      </div>
      <Sparkline series={series} low={band?.rest_hr_low ?? null} high={band?.work_hr_high ?? null} width={280} height={56} />
      <div className="space-y-1">
        {data.alerts.length === 0 ? (
          <div className="text-xs text-slate-400">경보 이력 없음</div>
        ) : data.alerts.map((a) => {
          const learnable = a.kind === 'vital_anomaly' || a.kind === 'heat_risk';
          return (
            <div key={a.id} className="flex items-center gap-2 text-xs">
              <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${a.resolved ? 'bg-slate-300' : 'bg-rose-500'}`} />
              <span className="text-slate-600 truncate flex-1">{a.message ?? a.kind}</span>
              <span className="text-[11px] text-slate-400 shrink-0">{new Date(a.created_at).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>
              {canManage && learnable && !a.resolved && (
                <span className="flex gap-1 shrink-0">
                  <button type="button" onClick={() => resolve(a.id, true)}
                    className="px-1.5 py-0.5 rounded bg-rose-50 text-rose-600 hover:bg-rose-100">실제상황</button>
                  <button type="button" onClick={() => resolve(a.id, false)}
                    className="px-1.5 py-0.5 rounded bg-slate-100 text-slate-500 hover:bg-slate-200">오탐</button>
                </span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function InspectionTab({ board, canManage, siteId }: { board: SiteBoard | null; canManage: boolean; siteId: number | null }) {
  const s = board?.summary;
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <InspectionCard title="법정점검 (안전점검원 NFC)" done={s?.legal_done ?? 0} target={s?.legal_target ?? 0}
          desc="오늘 배치 장비 법정점검 · 조종원 일일점검과 별도 트랙" tone="indigo" />
        <InspectionCard title="조종원 일일점검" done={s?.operator_done ?? 0} target={s?.operator_target ?? 0}
          desc="오늘 배치 장비 조종원 점검" tone="emerald" />
      </div>
      {canManage && siteId != null && <LegalInspectionStatusCard siteId={siteId} />}
    </div>
  );
}

function InspectionCard({ title, done, target, desc, tone }: { title: string; done: number; target: number; desc: string; tone: 'indigo' | 'emerald' }) {
  const pct = target > 0 ? Math.round((done / target) * 100) : 0;
  const bar = tone === 'indigo' ? 'bg-indigo-500' : 'bg-emerald-500';
  const border = tone === 'indigo' ? 'border-indigo-500' : 'border-emerald-500';
  return (
    <div className={`rounded-lg border border-slate-200 border-l-4 ${border} bg-white p-3`}>
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-bold text-slate-900">{title}</h3>
          <p className="text-xs text-slate-500">{desc}</p>
        </div>
        <span className="text-2xl font-bold tabular-nums text-slate-800">{done}<span className="text-sm font-medium text-slate-400"> / {target}대</span></span>
      </div>
      {target > 0 && (
        <div className="mt-2 h-2 overflow-hidden rounded bg-slate-100">
          <div className={`h-full rounded ${pct >= 100 ? 'bg-emerald-500' : bar}`} style={{ width: `${pct}%` }} />
        </div>
      )}
      {target === 0 && <p className="mt-2 text-xs text-slate-400">배치된 점검 대상 장비가 없습니다.</p>}
    </div>
  );
}

function AnnouncementTab({ board, canManage, expanded, recipients, onToggle }: {
  board: SiteBoard | null; canManage: boolean; expanded: number | null;
  recipients: Record<number, RecipientStatus[]>; onToggle: (id: number) => void;
}) {
  const anns = board?.announcements ?? [];
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="text-sm text-slate-600">이 현장 공지 {anns.length}건 · 확인율·미확인자</div>
        {canManage && <Link to="/admin/announcements" className="text-sm text-brand-600 hover:underline">공지 발송 →</Link>}
      </div>
      {anns.length === 0 ? (
        <div className="rounded border border-dashed border-slate-300 p-8 text-center text-slate-500">
          이 현장으로 발송한 공지가 없습니다.{canManage && ' 공지 발송 시 현장을 지정하면 여기에 확인율이 표시됩니다.'}
        </div>
      ) : (
        <div className="space-y-2">
          {anns.map((a) => {
            const pct = a.recipient_count > 0 ? Math.round((a.read_count / a.recipient_count) * 100) : 0;
            const recips = recipients[a.id];
            const unread = recips?.filter((r) => r.read_at == null) ?? [];
            return (
              <div key={a.id} className="rounded-lg border border-slate-200 bg-white p-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <div className="font-medium text-slate-800 truncate">{a.title}</div>
                    <div className="text-xs text-slate-400">{new Date(a.created_at).toLocaleString('ko-KR')}</div>
                  </div>
                  <div className="text-right shrink-0">
                    <div className="text-lg font-bold tabular-nums text-slate-800">{a.read_count}<span className="text-sm font-medium text-slate-400"> / {a.recipient_count}</span></div>
                    <button onClick={() => onToggle(a.id)} className="text-xs text-brand-600 hover:underline">
                      {expanded === a.id ? '접기' : '미확인자 보기'}
                    </button>
                  </div>
                </div>
                <div className="mt-2 h-2 overflow-hidden rounded bg-slate-100">
                  <div className={`h-full rounded ${pct >= 100 ? 'bg-emerald-500' : 'bg-brand-500'}`} style={{ width: `${pct}%` }} />
                </div>
                {expanded === a.id && recips && (
                  <div className="mt-3 border-t border-slate-100 pt-2">
                    <div className="text-[11px] font-semibold text-slate-500 mb-1">미확인 ({unread.length})</div>
                    {unread.length === 0 ? (
                      <div className="text-xs text-emerald-600">전원 확인 완료</div>
                    ) : (
                      <div className="flex flex-wrap gap-1.5">
                        {unread.map((r) => (
                          <span key={r.person_id} className="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700">{r.name}</span>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function LinkCard({ to, title, desc }: { to: string; title: string; desc: string }) {
  return (
    <Link to={to} className="block rounded-lg border border-slate-200 bg-white p-4 hover:border-brand-300 hover:bg-slate-50 transition-colors">
      <div className="flex items-center justify-between">
        <div>
          <div className="text-sm font-semibold text-slate-900">{title}</div>
          <div className="text-xs text-slate-500 mt-0.5">{desc}</div>
        </div>
        <span className="text-brand-600">→</span>
      </div>
    </Link>
  );
}
