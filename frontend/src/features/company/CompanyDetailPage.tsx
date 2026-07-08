import { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate, Link, useSearchParams } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { COMPANY_TYPE_LABEL, type CompanyResponse } from '../../types/auth';
import { EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory } from '../../types/equipment';
import { PERSON_ROLE_LABEL, type PersonRole } from '../../types/person';
import DocumentSection from '../document/DocumentSection';
import Avatar from '../../components/Avatar';
import EquipmentCreateForm from '../equipment/EquipmentCreateForm';
import PersonCreateForm from '../person/PersonCreateForm';
import ComparisonSnapshotsList from './ComparisonSnapshotsList';

type Tab = 'info' | 'equipment' | 'persons' | 'users' | 'partners' | 'docs' | 'history';

interface Equipment {
  id: number;
  vehicle_no?: string;
  model?: string;
  manufacturer?: string;
  category: string;
  year?: number;
  has_photo?: boolean;
  expiring_count?: number;
  current_site_name?: string | null;
}
interface Person {
  id: number;
  name: string;
  phone?: string;
  roles?: string[];
  team?: string;
  status?: string;
  has_photo?: boolean;
  expiring_count?: number;
  document_count?: number;
}
interface UserRow {
  id: number; email: string; name: string; phone?: string;
  role: string; is_company_admin: boolean; enabled: boolean;
}
interface PartnerCompany {
  id: number; name: string; type: 'BP' | 'EQUIPMENT' | 'MANPOWER'; business_number: string;
}

