// 백엔드 audit / role enum → 한국어 라벨.
// 추가는 백엔드 AuditAction / AuditTargetType 와 동기.

export const AUDIT_ACTION_LABEL: Record<string, string> = {
  // 현장
  SITE_CREATED: '현장 생성',
  SITE_UPDATED: '현장 수정',
  PARTICIPANT_ADDED: '참여 업체 추가',
  PARTICIPANT_REMOVED: '참여 업체 해제',
  // 배치
  EQUIPMENT_ASSIGNED: '장비 배치',
  EQUIPMENT_UNASSIGNED: '장비 해제',
  PERSON_ASSIGNED: '인원 배치',
  PERSON_UNASSIGNED: '인원 해제',
  EQUIPMENT_STATUS_CHANGED: '장비 상태 변경',
  // 서류
  DOCUMENT_UPLOADED: '서류 업로드',
  DOCUMENT_VERIFIED: '서류 검증',
  DOCUMENT_RENEWED: '서류 갱신',
  // 작업계획서
  WORK_PLAN_CREATED: '작업계획서 생성',
  WORK_PLAN_CLONED: '작업계획서 복제',
  WORK_PLAN_UPDATED: '작업계획서 수정',
  WORK_PLAN_SUBMITTED: '작업계획서 제출',
  WORK_PLAN_APPROVED: '작업계획서 승인',
  WORK_PLAN_STARTED: '작업 시작',
  WORK_PLAN_FORCE_STARTED: '작업 강제 시작',
  WORK_PLAN_COMPLETED: '작업 완료',
  WORK_PLAN_CANCELLED: '작업 취소',
  WORK_PLAN_EQUIPMENT_ADDED: '작업계획서 장비 추가',
  WORK_PLAN_EQUIPMENT_REMOVED: '작업계획서 장비 제거',
  WORK_PLAN_PERSON_ADDED: '작업계획서 인원 추가',
  WORK_PLAN_PERSON_REMOVED: '작업계획서 인원 제거',
  WORK_PLAN_RESOURCE_CONFLICT: '자원 충돌 감지',
  // 견적
  QUOTATION_CREATED: '견적 요청 생성',
  QUOTATION_RESPONDED: '견적 응답',
  QUOTATION_FINALIZED: '견적 확정',
  QUOTATION_CANCELLED: '견적 취소',
};

export const AUDIT_TARGET_LABEL: Record<string, string> = {
  SITE: '현장',
  SITE_PARTICIPANT: '참여 업체',
  EQUIPMENT: '장비',
  PERSON: '인원',
  DOCUMENT: '서류',
  WORK_PLAN: '작업계획서',
  WORK_PLAN_EQUIPMENT: '작업계획서-장비',
  WORK_PLAN_PERSON: '작업계획서-인원',
  QUOTATION_REQUEST: '견적 요청',
};

export const ROLE_LABEL: Record<string, string> = {
  ADMIN: '관리자',
  BP: '발주사',
  EQUIPMENT_SUPPLIER: '장비공급사',
  MANPOWER_SUPPLIER: '인력공급사',
};

export function labelAction(action: string): string {
  return AUDIT_ACTION_LABEL[action] ?? action;
}

export function labelTarget(targetType: string): string {
  return AUDIT_TARGET_LABEL[targetType] ?? targetType;
}

export function labelRole(role: string | null | undefined): string {
  if (!role) return '시스템';
  return ROLE_LABEL[role] ?? role;
}
