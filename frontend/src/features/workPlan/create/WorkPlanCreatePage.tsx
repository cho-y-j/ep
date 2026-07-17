import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../../../lib/api';
import type { WorkPlanResponse } from '../../../types/workPlan';
import type { QuotationRequestResponse, QuotationBundleResponse } from '../../../types/quotation';
import { equipmentCategoryLabel } from '../../../types/equipment';
import { PERSON_ROLE_LABEL } from '../../../types/person';
import { daysUntilExpiry, type DocumentResponse } from '../../../types/document';
import { renderWorksheet, type Attachment } from '../../../lib/worksheet/engine';
import { computeProgress, useWorkPlanCreate, type WorkPlanCreateState } from './hooks/useWorkPlanCreate';
import { useEditorSync } from './hooks/useEditorSync';
import { REQUIRED_ROLES, type DocPreviewTarget, type RoleKey } from './types';
import { DocLightbox } from './components/DocLightbox';
import { WorksheetSections } from './components/WorksheetSections';
import { PdfMailDialog } from './components/PdfMailDialog';
import MissingDocsDialog from './components/MissingDocsDialog';
import { Step1SiteAndBp } from './sections/Step1SiteAndBp';
import { Step2Equipment } from './sections/Step2Equipment';
import { Step3Manpower } from './sections/Step3Manpower';
import { SignaturePanel, type SignaturePanelHandle } from './components/SignaturePanel';
import { CollapsibleSection } from '../../../components/ui';
import SubmitChecklist from './components/SubmitChecklist';

/**
 * 작업계획서 생성 페이지 (skep 원본 WorkPlanCreate 의 풀 이식).
 *
 * 구조:
 * - Step 1 (사이트/BP/메타) — Step 2 (장비공급사+장비+조종원) — Step 3 (인력공급사+4역할)
 * - 워크시트 132 필드 (SCHEMA · p1~p5 · 20섹션)
 * - 우측 제원표 사이드 패널 (lg 이상)
 * - 액션바: DOCX (클라이언트 docxtemplater) · PDF (서버 LibreOffice) · 편집기 새 탭 · 메일 발송
 */