/** ADMIN 회사 상세 — 정보/장비/인원/서류 탭. */
export default function CompanyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const companyId = Number(id);
  const [tab, setTab] = useState<Tab>(() => {
    const initial = new URLSearchParams(window.location.search).get('tab');
    return (initial === 'equipment' || initial === 'persons' || initial === 'users' || initial === 'partners' || initial === 'docs' || initial === 'history') ? initial : 'info';
  });
  const [company, setCompany] = useState<CompanyResponse | null>(null);
  const [equipments, setEquipments] = useState<Equipment[]>([]);
  const [persons, setPersons] = useState<Person[]>([]);
  const [users, setUsers] = useState<UserRow[]>([]);
  const [partners, setPartners] = useState<PartnerCompany[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [nameDraft, setNameDraft] = useState('');
  const [saveError, setSaveError] = useState<string | null>(null);
  const [creatingEquipment, setCreatingEquipment] = useState(false);
  const [creatingPerson, setCreatingPerson] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const fromBpId = searchParams.get('fromBp') ? Number(searchParams.get('fromBp')) : null;

  const changeTab = (next: Tab) => {
    setTab(next);
    const params = new URLSearchParams(searchParams);
    if (next === 'info') params.delete('tab');
    else params.set('tab', next);
    setSearchParams(params, { replace: true });
  };
  const [fromBp, setFromBp] = useState<CompanyResponse | null>(null);

  useEffect(() => {
    if (!fromBpId) { setFromBp(null); return; }
    api.get<CompanyResponse>(`/api/companies/${fromBpId}`).then((res) => setFromBp(res.data)).catch(() => setFromBp(null));
  }, [fromBpId]);

  async function load() {
    setLoading(true);
    try {
      const co = await api.get<CompanyResponse>(`/api/companies/${companyId}`);
      setCompany(co.data);
      setNameDraft(co.data.name);
      const eq = await api.get<Equipment[]>('/api/equipment');
      setEquipments(eq.data.filter((e: any) => e.supplier_id === companyId));
      const pp = await api.get<any>('/api/persons?size=200');
      const list = Array.isArray(pp.data) ? pp.data : (pp.data.content ?? []);
      setPersons(list.filter((p: any) => p.supplier_id === companyId));
      // BP/공급사 모두 직원 목록 — 가입된 user
      try {
        const us = await api.get<UserRow[]>(`/api/companies/${companyId}/users`);
        setUsers(us.data);
      } catch { setUsers([]); }
      // BP 일 때만 연동 공급사
      if (co.data.type === 'BP') {
        try {
          const pa = await api.get<PartnerCompany[]>(`/api/companies/${companyId}/partners`);
          setPartners(pa.data);
        } catch { setPartners([]); }
      }
    } catch (e) {
      // ignore
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void load(); }, [companyId]);

  async function saveName() {
    setSaveError(null);
    try {
      const res = await api.patch<CompanyResponse>(`/api/companies/${companyId}`, { name: nameDraft });
      setCompany(res.data);
      setEditing(false);
    } catch (e: any) {
      setSaveError(e?.response?.data?.message || '저장 실패');
    }
  }

  const breadcrumb = useMemo(() => {
    // fromBp 가 있으면 BP사 관리 > BP명 > 연동 공급사 > 공급사명 4단계
    if (fromBp && company && company.type !== 'BP') {
      return [
        { label: 'BP사 관리', to: '/admin/bp' },
        { label: fromBp.name, to: `/admin/companies/${fromBp.id}` },
        { label: '연동 공급사', to: `/admin/companies/${fromBp.id}?tab=partners` },
        { label: company.name, to: '' },
      ];
    }
    const list = company?.type === 'BP'
      ? [{ label: 'BP사 관리', to: '/admin/bp' }]
      : [{ label: '공급사 관리', to: '/admin/suppliers' }];
    list.push({ label: company?.name ?? `회사 #${companyId}`, to: '' });
    return list;
  }, [company, companyId, fromBp]);

  if (loading && !company) {
    return <AppShell><p className="text-slate-400 p-6">로딩 중…</p></AppShell>;
  }
  if (!company) {
    return <AppShell><p className="text-rose-600 p-6">회사를 찾을 수 없습니다.</p></AppShell>;
  }

  return (
    <AppShell breadcrumb={breadcrumb as any}>
      <div className="max-w-5xl mx-auto px-6 py-6">
        <div className="flex items-start justify-between mb-1">
          <div>
            <h1 className="text-2xl font-bold">
              {company.name}
              {tab !== 'info' && (
                <span className="text-slate-400 font-normal"> · {
                  tab === 'equipment' ? `장비 ${equipments.length}대`
                  : tab === 'persons' ? `인원 ${persons.length}명`
                  : tab === 'users' ? `직원 ${users.length}명`
                  : tab === 'partners' ? `연동 공급사 ${partners.length}곳`
                  : '서류'
                }</span>
              )}
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              {COMPANY_TYPE_LABEL[company.type]} · 사업자번호 {company.business_number}
            </p>
          </div>
          <button onClick={() => navigate(-1)} className="text-xs text-slate-500 hover:text-slate-700">
            ← 목록
          </button>
        </div>

        {/* 탭 — type 별 분기 */}
        <div className="flex gap-1 mt-6 border-b border-slate-200 overflow-x-auto">
          <TabBtn active={tab === 'info'} onClick={() => changeTab('info')}>정보</TabBtn>
          <TabBtn active={tab === 'users'} onClick={() => changeTab('users')}>
            하위 직원 <span className="text-slate-400 text-xs ml-1">({users.length})</span>
          </TabBtn>
          {company.type === 'BP' ? (
            <>
              <TabBtn active={tab === 'partners'} onClick={() => changeTab('partners')}>
                연동 공급사 <span className="text-slate-400 text-xs ml-1">({partners.length})</span>
              </TabBtn>
              <TabBtn active={tab === 'history'} onClick={() => changeTab('history')}>
                거래 이력
              </TabBtn>
            </>
          ) : (
            <>
              {company.type === 'EQUIPMENT' && (
                <TabBtn active={tab === 'equipment'} onClick={() => changeTab('equipment')}>
                  장비 <span className="text-slate-400 text-xs ml-1">({equipments.length})</span>
                </TabBtn>
              )}
              <TabBtn active={tab === 'persons'} onClick={() => changeTab('persons')}>
                인원 <span className="text-slate-400 text-xs ml-1">({persons.length})</span>
              </TabBtn>
            </>
          )}
          <TabBtn active={tab === 'docs'} onClick={() => changeTab('docs')}>서류</TabBtn>
        </div>

        <div key={tab} className="mt-5 tab-fade-in">
          {tab === 'info' && (
            <div className="card p-6 space-y-4 text-sm">
              <Row label="회사명" value={
                editing ? (
                  <input className="input" value={nameDraft} onChange={(e) => setNameDraft(e.target.value)} />
                ) : (
                  <span className="text-slate-900">{company.name}</span>
                )
              } />
              <Row label="사업자번호" value={<span className="text-slate-900">{company.business_number}</span>} />
              <Row label="유형" value={<span className="text-slate-900">{COMPANY_TYPE_LABEL[company.type]}</span>} />
              <Row label="등록일" value={
                <span className="text-slate-900">{new Date(company.created_at).toLocaleString('ko-KR')}</span>
              } />
              {saveError && <div className="text-xs text-rose-600">{saveError}</div>}
              <div className="flex justify-end gap-2 pt-2">
                {editing ? (
                  <>
                    <button onClick={() => { setEditing(false); setNameDraft(company.name); }} className="btn-ghost">취소</button>
                    <button onClick={saveName} className="btn-primary">저장</button>
                  </>
                ) : (
                  <button onClick={() => setEditing(true)} className="btn-primary">회사명 수정</button>
                )}
              </div>
            </div>
          )}

          {tab === 'equipment' && (
            <div className="space-y-4">
              {company.type !== 'BP' && (
                <div className="flex justify-end">
                  {!creatingEquipment ? (
                    <button type="button" onClick={() => setCreatingEquipment(true)} className="btn-primary">
                      + 장비 추가
                    </button>
                  ) : null}
                </div>
              )}
              {creatingEquipment && (
                <EquipmentCreateForm
                  equipmentSuppliers={[company]}
                  requireSupplierId
                  onCreated={() => { setCreatingEquipment(false); void load(); }}
                  onCancel={() => setCreatingEquipment(false)}
                />
              )}
              <div className="card p-0 overflow-x-auto">
              {equipments.length === 0 ? (
                <div className="p-8 text-center text-slate-400">등록된 장비 없음</div>
              ) : (
                <table className="w-full text-sm">
                  <thead className="bg-slate-50 border-b border-slate-200 text-left text-slate-500">
                    <tr>
                      <th className="px-4 py-2 font-medium">사진</th>
                      <th className="px-4 py-2 font-medium">차량번호</th>
                      <th className="px-4 py-2 font-medium">모델</th>
                      <th className="px-4 py-2 font-medium">제조사</th>
                      <th className="px-4 py-2 font-medium">카테고리</th>
                      <th className="px-4 py-2 font-medium">연식</th>
                      <th className="px-4 py-2 font-medium">만료 임박</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {equipments.map((e) => (
                      <tr key={e.id} className="hover:bg-slate-50 cursor-pointer" onClick={() => navigate(`/equipment/${e.id}?fromCompany=${companyId}`)}>
                        <td className="px-4 py-3">
                          <Avatar
                            fetchUrl={e.has_photo ? `/api/equipment/${e.id}/photo` : undefined}
                            fallbackText={e.vehicle_no ?? e.model ?? '#'}
                            alt={e.vehicle_no ?? `장비 #${e.id}`}
                            size={40}
                            rounded="lg"
                          />
                        </td>
                        <td className="px-4 py-3">
                          <Link to={`/equipment/${e.id}?fromCompany=${companyId}`} className="font-medium hover:underline" onClick={(ev) => ev.stopPropagation()}>
                            {e.vehicle_no ?? `장비 #${e.id}`}
                          </Link>
                        </td>
                        <td className="px-4 py-3 text-slate-700">{e.model ?? '-'}</td>
                        <td className="px-4 py-3 text-slate-700">{e.manufacturer ?? '-'}</td>
                        <td className="px-4 py-3 text-slate-700">{EQUIPMENT_CATEGORY_LABEL[e.category as EquipmentCategory] ?? e.category}</td>
                        <td className="px-4 py-3 text-slate-500">{e.year ?? '-'}</td>
                        <td className="px-4 py-3">
                          {(e.expiring_count ?? 0) > 0 ? (
                            <span className="inline-flex min-w-[24px] px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 text-xs font-semibold">{e.expiring_count}</span>
                          ) : <span className="text-xs text-slate-400">-</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              </div>
            </div>
          )}

          {tab === 'persons' && (
            <div className="space-y-4">
              <div className="flex justify-end">
                {!creatingPerson ? (
                  <button type="button" onClick={() => setCreatingPerson(true)} className="btn-primary">
                    + 인원 추가
                  </button>
                ) : null}
              </div>
              {creatingPerson && (
                <PersonCreateForm
                  suppliers={[company]}
                  selfSupplierType={company.type}
                  requireSupplierId
                  onCreated={() => { setCreatingPerson(false); void load(); }}
                  onCancel={() => setCreatingPerson(false)}
                />
              )}
              <div className="card p-0 overflow-x-auto">
              {persons.length === 0 ? (
                <div className="p-8 text-center text-slate-400">등록된 인원 없음</div>
              ) : (
                <table className="w-full text-sm">
                  <thead className="bg-slate-50 border-b border-slate-200 text-left text-slate-500">
                    <tr>
                      <th className="px-4 py-2 font-medium">사진</th>
                      <th className="px-4 py-2 font-medium">이름</th>
                      <th className="px-4 py-2 font-medium">전화</th>
                      <th className="px-4 py-2 font-medium">팀</th>
                      <th className="px-4 py-2 font-medium">역할</th>
                      <th className="px-4 py-2 font-medium">서류</th>
                      <th className="px-4 py-2 font-medium">만료 임박</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {persons.map((p) => (
                      <tr key={p.id} className="hover:bg-slate-50 cursor-pointer" onClick={() => navigate(`/persons/${p.id}?fromCompany=${companyId}`)}>
                        <td className="px-4 py-3">
                          <Avatar
                            fetchUrl={p.has_photo ? `/api/persons/${p.id}/photo` : undefined}
                            fallbackText={p.name}
                            alt={p.name}
                            size={40}
                          />
                        </td>
                        <td className="px-4 py-3">
                          <Link to={`/persons/${p.id}?fromCompany=${companyId}`} className="font-medium hover:underline" onClick={(e) => e.stopPropagation()}>{p.name}</Link>
                        </td>
                        <td className="px-4 py-3 text-slate-700">{p.phone ?? '-'}</td>
                        <td className="px-4 py-3 text-slate-700">{p.team ?? '-'}</td>
                        <td className="px-4 py-3 text-slate-700">{(p.roles ?? []).map((r) => PERSON_ROLE_LABEL[r as PersonRole] ?? r).join(', ')}</td>
                        <td className="px-4 py-3 text-slate-700">{p.document_count ?? 0}</td>
                        <td className="px-4 py-3">
                          {(p.expiring_count ?? 0) > 0 ? (
                            <span className="inline-flex min-w-[24px] px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 text-xs font-semibold">{p.expiring_count}</span>
                          ) : <span className="text-xs text-slate-400">-</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              </div>
            </div>
          )}

          {tab === 'users' && (
            <div className="card p-0 overflow-x-auto">
              {users.length === 0 ? (
                <div className="p-8 text-center text-slate-400">가입된 직원 없음</div>
              ) : (
                <table className="w-full text-sm">
                  <thead className="bg-slate-50 border-b border-slate-200 text-left text-slate-500">
                    <tr>
                      <th className="px-4 py-2 font-medium">이름</th>
                      <th className="px-4 py-2 font-medium">이메일</th>
                      <th className="px-4 py-2 font-medium">전화</th>
                      <th className="px-4 py-2 font-medium">역할</th>
                      <th className="px-4 py-2 font-medium">권한</th>
                      <th className="px-4 py-2 font-medium">상태</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {users.map((u) => (
                      <tr key={u.id} className="hover:bg-slate-50">
                        <td className="px-4 py-3 font-medium">{u.name}</td>
                        <td className="px-4 py-3 text-slate-700">{u.email}</td>
                        <td className="px-4 py-3 text-slate-700">{u.phone ?? '-'}</td>
                        <td className="px-4 py-3 text-slate-700">{u.role}</td>
                        <td className="px-4 py-3">
                          {u.is_company_admin
                            ? <span className="text-xs px-2 py-0.5 rounded bg-amber-100 text-amber-700">회사 관리자</span>
                            : <span className="text-xs text-slate-500">직원</span>}
                        </td>
                        <td className="px-4 py-3">
                          {u.enabled
                            ? <span className="text-xs px-2 py-0.5 rounded bg-emerald-100 text-emerald-700">활성</span>
                            : <span className="text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-500">비활성</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          {tab === 'partners' && (
            <div className="card p-0 overflow-x-auto">
              {partners.length === 0 ? (
                <div className="p-8 text-center text-slate-400">아직 견적 거래 이력이 있는 공급사가 없습니다.</div>
              ) : (
                <table className="w-full text-sm">
                  <thead className="bg-slate-50 border-b border-slate-200 text-left text-slate-500">
                    <tr>
                      <th className="px-4 py-2 font-medium">회사명</th>
                      <th className="px-4 py-2 font-medium">유형</th>
                      <th className="px-4 py-2 font-medium">사업자번호</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {partners.map((p) => (
                      <tr key={p.id} className="hover:bg-slate-50 cursor-pointer"
                          onClick={() => navigate(`/admin/companies/${p.id}?fromBp=${companyId}`)}>
                        <td className="px-4 py-3 font-medium">{p.name}</td>
                        <td className="px-4 py-3 text-slate-700">
                          {p.type === 'EQUIPMENT' ? '장비공급사' : p.type === 'MANPOWER' ? '인력공급사' : 'BP'}
                        </td>
                        <td className="px-4 py-3 text-slate-500">{p.business_number}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          {tab === 'history' && company.type === 'BP' && (
            <div className="space-y-2">
              <p className="text-xs text-slate-500">이 BP사가 선정한 견적의 비교증거 영구 보존. 각 행 펼치면 응찰 공급사 + 가격 확인.</p>
              <ComparisonSnapshotsList companyId={companyId} />
            </div>
          )}

          {tab === 'docs' && (
            <div className="card p-4">
              <DocumentSection ownerType="COMPANY" ownerId={companyId} canEdit={true} />
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}

function TabBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick}
            className={`relative px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
              active
                ? 'border-brand-600 text-brand-700 bg-brand-50/60 rounded-t-md font-semibold'
                : 'border-transparent text-slate-500 hover:text-slate-700 hover:bg-slate-50'
            }`}>
      {children}
    </button>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-start gap-4">
      <dt className="w-28 shrink-0 text-slate-500">{label}</dt>
      <dd className="flex-1">{value}</dd>
    </div>
  );
}
