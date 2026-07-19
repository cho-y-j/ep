import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import type { CompanyResponse } from '../../types/auth';
import { SITE_STATUS_LABEL, type CreateSitePayload, type SiteResponse, type SiteStatus } from '../../types/site';
import SiteMapEditor from './SiteMapEditor';

export default function SitePage() {
  const navigate = useNavigate();
  const { user, company } = useAuth();
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [bpCompanies, setBpCompanies] = useState<CompanyResponse[]>([]);
  const [eqSuppliers, setEqSuppliers] = useState<CompanyResponse[]>([]);
  const [mpSuppliers, setMpSuppliers] = useState<CompanyResponse[]>([]);
  const [selectedSuppliers, setSelectedSuppliers] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<CreateSitePayload>({ name: '' });
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState<SiteStatus | ''>('');

  const canCreate = user?.role === 'ADMIN' || user?.role === 'BP';
  const isAdmin = user?.role === 'ADMIN';

  const stats = useMemo(() => {
    const active = sites.filter((s) => s.status === 'ACTIVE').length;
    const participants = sites.reduce((sum, s) => sum + s.participant_count, 0);
    return { active, participants };
  }, [sites]);

  const qLower = q.trim().toLowerCase();
  const filteredSites = useMemo(() => sites.filter((s) => {
    if (statusFilter && s.status !== statusFilter) return false;
    if (qLower) {
      const hay = `${s.name} ${s.code ?? ''} ${s.bp_company_name ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  }), [sites, statusFilter, qLower]);
  const activeFilterCount = [q, statusFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setStatusFilter(''); };

  async function load() {
    setLoading(true);
    try {
      const [siteRes, bpRes, eqRes, mpRes] = await Promise.all([
        api.get<SiteResponse[]>('/api/sites'),
        isAdmin ? api.get<CompanyResponse[]>('/api/companies', { params: { type: 'BP' } }) : Promise.resolve({ data: [] as CompanyResponse[] }),
        canCreate ? api.get<CompanyResponse[]>('/api/companies/suppliers', { params: { type: 'EQUIPMENT' } }) : Promise.resolve({ data: [] as CompanyResponse[] }),
        canCreate ? api.get<CompanyResponse[]>('/api/companies/suppliers', { params: { type: 'MANPOWER' } }) : Promise.resolve({ data: [] as CompanyResponse[] }),
      ]);
      setSites(siteRes.data);
      setBpCompanies(bpRes.data);
      setEqSuppliers(eqRes.data);
      setMpSuppliers(mpRes.data);
    } finally {
      setLoading(false);
    }
  }

  function toggleSupplier(id: number) {
    setSelectedSuppliers((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  useEffect(() => { void load(); }, [isAdmin]);

  function startCreate() {
    setError(null);
    setForm({
      name: '',
      bp_company_id: isAdmin ? undefined : company?.id,
    });
    setSelectedSuppliers(new Set());
    setCreating(true);
  }

  async function createSite(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const payload = {
        ...form,
        name: form.name.trim(),
        code: form.code?.trim() || undefined,
        address: form.address?.trim() || undefined,
        detail_address: form.detail_address?.trim() || undefined,
      };
      const res = await api.post<SiteResponse>('/api/sites', payload);
      const siteId = res.data.id;
      // 선택된 공급사들 일괄 참여 등록
      const failed: string[] = [];
      for (const supplierId of selectedSuppliers) {
        try {
          await api.post(`/api/sites/${siteId}/participants`, { company_id: supplierId });
        } catch (err) {
          const name = [...eqSuppliers, ...mpSuppliers].find((c) => c.id === supplierId)?.name ?? `회사 #${supplierId}`;
          failed.push(name);
        }
      }
      setCreating(false);
      if (failed.length > 0) {
        alert(`현장은 생성됐지만 일부 공급사 추가 실패: ${failed.join(', ')}\n현장 상세에서 다시 추가하세요.`);
      }
      navigate(`/sites/${siteId}`);
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '현장 등록 실패');
      } else {
        setError('현장 등록 실패');
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <AppShell
      breadcrumb={[{ label: '현장 관리' }]}
     
    >
      <div className="mx-auto max-w-7xl space-y-6">
        <PageHeader
          title="현장 관리"
          subtitle="BP사가 현장을 만들고 장비공급사와 인력공급사를 현장 참여 업체로 구성합니다."
          actions={canCreate && !creating ? (
            <button type="button" onClick={startCreate} className="btn-primary">
              현장 등록
            </button>
          ) : null}
        />

        <section className="grid gap-4 md:grid-cols-3">
          <StatCard label="전체 현장" value={sites.length} />
          <StatCard label="진행중 현장" value={stats.active} tone="blue" />
          <StatCard label="참여 업체" value={stats.participants} tone="emerald" />
        </section>

        {creating && (
          <form onSubmit={createSite} className="card space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-900">현장 등록</h2>
              <button type="button" onClick={() => setCreating(false)} className="text-sm text-slate-500 hover:text-slate-900">
                취소
              </button>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              {isAdmin && (
                <label className="block">
                  <span className="text-xs font-semibold text-slate-500">BP사</span>
                  <select
                    value={form.bp_company_id ?? ''}
                    onChange={(e) => setForm((prev) => ({ ...prev, bp_company_id: e.target.value ? Number(e.target.value) : undefined }))}
                    required
                    className="input mt-1 bg-white"
                  >
                    <option value="">BP사 선택</option>
                    {bpCompanies.map((bp) => (
                      <option key={bp.id} value={bp.id}>{bp.name}</option>
                    ))}
                  </select>
                </label>
              )}
              <TextInput label="현장명" value={form.name} required onChange={(name) => setForm((prev) => ({ ...prev, name }))} />
              <TextInput label="현장코드" value={form.code ?? ''} onChange={(code) => setForm((prev) => ({ ...prev, code }))} />
              <TextInput label="주소" value={form.address ?? ''} onChange={(address) => setForm((prev) => ({ ...prev, address }))} />
              <TextInput label="상세주소" value={form.detail_address ?? ''} onChange={(detail_address) => setForm((prev) => ({ ...prev, detail_address }))} />
              <DateInput label="시작일" value={form.start_date ?? ''} onChange={(start_date) => setForm((prev) => ({ ...prev, start_date }))} />
              <DateInput label="종료일" value={form.end_date ?? ''} onChange={(end_date) => setForm((prev) => ({ ...prev, end_date }))} />
            </div>

            <div className="space-y-2">
              <span className="text-xs font-semibold text-slate-500">위치 (지도)</span>
              <SiteMapEditor
                address={[form.address, form.detail_address].filter(Boolean).join(' ').trim()}
                onChange={(map) => setForm((prev) => ({
                  ...prev,
                  latitude: map.latitude,
                  longitude: map.longitude,
                  polygon_geojson: map.polygonGeojson,
                  map_zoom: map.mapZoom,
                }))}
              />
            </div>

            <div className="space-y-3 pt-2 border-t border-slate-100">
              <div>
                <div className="text-sm font-semibold text-slate-900 mb-1">참여 업체 (선택)</div>
                <p className="text-xs text-slate-500">함께 추가할 장비공급사·인력공급사를 미리 선택. 나중에 현장 상세에서 추가/해제 가능.</p>
              </div>

              {eqSuppliers.length > 0 && (
                <div>
                  <div className="text-xs font-semibold text-slate-500 mb-1.5">장비공급사</div>
                  <div className="flex flex-wrap gap-1.5">
                    {eqSuppliers.map((c) => {
                      const active = selectedSuppliers.has(c.id);
                      return (
                        <button key={c.id} type="button" onClick={() => toggleSupplier(c.id)}
                          className={`px-2.5 py-1 rounded-full text-xs font-semibold border ${
                            active ? 'bg-brand-600 text-white border-brand-600'
                                   : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                          }`}>
                          {active && '✓ '}{c.name}
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}

              {mpSuppliers.length > 0 && (
                <div>
                  <div className="text-xs font-semibold text-slate-500 mb-1.5">인력공급사</div>
                  <div className="flex flex-wrap gap-1.5">
                    {mpSuppliers.map((c) => {
                      const active = selectedSuppliers.has(c.id);
                      return (
                        <button key={c.id} type="button" onClick={() => toggleSupplier(c.id)}
                          className={`px-2.5 py-1 rounded-full text-xs font-semibold border ${
                            active ? 'bg-brand-600 text-white border-brand-600'
                                   : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                          }`}>
                          {active && '✓ '}{c.name}
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>

            {error && <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">{error}</p>}
            <div className="flex justify-end">
              <button type="submit" disabled={saving} className="btn-primary disabled:opacity-50">
                {saving ? '저장 중...' : '등록'}
              </button>
            </div>
          </form>
        )}

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '현장명·코드·BP사 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          <FilterSelect
            value={statusFilter}
            onChange={(v) => setStatusFilter(v as SiteStatus | '')}
            placeholder="상태 전체"
            options={(Object.keys(SITE_STATUS_LABEL) as SiteStatus[]).map((s) => ({ value: s, label: SITE_STATUS_LABEL[s] }))}
          />
        </FilterBar>

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중...</p>
        ) : (
          <div className="card overflow-x-auto p-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-semibold">현장명</th>
                  <th className="px-4 py-3 font-semibold">BP사</th>
                  <th className="px-4 py-3 font-semibold">주소</th>
                  <th className="px-4 py-3 font-semibold">참여 업체</th>
                  <th className="px-4 py-3 font-semibold">상태</th>
                  <th className="px-4 py-3 font-semibold">기간</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredSites.map((site) => (
                  <tr key={site.id} onClick={() => navigate(`/sites/${site.id}`)} className="cursor-pointer hover:bg-slate-50">
                    <td className="px-4 py-3">
                      <div className="font-semibold text-slate-900">{site.name}</div>
                      <div className="text-xs text-slate-500">{site.code ?? `SITE-${String(site.id).padStart(4, '0')}`}</div>
                    </td>
                    <td className="px-4 py-3 text-slate-700">{site.bp_company_name ?? '-'}</td>
                    <td className="px-4 py-3 text-slate-500">{site.address ?? '-'}</td>
                    <td className="px-4 py-3 font-semibold text-slate-900">{site.participant_count}</td>
                    <td className="px-4 py-3"><SiteStatusBadge status={site.status} /></td>
                    <td className="px-4 py-3 text-slate-500">
                      {site.start_date || site.end_date
                        ? `${site.start_date ?? '—'} ~ ${site.end_date ?? '—'}`
                        : <span className="text-slate-400">기간 미정</span>}
                    </td>
                  </tr>
                ))}
                {filteredSites.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-10 text-center text-slate-400">
                      {sites.length === 0 ? '등록된 현장이 없습니다.' : '조건에 맞는 현장이 없습니다.'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </AppShell>
  );
}

function StatCard({ label, value, tone = 'slate' }: { label: string; value: number; tone?: 'slate' | 'blue' | 'emerald' }) {
  const toneClass = tone === 'blue'
    ? 'bg-brand-50 text-brand-700'
    : tone === 'emerald'
      ? 'bg-emerald-50 text-emerald-700'
      : 'bg-slate-100 text-slate-700';
  return (
    <div className="card">
      <span className={`inline-flex rounded px-2 py-0.5 text-xs font-semibold ${toneClass}`}>{label}</span>
      <div className="mt-3 text-3xl font-bold text-slate-950">{value.toLocaleString()}</div>
    </div>
  );
}

function SiteStatusBadge({ status }: { status: SiteResponse['status'] }) {
  const tone = status === 'ACTIVE'
    ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
    : status === 'PAUSED'
      ? 'bg-amber-50 text-amber-700 ring-amber-200'
      : 'bg-slate-100 text-slate-600 ring-slate-200';
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${tone}`}>{SITE_STATUS_LABEL[status]}</span>;
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
