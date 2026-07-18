import { useEffect, useMemo, useState } from 'react';
import { api } from '../../../../lib/api';
import { useAuth } from '../../../auth/AuthContext';
import type { SiteResponse } from '../../../../types/site';
import type { EquipmentResponse } from '../../../../types/equipment';
import type { PersonResponse } from '../../../../types/person';
import type { DocumentResponse } from '../../../../types/document';
import type { CompanyResponse } from '../../../../types/auth';
import { buildDefaultValues } from '../../../../lib/worksheet/schema';
import { extractOperatorLicense, type OperatorLicenseInfo } from '../../../../lib/worksheet/operatorLicense';
import { buildAttachmentOrder } from '../../../../lib/worksheet/attachmentOrder';
import { EMPTY_ROLE_ASSIGN, REQUIRED_ROLES, type RoleAssign, type RoleKey } from '../types';

interface UseWorkPlanCreateState {
  loading: boolean;
  loadError: string;

  sites: SiteResponse[];                  // 전체 사이트 (BP 필터 적용 후 표시는 sitesForBp)
  sitesForBp: SiteResponse[];             // 현재 선택된 BP 의 사이트만 (ADMIN 분기용)
  siteId: number | null;
  setSiteId: (v: number | null) => void;
  selectedSite: SiteResponse | null;

  // ADMIN 이 BP 를 먼저 고름. BP 로그인은 자기 회사 자동 고정.
  bpCompanies: CompanyResponse[];
  bpCompanyId: number | null;
  setBpCompanyId: (v: number | null) => void;
  isAdminMode: boolean;

  // 장비공급사 + 장비
  equipmentSupplierId: number | null;
  setEquipmentSupplierId: (v: number | null) => void;
  equipmentList: EquipmentResponse[];
  loadingEquipmentSupplier: boolean;
  equipmentId: number | null;
  setEquipmentId: (v: number | null) => void;
  selectedEquipment: EquipmentResponse | null;
  /** V36+: BP 가 견적으로 연동한 장비/인력 공급사. WP dropdown 데이터 소스. */
  connectedEquipmentSuppliers: CompanyResponse[];
  connectedManpowerSuppliers: CompanyResponse[];
  connectedEquipmentIds: number[];
  connectedPersonIds: number[];
  /** 전체 보기 토글 — ON 시 견적 연동 무시 + 전체 공급사 표시. */
  bypassConnection: boolean;
  setBypassConnection: (v: boolean) => void;
  allEquipmentSuppliers: CompanyResponse[];
  allManpowerSuppliers: CompanyResponse[];

  // 장비공급사 인력 (조종원만)
  equipPersons: PersonResponse[];

  // 인력공급사 + 인력
  manpowerSupplierId: number | null;
  setManpowerSupplierId: (v: number | null) => void;
  manpowerPersons: PersonResponse[];
  loadingManpowerSupplier: boolean;

  // 역할 배정
  roleAssign: RoleAssign;
  toggleAssign: (role: RoleKey, personId: number) => void;
  removeAssign: (role: RoleKey, personId: number) => void;

  // 첨부 문서 (장비/인원별)
  equipDocs: DocumentResponse[];
  personDocs: Record<number, DocumentResponse[]>;
  selectedEquipDocIds: Set<number>;
  setSelectedEquipDocIds: (s: Set<number>) => void;
  selectedPersonDocIds: Set<number>;
  setSelectedPersonDocIds: (s: Set<number>) => void;

  // 폼 메타
  workDate: string;          // 작업 시작일 (work_plans.work_date 와 매핑)
  setWorkDate: (v: string) => void;
  workEndDate: string;       // 작업 종료일 (form_values.workPeriodEnd 로 저장)
  setWorkEndDate: (v: string) => void;
  startTime: string;
  setStartTime: (v: string) => void;
  endTime: string;
  setEndTime: (v: string) => void;
  title: string;
  setTitle: (v: string) => void;
  workLocation: string;
  setWorkLocation: (v: string) => void;
  description: string;
  setDescription: (v: string) => void;

