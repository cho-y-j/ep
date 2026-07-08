import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import KakaoMap, { type MapMarker } from '../../components/KakaoMap';

type BoardItem = {
  deployment_id: number;
  resource_type: 'EQUIPMENT' | 'PERSON';
  resource_id: number;
  resource_label: string;
  has_photo?: boolean | null;
  work_plan_id?: number | null;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  target_site_id?: number | null;
  target_site_name?: string | null;
  site_latitude?: number | null;
  site_longitude?: number | null;
  start_date?: string | null;
  activated_at?: string | null;
  total_days?: number | null;
  total_hours?: number | null;
  last_work_date?: string | null;
  today_attended?: boolean | null;
  today_check_in_lat?: number | null;
  today_check_in_lng?: number | null;
  today_check_out_lat?: number | null;
  today_check_out_lng?: number | null;
  recent_confirmations?: Array<{
    id: number;
    work_date: string;
    total_hours?: number | null;
    morning_time?: string | null;
    afternoon_time?: string | null;
    signed_by_supplier?: boolean;
    signed_by_bp?: boolean;
    attendance_photo_doc_id?: number | null;
  }>;
};

const todayStr = () => new Date().toISOString().slice(0, 10);

function Icon({ name, className = '', size = 20 }: { name: string; className?: string; size?: number }) {
  return (
    <span className={`material-symbols-outlined ${className}`}
          style={{ fontSize: size, lineHeight: 1, verticalAlign: 'middle' }}>
      {name}
    </span>
  );
}

function ResourcePhoto({ type, id, hasPhoto, size }:
  { type: 'EQUIPMENT' | 'PERSON'; id: number; hasPhoto: boolean; size: number }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    if (!hasPhoto) return;
    let url: string | null = null;
    let cancelled = false;
    const endpoint = type === 'EQUIPMENT' ? `/api/equipment/${id}/photo` : `/api/persons/${id}/photo`;
    api.get(endpoint, { responseType: 'blob' })
      .then((r) => {
        if (cancelled) return;
        url = URL.createObjectURL(r.data as Blob);
        setSrc(url);
      })
      .catch(() => {});
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [type, id, hasPhoto]);

  const fallbackIcon = type === 'EQUIPMENT' ? 'local_shipping' : 'person';
  const radius = type === 'PERSON' ? 'rounded-full' : 'rounded';
  return (
    <div className={`${radius} bg-slate-100 overflow-hidden flex items-center justify-center text-slate-400`}
         style={{ width: size, height: size }}>
      {src ? (
        <img src={src} alt="" className="w-full h-full object-cover" />
      ) : (
        <Icon name={fallbackIcon} size={Math.floor(size * 0.6)} />
      )}
    </div>
  );
}

