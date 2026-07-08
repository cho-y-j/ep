package com.skep.quotation.dto;

import com.skep.equipment.EquipmentCategory;
import com.skep.person.PersonRole;
import com.skep.quotation.QuotationRequestType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * S-10: 견적 요청 생성.
 *
 * - requestType=EQUIPMENT: equipmentCategory 필수, target.personId=null
 * - requestType=MANPOWER: manpowerRole 필수, target.personId optional (제안 인원)
 *
 * onBehalfOfBpCompanyId — ADMIN 가 특정 BP 컨텍스트로 만들 때 그 BP 회사 ID.
 */
public record CreateQuotationRequest(
        /** Site-C: 견적 단계에선 site 미정. 작업계획서 작성 시 BP 가 선택. */
        Long siteId,
        @NotNull LocalDate workPeriodStart,
        @NotNull LocalDate workPeriodEnd,
        QuotationRequestType requestType,
        EquipmentCategory equipmentCategory,
        PersonRole manpowerRole,
        @Size(max = 4000) String specText,
        Integer proposedDailyRate,
        Integer proposedMonthlyRate,
        Integer count,
        @Size(max = 4000) String notes,
        Long onBehalfOfBpCompanyId,
        /** 같은 발송 묶음의 견적은 같은 UUID. 한 사이트에 장비+인력을 한 번에 보낼 때 그룹화. */
        UUID bundleId,
        @NotEmpty List<TargetInput> targets
) {
    public record TargetInput(
            @NotNull Long supplierCompanyId,
            /** 장비공급 견적의 제안 장비 (선택). manpower 견적에서는 무시. */
            Long equipmentId,
            /** 인력공급 견적의 제안 인원 (선택). equipment 견적에서는 무시. */
            Long personId
    ) {}
}
