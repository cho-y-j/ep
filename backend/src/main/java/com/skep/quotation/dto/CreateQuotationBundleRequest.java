package com.skep.quotation.dto;

import com.skep.equipment.EquipmentCategory;
import com.skep.person.PersonRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 현장 1개에 장비 + 인력 N역할을 한 번에 묶어서 발송하는 요청.
 *
 * - equipment 가 비어있지 않거나 manpower 가 1개 이상은 있어야 함.
 * - manpower 인원 중복 (서로 다른 역할 행에 같은 person_id) 은 서버에서 차단.
 * - 전체가 한 트랜잭션. 하나라도 실패하면 전체 롤백.
 */
public record CreateQuotationBundleRequest(
        /** Site-C: 견적 단계에선 site 미정. */
        Long siteId,
        @NotNull LocalDate workPeriodStart,
        @NotNull LocalDate workPeriodEnd,
        @Size(max = 4000) String notes,
        Long onBehalfOfBpCompanyId,
        @Valid EquipmentItem equipment,
        @Valid List<ManpowerItem> manpower,
        /** 다온톡 알림톡 수신번호 (선택). 장비 포함 시 SJR_254094 발송. */
        List<String> alimtalkPhones
) {
    public record EquipmentItem(
            @NotNull EquipmentCategory category,
            @Size(max = 4000) String specText,
            Integer proposedDailyRate,
            Integer proposedMonthlyRate,
            Integer count,
            @NotEmpty List<TargetEquipment> targets
    ) {
        public record TargetEquipment(@NotNull Long supplierCompanyId, @NotNull Long equipmentId) {}
    }

    public record ManpowerItem(
            @NotNull PersonRole role,
            @Size(max = 4000) String specText,
            Integer proposedDailyRate,
            Integer proposedMonthlyRate,
            Integer count,
            @NotEmpty List<TargetPerson> targets
    ) {
        public record TargetPerson(@NotNull Long supplierCompanyId, @NotNull Long personId) {}
    }
}