  // 이미지 첨부의 사용자 정렬 순서. 여기 없는 doc id 는 자연 순서로 뒤에 붙는다.
  attachmentOrder: number[];
  moveAttachment: (docId: number, dir: -1 | 1) => void;
  swapAttachment: (aId: number, bId: number) => void;

  // 조종원 주야 2인 슬롯의 면허·자격 자동 채움 정보 (원천 표시용 — Step2 뱃지).
  operatorLicenseInfos: OperatorLicenseInfo[];

  // S-9-B: 워크시트 132 필드 + 작업배치도 키
  values: Record<string, any>;
  setValues: (v: Record<string, any>) => void;
  setValue: (key: string, val: any) => void;
  workSiteDiagramKey: string;
  setWorkSiteDiagramKey: (v: string) => void;
}

export function useWorkPlanCreate(): UseWorkPlanCreateState {
  const { user } = useAuth();
  const isAdminMode = user?.role === 'ADMIN';
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [siteId, setSiteId] = useState<number | null>(null);

  // ADMIN: BP 회사 목록 / BP: 자기 회사 자동 사용.
  const [bpCompanies, setBpCompanies] = useState<CompanyResponse[]>([]);
  const [bpCompanyId, setBpCompanyId] = useState<number | null>(
    isAdminMode ? null : (user?.company_id ?? null)
  );
  // useAuth 가 비동기로 user 를 가져오는 케이스: 첫 mount 시 user 가 null 이면 bpCompanyId 도 null 로 굳어 dropdown 비어 보임.
  // user 가 늦게 들어오면 BP 로그인의 회사 id 를 채워준다.
  useEffect(() => {
    if (isAdminMode) return;
    if (user?.company_id && bpCompanyId !== user.company_id) {
      setBpCompanyId(user.company_id);
    }
  }, [user?.company_id, isAdminMode, bpCompanyId]);

  const [equipmentSupplierId, setEquipmentSupplierIdState] = useState<number | null>(null);
  const [equipmentList, setEquipmentList] = useState<EquipmentResponse[]>([]);
  const [loadingEquipmentSupplier, setLoadingEquipmentSupplier] = useState(false);
  const [equipmentId, setEquipmentId] = useState<number | null>(null);
  /** 현재 BP 가 견적/사인으로 연동된 공급사. WP 작성 시 dropdown 데이터 소스. */
  const [connectedEquipmentSuppliers, setConnectedEquipmentSuppliers] = useState<CompanyResponse[]>([]);
  const [connectedManpowerSuppliers, setConnectedManpowerSuppliers] = useState<CompanyResponse[]>([]);
  /** 선택된 장비공급사로부터 BP 가 연동한 자원 id 집합 (장비/인원). 비어있으면 필터 안 함. */
  const [connectedEquipmentIds, setConnectedEquipmentIds] = useState<number[]>([]);
  const [connectedPersonIds, setConnectedPersonIds] = useState<number[]>([]);
  /** 전체 보기 토글 ON — 견적 연동 무관하게 모든 공급사 + 자원 표시. 기본 ON. */
  const [bypassConnection, setBypassConnection] = useState(true);
  const [allEquipmentSuppliers, setAllEquipmentSuppliers] = useState<CompanyResponse[]>([]);
  const [allManpowerSuppliers, setAllManpowerSuppliers] = useState<CompanyResponse[]>([]);

  useEffect(() => {
    if (!bypassConnection) return;
    if (allEquipmentSuppliers.length === 0) {
      api.get<CompanyResponse[]>('/api/companies/suppliers', { params: { type: 'EQUIPMENT' } })
        .then((r) => setAllEquipmentSuppliers(r.data))
        .catch(() => setAllEquipmentSuppliers([]));
    }
    if (allManpowerSuppliers.length === 0) {
      api.get<CompanyResponse[]>('/api/companies/suppliers', { params: { type: 'MANPOWER' } })
        .then((r) => setAllManpowerSuppliers(r.data))
        .catch(() => setAllManpowerSuppliers([]));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bypassConnection]);

  useEffect(() => {
    if (!bpCompanyId) {
      setConnectedEquipmentSuppliers([]);
      setConnectedManpowerSuppliers([]);
      return;
    }
    api.get<CompanyResponse[]>('/api/companies/connected-suppliers', { params: { type: 'EQUIPMENT' } })
      .then((r) => setConnectedEquipmentSuppliers(r.data))
      .catch(() => setConnectedEquipmentSuppliers([]));
    api.get<CompanyResponse[]>('/api/companies/connected-suppliers', { params: { type: 'MANPOWER' } })
      .then((r) => setConnectedManpowerSuppliers(r.data))
      .catch(() => setConnectedManpowerSuppliers([]));
  }, [bpCompanyId]);


  const [equipPersons, setEquipPersons] = useState<PersonResponse[]>([]);

  const [manpowerSupplierId, setManpowerSupplierIdState] = useState<number | null>(null);
  const [manpowerPersons, setManpowerPersons] = useState<PersonResponse[]>([]);
  const [loadingManpowerSupplier, setLoadingManpowerSupplier] = useState(false);

  // 선택된 공급사 → 그 공급사로부터 BP 가 연동한 자원 id 집합 fetch
  useEffect(() => {
    const supId = equipmentSupplierId ?? manpowerSupplierId;
    if (!supId) {
      setConnectedEquipmentIds([]);
      setConnectedPersonIds([]);
      return;
    }
    api.get<{ equipmentIds: number[]; personIds: number[] }>(
      '/api/companies/connected-resources',
      { params: { supplierId: supId } }
    ).then((r) => {
      setConnectedEquipmentIds(r.data.equipmentIds ?? []);
      setConnectedPersonIds(r.data.personIds ?? []);
    }).catch(() => {
      setConnectedEquipmentIds([]);
      setConnectedPersonIds([]);
    });
  }, [equipmentSupplierId, manpowerSupplierId]);

  const [roleAssign, setRoleAssign] = useState<RoleAssign>(EMPTY_ROLE_ASSIGN);

  const [equipDocs, setEquipDocs] = useState<DocumentResponse[]>([]);
  const [personDocs, setPersonDocs] = useState<Record<number, DocumentResponse[]>>({});
  const [selectedEquipDocIds, setSelectedEquipDocIds] = useState<Set<number>>(new Set());
  const [selectedPersonDocIds, setSelectedPersonDocIds] = useState<Set<number>>(new Set());

  const [workDate, setWorkDate] = useState<string>(new Date().toISOString().slice(0, 10));
  const [workEndDate, setWorkEndDate] = useState<string>(new Date().toISOString().slice(0, 10));
  const [startTime, setStartTime] = useState('08:00');
  const [endTime, setEndTime] = useState('17:00');
  const [title, setTitle] = useState('');
  const [workLocation, setWorkLocation] = useState('');
  const [description, setDescription] = useState('');
  const [attachmentOrder, setAttachmentOrder] = useState<number[]>([]);
  // 첨부 순서 자동 조립 ON — 사용자가 수동 재정렬하면 OFF 되어 자동 재계산을 멈춘다.
  const [autoAttachmentOrder, setAutoAttachmentOrder] = useState(true);
  const [operatorLicenseInfos, setOperatorLicenseInfos] = useState<OperatorLicenseInfo[]>([]);

  const moveAttachment = (docId: number, dir: -1 | 1) => {
    setAutoAttachmentOrder(false);
    setAttachmentOrder((prev) => {
      const idx = prev.indexOf(docId);
      const arr = idx === -1 ? [...prev, docId] : prev.slice();
      const i = arr.indexOf(docId);
      const j = i + dir;
      if (j < 0 || j >= arr.length) return prev;
      [arr[i], arr[j]] = [arr[j], arr[i]];
      return arr;
    });
  };

  /** 같은 그룹 내에서 두 첨부 위치를 교환. 둘 다 attachmentOrder에 없으면 추가 후 교환. */
  const swapAttachment = (aId: number, bId: number) => {
    setAutoAttachmentOrder(false);
    setAttachmentOrder((prev) => {
      const arr = prev.slice();
      if (!arr.includes(aId)) arr.push(aId);
      if (!arr.includes(bId)) arr.push(bId);
      const i = arr.indexOf(aId);
      const j = arr.indexOf(bId);
      [arr[i], arr[j]] = [arr[j], arr[i]];
      return arr;
    });
  };

  // S-9-B: 워크시트 132 필드 + workSiteDiagramKey
  const [values, setValues] = useState<Record<string, any>>(() => buildDefaultValues());
  const [workSiteDiagramKey, setWorkSiteDiagramKey] = useState<string>('');
  const setValue = (key: string, val: any) => setValues((v) => ({ ...v, [key]: val }));

  // 사이트 목록 + ADMIN 의 경우 BP 회사 목록 로드
  useEffect(() => {
    (async () => {
      try {
        const sitesRes = await api.get<SiteResponse[]>('/api/sites');
        setSites(sitesRes.data);
        if (isAdminMode) {
          const companiesRes = await api.get<CompanyResponse[]>('/api/companies');
          setBpCompanies(companiesRes.data.filter((c) => c.type === 'BP'));
        }
      } catch (e: any) {
        setLoadError(e?.response?.data?.message || '현장 목록 로드 실패');
      } finally {
        setLoading(false);
      }
    })();
  }, [isAdminMode]);

  // 현재 선택된 BP 의 사이트만 노출. BP 로그인은 자기 회사 id 자동 적용.
  const sitesForBp = useMemo(
    () => (bpCompanyId ? sites.filter((s) => s.bp_company_id === bpCompanyId) : []),
    [sites, bpCompanyId]
  );

  // BP 변경 시 사이트 선택 초기화 (다른 BP 의 사이트가 stale 상태로 남지 않게)
  useEffect(() => {
    if (siteId && !sitesForBp.some((s) => s.id === siteId)) setSiteId(null);
  }, [bpCompanyId, sitesForBp, siteId]);

  const selectedSite = useMemo(
    () => sites.find((s) => s.id === siteId) ?? null,
    [sites, siteId]
  );

  // 사이트 상세 (참여 공급사 포함) 로드
  useEffect(() => {
    if (!siteId) return;
    (async () => {
      try {
        const res = await api.get<SiteResponse>(`/api/sites/${siteId}`);
        setSites((prev) => prev.map((s) => (s.id === siteId ? res.data : s)));
      } catch {
        /* 무시 — list 응답 사용 */
      }
    })();
  }, [siteId]);

  // 현장은 옵션 (나중에 자리 나면 정하는 흐름). 변경 시 자원/조종원 초기화하지 않는다.

  const setEquipmentSupplierId = (v: number | null) => {
    setEquipmentSupplierIdState(v);
    setEquipmentId(null);
    setEquipmentList([]);
    setEquipPersons([]);
    setRoleAssign((prev) => ({ ...prev, operator: [] }));
  };

  const setManpowerSupplierId = (v: number | null) => {
    setManpowerSupplierIdState(v);
    setManpowerPersons([]);
    setRoleAssign((prev) => ({
      ...prev,
      supervisor: [],
      signalman: [],
      firewatch: [],
      signaler: [],
    }));
  };

  // V36: 장비 선택 시 기본 조종원 (default operators) 우선순위대로 자동 배정.
  // 이미 사용자가 수동으로 선택한 조종원이 있으면 덮어쓰지 않음.
  useEffect(() => {
    if (!equipmentId) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await api.get<Array<{ id: number; person_id: number; priority: number }>>(
          `/api/equipment/${equipmentId}/default-operators`
        );
        if (cancelled) return;
        const personIds = res.data.map((d) => d.person_id);
        if (personIds.length === 0) return;
        setRoleAssign((prev) => {
          if (prev.operator.length > 0) return prev;
          return { ...prev, operator: personIds };
        });
      } catch {
        // ignore
      }
    })();
    return () => { cancelled = true; };
  }, [equipmentId]);

  // 장비공급사 선택 → 그 공급사의 장비 + 조종원 후보 로드
  useEffect(() => {
    if (!equipmentSupplierId) return;
    let cancelled = false;
    setLoadingEquipmentSupplier(true);
    (async () => {
      try {
        const [eqRes, psRes] = await Promise.all([
          api.get<EquipmentResponse[]>('/api/equipment', {
            params: { supplierId: equipmentSupplierId },
          }),
          api.get<{ content: PersonResponse[] }>('/api/persons', {
            params: { supplierId: equipmentSupplierId, role: 'OPERATOR', size: 200 },
          }),
        ]);
        if (cancelled) return;
        setEquipmentList(eqRes.data);
        setEquipPersons(psRes.data.content ?? []);
      } catch (e: any) {
        if (!cancelled) {
          setLoadError(e?.response?.data?.message || '장비공급사 데이터 로드 실패');
        }
      } finally {
        if (!cancelled) setLoadingEquipmentSupplier(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [equipmentSupplierId]);

  // 인력공급사 선택 → 비조종원 역할 후보 로드 (역할 다중 매핑이므로 한번에)
  useEffect(() => {
    if (!manpowerSupplierId) return;
    let cancelled = false;
    setLoadingManpowerSupplier(true);
    (async () => {
      try {
        const res = await api.get<{ content: PersonResponse[] }>('/api/persons', {
          params: { supplierId: manpowerSupplierId, size: 500 },
        });
        if (cancelled) return;
        const all = res.data.content ?? [];
        // 조종원 외 역할만
        setManpowerPersons(all.filter((p) => !p.roles.includes('OPERATOR')));
      } catch (e: any) {
        if (!cancelled) {
          setLoadError(e?.response?.data?.message || '인력공급사 데이터 로드 실패');
        }
      } finally {
        if (!cancelled) setLoadingManpowerSupplier(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [manpowerSupplierId]);

  // 장비 선택 시 그 장비의 서류 로드 + 기본 전체 선택 + 워크시트 자동 채움
  useEffect(() => {
    if (!equipmentId) {
      setEquipDocs([]);
      setSelectedEquipDocIds(new Set());
      return;
    }
    (async () => {
      try {
        const res = await api.get<DocumentResponse[]>('/api/documents', {
          params: { ownerType: 'EQUIPMENT', ownerId: equipmentId },
        });
        setEquipDocs(res.data);
        setSelectedEquipDocIds(new Set(res.data.map((d) => d.id)));
      } catch {
        setEquipDocs([]);
        setSelectedEquipDocIds(new Set());
      }
    })();
    // 워크시트 schema 의 equipment 관련 필드 자동 채움
    const eq = equipmentList.find((e) => e.id === equipmentId);
    if (eq) {
      setValues((v) => ({
        ...v,
        equipmentName: eq.model || '',
        equipmentModel: eq.model || '',
        vehicleNo: eq.vehicle_no || '',
        equipmentSerialNo: eq.serial_number || '',
        manufacturer: eq.manufacturer || '',
        manufactureYear: eq.year ? String(eq.year) : '',
      }));
    }
  }, [equipmentId, equipmentList]);

  // 사이트 선택 시 워크시트 schema 의 사이트/업체 필드 자동 채움
  useEffect(() => {
    if (!selectedSite) return;
    setValues((v) => ({
      ...v,
      siteName: selectedSite.name,
      submitCompany: selectedSite.bp_company_name || v.submitCompany,
    }));
  }, [selectedSite]);

  // 폼의 작업 시작/종료일 ↔ 워크시트 workPeriodStart/End 동기화 (Word 양식 작업기간 그대로 채움).
  useEffect(() => {
    setValues((v) => ({
      ...v,
      workPeriodStart: workDate,
      workPeriodEnd: workEndDate,
    }));
  }, [workDate, workEndDate]);

  // 역할 인력 선택 시 워크시트 schema 자동 채움.
  // 조종원 = 주야 2인 슬롯(1·2)에 각각 면허번호(OCR 검증값)·자격종류·취득일·교육이수일 자동 배선.
  useEffect(() => {
    const ops = roleAssign.operator
      .map((id) => equipPersons.find((p) => p.id === id))
      .filter(Boolean) as PersonResponse[];
    const sups = roleAssign.supervisor
      .map((id) => manpowerPersons.find((p) => p.id === id))
      .filter(Boolean) as PersonResponse[];
    const join = (arr: string[]) => arr.filter(Boolean).join(' / ');

    // 조종원 앞 2인 = 주간(슬롯1)·야간(슬롯2). 각자 서류 extracted_data 에서 면허·자격 파싱(폴백=qualification).
    const infos = ops.slice(0, 2).map((p) => extractOperatorLicense(p, personDocs[p.id] ?? []));
    setOperatorLicenseInfos(infos);
    const s1 = infos[0];
    const s2 = infos[1];

    setValues((v) => ({
      ...v,
      // 주간 (슬롯1)
      operatorName: s1?.name ?? '',
      operatorLicense: s1?.licenseType ?? '',
      operatorLicenseNo: s1?.licenseNo ?? '',
      operatorLicenseDate: s1?.licenseDate ?? '',
      operatorEduDate: s1?.eduDate ?? '',
      // 야간 (슬롯2 · 선택)
      operatorName2: s2?.name ?? '',
      operatorLicense2: s2?.licenseType ?? '',
      operatorLicenseNo2: s2?.licenseNo ?? '',
      operatorLicenseDate2: s2?.licenseDate ?? '',
      operatorEduDate2: s2?.eduDate ?? '',
      // 작업지휘자
      supervisor_name: join(sups.map((p) => p.name)),
      supervisor_position: sups.length > 0 ? '작업지휘자' : v.supervisor_position || '',
      // 소속 = 작업지휘자의 공급사명. 기존 저장본(로드된 값)은 덮지 않음(하위호환).
      supervisor_company: v.supervisor_company?.trim()
        ? v.supervisor_company
        : join(sups.map((p) => p.supplier_name ?? '')),
    }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roleAssign, equipPersons, manpowerPersons, personDocs]);

  // 역할 배정 변경 시 — 배정된 인원의 서류 로드 + 기본 전체 선택
  useEffect(() => {
    const assignedIds = new Set<number>();
    (Object.keys(roleAssign) as RoleKey[]).forEach((k) => {
      roleAssign[k].forEach((id) => assignedIds.add(id));
    });
    const toFetch = Array.from(assignedIds).filter((id) => !(id in personDocs));
    if (toFetch.length === 0) return;
    (async () => {
      const newMap = { ...personDocs };
      const newIds = new Set(selectedPersonDocIds);
      await Promise.all(
        toFetch.map(async (pid) => {
          try {
            const res = await api.get<DocumentResponse[]>('/api/documents', {
              params: { ownerType: 'PERSON', ownerId: pid },
            });
            newMap[pid] = res.data;
            res.data.forEach((d) => newIds.add(d.id));
          } catch {
            newMap[pid] = [];
          }
        })
      );
      setPersonDocs(newMap);
      setSelectedPersonDocIds(newIds);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roleAssign]);

  // 첨부 자동 조립 — 선택 서류가 바뀌면 표지 체크리스트 순서(§3.6.2)로 attachmentOrder 자동 산출.
  // 사용자가 수동 재정렬(move/swap)하면 autoAttachmentOrder=false 로 멈춘다(기존 수동 UI 유지).
  useEffect(() => {
    if (!autoAttachmentOrder) return;
    const orderedPersonIds: number[] = [];
    (['operator', 'supervisor', 'signalman', 'firewatch', 'signaler'] as RoleKey[]).forEach((k) => {
      roleAssign[k].forEach((id) => { if (!orderedPersonIds.includes(id)) orderedPersonIds.push(id); });
    });
    const order = buildAttachmentOrder({
      equipDocs,
      selectedEquipDocIds,
      orderedPersonIds,
      personDocs,
      selectedPersonDocIds,
    });
    setAttachmentOrder((prev) =>
      prev.length === order.length && prev.every((x, i) => x === order[i]) ? prev : order
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoAttachmentOrder, equipDocs, selectedEquipDocIds, roleAssign, personDocs, selectedPersonDocIds]);

  const selectedEquipment = useMemo(
    () => equipmentList.find((e) => e.id === equipmentId) ?? null,
    [equipmentList, equipmentId]
  );

  const toggleAssign = (role: RoleKey, personId: number) => {
    setRoleAssign((prev) => {
      const cur = prev[role];
      return {
        ...prev,
        [role]: cur.includes(personId) ? cur.filter((id) => id !== personId) : [...cur, personId],
      };
    });
  };

  const removeAssign = (role: RoleKey, personId: number) => {
    setRoleAssign((prev) => ({
      ...prev,
      [role]: prev[role].filter((id) => id !== personId),
    }));
  };

  return {
    loading,
    loadError,
    sites,
    siteId,
    setSiteId,
    selectedSite,
    equipmentSupplierId,
    setEquipmentSupplierId,
    equipmentList,
    loadingEquipmentSupplier,
    equipmentId,
    setEquipmentId,
    selectedEquipment,
    connectedEquipmentSuppliers,
    connectedManpowerSuppliers,
    connectedEquipmentIds,
    connectedPersonIds,
    bypassConnection,
    setBypassConnection,
    allEquipmentSuppliers,
    allManpowerSuppliers,
    equipPersons,
    manpowerSupplierId,
    setManpowerSupplierId,
    manpowerPersons,
    loadingManpowerSupplier,
    roleAssign,
    toggleAssign,
    removeAssign,
    equipDocs,
    personDocs,
    selectedEquipDocIds,
    setSelectedEquipDocIds,
    selectedPersonDocIds,
    setSelectedPersonDocIds,
    sitesForBp,
    bpCompanies,
    bpCompanyId,
    setBpCompanyId,
    isAdminMode,
    workDate,
    setWorkDate,
    workEndDate,
    setWorkEndDate,
    startTime,
    setStartTime,
    endTime,
    setEndTime,
    title,
    setTitle,
    attachmentOrder,
    moveAttachment,
    swapAttachment,
    operatorLicenseInfos,
    workLocation,
    setWorkLocation,
    description,
    setDescription,
    values,
    setValues,
    setValue,
    workSiteDiagramKey,
    setWorkSiteDiagramKey,
  };
}

export type WorkPlanCreateState = ReturnType<typeof useWorkPlanCreate>;

/** 진행률 계산 (필수 항목 기준). */
export function computeProgress(state: WorkPlanCreateState) {
  // 현장은 옵션. 표시용 체크는 site OR workLocation 자유텍스트로 통과.
  const siteOk = !!state.selectedSite || !!state.workLocation.trim();
  const equipOk = !!state.selectedEquipment;
  const titleOk = !!state.title.trim();
  const requiredRoles = REQUIRED_ROLES.filter((r) => r.required);
  const filledRequired = requiredRoles.filter((r) => state.roleAssign[r.key].length > 0).length;
  const total = 3 + requiredRoles.length;
  const filled =
    (siteOk ? 1 : 0) + (equipOk ? 1 : 0) + (titleOk ? 1 : 0) + filledRequired;
  return {
    siteOk,
    equipOk,
    titleOk,
    filledRequired,
    requiredRolesTotal: requiredRoles.length,
    overall: Math.round((filled / total) * 100),
    missing: [
      !siteOk && '현장 또는 작업 위치',
      !titleOk && '제목',
      !equipOk && '장비',
      ...requiredRoles
        .filter((r) => state.roleAssign[r.key].length === 0)
        .map((r) => r.label),
    ].filter(Boolean) as string[],
  };
}
