import { useEffect, useState, type ReactNode } from 'react';
import { api } from '../../lib/api';
import { LEVEL_BADGE, type AlertLevel } from '../../types/safetyAlert';

/** GET /api/safety-alerts/site-weather 응답 행. */
export type SiteWeatherRow = {
  site_id: number;
  site_name: string;
  worker_count: number;
  available?: boolean;
  temp_c?: number;
  humidity?: number;
  feels_like?: number;
  stage?: string;
  stage_label?: string;
  level?: AlertLevel | string;
};

export function SiteWeatherCard({ w }: { w: SiteWeatherRow }) {
  const badge = w.level ? (LEVEL_BADGE[w.level] ?? 'bg-slate-100 text-slate-700') : 'bg-slate-100 text-slate-700';
  const stageLabel = w.stage_label ?? (w.available ? '정상' : '기온 정보 없음');
  const ring = w.level === 'danger'
    ? 'border-rose-300' : w.level === 'warning'
    ? 'border-orange-300' : w.level === 'caution'
    ? 'border-amber-300' : 'border-slate-200';
  return (
    <div className={`rounded-lg border bg-white p-3 ${ring}`}>
      <div className="flex items-center justify-between gap-2">
        <div className="font-semibold text-slate-900 truncate">{w.site_name}</div>
        <span className={`shrink-0 inline-block rounded px-2 py-0.5 text-xs font-medium ${badge}`}>{stageLabel}</span>
      </div>
      <div className="mt-2 flex items-end gap-2">
        {w.available && w.feels_like != null ? (
          <>
            <span className="text-2xl font-bold text-slate-900">체감 {w.feels_like}℃</span>
            <span className="text-xs text-slate-500 mb-1">
              기온 {w.temp_c}℃{w.humidity != null ? ` · 습도 ${w.humidity}%` : ''}
            </span>
          </>
        ) : (
          <span className="text-sm text-slate-400">현장 좌표 미설정 또는 조회 실패</span>
        )}
      </div>
      <div className="mt-1 text-xs text-slate-500">출근 {w.worker_count}명</div>
    </div>
  );
}

export function TabButton({ children, active, count, onClick }: {
  children: ReactNode; active: boolean; count: number; onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`px-3 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
        active
          ? 'border-rose-500 text-rose-600'
          : 'border-transparent text-slate-600 hover:text-slate-900'
      }`}
    >
      {children}
      {count > 0 && (
        <span className={`ml-1.5 inline-block rounded-full px-2 py-0.5 text-xs ${
          active ? 'bg-rose-100 text-rose-700' : 'bg-slate-100 text-slate-600'
        }`}>{count}</span>
      )}
    </button>
  );
}

export function PersonAvatar({ personId, hasPhoto }: { personId: number; hasPhoto: boolean }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    if (!hasPhoto) return;
    let url: string | null = null;
    let cancelled = false;
    api.get(`/api/persons/${personId}/photo`, { responseType: 'blob' })
      .then((r) => {
        if (cancelled) return;
        url = URL.createObjectURL(r.data as Blob);
        setSrc(url);
      })
      .catch(() => {});
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [personId, hasPhoto]);
  return (
    <div className="w-7 h-7 rounded-full bg-slate-100 overflow-hidden flex items-center justify-center text-slate-400 shrink-0">
      {src
        ? <img src={src} alt="" className="w-full h-full object-cover" />
        : <span className="text-xs">{(personId ?? '?').toString().slice(0, 1)}</span>}
    </div>
  );
}
