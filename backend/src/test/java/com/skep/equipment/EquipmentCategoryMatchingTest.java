package com.skep.equipment;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyType;
import com.skep.compliance.dto.ResourceCompliance;
import com.skep.quotation.QuotationMode;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestType;
import com.skep.quotation.QuotationService;
import com.skep.quotation.dto.QuotationCandidateResponse;
import com.skep.quotation.proposal.QuotationProposal;
import com.skep.quotation.proposal.QuotationProposalService;
import com.skep.quotation.proposal.dto.CreateProposalRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * enum(EquipmentCategory)→String 전환의 매칭 회귀 방어.
 *
 * 카테고리 비교가 enum {@code ==}/{@code !=} 에서 {@code Objects.equals}/{@code !Objects.equals} 로 바뀌었고
 * 이 로직은 견적 후보·응찰·장비목록 필터의 핵심이라 조용히 깨지면 배차/견적이 틀린다.
 * 같은 코드=포함/통과, 다른 코드=제외/거부, null 경계(옛 enum 동작과 동일)를 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class EquipmentCategoryMatchingTest {

    // ── EquipmentService 의존 ──
    @Mock EquipmentRepository equipmentRepo;
    @Mock com.skep.company.CompanyRepository companyRepo;
    @Mock com.skep.company.CompanyService companyService;
    @Mock com.skep.document.DocumentRepository documentRepo;
    @Mock com.skep.storage.FileStorage fileStorage;
    @Mock com.skep.site.SiteRepository siteRepo;
    @Mock com.skep.site.SiteParticipantRepository siteParticipantRepo;
    @Mock EquipmentDefaultOperatorRepository defaultOperatorRepo;
    @Mock com.skep.person.PersonRepository personRepo;
    @Mock com.skep.person.PersonService personService;
    @Mock EquipmentTypeService equipmentTypeService;

    // ── QuotationService 추가 의존 ──
    @Mock com.skep.quotation.QuotationRequestRepository requestRepo;
    @Mock com.skep.quotation.QuotationRequestTargetRepository targetRepo;
    @Mock com.skep.clientorg.ClientOrgRepository clientOrgRepo;
    @Mock com.skep.quotation.proposal.QuotationProposalRepository proposalRepo;
    @Mock com.skep.user.UserRepository userRepo;
    @Mock com.skep.notification.NotificationService notifications;
    @Mock com.skep.audit.AuditLogService auditLog;
    @Mock com.skep.compliance.ComplianceService compliance;
    @Mock ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock com.skep.alimtalk.AlimTalkService alimTalk;
    @Mock com.skep.quotation.dispatch.DispatchedEquipmentRepository dispatchedEqRepo;
    @Mock com.skep.quotation.dispatch.DispatchedPersonRepository dispatchedPersonRepo;
    @Mock com.skep.quotation.bundle.DocumentBundleRepository documentBundleRepo;

    // ── QuotationProposalService 추가 의존 ──
    @Mock com.skep.quotation.snapshot.ComparisonSnapshotService snapshotService;
    @Mock com.skep.quotation.dispatch.draft.DispatchDraftService dispatchDrafts;

    @InjectMocks EquipmentService equipmentService;
    @InjectMocks QuotationService quotationService;
    @InjectMocks QuotationProposalService proposalService;

    private static final AuthenticatedUser EQ_SUPPLIER =
            new AuthenticatedUser(2L, "eq@x", "Eq", Role.EQUIPMENT_SUPPLIER, 10L, true);
    private static final AuthenticatedUser ADMIN =
            new AuthenticatedUser(1L, "admin@x", "Admin", Role.ADMIN, null, true);

    private static Equipment equipment(Long id, String category) {
        Equipment e = Equipment.builder().supplierId(10L).vehicleNo("v" + id).category(category).build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    // ── EquipmentService.list 필터 (Objects.equals: 같으면 포함) ──

    @Test
    void listFiltersByExactCategoryAndExcludesNullCategoryWhenQueried() {
        when(companyService.selfAndChildren(10L)).thenReturn(List.of(10L));
        when(equipmentRepo.findBySupplierIdInOrderByIdDesc(any())).thenReturn(List.of(
                equipment(1L, "CRANE"), equipment(2L, "EXCAVATOR"), equipment(3L, null)));

        // 같은 코드만 포함 — 다른 코드/ null 코드 장비는 제외
        List<Equipment> crane = equipmentService.list(EQ_SUPPLIER, null, "CRANE");
        assertEquals(1, crane.size());
        assertEquals("CRANE", crane.get(0).getCategory());

        // 다른 코드도 대칭적으로 정확히 매칭
        List<Equipment> excavator = equipmentService.list(EQ_SUPPLIER, null, "EXCAVATOR");
        assertEquals(1, excavator.size());
        assertEquals("EXCAVATOR", excavator.get(0).getCategory());

        // 카테고리 미지정이면 필터 없음 — null 카테고리 장비 포함 전체 반환
        assertEquals(3, equipmentService.list(EQ_SUPPLIER, null, null).size());
    }

    // ── QuotationService.candidates 필터 (같은 코드만 후보) ──

    @Test
    void candidatesIncludesSameCategoryAndExcludesOthers() {
        Company eqCo = mock(Company.class);
        when(eqCo.getId()).thenReturn(10L);
        when(eqCo.getName()).thenReturn("EqCo");
        ResourceCompliance ready = mock(ResourceCompliance.class);
        when(ready.readyForWorkPlan()).thenReturn(true);
        when(ready.expiringCount()).thenReturn(0);

        when(companyRepo.findByType(CompanyType.EQUIPMENT)).thenReturn(List.of(eqCo));
        when(compliance.forCompany(eq(10L), any())).thenReturn(ready);
        when(equipmentRepo.findBySupplierIdInOrderByIdDesc(any())).thenReturn(List.of(
                equipment(1L, "CRANE"), equipment(2L, "EXCAVATOR")));
        when(compliance.forEquipment(anyLong(), any())).thenReturn(ready);

        List<QuotationCandidateResponse> result = quotationService.candidates("CRANE", ADMIN);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).equipments().size());
        assertEquals("CRANE", result.get(0).equipments().get(0).category());
    }

    // ── QuotationProposalService.submit 응찰검증 (!Objects.equals: 다르면 거부) ──

    private QuotationRequest openBid(String equipmentCategory) {
        QuotationRequest qr = QuotationRequest.builder()
                .requestType(QuotationRequestType.EQUIPMENT)
                .equipmentCategory(equipmentCategory)
                .mode(QuotationMode.OPEN_BID)
                .count(1)
                .build();
        ReflectionTestUtils.setField(qr, "id", 100L);
        return qr;
    }

    private static CreateProposalRequest bidWithEquipment5() {
        return new CreateProposalRequest(5L, null, 1000, null, null, null,
                null, null, null, null, null);
    }

    @Test
    void submitRejectsEquipmentOfDifferentCategory() {
        when(requestRepo.findById(100L)).thenReturn(Optional.of(openBid("CRANE")));
        when(equipmentRepo.findById(5L)).thenReturn(Optional.of(equipment(5L, "EXCAVATOR")));

        ApiException ex = assertThrows(ApiException.class,
                () -> proposalService.submit(100L, bidWithEquipment5(), EQ_SUPPLIER));
        assertEquals("EQUIPMENT_CATEGORY_MISMATCH", ex.getCode());
    }

    @Test
    void submitAcceptsEquipmentOfSameCategory() {
        when(requestRepo.findById(100L)).thenReturn(Optional.of(openBid("CRANE")));
        when(equipmentRepo.findById(5L)).thenReturn(Optional.of(equipment(5L, "CRANE")));
        // 카테고리 검증을 통과했음을 증명 — 이후 중복검사에서 걸리게 하여 저장/알림 경로를 단락.
        when(proposalRepo.findByRequestIdAndSupplierCompanyIdAndEquipmentId(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(mock(QuotationProposal.class)));

        ApiException ex = assertThrows(ApiException.class,
                () -> proposalService.submit(100L, bidWithEquipment5(), EQ_SUPPLIER));
        assertEquals("PROPOSAL_DUPLICATE", ex.getCode());
    }

    @Test
    void submitSkipsCategoryCheckWhenRequestCategoryNull() {
        when(requestRepo.findById(100L)).thenReturn(Optional.of(openBid(null)));
        when(equipmentRepo.findById(5L)).thenReturn(Optional.of(equipment(5L, "CRANE")));
        when(proposalRepo.findByRequestIdAndSupplierCompanyIdAndEquipmentId(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.of(mock(QuotationProposal.class)));

        // qr 카테고리 null 이면 매칭 게이트를 건너뛴다(옛 enum 가드와 동일) — MISMATCH 아닌 중복에서 걸림.
        ApiException ex = assertThrows(ApiException.class,
                () -> proposalService.submit(100L, bidWithEquipment5(), EQ_SUPPLIER));
        assertEquals("PROPOSAL_DUPLICATE", ex.getCode());
    }
}
