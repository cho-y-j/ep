// P4a 안전 상황판 응답 타입 (백엔드 SafetyBoardDtos, 전역 SNAKE_CASE).

export type BoardSite = {
  id: number;
  name: string;
  code: string | null;
  has_geo: boolean;
  unresolved_alerts: number;
};

export type WorkerMarker = {
  person_id: number;
  name: string;
  lat: number | null;
  lng: number | null;
  checked_in: boolean;
  check_in_at: string;
};

export type AlertMarker = {
  id: number;
  kind: string;
  level: string;
  severity: string | null;
  message: string | null;
  person_name: string | null;
  lat: number | null;
  lng: number | null;
  acknowledged_at: string | null;
  escalated_at: string | null;
  created_at: string;
  unacked: boolean;
};

export type BoardWeather = {
  available: boolean;
  feels_like: number | null;
  stage: string | null;
  stage_label: string | null;
  level: string | null;
  wind_mps: number | null;
  wind_stop_active: boolean;
};

export type BoardSummary = {
  weather: BoardWeather;
  deployed: number;
  attended: number;
  checked_in: number;
  unacked_alerts: number;
  legal_done: number;
  legal_target: number;
  operator_done: number;
  operator_target: number;
  announcement_read: number;
  announcement_total: number;
};

export type AnnouncementSummary = {
  id: number;
  title: string;
  created_at: string;
  recipient_count: number;
  read_count: number;
};

// P5-W0 워커 워치 타일. state=GREEN|YELLOW|RED, worn/seconds_since_seen 로 회색(미착용/두절) 파생.
export type WatchWorker = {
  person_id: number;
  name: string;
  state: string | null;
  last_seen_at: string | null;
  seconds_since_seen: number | null;
  battery: number | null;
  worn: boolean | null;
  hr: number | null;
};

export type SiteBoard = {
  site_id: number;
  site_name: string;
  code: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  polygon_geojson: string | null;
  map_zoom: number | null;
  geofence_radius_m: number | null;
  workers: WorkerMarker[];
  alerts: AlertMarker[];
  summary: BoardSummary;
  announcements: AnnouncementSummary[];
  watch_workers: WatchWorker[];
};

export type RecipientStatus = {
  person_id: number;
  name: string;
  read_at: string | null;
};
