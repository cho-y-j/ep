import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import { COMPANY_TYPE_LABEL, type CompanyResponse, type CompanyType } from '../../types/auth';
import {
  SITE_PARTICIPANT_TYPE_LABEL,
  SITE_STATUS_LABEL,
  type SiteParticipantResponse,
  type SiteResponse,
  type SiteStatus,
  type UpdateSitePayload,
} from '../../types/site';
import SiteResourcesSection from '../assignment/SiteResourcesSection';
import SiteMapEditor from './SiteMapEditor';
import KakaoMap from '../../components/KakaoMap';
import type { PolygonGeoJson } from '../../components/KakaoMap';

type SupplierTypeFilter = Extract<CompanyType, 'EQUIPMENT' | 'MANPOWER'>;

type AttendanceRow = {
  resource_type: 'PERSON' | 'EQUIPMENT';
  resource_id: number;
  resource_label: string;
  has_photo?: boolean | null;
  supplier_company_name?: string | null;
  today_attended?: boolean | null;
};

export default function SiteDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const [site, setSite] = useState<SiteResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<UpdateSitePayload | null>(null);
  const [saving, setSaving] = useState(false);
  const [supplierType, setSupplierType] = useState<SupplierTypeFilter>('EQUIPMENT');
  const [suppliers, setSuppliers] = useState<CompanyResponse[]>([]);
  const [selectedSupplierId, setSelectedSupplierId] = useState<number | ''>('');
  const [participantBusy, setParticipantBusy] = useState(false);
  const [participantError, setParticipantError] = useState<string | null>(null);
  const [attendance, setAttendance] = useState<AttendanceRow[]>([]);

  const canManage = !!site && (user?.role === 'ADMIN' || (user?.role === 'BP' && user.company_id === site.bp_company_id));
  const activeParticipants = useMemo(
    () => (site?.participants ?? []).filter((p) => p.status === 'ACTIVE'),
    [site]
  );

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<SiteResponse>(`/api/sites/${id}`);
      setSite(res.data);
      setForm(toForm(res.data));
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '현장 정보를 불러오지 못했습니다');
      } else {
        setError('현장 정보를 불러오지 못했습니다');
      }
    } finally {
      setLoading(false);
    }
  }, [id]);

  const loadSuppliers = useCallback(async () => {
    if (!canManage) return;
    try {
      const res = await api.get<CompanyResponse[]>('/api/sites/supplier-companies', { params: { type: supplierType } });
      setSuppliers(res.data);
    } catch {
      setSuppliers([]);
    }
  }, [canManage, supplierType]);

  useEffect(() => { void load(); }, [load]);

  // 오늘 출퇴근 현황 — 이 현장에 배정된 인원/장비 + today_attended.
  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    api.get<AttendanceRow[]>('/api/field-deployments/bp/board')
      .then((r) => {
        if (cancelled) return;
        const numericId = Number(id);
        setAttendance(r.data.filter((it) => Number((it as any).target_site_id) === numericId));
      })
      .catch(() => { if (!cancelled) setAttendance([]); });
    return () => { cancelled = true; };
  }, [id]);
  useEffect(() => { void loadSuppliers(); }, [loadSuppliers]);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!site || !form) return;
    setSaving(true);
    setError(null);
    try {
      const res = await api.patch<SiteResponse>(`/api/sites/${site.id}`, {
        ...form,
        name: form.name.trim(),
        code: form.code?.trim() || undefined,
        address: form.address?.trim() || undefined,
        detail_address: form.detail_address?.trim() || undefined,
      });
      setSite(res.data);
      setForm(toForm(res.data));
      setEditing(false);
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '현장 저장 실패');
      } else {
        setError('현장 저장 실패');
      }
    } finally {
      setSaving(false);
    }
  }

  async function addParticipant(e: React.FormEvent) {
    e.preventDefault();
    if (!site || !selectedSupplierId) return;
    setParticipantBusy(true);
    setParticipantError(null);
    try {
      const res = await api.post<SiteResponse>(`/api/sites/${site.id}/participants`, {
        company_id: selectedSupplierId,
      });
      setSite(res.data);
      setSelectedSupplierId('');
    } catch (err) {
      if (err instanceof AxiosError) {
        setParticipantError(err.response?.data?.message ?? '참여 업체 추가 실패');
      } else {
        setParticipantError('참여 업체 추가 실패');
      }
    } finally {
      setParticipantBusy(false);
    }
  }

  async function removeParticipant(participant: SiteParticipantResponse) {
    if (!site) return;
    setParticipantBusy(true);
    setParticipantError(null);
    try {
      const res = await api.delete<SiteResponse>(`/api/sites/${site.id}/participants/${participant.id}`);
      setSite(res.data);
    } catch (err) {
      if (err instanceof AxiosError) {
        setParticipantError(err.response?.data?.message ?? '참여 업체 해제 실패');
      } else {
        setParticipantError('참여 업체 해제 실패');
      }
    } finally {
      setParticipantBusy(false);
    }
  }

  const availableSuppliers = suppliers.filter((supplier) => {
    const alreadyActive = activeParticipants.some((p) => p.company_id === supplier.id);
    return !alreadyActive;
  });

  if (loading) {
    return (
      <AppShell breadcrumb={[{ label: '현장 관리', to: '/sites' }, { label: '현장 상세' }]}>
        <p className="text-sm text-slate-400">불러오는 중...</p>
      </AppShell>
    );
  }

  if (error && !site) {
    return (
      <AppShell breadcrumb={[{ label: '현장 관리', to: '/sites' }, { label: '현장 상세' }]}>
        <div className="card py-12 text-center">
          <p className="mb-4 text-slate-700">{error}</p>
          <Link to="/sites" className="btn-primary inline-flex">목록으로</Link>
        </div>
      </AppShell>
    );
  }

  if (!site || !form) return null;

  return (
    <AppShell
      breadcrumb={[{ label: '현장 관리', to: '/sites' }, { label: site.name }]}
     
    >
      <div className="mx-auto max-w-7xl space-y-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="mb-2 text-sm text-slate-500">
              <Link to="/sites" className="font-semibold text-brand-700 hover:text-brand-800">현장 관리</Link>
              <span className="mx-2">/</span>
              <span>현장 상세</span>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-bold text-slate-950">{site.name}</h1>
              <StatusBadge status={site.status} />
            </div>
            <p className="mt-1 text-sm text-slate-500">{site.bp_company_name ?? 'BP사'}가 구성한 현장 참여 업체를 관리합니다.</p>
          </div>
          {canManage && !editing && (
            <button type="button" onClick={() => setEditing(true)} className="btn-primary">현장 수정</button>
          )}
        </div>

        {editing ? (
          <form onSubmit={save} className="card space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-900">현장 정보 수정</h2>
              <button type="button" onClick={() => { setForm(toForm(site)); setEditing(false); }} className="text-sm text-slate-500 hover:text-slate-900">
                취소
              </button>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <TextInput label="현장명" value={form.name} required onChange={(name) => setForm((prev) => prev && ({ ...prev, name }))} />
              <TextInput label="현장코드" value={form.code ?? ''} onChange={(code) => setForm((prev) => prev && ({ ...prev, code }))} />
              <TextInput label="주소" value={form.address ?? ''} onChange={(address) => setForm((prev) => prev && ({ ...prev, address }))} />
              <TextInput label="상세주소" value={form.detail_address ?? ''} onChange={(detail_address) => setForm((prev) => prev && ({ ...prev, detail_address }))} />
              <DateInput label="시작일" value={form.start_date ?? ''} onChange={(start_date) => setForm((prev) => prev && ({ ...prev, start_date }))} />
              <DateInput label="종료일" value={form.end_date ?? ''} onChange={(end_date) => setForm((prev) => prev && ({ ...prev, end_date }))} />
              <label className="block">
                <span className="text-xs font-semibold text-slate-500">상태</span>
                <select
                  value={form.status}
                  onChange={(e) => setForm((prev) => prev && ({ ...prev, status: e.target.value as SiteStatus }))}
                  className="input mt-1 bg-white"
                >
                  {Object.entries(SITE_STATUS_LABEL).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
              </label>
            </div>
            <div className="space-y-2">
              <span className="text-xs font-semibold text-slate-500">위치 (지도)</span>
              <SiteMapEditor
                address={[form.address, form.detail_address].filter(Boolean).join(' ').trim()}
                initial={{
                  latitude: form.latitude ?? null,
                  longitude: form.longitude ?? null,
                  polygonGeojson: form.polygon_geojson ?? null,
                  mapZoom: form.map_zoom ?? null,
                }}
                onChange={(map) => setForm((prev) => prev && ({
                  ...prev,
                  latitude: map.latitude,
                  longitude: map.longitude,
                  polygon_geojson: map.polygonGeojson,
                  map_zoom: map.mapZoom,
                }))}
              />
            </div>
            {error && <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">{error}</p>}
            <div className="flex justify-end">
              <button type="submit" disabled={saving} className="btn-primary disabled:opacity-50">
                {saving ? '저장 중...' : '저장'}
              </button>
            </div>
          </form>
        ) : (
          <>
            <section className="grid gap-4 lg:grid-cols-4">
              <InfoCard label="BP사" value={site.bp_company_name ?? '-'} />
              <InfoCard label="현장코드" value={site.code ?? `SITE-${String(site.id).padStart(4, '0')}`} />
              <InfoCard label="참여 업체" value={`${activeParticipants.length}개`} />
              <InfoCard label="기간" value={site.start_date || site.end_date
                ? `${site.start_date ?? '—'} ~ ${site.end_date ?? '—'}`
                : '기간 미정'} />
            </section>

            {site.latitude != null && site.longitude != null && (
              <section className="card space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-lg font-bold text-slate-900">현장 위치</h2>
                    <p className="mt-1 text-sm text-slate-500">
                      {site.address ?? '주소 미입력'}
                      {site.detail_address ? ` ${site.detail_address}` : ''}
                    </p>
                  </div>
                  <span className="text-xs font-mono text-slate-500">
                    {site.latitude.toFixed(5)}, {site.longitude.toFixed(5)}
                  </span>
                </div>
                <KakaoMap
                  center={{ lat: site.latitude, lng: site.longitude }}
                  zoom={site.map_zoom ?? 4}
                  markers={[{ id: 'site', position: { lat: site.latitude, lng: site.longitude }, color: 'blue', label: '현장' }]}
                  polygon={(() => {
                    if (!site.polygon_geojson) return null;
                    try { return JSON.parse(site.polygon_geojson) as PolygonGeoJson; } catch { return null; }
                  })()}
                  height="380px"
                />
              </section>
            )}

            <AttendanceTodaySection items={attendance} />
          </>
        )}

        {/* 현장 참여 업체 — 어디서 자원이 오는지 먼저 확인할 수 있게 최상단에 배치 */}
        <section className="card space-y-5">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <h2 className="text-lg font-bold text-slate-900">현장 참여 업체</h2>
              <p className="mt-1 text-sm text-slate-500">BP사가 선정한 장비공급사와 인력공급사만 작업계획서 후보가 됩니다.</p>
            </div>
          </div>

          {canManage && (
            <form onSubmit={addParticipant} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="grid gap-3 md:grid-cols-[180px_1fr_auto]">
                <select
                  value={supplierType}
                  onChange={(e) => { setSupplierType(e.target.value as SupplierTypeFilter); setSelectedSupplierId(''); }}
                  className="input bg-white"
                >
                  <option value="EQUIPMENT">장비공급사</option>
                  <option value="MANPOWER">인력공급사</option>
                </select>
                <select
                  value={selectedSupplierId}
                  onChange={(e) => setSelectedSupplierId(e.target.value ? Number(e.target.value) : '')}
                  required
                  className="input bg-white"
                >
                  <option value="">참여 업체 선택</option>
                  {availableSuppliers.map((supplier) => (
                    <option key={supplier.id} value={supplier.id}>
                      {supplier.name} · {COMPANY_TYPE_LABEL[supplier.type]}
                    </option>
                  ))}
                </select>
                <button type="submit" disabled={participantBusy || !selectedSupplierId} className="btn-primary disabled:opacity-50">
                  추가
                </button>
              </div>
              {participantError && <p className="mt-3 text-sm text-rose-600">{participantError}</p>}
            </form>
          )}

          <div className="overflow-x-auto rounded-xl border border-slate-200">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-semibold">회사명</th>
                  <th className="px-4 py-3 font-semibold">구분</th>
                  <th className="px-4 py-3 font-semibold">상태</th>
                  <th className="px-4 py-3 font-semibold">추가일</th>
                  {canManage && <th className="px-4 py-3 font-semibold">작업</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {(site.participants ?? []).map((participant) => (
                  <tr key={participant.id} className={participant.status !== 'ACTIVE' ? 'bg-slate-50 text-slate-400' : ''}>
                    <td className="px-4 py-3 font-semibold text-slate-900">{participant.company_name ?? `id=${participant.company_id}`}</td>
                    <td className="px-4 py-3 text-slate-600">{SITE_PARTICIPANT_TYPE_LABEL[participant.participant_type]}</td>
                    <td className="px-4 py-3">
                      <ParticipantStatusBadge status={participant.status} />
                    </td>
                    <td className="px-4 py-3 text-slate-500">
                      {new Date(participant.added_at).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}
                    </td>
                    {canManage && (
                      <td className="px-4 py-3">
                        {participant.status === 'ACTIVE' ? (
                          <button
                            type="button"
                            onClick={() => void removeParticipant(participant)}
                            disabled={participantBusy}
                            className="rounded-lg px-3 py-1.5 text-xs font-semibold text-rose-600 hover:bg-rose-50 disabled:opacity-50"
                          >
                            해제
                          </button>
                        ) : (
                          <span className="text-xs text-slate-400">해제됨</span>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
                {(site.participants ?? []).length === 0 && (
                  <tr>
                    <td colSpan={canManage ? 5 : 4} className="px-4 py-10 text-center text-slate-400">참여 업체가 없습니다.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        {/* 배치 장비 / 배치 인원 — 참여 업체 아래로 이동. 참여 업체 확인 후 자원 배치 흐름. */}
        <SiteResourcesSection siteId={site.id} canManage={canManage} />
      </div>
    </AppShell>
  );
}

function toForm(site: SiteResponse): UpdateSitePayload {
  return {
    name: site.name,
    code: site.code ?? '',
    address: site.address ?? '',
    detail_address: site.detail_address ?? '',
    start_date: site.start_date ?? '',
    end_date: site.end_date ?? '',
    status: site.status,
    latitude: site.latitude ?? null,
    longitude: site.longitude ?? null,
    polygon_geojson: site.polygon_geojson ?? null,
    map_zoom: site.map_zoom ?? null,
  };
}

function InfoCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-xs font-semibold text-slate-500">{label}</p>
      <p className="mt-2 truncate text-base font-bold text-slate-950">{value}</p>
    </div>
  );
}

function StatusBadge({ status }: { status: SiteStatus }) {
  const tone = status === 'ACTIVE'
    ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
    : status === 'PAUSED'
      ? 'bg-amber-50 text-amber-700 ring-amber-200'
      : 'bg-slate-100 text-slate-600 ring-slate-200';
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${tone}`}>{SITE_STATUS_LABEL[status]}</span>;
}

function ParticipantStatusBadge({ status }: { status: SiteParticipantResponse['status'] }) {
  const label = status === 'ACTIVE' ? '참여중' : status === 'SUSPENDED' ? '중지' : '해제';
  const tone = status === 'ACTIVE'
    ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
    : 'bg-slate-100 text-slate-600 ring-slate-200';
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${tone}`}>{label}</span>;
}

function TextInput({ label, value, onChange, required }: { label: string; value: string; onChange: (value: string) => void; required?: boolean }) {
  return (
    <label className="block">
      <span className="text-xs font-semibold text-slate-500">{label}</span>
      <input value={value} onChange={(e) => onChange(e.target.value)} required={required} className="input mt-1" />
    </label>
  );
}

function DateInput({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="block">
      <span className="text-xs font-semibold text-slate-500">{label}</span>
      <input type="date" value={value} onChange={(e) => onChange(e.target.value)} className="input mt-1" />
    </label>
  );
}

function AttendanceTodaySection({ items }: { items: AttendanceRow[] }) {
  const persons = items.filter((i) => i.resource_type === 'PERSON');
  const equipments = items.filter((i) => i.resource_type === 'EQUIPMENT');
  const personPresent = persons.filter((p) => p.today_attended === true).length;
  const personTotal = persons.length;
  const eqRunning = equipments.filter((e) => e.today_attended === true).length;
  const eqTotal = equipments.length;
  if (personTotal === 0 && eqTotal === 0) return null;
  return (
    <section className="card space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-slate-900">오늘 출퇴근 현황</h2>
          <p className="mt-1 text-sm text-slate-500">현장에 배정된 인원·장비의 오늘 출근 여부.</p>
        </div>
        <div className="flex gap-2 text-sm">
          <span className="rounded-full bg-emerald-50 text-emerald-700 px-3 py-1 font-semibold">
            인원 {personPresent}/{personTotal}
          </span>
          <span className="rounded-full bg-blue-50 text-blue-700 px-3 py-1 font-semibold">
            장비 {eqRunning}/{eqTotal}
          </span>
        </div>
      </div>
      {personTotal > 0 && (
        <div>
          <div className="text-xs font-semibold text-slate-600 mb-2">인원</div>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
            {persons.map((p) => (
              <AttendanceCard key={`p-${p.resource_id}`} item={p} type="PERSON" />
            ))}
          </div>
        </div>
      )}
      {eqTotal > 0 && (
        <div>
          <div className="text-xs font-semibold text-slate-600 mb-2">장비</div>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
            {equipments.map((e) => (
              <AttendanceCard key={`e-${e.resource_id}`} item={e} type="EQUIPMENT" />
            ))}
          </div>
        </div>
      )}
    </section>
  );
}

function AttendanceCard({ item, type }: { item: AttendanceRow; type: 'PERSON' | 'EQUIPMENT' }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    if (!item.has_photo) return;
    let url: string | null = null;
    let cancelled = false;
    const endpoint = type === 'EQUIPMENT' ? `/api/equipment/${item.resource_id}/photo` : `/api/persons/${item.resource_id}/photo`;
    api.get(endpoint, { responseType: 'blob' })
      .then((r) => {
        if (cancelled) return;
        url = URL.createObjectURL(r.data as Blob);
        setSrc(url);
      })
      .catch(() => {});
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [type, item.resource_id, item.has_photo]);

  const present = item.today_attended === true;
  return (
    <div className={`rounded-lg border p-3 flex flex-col items-center text-center ${
      present ? 'border-emerald-200 bg-emerald-50/40' : 'border-slate-200 bg-slate-50/40'
    }`}>
      <div className={`w-14 h-14 rounded-full bg-white overflow-hidden flex items-center justify-center mb-2 ring-2 ${
        present ? 'ring-emerald-300' : 'ring-slate-200'
      }`}>
        {src ? <img src={src} alt="" className="w-full h-full object-cover" />
             : <span className="text-slate-400 text-xs">{type === 'PERSON' ? '사람' : '장비'}</span>}
      </div>
      <div className="font-medium text-sm text-slate-900 truncate w-full">{item.resource_label}</div>
      {item.supplier_company_name && (
        <div className="text-xs text-slate-500 truncate w-full">{item.supplier_company_name}</div>
      )}
      <span className={`mt-1 inline-block rounded-full px-2 py-0.5 text-xs font-semibold ${
        present ? 'bg-emerald-600 text-white' : 'bg-slate-300 text-slate-700'
      }`}>
        {present ? '출근' : '미출근'}
      </span>
    </div>
  );
}
