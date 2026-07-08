package com.skep.workplan.dto;

import java.util.Map;

/** S-9-B: skep 워크시트 폼 상태 (132 필드 + role_assign + 첨부 선택) 부분/전체 갱신. */
public record UpdateFormValuesRequest(
        Map<String, Object> formValues,
        Long equipmentSupplierCompanyId,
        Long manpowerSupplierCompanyId,
        Long currentEquipmentId
) {
}