export default function SiteBoardPage() {
  const { siteId } = useParams<{ siteId: string }>();
  const navigate = useNavigate();
  const [items, setItems] = useState<BoardItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<'equipment' | 'person'>('equipment');

  useEffect(() => {
    let cancelled = false;
    api.get<BoardItem[]>('/api/field-deployments/bp/board')
      .then((r) => {
        if (cancelled) return;
        const filtered = r.data.filter((it) => {
          if (siteId === 'none') return it.target_site_id == null;
          return String(it.target_site_id) === siteId;
        });
        setItems(filtered);
      })
      .catch(() => { if (!cancelled) setItems([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [siteId]);

  const equipments = items.filter((i) => i.resource_type === 'EQUIPMENT');
  const persons = items.filter((i) => i.resource_type === 'PERSON');

  const stats = useMemo(() => {
    const eqRunning = equipments.filter((e) => e.today_attended === true).length;
    const eqWaiting = equipments.length - eqRunning;
    const personPresent = persons.filter((p) => p.today_attended === true).length;
    const personAbsent = persons.length - personPresent;
    const todayHours = items.reduce((sum, it) => {
      const wc = (it.recent_confirmations ?? []).find((c) => c.work_date === todayStr());
      return sum + Number(wc?.total_hours ?? 0);
    }, 0);
    const utilEq = equipments.length === 0 ? 0 : Math.round((eqRunning / equipments.length) * 100);
    const utilPerson = persons.length === 0 ? 0 : Math.round((personPresent / persons.length) * 100);
    return {
      eqTotal: equipments.length, eqRunning, eqWaiting,
      personTotal: persons.length, personPresent, personAbsent,
      todayHours, utilEq, utilPerson,
    };
  }, [items, equipments, persons]);

  const siteName = items[0]?.target_site_name ?? '현장 미지정';
  const suppliers = Array.from(new Set(items.map((i) => i.supplier_company_name).filter(Boolean))) as string[];
  const earliestStart = items.map((i) => i.start_date).filter(Boolean).sort()[0];

  const statusBanner = useMemo(() => {
    if (items.length === 0) return null;
    const parts: string[] = [];
    if (stats.eqWaiting > 0) parts.push(`차량 ${stats.eqWaiting}대 대기 중`);
    if (stats.eqRunning > 0) parts.push(`차량 ${stats.eqRunning}대 운행 중`);
    if (stats.personAbsent > 0) parts.push(`인원 ${stats.personAbsent}명 미출근`);
    if (stats.personPresent > 0) parts.push(`인원 ${stats.personPresent}명 출근`);
    const tail = stats.personPresent === 0 && stats.eqRunning === 0
      ? '으로 작업 시작 전 상태입니다.'
      : '으로 현장 진행 중입니다.';
    return parts.join(', ') + tail;
  }, [items.length, stats]);

  const isWorking = stats.eqRunning > 0 || stats.personPresent > 0;
  const firstWpId = items[0]?.work_plan_id;

  const siteCenter = (items[0]?.site_latitude != null && items[0]?.site_longitude != null)
    ? { lat: items[0].site_latitude, lng: items[0].site_longitude }
    : null;
  const mapMarkers = useMemo<MapMarker[]>(() => {
    const out: MapMarker[] = [];
    if (siteCenter) out.push({ id: 'site', position: siteCenter, label: siteName, color: 'amber' });
    for (const it of items) {
      if (it.today_check_in_lat != null && it.today_check_in_lng != null) {
        out.push({
          id: `${it.resource_type}-${it.resource_id}-in`,
          position: { lat: it.today_check_in_lat, lng: it.today_check_in_lng },
          label: `${it.resource_label} 출근`,
          color: it.resource_type === 'PERSON' ? 'blue' : 'emerald',
        });
      }
      if (it.today_check_out_lat != null && it.today_check_out_lng != null) {
        out.push({
          id: `${it.resource_type}-${it.resource_id}-out`,
          position: { lat: it.today_check_out_lat, lng: it.today_check_out_lng },
          label: `${it.resource_label} 퇴근`,
          color: 'slate',
        });
      }
    }
    return out;
  }, [items, siteCenter, siteName]);

  return (
    <AppShell breadcrumb={[{ label: '투입 현황', to: '/work-plans/active' }, { label: siteName }]}>
      <div className="mx-auto max-w-7xl space-y-4">
        {/* 헤더 */}
        <div className="flex items-start justify-between gap-3">
          <div>
            <Link to="/work-plans/active" className="inline-flex items-center gap-1 text-xs text-slate-500 hover:text-slate-900">
              <Icon name="arrow_back" size={14} /> 현장 목록
            </Link>
            <h1 className="text-3xl font-bold text-slate-950 mt-1">{siteName}</h1>
            <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-xs text-slate-500">
              {earliestStart && (
                <span className="inline-flex items-center gap-1.5">
                  <Icon name="calendar_today" size={14} /> 공사 기간
                  <span className="text-slate-700 font-semibold tabular-nums">{earliestStart} ~ 진행 중</span>
                </span>
              )}
              {suppliers.length > 0 && (
                <span className="inline-flex items-center gap-1.5">
                  <Icon name="inventory_2" size={14} /> 공급사
                  <span className="text-slate-700">{suppliers.join(' · ')}</span>
                </span>
              )}
            </div>
          </div>
          {firstWpId && (
            <button onClick={() => navigate(`/work-plans/${firstWpId}`)}
                    className="inline-flex items-center gap-1 px-3 py-1.5 text-sm border border-slate-300 rounded hover:bg-slate-50">
              <Icon name="edit" size={16} /> 작업계획서 열기
            </button>
          )}
        </div>

        {/* 현재 현장 상태 배너 */}
        {statusBanner && (
          <div className="rounded-xl border border-blue-200 bg-blue-50/40 p-4 flex items-start justify-between gap-3">
            <div className="flex items-start gap-3 min-w-0">
              <div className="shrink-0 w-9 h-9 rounded-full bg-blue-500 text-white flex items-center justify-center">
                <Icon name="info" size={20} />
              </div>
              <div>
                <div className="text-xs font-semibold text-slate-600 mb-1">현재 현장 상태</div>
                <div className="text-base font-bold text-slate-900">{statusBanner}</div>
              </div>
            </div>
            <div className="shrink-0 text-right">
              <div className="text-xs text-slate-500 mb-1">전체 상태</div>
              <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold border ${
                isWorking ? 'bg-emerald-50 border-emerald-200 text-emerald-700'
                          : 'bg-slate-50 border-slate-200 text-slate-700'
              }`}>
                <span className={`w-1.5 h-1.5 rounded-full ${isWorking ? 'bg-emerald-500' : 'bg-slate-400'}`} />
                {isWorking ? '현장 진행 중' : '현재 현장 여유'}
              </span>
            </div>
          </div>
        )}

        {/* 현장 + 출퇴근 위치 지도 */}
        {siteCenter && (
          <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
            <div className="flex items-center justify-between px-4 py-2 border-b border-slate-100">
              <div className="text-sm font-semibold text-slate-800">현장 + 오늘 출퇴근 위치</div>
              <div className="flex items-center gap-3 text-xs text-slate-500">
                <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-amber-500" />현장</span>
                <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-blue-500" />인원 출근</span>
                <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-emerald-500" />장비 운전자 출근</span>
                <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-slate-400" />퇴근</span>
              </div>
            </div>
            <KakaoMap center={siteCenter} zoom={3} markers={mapMarkers} height="320px" />
          </div>
        )}

        {/* KPI 카드 4개 */}
        <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
          <KpiCard label="차량 현황" iconName="local_shipping" iconColor="bg-blue-50 text-blue-700"
                   main={<><strong className="tabular-nums">{stats.eqTotal}</strong><span className="text-base text-slate-500"> 대</span></>}>
            <Chip color="emerald">운행중 {stats.eqRunning}대</Chip>
            <Chip color="blue">대기 {stats.eqWaiting}대</Chip>
            <Chip color="amber">정비중 0대</Chip>
          </KpiCard>
          <KpiCard label="인원 현황" iconName="groups" iconColor="bg-purple-50 text-purple-700"
                   main={<><strong className="tabular-nums">{stats.personTotal}</strong><span className="text-base text-slate-500"> 명</span></>}>
            <Chip color="emerald">출근 {stats.personPresent}명</Chip>
            <Chip color="rose">미출근 {stats.personAbsent}명</Chip>
            <Chip color="slate">퇴근 0명</Chip>
          </KpiCard>
          <KpiCard label="오늘 진행 현황" iconName="event_available" iconColor="bg-emerald-50 text-emerald-700">
            <div className="grid grid-cols-2 gap-3 mt-1">
              <Stat label="차량 가동" value={`${stats.todayHours.toFixed(0)}h`} />
              <Stat label="인원 출근" value={`${stats.personPresent}명`} />
            </div>
          </KpiCard>
          <KpiCard label="현장 가동률" iconName="speed" iconColor="bg-amber-50 text-amber-700">
            <div className="grid grid-cols-2 gap-3 mt-1">
              <Stat label="차량 가동률" value={`${stats.utilEq}%`} />
              <Stat label="인원 출근률" value={`${stats.utilPerson}%`} />
            </div>
          </KpiCard>
        </section>

        {/* 탭 */}
        <div className="border-b border-slate-200 flex items-center gap-1">
          <TabBtn active={tab === 'equipment'} onClick={() => setTab('equipment')}>
            <Icon name="local_shipping" size={18} className="mr-1" />
            차량 현황 ({equipments.length})
          </TabBtn>
          <TabBtn active={tab === 'person'} onClick={() => setTab('person')}>
            <Icon name="groups" size={18} className="mr-1" />
            인원 현황 ({persons.length})
          </TabBtn>
          <div className="ml-auto text-xs text-slate-500 inline-flex items-center gap-1">
            최종 업데이트: <span className="tabular-nums">{new Date().toLocaleString('ko-KR', { hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</span>
            <button onClick={() => window.location.reload()} className="hover:text-slate-900">
              <Icon name="refresh" size={16} />
            </button>
          </div>
        </div>

        {/* 자원 리스트 */}
        {loading ? <p className="text-sm text-slate-400">불러오는 중...</p>
         : tab === 'equipment' ? (
          equipments.length === 0 ? (
            <div className="card p-10 text-center text-sm text-slate-400">투입된 차량이 없습니다.</div>
          ) : (
            <div className="space-y-2">
              {equipments.map((it) => <EquipmentRow key={it.resource_id} item={it} />)}
              <p className="text-xs text-slate-500 px-1 py-2 inline-flex items-center gap-1">
                <Icon name="info" size={14} /> 차량 가동시간은 작업계획서에서 인원-장비 매핑된 운전자의 작업확인서 시간을 합산합니다.
              </p>
            </div>
          )
        ) : (
          persons.length === 0 ? (
            <div className="card p-10 text-center text-sm text-slate-400">투입된 인원이 없습니다.</div>
          ) : (
            <div className="space-y-2">
              {persons.map((it) => <PersonRow key={it.resource_id} item={it} />)}
            </div>
          )
        )}

        <p className="text-xs text-slate-400 px-1 inline-flex items-center gap-1">
          <Icon name="info" size={12} /> 상태 및 시간 정보는 공급사 보고 기준으로 제공됩니다.
        </p>
      </div>
    </AppShell>
  );
}

function KpiCard({ label, iconName, iconColor, main, children }:
  { label: string; iconName: string; iconColor: string; main?: React.ReactNode; children?: React.ReactNode }) {
  return (
    <div className="card p-4">
      <div className="flex items-start gap-3">
        <div className={`shrink-0 w-9 h-9 rounded-full flex items-center justify-center ${iconColor}`}>
          <Icon name={iconName} size={20} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="text-xs font-semibold text-slate-500 mb-0.5">{label}</div>
          {main && <div className="text-2xl font-bold text-slate-900">{main}</div>}
        </div>
      </div>
      <div className="mt-2 flex flex-wrap gap-1.5">{children}</div>
    </div>
  );
}

function Chip({ color, children }: { color: 'emerald' | 'blue' | 'amber' | 'rose' | 'slate'; children: React.ReactNode }) {
  const cls: Record<string, string> = {
    emerald: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
    blue: 'bg-blue-50 text-blue-700 ring-blue-200',
    amber: 'bg-amber-50 text-amber-700 ring-amber-200',
    rose: 'bg-rose-50 text-rose-700 ring-rose-200',
    slate: 'bg-slate-50 text-slate-700 ring-slate-200',
  };
  return <span className={`px-2 py-0.5 rounded-full text-[11px] font-semibold ring-1 ${cls[color]}`}>{children}</span>;
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-[11px] text-slate-500">{label}</div>
      <div className="text-xl font-bold text-slate-900 tabular-nums">{value}</div>
    </div>
  );
}

function TabBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick}
            className={`px-4 py-2 text-sm font-semibold border-b-2 inline-flex items-center ${active ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-800'}`}>
      {children}
    </button>
  );
}

function EquipmentRow({ item }: { item: BoardItem }) {
  const navigate = useNavigate();
  const todayWc = (item.recent_confirmations ?? []).find((c) => c.work_date === todayStr());
  const [wcOpen, setWcOpen] = useState(false);
  return (
    <div className="card p-4 flex items-stretch gap-4 hover:shadow-sm transition">
      <div className="shrink-0 w-[100px]">
        <div className="inline-block px-1.5 py-0.5 rounded text-[10px] font-bold bg-slate-900 text-white">장비</div>
        <div className="mt-1">
          <ResourcePhoto type="EQUIPMENT" id={item.resource_id} hasPhoto={!!item.has_photo} size={72} />
        </div>
      </div>
      <div className="grid grid-cols-2 md:grid-cols-6 gap-3 flex-1 text-xs">
        <div>
          <div className="text-base font-bold text-slate-900">
            <Link to={`/equipment/${item.resource_id}`} className="hover:text-brand-700">{item.resource_label}</Link>
          </div>
          <div className="text-slate-400 mt-1">공급사</div>
          <div className="text-slate-700">{item.supplier_company_name ?? '#' + item.supplier_company_id}</div>
        </div>
        <div>
          <div className="text-slate-400">시작일</div>
          <div className="font-semibold tabular-nums mt-1">{item.start_date ?? '-'}</div>
        </div>
        <div>
          <div className="text-slate-400">오늘 상태</div>
          <div className="mt-1">
            {item.today_attended === true
              ? <Chip color="emerald">운행중</Chip>
              : <Chip color="blue">대기</Chip>}
          </div>
        </div>
        <div>
          <div className="text-slate-400">누적 가동시간</div>
          <div className="font-semibold tabular-nums mt-1">
            {item.total_days == null ? '운전자 매핑 X' : `${Number(item.total_hours ?? 0).toFixed(1)}h`}
          </div>
          <div className="text-slate-400 mt-1">오늘 가동시간</div>
          <div className="font-semibold tabular-nums">{Number(todayWc?.total_hours ?? 0).toFixed(1)}h</div>
        </div>
        <div>
          <div className="text-slate-400">최근 작업</div>
          <div className="font-semibold tabular-nums mt-1">{item.last_work_date ?? '-'}</div>
          {item.work_plan_id && (
            <div className="flex flex-col gap-1 mt-2">
              <button onClick={() => setWcOpen(true)}
                      className="inline-flex items-center gap-1 text-[11px] text-emerald-700 hover:underline">
                <Icon name="receipt_long" size={13} /> 일일 작업확인서
              </button>
              <button onClick={() => navigate(`/work-plans/${item.work_plan_id}`)}
                      className="inline-flex items-center gap-1 text-[11px] text-blue-600 hover:underline">
                <Icon name="edit_document" size={13} /> 작업계획서
              </button>
            </div>
          )}
        </div>
        <div className="text-right">
          <div className="text-slate-400">가동률 (오늘)</div>
          <div className="text-2xl font-bold text-slate-900 tabular-nums">
            {Math.min(100, Math.round((Number(todayWc?.total_hours ?? 0) / 8) * 100))}%
          </div>
        </div>
      </div>
      {wcOpen && (
        <DailyWorkConfirmationModal item={item} wc={todayWc} resourceType="EQUIPMENT"
                                    onClose={() => setWcOpen(false)} />
      )}
    </div>
  );
}

function PersonRow({ item }: { item: BoardItem }) {
  const navigate = useNavigate();
  const todayWc = (item.recent_confirmations ?? []).find((c) => c.work_date === todayStr());
  const [wcOpen, setWcOpen] = useState(false);
  const totalDays = item.total_days ?? 0;
  return (
    <div className="card p-4 flex items-stretch gap-4 hover:shadow-sm transition">
      <div className="shrink-0 w-[100px]">
        <div className="inline-block px-1.5 py-0.5 rounded text-[10px] font-bold bg-purple-600 text-white">인원</div>
        <div className="mt-1">
          <ResourcePhoto type="PERSON" id={item.resource_id} hasPhoto={!!item.has_photo} size={64} />
        </div>
      </div>
      <div className="grid grid-cols-2 md:grid-cols-6 gap-3 flex-1 text-xs">
        <div>
          <div className="text-base font-bold text-slate-900">
            <Link to={`/persons/${item.resource_id}`} className="hover:text-brand-700">{item.resource_label}</Link>
          </div>
          <div className="text-slate-400 mt-1">공급사</div>
          <div className="text-slate-700">{item.supplier_company_name ?? '#' + item.supplier_company_id}</div>
        </div>
        <div>
          <div className="text-slate-400">시작일</div>
          <div className="font-semibold tabular-nums mt-1">{item.start_date ?? '-'}</div>
        </div>
        <div>
          <div className="text-slate-400">출근 상태</div>
          <div className="mt-1">
            {item.today_attended === true
              ? <Chip color="emerald">출근</Chip>
              : <Chip color="rose">미출근</Chip>}
          </div>
        </div>
        <div>
          <div className="text-slate-400">오늘 근무시간</div>
          <div className="font-semibold tabular-nums mt-1">{Number(todayWc?.total_hours ?? 0).toFixed(1)}h</div>
          <div className="text-slate-400 mt-1">누적 출근일</div>
          <div className="font-semibold tabular-nums">{totalDays}일</div>
        </div>
        <div>
          <div className="text-slate-400">최근 작업</div>
          <div className="font-semibold tabular-nums mt-1">{item.last_work_date ?? '-'}</div>
          {item.work_plan_id && (
            <div className="flex flex-col gap-1 mt-2">
              <button onClick={() => setWcOpen(true)}
                      className="inline-flex items-center gap-1 text-[11px] text-emerald-700 hover:underline">
                <Icon name="receipt_long" size={13} /> 일일 작업확인서
              </button>
              <button onClick={() => navigate(`/work-plans/${item.work_plan_id}`)}
                      className="inline-flex items-center gap-1 text-[11px] text-blue-600 hover:underline">
                <Icon name="edit_document" size={13} /> 작업계획서
              </button>
            </div>
          )}
        </div>
        <div className="text-right">
          <div className="text-slate-400">출근률 (누적)</div>
          <div className="text-2xl font-bold text-slate-900 tabular-nums">
            {totalDays > 0 ? 100 : 0}%
          </div>
        </div>
      </div>
      {wcOpen && (
        <DailyWorkConfirmationModal item={item} wc={todayWc} resourceType="PERSON"
                                    onClose={() => setWcOpen(false)} />
      )}
    </div>
  );
}

type WcRow = NonNullable<BoardItem['recent_confirmations']>[number];

function DailyWorkConfirmationModal({ item, wc, onClose }: {
  item: BoardItem;
  wc: WcRow | undefined;
  resourceType: 'EQUIPMENT' | 'PERSON';
  onClose: () => void;
}) {
  const today = todayStr();
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!wc?.id) return;
    let cancelled = false;
    let url: string | null = null;
    api.get(`/api/work-confirmations/${wc.id}/pdf?disposition=inline`, { responseType: 'blob' })
      .then((r) => {
        if (cancelled) return;
        url = URL.createObjectURL(r.data as Blob);
        setPdfUrl(url);
      })
      .catch(() => { if (!cancelled) setError('PDF를 불러올 수 없습니다'); });
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [wc?.id]);

  function download() {
    if (!wc?.id) return;
    window.open(`/api/work-confirmations/${wc.id}/pdf?disposition=attachment`, '_blank');
  }

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/60 flex items-center justify-center p-4" onClick={onClose}>
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-4xl h-[92vh] flex flex-col overflow-hidden"
           onClick={(e) => e.stopPropagation()}>
        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between shrink-0">
          <div>
            <h2 className="text-lg font-bold text-slate-900">일일 작업확인서</h2>
            <p className="text-xs text-slate-500 mt-0.5">{today} · {item.target_site_name ?? '현장 미지정'} · {item.resource_label}</p>
          </div>
          <div className="flex items-center gap-2">
            {wc?.id && (
              <button onClick={download}
                      className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700">
                <Icon name="download" size={14} /> PDF 다운로드
              </button>
            )}
            <button onClick={onClose} className="rounded hover:bg-slate-100 p-1">
              <Icon name="close" size={20} />
            </button>
          </div>
        </div>
        <div className="flex-1 bg-slate-100 overflow-hidden">
          {!wc?.id && (
            <div className="h-full flex items-center justify-center text-slate-600 text-sm">
              <div className="text-center">
                <Icon name="info" size={32} />
                <p className="mt-2">오늘 발급된 작업확인서가 없습니다.</p>
                <p className="text-xs text-slate-500 mt-1">출근/서명 완료 후 자동 발급됩니다.</p>
              </div>
            </div>
          )}
          {error && (
            <div className="h-full flex items-center justify-center text-rose-600 text-sm">{error}</div>
          )}
          {pdfUrl && (
            <iframe src={pdfUrl} title="작업확인서 PDF" className="w-full h-full bg-white" />
          )}
        </div>
      </div>
    </div>
  );
}