export default function WorkPlanCreatePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const state = useWorkPlanCreate();
  const editor = useEditorSync();
  const progress = useMemo(() => computeProgress(state), [state]);
  const [lightbox, setLightbox] = useState<DocPreviewTarget | null>(null);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [saveMsg, setSaveMsg] = useState('');
  const [mailOpen, setMailOpen] = useState(false);
  const [missingDocsOpen, setMissingDocsOpen] = useState(false);
  /** 임시저장된 작업계획서 id — SignaturePanel 활성화에 사용. 저장 후에도 페이지 유지. */
  const [savedWorkPlanId, setSavedWorkPlanId] = useState<number | null>(null);
  const [allSigned, setAllSigned] = useState(false);
  /** 사인 한 명이라도 SIGNED 면 워크시트 변경 감지 시 무효화 다이얼로그 표시. */
  const [hasAnySigned, setHasAnySigned] = useState(false);
  /** Signature 패널의 SIGNED 카운트 — 요약/체크리스트 표시용. */
  const [signedCount, setSignedCount] = useState(0);
  const [invalidateDialogOpen, setInvalidateDialogOpen] = useState(false);
  /** 첫 SIGNED 전이 시점의 폼 스냅샷. 이후 refresh 시 갱신 X — 변경 감지 무력화 방지. */
  const formSnapshotRef = useRef<string>('');
  /** SignaturePanel 이 fetch한 사인 데이터 (PNG 포함) 캐시 — buildDocxBlob 가 매번 재fetch 하지 않도록. */
  const signaturesCacheRef = useRef<Array<{ role: string; status: string; signature_png_base64?: string | null }>>([]);
  /** SignaturePanel imperative handle — sendAllPending() 호출용. */
  const sigPanelRef = useRef<SignaturePanelHandle>(null);
  /** Site-D: ?fromQuotation= 또는 ?fromQuotationBundle= 1회 prefill 가드. */
  const prefilledRef = useRef(false);
  const [prefillNote, setPrefillNote] = useState<string | null>(null);
  /** prefill 시 dispatched 자원 id를 보류 — equipmentList/equipPersons 로드되면 적용. */
  const [pendingEquipmentId, setPendingEquipmentId] = useState<number | null>(null);
  const [pendingOperatorIds, setPendingOperatorIds] = useState<number[]>([]);
  const [pendingManpowerByRole, setPendingManpowerByRole] = useState<Record<string, number[]>>({});

  useEffect(() => {
    if (state.loading || prefilledRef.current) return;
    const q = params.get('fromQuotation');
    const b = params.get('fromQuotationBundle');
    const siteIdParam = params.get('siteId');
    const titleParam = params.get('title');
    // 서류관리(현장)에서 진입 시 siteId/title 만 prefill
    if (!q && !b && (siteIdParam || titleParam)) {
      prefilledRef.current = true;
      if (siteIdParam) state.setSiteId(Number(siteIdParam));
      if (titleParam) state.setTitle(titleParam);
      setPrefillNote('서류관리에서 진입했습니다. 현장과 제목이 자동으로 채워졌습니다.');
      return;
    }
    if (!q && !b) return;
    prefilledRef.current = true;
    // 견적/번들 진입 시 "BP 거래 자원만" 필터 ON (사용자가 토글로 풀 수 있음).
    state.setBypassConnection(false);
    (async () => {
      try {
        if (q) {
          const res = await api.get<QuotationRequestResponse>(`/api/quotations/${q}`);
          applyQuotationPrefill([res.data]);
          await prefillSupplierFromQuotation(Number(q));
        } else if (b) {
          const res = await api.get<QuotationBundleResponse>(`/api/quotations/bundles/${b}`);
          applyQuotationPrefill(res.data.items ?? []);
          // bundle 의 첫 견적 기준으로 공급사 자동 설정
          const firstId = (res.data.items ?? [])[0]?.id;
          if (firstId) await prefillSupplierFromQuotation(firstId);
        }
      } catch (e) {
        console.warn('견적 prefill 실패', e);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.loading, params]);

  /** 발송된 차량/인원 또는 finalize 된 target/proposal 에서 공급사 + 자원 ID 추출 → 자동 설정. */
  const prefillSupplierFromQuotation = async (qId: number) => {
    try {
      const [dispRes, dispPRes, propRes, qrRes] = await Promise.all([
        api.get<Array<{ supplier_company_id: number; equipment_id: number }>>(`/api/quotations/${qId}/dispatched`).catch(() => ({ data: [] as any[] })),
        api.get<Array<{ supplier_company_id: number; person_id: number }>>(`/api/quotations/${qId}/dispatched-persons`).catch(() => ({ data: [] as any[] })),
        api.get<Array<{ supplier_company_id: number; equipment_id?: number | null; person_id?: number | null; status: string }>>(`/api/quotations/${qId}/proposals`).catch(() => ({ data: [] as any[] })),
        api.get<{ manpower_role?: string | null }>(`/api/quotations/${qId}`).catch(() => ({ data: {} as any })),
      ]);
      const dispList = dispRes.data ?? [];
      const dispPList = dispPRes.data ?? [];
      const proposalList = propRes.data ?? [];
      const eqSupplierId = dispList[0]?.supplier_company_id ?? null;
      const pSupplierId = dispPList[0]?.supplier_company_id ?? null;
      const finalProp = proposalList.find((p) => p.status === 'FINAL_ACCEPTED');
      if (eqSupplierId) state.setEquipmentSupplierId(eqSupplierId);
      else if (finalProp?.supplier_company_id) state.setEquipmentSupplierId(finalProp.supplier_company_id);
      if (pSupplierId) state.setManpowerSupplierId(pSupplierId);

      // 자원 자동 prefill — 첫 dispatched 장비 + dispatched 인원 모두
      const firstEqId = dispList[0]?.equipment_id ?? finalProp?.equipment_id ?? null;
      if (firstEqId) setPendingEquipmentId(firstEqId);
      const personIds = dispPList.map((d) => d.person_id).filter((x): x is number => typeof x === 'number');
      const role = (qrRes.data as any)?.manpower_role as string | undefined;
      if (personIds.length > 0) {
        if (role) {
          setPendingManpowerByRole({ [role.toLowerCase()]: personIds });
        } else {
          // 역할 정보가 없으면 일단 operator 로 시도 (장비 조종원 흐름)
          setPendingOperatorIds(personIds);
        }
      }
    } catch (e) {
      console.warn('공급사 prefill 실패', e);
    }
  };

  /** equipmentList 가 채워지면 pending equipment id 적용 (한번만). */
  useEffect(() => {
    if (pendingEquipmentId == null) return;
    if (state.equipmentList.some((e) => e.id === pendingEquipmentId)) {
      state.setEquipmentId(pendingEquipmentId);
      setPendingEquipmentId(null);
    }
  }, [state.equipmentList, pendingEquipmentId]);

  /** equipPersons 가 채워지면 pending operator ids 적용. */
  useEffect(() => {
    if (pendingOperatorIds.length === 0) return;
    const matched = pendingOperatorIds.filter((id) => state.equipPersons.some((p) => p.id === id));
    if (matched.length === 0) return;
    matched.forEach((pid) => {
      if (!state.roleAssign.operator.includes(pid)) state.toggleAssign('operator', pid);
    });
    setPendingOperatorIds([]);
  }, [state.equipPersons, pendingOperatorIds]);

  /** manpowerPersons 가 채워지면 역할별 pending 인원 적용. */
  useEffect(() => {
    const roles = Object.keys(pendingManpowerByRole);
    if (roles.length === 0) return;
    let applied = false;
    const next = { ...pendingManpowerByRole };
    for (const roleKey of roles) {
      const ids = pendingManpowerByRole[roleKey] ?? [];
      const matched = ids.filter((id) => state.manpowerPersons.some((p) => p.id === id));
      if (matched.length === 0) continue;
      matched.forEach((pid) => {
        if (!(state.roleAssign as any)[roleKey]?.includes(pid)) {
          state.toggleAssign(roleKey as any, pid);
        }
      });
      delete next[roleKey];
      applied = true;
    }
    if (applied) setPendingManpowerByRole(next);
  }, [state.manpowerPersons, pendingManpowerByRole]);

  const applyQuotationPrefill = (items: QuotationRequestResponse[]) => {
    if (items.length === 0) return;
    const head = items[0];
    state.setWorkDate(head.work_period_start);
    if (head.work_period_end) state.setWorkEndDate(head.work_period_end);
    const labels = items
      .map((it) => it.request_type === 'MANPOWER'
          ? (it.manpower_role ? PERSON_ROLE_LABEL[it.manpower_role] : '인력')
          : (it.equipment_category ? equipmentCategoryLabel(it.equipment_category) : '장비'))
      .join(' + ');
    state.setTitle(`[견적 #${head.id}] ${labels} ${head.work_period_start}`);
    if (head.notes) state.setDescription(head.notes);
    if (head.bp_company_id) state.setBpCompanyId(head.bp_company_id);
    setPrefillNote(`견적 #${head.id} 의 작업 기간·제목·메모를 자동으로 채웠습니다. 현장(Site)을 선택하고 자원을 추가하세요.`);
  };

  const assignedManpowerCount = useMemo(
    () => REQUIRED_ROLES.filter((r) => r.key !== 'operator').reduce((sum, r) => sum + state.roleAssign[r.key].length, 0),
    [state.roleAssign]
  );

  const allAssignedPersons = useMemo(() => {
    const ids = new Set<number>();
    REQUIRED_ROLES.forEach((r) => state.roleAssign[r.key].forEach((id) => ids.add(id)));
    const all = [...state.equipPersons, ...state.manpowerPersons];
    return Array.from(ids)
      .map((id) => all.find((p) => p.id === id))
      .filter(Boolean) as typeof all;
  }, [state.roleAssign, state.equipPersons, state.manpowerPersons]);

  /** 현재 폼+첨부 → DOCX Blob (클라이언트 docxtemplater 렌더링). */
  const buildDocxBlob = async (includeAttachments: boolean = true): Promise<{ blob: Blob; baseName: string }> => {
    const attachments: Attachment[] = [];
    if (includeAttachments) {
      // 장비 서류
      for (const d of state.equipDocs) {
        if (state.selectedEquipDocIds.has(d.id)) {
          attachments.push({
            storageKey: String(d.id),
            category: `장비 · ${d.document_type_name}`,
            originalName: d.file_name,
            mimeType: d.content_type,
          });
        }
      }
      // 역할별 인원 서류
      for (const r of REQUIRED_ROLES) {
        for (const personId of state.roleAssign[r.key]) {
          const docs = state.personDocs[personId] ?? [];
          const person = allAssignedPersons.find((p) => p.id === personId);
          for (const d of docs) {
            if (state.selectedPersonDocIds.has(d.id)) {
              attachments.push({
                storageKey: String(d.id),
                category: `${r.label} ${person?.name ?? ''} · ${d.document_type_name}`,
                originalName: d.file_name,
                mimeType: d.content_type,
              });
            }
          }
        }
      }
    }
    // 이미지 첨부는 사용자 정렬(state.attachmentOrder) 적용. 정렬에 없는 것은 자연 순서로 뒤에.
    const orderIdx = (id: number) => {
      const i = state.attachmentOrder.indexOf(id);
      return i === -1 ? Number.MAX_SAFE_INTEGER : i;
    };
    attachments.sort((a, b) => orderIdx(Number(a.storageKey)) - orderIdx(Number(b.storageKey)));

    // S-12: 사인 PNG — SignaturePanel 이 fetch 한 캐시 사용. buildDocxBlob 호출마다 재fetch 안 함.
    const signatures: import('../../../lib/worksheet/engine').SignatureImages = {};
    const roleMap: Record<string, keyof import('../../../lib/worksheet/engine').SignatureImages> = {
      AUTHOR: 'authorSign',
      SUPERVISOR: 'supervisorSign',
      CONFIRMER: 'confirmerSign',
      REVIEWER: 'reviewerSign',
      APPROVER: 'approverSign',
    };
    for (const s of signaturesCacheRef.current) {
      if (s.status !== 'SIGNED' || !s.signature_png_base64) continue;
      const k = roleMap[s.role];
      if (k) signatures[k] = s.signature_png_base64;
    }

    const blob = await renderWorksheet(state.values, state.workSiteDiagramKey, attachments, signatures);
    const baseName = `작업계획서_${state.values.vehicleNo || 'NEW'}`;
    return { blob, baseName };
  };

  /** DOCX 다운로드 (클라이언트 생성). */
  const downloadDocx = async () => {
    setGenerating(true);
    try {
      const { blob, baseName } = await buildDocxBlob(true);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${baseName}.docx`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e: any) {
      alert('DOCX 생성 실패: ' + (e?.message || e));
    } finally {
      setGenerating(false);
    }
  };

  /** PDF 다운로드 (서버 LibreOffice 변환). */
  const downloadPdf = async () => {
    setGenerating(true);
    try {
      const { blob, baseName } = await buildDocxBlob(true);
      const fd = new FormData();
      fd.append('file', blob, `${baseName}.docx`);
      fd.append('name', baseName);
      const res = await api.post('/api/worksheet/to-pdf', fd, {
        responseType: 'blob',
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const pdfBlob = res.data instanceof Blob ? res.data : new Blob([res.data as any], { type: 'application/pdf' });
      const url = URL.createObjectURL(pdfBlob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${baseName}.pdf`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e: any) {
      alert('PDF 다운로드 실패: ' + (e?.response?.data?.message || e?.message || e));
    } finally {
      setGenerating(false);
    }
  };

  /** OnlyOffice editor session 생성 — DOCX 업로드 후 sessionId + config 받아옴. */
  const createEditorSession = async (blob: Blob, baseName: string) => {
    const fd = new FormData();
    fd.append('file', blob, `${baseName}.docx`);
    fd.append('name', baseName);
    const res = await api.post<{ sessionId: string; fileName: string; config: any }>(
      '/api/worksheet/editor-session',
      fd,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return res.data;
  };

  /** 새 탭으로 OnlyOffice 열기. mode='view' = 읽기전용 미리보기, 'edit' = 편집 가능.
   *  열린 후엔 좌측 폼 변경을 BroadcastChannel 로 그 탭에 자동 반영. */
  const openEditor = async (mode: 'view' | 'edit' = 'edit') => {
    setGenerating(true);
    try {
      const { blob, baseName } = await buildDocxBlob(true);
      const s = await createEditorSession(blob, baseName);
      if (mode === 'view' && s.config?.editorConfig) {
        s.config.editorConfig.mode = 'view';
      }
      localStorage.setItem(`worksheet-editor-${s.sessionId}-config`, JSON.stringify(s.config));
      localStorage.setItem(`worksheet-editor-${s.sessionId}-fileName`, s.fileName);
      editor.start(s.sessionId);
      window.open(`/worksheet/edit/${s.sessionId}?ch=${s.sessionId}`, '_blank');
    } catch (e: any) {
      alert('편집기 열기 실패: ' + (e?.response?.data?.message || e?.message || e));
    } finally {
      setGenerating(false);
    }
  };

  // S-12: 사인 완료 후 워크시트가 변경됐는지 감지 — 변경 시 무효화 다이얼로그.
  useEffect(() => {
    if (!hasAnySigned || !savedWorkPlanId || !formSnapshotRef.current) return;
    const current = JSON.stringify({
      values: state.values,
      roleAssign: state.roleAssign,
      siteId: state.siteId,
      equipmentId: state.equipmentId,
    });
    if (current !== formSnapshotRef.current) {
      setInvalidateDialogOpen(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.values, state.roleAssign, state.siteId, state.equipmentId, hasAnySigned, savedWorkPlanId]);

  const handleInvalidate = async (confirm: boolean) => {
    setInvalidateDialogOpen(false);
    if (!savedWorkPlanId) return;
    if (confirm) {
      try {
        await api.post(`/api/work-plans/${savedWorkPlanId}/signatures/invalidate`);
        setSaveMsg('기존 사인이 모두 무효화되었습니다. 다시 사인 요청을 보내세요.');
        // 스냅샷 초기화 — 다음 사인 완료 시 다시 기록
        formSnapshotRef.current = '';
        setHasAnySigned(false);
        setAllSigned(false);
      } catch (e: any) {
        alert('무효화 실패: ' + (e?.response?.data?.error || e?.message || e));
      }
    } else {
      // 사용자가 유지 선택 — 다이얼로그 닫고 다음 변경까지 무시
      formSnapshotRef.current = JSON.stringify({
        values: state.values,
        roleAssign: state.roleAssign,
        siteId: state.siteId,
        equipmentId: state.equipmentId,
      });
    }
  };

  // 열린 새 탭 OnlyOffice 와 폼 변경 자동 동기화 (디바운스 1.5초). 열리지 않았으면 noop.
  useEffect(() => {
    if (!editor.active) return;
    const t = setTimeout(async () => {
      try {
        const { blob, baseName } = await buildDocxBlob(true);
        const s = await createEditorSession(blob, baseName);
        localStorage.setItem(`worksheet-editor-${s.sessionId}-config`, JSON.stringify(s.config));
        localStorage.setItem(`worksheet-editor-${s.sessionId}-fileName`, s.fileName);
        editor.sendReload(s.sessionId);
      } catch (e: any) {
        editor.fail(e?.response?.data?.message || e?.message || '오류');
      }
    }, 1500);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    state.values,
    state.equipmentId,
    state.roleAssign,
    state.workSiteDiagramKey,
    state.selectedEquipDocIds,
    state.selectedPersonDocIds,
    state.attachmentOrder,
    editor.active,
    // 메타 변경 (BP/현장/제목/일자/시간/위치/설명/공급사) 도 동기화 트리거.
    state.bpCompanyId,
    state.siteId,
    state.title,
    state.workDate,
    state.workEndDate,
    state.startTime,
    state.endTime,
    state.workLocation,
    state.description,
    state.equipmentSupplierId,
    state.manpowerSupplierId,
  ]);

  /** 작업계획서 새로 생성 또는 갱신 (DRAFT) — 셸 + 장비 + 역할별 인원 + formValues 일괄.
   *  저장 후 페이지 유지 (사인 패널 활성화 위해). 이미 savedWorkPlanId 가 있으면 그 행을 업데이트. */
  const save = async (): Promise<number | null> => {
    // 현장은 옵션. site 가 없으면 bpCompanyId 가 필수 — 견적 prefill / signup 통해 자동 주입됨.
    if (!state.title.trim() || !state.selectedEquipment) return null;
    if (!state.siteId && !state.bpCompanyId) return null;
    setSaving(true);
    setSaveMsg('');
    try {
      let planId = savedWorkPlanId;
      if (planId == null) {
        const fromQuotationParam = params.get('fromQuotation');
        const wpRes = await api.post<WorkPlanResponse>('/api/work-plans', {
          site_id: state.siteId || undefined,
          bp_company_id: state.siteId ? undefined : state.bpCompanyId,
          work_date: state.workDate,
          start_time: state.startTime || undefined,
          end_time: state.endTime || undefined,
          title: state.title.trim(),
          work_location: state.workLocation || undefined,
          description: state.description || undefined,
          from_quotation_request_id: fromQuotationParam ? Number(fromQuotationParam) : undefined,
        });
        planId = wpRes.data.id;
        setSavedWorkPlanId(planId);
      }

      // 자원 sync — 매 save 시 시도. ALREADY_ADDED 면 무시 (idempotent).
      if (state.selectedEquipment) {
        try {
          await api.post(`/api/work-plans/${planId}/equipment`, {
            equipment_id: state.selectedEquipment.id,
          });
        } catch (e: any) {
          if (e?.response?.data?.code !== 'ALREADY_ADDED') throw e;
        }
      }
      for (const r of REQUIRED_ROLES) {
        for (const personId of state.roleAssign[r.key]) {
          try {
            await api.post(`/api/work-plans/${planId}/persons`, {
              person_id: personId,
              equipment_id: r.key === 'operator' && state.selectedEquipment ? state.selectedEquipment.id : undefined,
              role: r.serverRole,
            });
          } catch (e: any) {
            if (e?.response?.data?.code !== 'ALREADY_ADDED') throw e;
          }
        }
      }

      // 워크시트 폼 + supplier context 저장 (재저장 시 폼 갱신만)
      await api.patch(`/api/work-plans/${planId}/form-values`, {
        form_values: {
          values: state.values,
          roleAssign: state.roleAssign,
          workSiteDiagramKey: state.workSiteDiagramKey,
          equipDocIds: Array.from(state.selectedEquipDocIds),
          personDocIds: Array.from(state.selectedPersonDocIds),
        },
        equipment_supplier_company_id: state.equipmentSupplierId,
        manpower_supplier_company_id: state.manpowerSupplierId,
        current_equipment_id: state.selectedEquipment.id,
      });

      setSaveMsg(`✓ 임시저장 완료 (#${planId}) — 사인 요청 후 제출하세요`);
      return planId;
    } catch (e: any) {
      setSaveMsg('✕ ' + (e?.response?.data?.message || e?.message || '저장 실패'));
      return null;
    } finally {
      setSaving(false);
    }
  };

  /** 제출하기 — 한 번에 처리:
   *  1) DRAFT 저장 (id 부여)
   *  2) 입력된 4명 이메일에 사인 요청 일괄 발송
   *  3) 5/5 SIGNED 이면 SUBMITTED 전이, 아니면 detail 페이지로 이동 (사인 상태 확인)
   */
  const submitWorkPlan = async () => {
    setSubmitting(true);
    try {
      let planId = savedWorkPlanId;
      if (planId == null) {
        planId = await save();
        if (planId == null) return;
      }

      // 입력된 4명 이메일 → 사인 요청 메일 일괄 발송 (방금 부여된 planId 명시적으로 전달)
      let sent = 0;
      let mailError: string | null = null;
      if (!sigPanelRef.current) {
        console.error('[submit] SignaturePanel ref is null — 4번 섹션이 mount 안 됨');
      }
      try {
        const result = await sigPanelRef.current?.sendAllPending(planId);
        sent = result?.sent ?? 0;
      } catch (e: any) {
        mailError = e?.response?.data?.message || e?.message || '메일 발송 중 오류';
        console.error('[submit] sendAllPending failed', e);
      }
      if (mailError) {
        alert('사인 요청 메일 발송에 실패했습니다: ' + mailError + '\n작업계획서는 저장됐습니다. 상세 페이지에서 [다같이 재발송] 으로 재시도하세요.');
      } else if (sent === 0) {
        // 4명 이메일 미입력 — 사용자에게 안내 (필수는 아님, 상세에서 입력 가능)
        const proceed = confirm('사인 요청 이메일이 입력되지 않았습니다.\n\n[확인]을 누르면 작업계획서는 저장되고 상세 페이지로 이동합니다.\n[취소]를 누르면 이 화면에 머물러 4번 섹션에서 이메일을 입력할 수 있습니다.');
        if (!proceed) {
          setSubmitting(false);
          return;
        }
      }

      // 5/5 SIGNED 이면 백엔드 /submit 호출 (SUBMITTED 전이)
      if (allSigned) {
        await api.post(`/api/work-plans/${planId}/submit`);
        setSaveMsg(`✓ 제출 완료 (#${planId})`);
        setTimeout(() => navigate(`/work-plans/${planId}`), 600);
        return;
      }

      // 사인 미완료 — detail 페이지로 이동 (거기서 사인 상태 추적)
      setSaveMsg(
        sent > 0
          ? `✓ 임시저장 + 사인 요청 ${sent}명 발송. 상세 페이지에서 진행 상태를 확인하세요.`
          : `✓ 임시저장 완료 (#${planId}). 상세 페이지에서 사인을 진행하세요.`
      );
      setTimeout(() => navigate(`/work-plans/${planId}`), 800);
      return;
    } catch (e: any) {
      const code = e?.response?.data?.code;
      const msg = e?.response?.data?.message || e?.response?.data?.error || e?.message || '제출 실패';
      setSaveMsg('✕ ' + msg);
      if (code === 'DOCUMENTS_BLOCKED_AT_SUBMIT') {
        setMissingDocsOpen(true);
        return;
      }
      alert('제출 실패: ' + msg);
    } finally {
      setSubmitting(false);
    }
  };

  if (state.loading) return <div className="p-8 text-center text-slate-500">로딩 중...</div>;

  const previewBusy = generating;

  return (
    <div className="min-h-screen bg-slate-50 pb-24 text-[13px]">
      {/* 페이지 상단 헤더 — 제목 + DRAFT chip + 핵심 액션 4개 */}
      <header className="sticky top-0 z-30 bg-white border-b border-slate-200">
        <div className="mx-auto flex max-w-[1380px] items-center justify-between gap-4 px-4 py-3">
          <div className="flex items-center gap-3 min-w-0">
            <div>
              <div className="text-xs text-blue-600">작업 관리 › 작업계획서 › 신규 작성</div>
              <h1 className="mt-0.5 text-[19px] font-bold text-slate-950">작업계획서 신규 작성</h1>
            </div>
            <span className="inline-flex rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">DRAFT</span>
            {editor.syncMsg && (
              <span className="text-xs text-slate-500">{editor.syncMsg}</span>
            )}
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              onClick={() => void openEditor('view')}
              disabled={previewBusy}
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50"
              title="새 탭에서 읽기전용 미리보기 — 좌측 폼 변경 시 자동 갱신"
            >
              미리보기 ↗
            </button>
            <button
              type="button"
              onClick={() => void openEditor('edit')}
              disabled={previewBusy}
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50"
              title="새 탭에서 OnlyOffice 편집 — 좌측 폼 변경 시 자동 반영"
            >
              OnlyOffice 편집 ↗
            </button>
          </div>
        </div>
        {saveMsg && (
          <div className="mx-auto max-w-[1380px] px-4 pb-2 text-xs text-slate-600">{saveMsg}</div>
        )}
        {prefillNote && (
          <div className="mx-auto max-w-[1380px] px-4 pb-2 text-xs text-blue-700 bg-blue-50 border-t border-blue-200 py-2">
            <span className="font-semibold">견적 연동:</span> {prefillNote}
            <button
              type="button"
              onClick={() => setPrefillNote(null)}
              className="ml-2 text-blue-500 hover:text-blue-700"
            >
              닫기
            </button>
          </div>
        )}
      </header>

      <div className="mx-auto max-w-[1380px] px-4 py-4">
        <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_390px]">
          <main className="min-w-0 space-y-3">
            {state.loadError && (
              <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                ! {state.loadError}
              </div>
            )}

            <StatusChipsBar state={state} overall={progress.overall} siteOk={progress.siteOk} equipOk={progress.equipOk} />

            <label className="flex items-center gap-2 px-3 py-1.5 rounded-md border border-slate-200 bg-white text-xs cursor-pointer hover:bg-slate-50">
              <input
                type="checkbox"
                checked={state.bypassConnection}
                onChange={(e) => state.setBypassConnection(e.target.checked)}
                className="h-3.5 w-3.5"
              />
              <span className="font-semibold text-slate-700">견적 연동 없이 전체 공급사 보기</span>
              <span className="text-slate-400">— 긴급 투입/상시 협력사 등 견적 단계 생략 시</span>
            </label>

            {/* 기본 정보 — 항상 펼침 */}
            <Step1SiteAndBp state={state} />

            {/* 장비 + 조종원 — 기본 접힘. 요약은 선택된 장비. */}
            <CollapsibleSection
              id="step-equipment"
              title="2. 장비 및 조종원 선택"
              status={
                <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${
                  progress.equipOk ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
                }`}>
                  {progress.equipOk ? '선택됨' : '미선택'}
                </span>
              }
              summary={
                state.selectedEquipment
                  ? `${state.selectedEquipment.vehicle_no || state.selectedEquipment.model || `장비 #${state.selectedEquipment.id}`} · 조종원 ${state.roleAssign.operator.length}명`
                  : '장비공급사 선택 필요'
              }
              defaultOpen={progress.equipOk === false}
            >
              <div className="p-4">
                <Step2Equipment state={state} onPreview={setLightbox} showDocuments={false} />
              </div>
            </CollapsibleSection>

            {/* 인원 — 기본 접힘. 요약은 배정 인원 수. */}
            <CollapsibleSection
              id="step-manpower"
              title="3. 인원 선택 (현장관리자/안전관리자/신호수/유도원)"
              status={
                <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${
                  assignedManpowerCount > 0 ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
                }`}>
                  {assignedManpowerCount > 0 ? `${assignedManpowerCount}명` : '0명'}
                </span>
              }
              summary={assignedManpowerCount > 0 ? `${assignedManpowerCount}명 배정` : '인력공급사 선택 필요'}
              defaultOpen={false}
            >
              <div className="p-4">
                <Step3Manpower state={state} onPreview={setLightbox} showDocuments={false} />
              </div>
            </CollapsibleSection>

            {/* 전자서명 — keepMounted 로 항상 mount: 접혀있어도 inputs/ref 유지되어 제출 시 자동 발송 보장. */}
            <CollapsibleSection
              id="step-signature"
              title="4. 전자서명"
              status={
                <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${
                  allSigned ? 'bg-emerald-100 text-emerald-800' : signedCount > 0 ? 'bg-blue-100 text-blue-800' : 'bg-slate-100 text-slate-600'
                }`}>
                  {signedCount}/5 완료
                </span>
              }
              summary={
                allSigned ? '5/5 사인 완료' :
                signedCount > 0 ? `${signedCount}명 사인 완료, ${5 - signedCount}명 대기` :
                '작성자/담당자/확인자/검토자/승인자 설정 필요'
              }
              defaultOpen={true}
              keepMounted={true}
            >
              <div className="p-4">
                <SignaturePanel
                  ref={sigPanelRef}
                  workPlanId={savedWorkPlanId}
                  onAllSignedChange={setAllSigned}
                  onAnySignedChange={(any) => {
                    setHasAnySigned((prev) => {
                      if (any && !prev && !formSnapshotRef.current) {
                        formSnapshotRef.current = JSON.stringify({
                          values: state.values,
                          roleAssign: state.roleAssign,
                          siteId: state.siteId,
                          equipmentId: state.equipmentId,
                        });
                      }
                      return any;
                    });
                  }}
                  onSignaturesChange={(items) => {
                    signaturesCacheRef.current = items;
                    setSignedCount(items.filter((s) => s.status === 'SIGNED').length);
                  }}
                />
              </div>
            </CollapsibleSection>

            {/* 워크시트 132 필드 — 선택사항. 기본 접힘. */}
            <details className="card bg-white border-slate-200">
              <summary className="cursor-pointer flex items-center justify-between px-4 py-3 hover:bg-slate-50 list-none rounded-lg">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-bold text-slate-900">워크시트 (132 필드 · 20 섹션)</span>
                  <span className="text-[11px] px-1.5 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">선택</span>
                </div>
                <span className="text-xs text-slate-400">▾ 펼쳐서 입력</span>
              </summary>
              <div className="border-t border-slate-100 p-4">
                <WorksheetSections
                  values={state.values}
                  onChange={state.setValue}
                  persons={allAssignedPersons}
                />
              </div>
            </details>
          </main>

          <aside className="space-y-3 xl:sticky xl:top-[78px] xl:self-start">
            <SubmitChecklist
              overall={progress.overall}
              missing={progress.missing}
              items={[
                { label: '기본 정보 입력', done: progress.siteOk },
                { label: '장비 선택', done: progress.equipOk },
                { label: '인원 선택', done: assignedManpowerCount > 0, hint: assignedManpowerCount > 0 ? `(${assignedManpowerCount}명)` : undefined },
                { label: '필수 첨부서류 선택', done: (state.selectedEquipDocIds.size + state.selectedPersonDocIds.size) > 0 },
                { label: '전자서명자 입력', done: allSigned, hint: `(${signedCount}/5)` },
              ]}
              onSubmit={submitWorkPlan}
              submitDisabled={saving || submitting || progress.overall < 100}
              submitLabel={submitting ? '처리 중…' : allSigned ? '최종 제출' : '제출하기'}
              submitTitle={progress.overall < 100 ? '필수 100% 입력 시 활성화' : allSigned ? '5/5 사인 완료 — 최종 제출' : '저장 + 사인 요청 발송 + 상세 페이지로 이동'}
            />
            <DocumentSelectionPanel state={state} onPreview={setLightbox} />
            <SelectedDocumentsPanel state={state} onPreview={setLightbox} />
          </aside>
        </div>
      </div>

      {/* 하단 보조 액션바 — DOCX/PDF 다운로드, 메일. 핵심 액션(임시저장/제출)은 상단. */}
      <div className="sticky bottom-0 z-20 bg-white border-t border-slate-200">
        <div className="mx-auto flex max-w-[1380px] items-center justify-between gap-3 px-4 py-2.5">
          <div className="min-w-0 text-xs text-slate-500">
            <span className="font-semibold text-blue-700">DRAFT</span>
            <span className="mx-2 text-slate-300">|</span>
            진행률 <span className="font-bold text-slate-900">{progress.overall}%</span>
            {progress.missing.length > 0 && (
              <span className="ml-2 inline-block max-w-[560px] truncate align-bottom text-rose-600">! 필수 누락: {progress.missing.join(', ')}</span>
            )}
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              onClick={downloadDocx}
              disabled={generating}
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50"
            >
              DOCX 다운로드
            </button>
            <button
              type="button"
              onClick={downloadPdf}
              disabled={generating}
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50"
            >
              PDF 다운로드
            </button>
            <button
              type="button"
              onClick={() => setMailOpen(true)}
              disabled={generating}
              className="rounded-lg border border-amber-200 bg-white px-3 py-2 text-xs font-semibold text-amber-700 shadow-sm hover:bg-amber-50 disabled:opacity-50"
            >
              PDF 메일
            </button>
            <button
              type="button"
              onClick={submitWorkPlan}
              disabled={saving || submitting || progress.overall < 100}
              className="rounded-lg bg-blue-600 px-5 py-2 text-xs font-bold text-white shadow-sm hover:bg-blue-700 disabled:opacity-50"
              title={progress.overall < 100 ? '필수 100% 입력 시 활성화' : allSigned ? '5/5 사인 완료 — 최종 제출' : '저장 + 사인 요청 발송 + 상세 페이지로 이동'}
            >
              {submitting ? '처리 중…' : allSigned ? '최종 제출' : '제출하기'}
            </button>
          </div>
        </div>
      </div>

      <DocLightbox target={lightbox} onClose={() => setLightbox(null)} />
      <PdfMailDialog open={mailOpen} buildBlob={() => buildDocxBlob(true)} onClose={() => setMailOpen(false)} />
      <MissingDocsDialog open={missingDocsOpen} workPlanId={savedWorkPlanId} onClose={() => setMissingDocsOpen(false)} />

      {invalidateDialogOpen && (
        <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-5">
            <h3 className="text-base font-bold text-slate-900 mb-2">사인 무효화 확인</h3>
            <p className="text-sm text-slate-700 mb-4">
              일부 사인이 완료된 후 작업계획서 내용이 변경되었습니다. 기존 사인을 무효화하고 다시 사인을 받으시겠습니까?
            </p>
            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => handleInvalidate(false)}
                className="text-sm px-3 py-1.5 rounded-md border border-slate-300 text-slate-700 hover:bg-slate-50"
              >
                유지
              </button>
              <button
                type="button"
                onClick={() => handleInvalidate(true)}
                className="text-sm px-4 py-1.5 rounded-md bg-rose-600 text-white font-medium hover:bg-rose-700"
              >
                기존 사인 무효화
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function StatusChipsBar({
  state,
  overall,
  siteOk,
  equipOk,
}: {
  state: WorkPlanCreateState;
  overall: number;
  siteOk: boolean;
  equipOk: boolean;
}) {
  const assignedPeople = REQUIRED_ROLES.reduce((sum, role) => sum + state.roleAssign[role.key].length, 0);
  const selectedDocs = state.selectedEquipDocIds.size + state.selectedPersonDocIds.size;
  const barColor = overall === 100 ? 'bg-emerald-500' : overall >= 70 ? 'bg-blue-500' : 'bg-amber-500';

  const chip = (label: string, done: boolean, value?: string) => (
    <span
      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium border ${
        done
          ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
          : 'bg-amber-50 text-amber-800 border-amber-200'
      }`}
    >
      {done ? '✓' : '!'} {label}
      {value && <span className="ml-0.5 opacity-70">{value}</span>}
    </span>
  );

  return (
    <div className="flex items-center justify-between gap-3 px-3 py-2 rounded-md bg-white border border-slate-200">
      <div className="flex flex-wrap items-center gap-1.5 min-w-0">
        {chip('기본정보', siteOk, siteOk ? '입력' : '미완료')}
        {chip('장비', equipOk, equipOk ? '선택' : '미선택')}
        {chip('인원', assignedPeople > 0, `${assignedPeople}명`)}
        {chip('첨부', selectedDocs > 0, `${selectedDocs}건`)}
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <div className="h-1.5 w-32 overflow-hidden rounded-full bg-slate-100">
          <div className={`h-full ${barColor}`} style={{ width: `${overall}%` }} />
        </div>
        <span className="w-10 text-right text-xs font-bold text-slate-700">{overall}%</span>
      </div>
    </div>
  );
}

function DocumentSelectionPanel({
  state,
  onPreview,
}: {
  state: WorkPlanCreateState;
  onPreview: (t: DocPreviewTarget) => void;
}) {
  const selectedEquipment = state.selectedEquipment;
  const assignedOperators = state.equipPersons.filter((p) => state.roleAssign.operator.includes(p.id));
  const nonOperatorRoles = REQUIRED_ROLES.filter((r) => r.key !== 'operator');

  return (
    <section id="documents-panel" className="rounded-lg border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-100 px-3 py-3">
        <div className="flex items-center justify-between gap-2">
          <div>
            <h2 className="text-sm font-bold text-slate-900">첨부 서류 선택</h2>
            <p className="mt-0.5 text-[11px] text-slate-500">공급사별 자원 서류를 선택하면 오른쪽 목록에 추가됩니다.</p>
          </div>
          <span className="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-semibold text-blue-700">
            {state.selectedEquipDocIds.size + state.selectedPersonDocIds.size}개
          </span>
        </div>
      </div>

      <div className="max-h-[560px] space-y-3 overflow-auto p-3">
        {selectedEquipment ? (
          <CompactDocGroup
            title={`${selectedEquipment.vehicle_no || selectedEquipment.model || `장비 #${selectedEquipment.id}`}`}
            subtitle={`장비 ${state.equipDocs.length}건`}
            docs={state.equipDocs.map((doc) => ({
              doc,
              ownerName: selectedEquipment.vehicle_no || selectedEquipment.model || `장비 #${selectedEquipment.id}`,
            }))}
            selectedIds={state.selectedEquipDocIds}
            onSelect={state.setSelectedEquipDocIds}
            onPreview={onPreview}
            tone="blue"
            onMove={state.moveAttachment}
          />
        ) : (
          <EmptyPanel text="장비를 선택하면 장비 서류가 표시됩니다." />
        )}

        {assignedOperators.length > 0 && (
          <CompactDocGroup
            title="조종원 서류"
            subtitle={`${assignedOperators.length}명`}
            docs={assignedOperators.flatMap((person) =>
              (state.personDocs[person.id] ?? []).map((doc) => ({ doc, ownerName: person.name }))
            )}
            selectedIds={state.selectedPersonDocIds}
            onSelect={state.setSelectedPersonDocIds}
            onPreview={onPreview}
            tone="emerald"
            onMove={state.moveAttachment}
          />
        )}

        {nonOperatorRoles.map((role) => {
          const people = state.manpowerPersons.filter((p) => state.roleAssign[role.key as RoleKey].includes(p.id));
          if (people.length === 0) return null;
          return (
            <CompactDocGroup
              key={role.key}
              title={`${role.label} 서류`}
              subtitle={`${people.length}명`}
              docs={people.flatMap((person) =>
                (state.personDocs[person.id] ?? []).map((doc) => ({ doc, ownerName: person.name }))
              )}
              selectedIds={state.selectedPersonDocIds}
              onSelect={state.setSelectedPersonDocIds}
              onPreview={onPreview}
              tone="violet"
              onMove={state.moveAttachment}
            />
          );
        })}
      </div>
    </section>
  );
}

function CompactDocGroup({
  title,
  subtitle,
  docs,
  selectedIds,
  onSelect,
  onPreview,
  tone,
  onMove,
}: {
  title: string;
  subtitle: string;
  docs: Array<{ doc: DocumentResponse; ownerName: string }>;
  selectedIds: Set<number>;
  onSelect: (s: Set<number>) => void;
  onPreview: (t: DocPreviewTarget) => void;
  tone: 'blue' | 'emerald' | 'violet';
  onMove?: (docId: number, dir: -1 | 1) => void;
}) {
  const checked = docs.filter(({ doc }) => selectedIds.has(doc.id)).length;
  const toneCls = tone === 'blue' ? 'text-blue-700 bg-blue-50' : tone === 'emerald' ? 'text-emerald-700 bg-emerald-50' : 'text-violet-700 bg-violet-50';

  const toggle = (id: number) => {
    const next = new Set(selectedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    onSelect(next);
  };

  return (
    <div className="rounded-lg border border-slate-200 bg-white">
      <div className="flex items-center justify-between gap-2 border-b border-slate-100 px-2.5 py-2">
        <div className="min-w-0">
          <div className="truncate text-xs font-bold text-slate-900">{title}</div>
          <div className="text-[10px] text-slate-500">{subtitle}</div>
        </div>
        <span className={`rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${toneCls}`}>
          선택 {checked}/{docs.length}
        </span>
      </div>
      {docs.length === 0 ? (
        <EmptyPanel text="등록된 서류가 없습니다." />
      ) : (
        <div className="divide-y divide-slate-100">
          {docs.map(({ doc, ownerName }) => (
            <CompactDocRow
              key={`${ownerName}-${doc.id}`}
              doc={doc}
              ownerName={ownerName}
              checked={selectedIds.has(doc.id)}
              onToggle={() => toggle(doc.id)}
              onPreview={() =>
                onPreview({
                  docId: doc.id,
                  category: doc.document_type_name,
                  mimeType: doc.content_type,
                  originalName: doc.file_name,
                  ownerName,
                })
              }
              onMove={onMove ? (dir) => onMove(doc.id, dir) : undefined}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function CompactDocRow({
  doc,
  ownerName,
  checked,
  onToggle,
  onPreview,
  onMove,
}: {
  doc: DocumentResponse;
  ownerName: string;
  checked: boolean;
  onToggle: () => void;
  onPreview: () => void;
  onMove?: (dir: -1 | 1) => void;
}) {
  const fileType = doc.content_type === 'application/pdf'
    ? 'PDF'
    : doc.content_type.startsWith('image/')
    ? 'IMG'
    : 'FILE';
  const days = daysUntilExpiry(doc.expiry_date);
  const status = doc.verification_status === 'REJECTED'
    ? { label: '반려', cls: 'bg-rose-100 text-rose-700' }
    : !doc.verified
    ? { label: '미검증', cls: 'bg-amber-100 text-amber-700' }
    : days == null
    ? { label: '만료일 없음', cls: 'bg-slate-100 text-slate-600' }
    : days < 0
    ? { label: '만료', cls: 'bg-rose-100 text-rose-700' }
    : days <= 7
    ? { label: `D-${days}`, cls: 'bg-orange-100 text-orange-700' }
    : days <= 30
    ? { label: `D-${days}`, cls: 'bg-amber-100 text-amber-700' }
    : { label: `D-${days}`, cls: 'bg-emerald-100 text-emerald-700' };

  return (
    <div className={`grid grid-cols-[18px_1fr_auto] items-center gap-2 px-2.5 py-2 ${checked ? 'bg-blue-50/60' : 'bg-white'}`}>
      <input type="checkbox" checked={checked} onChange={onToggle} className="h-3.5 w-3.5 rounded border-slate-300" />
      <button type="button" onClick={onPreview} className="min-w-0 text-left">
        <div className="flex min-w-0 items-center gap-1.5">
          <span className="rounded bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700">{fileType}</span>
          <span className="truncate text-[12px] font-semibold text-slate-900">{doc.document_type_name}</span>
        </div>
        <div className="mt-0.5 flex items-center gap-1.5 text-[10px] text-slate-500">
          <span className="truncate">{ownerName}</span>
          <span className="text-slate-300">|</span>
          <span>{doc.expiry_date ? `만료 ${doc.expiry_date}` : '만료일 없음'}</span>
        </div>
      </button>
      <div className="flex items-center gap-1">
        {checked && onMove && (
          <div className="hidden items-center gap-0.5 sm:flex">
            <button type="button" onClick={() => onMove(-1)} className="rounded border border-slate-200 px-1 text-[10px] text-slate-500 hover:bg-white">↑</button>
            <button type="button" onClick={() => onMove(1)} className="rounded border border-slate-200 px-1 text-[10px] text-slate-500 hover:bg-white">↓</button>
          </div>
        )}
        <span className={`whitespace-nowrap rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${status.cls}`}>{status.label}</span>
      </div>
    </div>
  );
}

function SelectedDocumentsPanel({
  state,
  onPreview,
}: {
  state: WorkPlanCreateState;
  onPreview: (t: DocPreviewTarget) => void;
}) {
  const people = [...state.equipPersons, ...state.manpowerPersons];
  const selectedDocs: Array<{ doc: DocumentResponse; ownerName: string }> = [];

  if (state.selectedEquipment) {
    state.equipDocs.forEach((doc) => {
      if (state.selectedEquipDocIds.has(doc.id)) {
        selectedDocs.push({
          doc,
          ownerName: state.selectedEquipment?.vehicle_no || state.selectedEquipment?.model || '장비',
        });
      }
    });
  }

  people.forEach((person) => {
    (state.personDocs[person.id] ?? []).forEach((doc) => {
      if (state.selectedPersonDocIds.has(doc.id)) {
        selectedDocs.push({ doc, ownerName: person.name });
      }
    });
  });

  const expiredCount = selectedDocs.filter(({ doc }) => {
    const d = daysUntilExpiry(doc.expiry_date);
    return d != null && d < 0;
  }).length;
  const soonCount = selectedDocs.filter(({ doc }) => {
    const d = daysUntilExpiry(doc.expiry_date);
    return d != null && d >= 0 && d <= 7;
  }).length;

  const remove = (doc: DocumentResponse) => {
    if (doc.owner_type === 'EQUIPMENT') {
      const next = new Set(state.selectedEquipDocIds);
      next.delete(doc.id);
      state.setSelectedEquipDocIds(next);
    } else {
      const next = new Set(state.selectedPersonDocIds);
      next.delete(doc.id);
      state.setSelectedPersonDocIds(next);
    }
  };

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
      <div className="mb-2 flex items-center justify-between gap-2">
        <div>
          <h2 className="text-sm font-bold text-slate-900">선택된 첨부 서류</h2>
          <p className="mt-0.5 text-[11px] text-slate-500">드래그 대신 위/아래 버튼으로 첨부 순서를 조정합니다.</p>
        </div>
        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-700">총 {selectedDocs.length}개</span>
      </div>
      {selectedDocs.length === 0 ? (
        <EmptyPanel text="선택된 서류가 없습니다." />
      ) : (
        <div className="max-h-[260px] divide-y divide-slate-100 overflow-auto rounded-lg border border-slate-100">
          {selectedDocs.map(({ doc, ownerName }, idx) => (
            <div key={`selected-${doc.id}`} className="grid grid-cols-[18px_1fr_auto] items-center gap-2 px-2.5 py-2">
              <span className="text-[10px] font-bold text-slate-400">{idx + 1}</span>
              <button
                type="button"
                onClick={() =>
                  onPreview({
                    docId: doc.id,
                    category: doc.document_type_name,
                    mimeType: doc.content_type,
                    originalName: doc.file_name,
                    ownerName,
                  })
                }
                className="min-w-0 text-left"
              >
                <div className="truncate text-[12px] font-semibold text-slate-900">{ownerName} - {doc.document_type_name}</div>
                <div className="text-[10px] text-slate-500">{doc.expiry_date ? `만료 ${doc.expiry_date}` : '만료일 없음'}</div>
              </button>
              <button type="button" onClick={() => remove(doc)} className="rounded px-1.5 py-0.5 text-xs text-slate-400 hover:bg-slate-100 hover:text-slate-700">
                ×
              </button>
            </div>
          ))}
        </div>
      )}
      {(expiredCount > 0 || soonCount > 0) && (
        <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] font-medium text-amber-800">
          만료 서류 {expiredCount}개, D-7 이내 서류 {soonCount}개가 포함되어 있습니다.
        </div>
      )}
    </section>
  );
}

function EmptyPanel({ text }: { text: string }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-200 px-3 py-5 text-center text-xs text-slate-400">
      {text}
    </div>
  );
}
