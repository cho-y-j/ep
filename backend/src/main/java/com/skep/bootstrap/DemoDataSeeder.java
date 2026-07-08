package com.skep.bootstrap;

import com.skep.clientorg.ClientOrg;
import com.skep.clientorg.ClientOrgRepository;
import com.skep.clientorg.history.EquipmentClientOrgHistory;
import com.skep.clientorg.history.EquipmentClientOrgHistoryRepository;
import com.skep.clientorg.history.HistorySource;
import com.skep.clientorg.history.PersonClientOrgHistory;
import com.skep.clientorg.history.PersonClientOrgHistoryRepository;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.document.*;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentCategory;
import com.skep.equipment.EquipmentRepository;
import com.skep.outgoing.OutgoingQuotation;
import com.skep.outgoing.OutgoingQuotationRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.person.PersonRole;
import com.skep.quotation.QuotationMode;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
import com.skep.quotation.QuotationRequestType;
import com.skep.quotation.proposal.QuotationProposal;
import com.skep.quotation.proposal.QuotationProposalRepository;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 서류 만료일 관리 시연용 더미 데이터 시드.
 * TestUserSeeder 가 만든 3개 회사(BP/장비공급/인력공급)에 사람·장비·서류를 idempotent 하게 추가한다.
 *
 * 시연 의도:
 *   - 만료됨 (today-15)
 *   - 만료 임박 (today+10) — Compliance EXPIRING_DAYS=30 안쪽
 *   - 정상 (today+200)
 *   - 만료일 없음 (null)
 *   - VERIFIED vs PENDING 섞음
 *
 * 시드는 DocumentService.upload 정식 경로를 우회한다 (audit/verify event/MultipartFile 불필요).
 * 모든 더미 문서가 같은 stub fileKey 를 공유 — 파일 시스템 가벼움.
 */
@Configuration
public class DemoDataSeeder {

    @Bean
    @Order(100)
    public ApplicationRunner seedDemoData(
            @Value("${skep.bootstrap.test-users.enabled:false}") boolean enabled,
            DemoSeederRunner runner
    ) {
        return args -> {
            if (!enabled) return;
            runner.run();
        };
    }

    @Component
    static class DemoSeederRunner {
        private static final Logger log = LoggerFactory.getLogger(DemoSeederRunner.class);
        private static final byte[] STUB_PDF = "%PDF-1.4\n%demo stub\n%%EOF".getBytes();

        private final UserRepository users;
        private final CompanyRepository companies;
        private final PersonRepository persons;
        private final EquipmentRepository equipments;
        private final DocumentRepository documents;
        private final DocumentTypeRepository documentTypes;
        private final FileStorage storage;
        private final ClientOrgRepository clientOrgs;
        private final EquipmentClientOrgHistoryRepository eqHistRepo;
        private final PersonClientOrgHistoryRepository ppHistRepo;
        private final QuotationRequestRepository quotationRequests;
        private final QuotationProposalRepository quotationProposals;
        private final OutgoingQuotationRepository outgoingQuotations;
        private final PasswordEncoder passwordEncoder;

        DemoSeederRunner(UserRepository users, CompanyRepository companies, PersonRepository persons,
                         EquipmentRepository equipments, DocumentRepository documents,
                         DocumentTypeRepository documentTypes, FileStorage storage,
                         ClientOrgRepository clientOrgs,
                         EquipmentClientOrgHistoryRepository eqHistRepo,
                         PersonClientOrgHistoryRepository ppHistRepo,
                         QuotationRequestRepository quotationRequests,
                         QuotationProposalRepository quotationProposals,
                         OutgoingQuotationRepository outgoingQuotations,
                         PasswordEncoder passwordEncoder) {
            this.users = users;
            this.companies = companies;
            this.persons = persons;
            this.equipments = equipments;
            this.documents = documents;
            this.documentTypes = documentTypes;
            this.storage = storage;
            this.clientOrgs = clientOrgs;
            this.eqHistRepo = eqHistRepo;
            this.ppHistRepo = ppHistRepo;
            this.quotationRequests = quotationRequests;
            this.quotationProposals = quotationProposals;
            this.outgoingQuotations = outgoingQuotations;
            this.passwordEncoder = passwordEncoder;
        }

        @Transactional
        public void run() {
            // 이미 시드된 경우 — history / quotation / outgoing 만 별도 idempotent 체크.
            if (persons.existsByNameStartingWith("데모 ")) {
                if (eqHistRepo.count() == 0 && ppHistRepo.count() == 0) {
                    seedHistoriesForExistingDemo();
                    log.info("demo seeder: history backfill done");
                }
                if (quotationRequests.count() == 0) {
                    seedQuotationsForExistingDemo();
                    log.info("demo seeder: OPEN_BID + proposals backfill done");
                }
                if (outgoingQuotations.count() == 0) {
                    seedOutgoingForExistingDemo();
                    log.info("demo seeder: outgoing-quotations backfill done");
                }
                return;
            }

            Company bp = companies.findByBusinessNumber("111-11-11111").orElse(null);
            Company eq = companies.findByBusinessNumber("222-22-22222").orElse(null);
            Company mp = companies.findByBusinessNumber("333-33-33333").orElse(null);
            if (bp == null || eq == null || mp == null) {
                log.warn("demo seeder: test companies missing, skip");
                return;
            }

            Long uploader = users.findAll().stream()
                    .filter(User::isEnabled).findFirst().map(User::getId).orElse(null);
            String stubKey = storage.storeBytes(STUB_PDF, "pdf");

            Map<String, DocumentType> typeIndex = new HashMap<>();
            for (OwnerType owner : OwnerType.values()) {
                for (DocumentType t : documentTypes.findByAppliesToAndActiveOrderBySortOrderAscIdAsc(owner, true)) {
                    typeIndex.put(owner + ":" + t.getName(), t);
                }
            }

            LocalDate today = LocalDate.now();
            LocalDate expired = today.minusDays(15);
            LocalDate expSoon = today.plusDays(10);
            LocalDate valid   = today.plusDays(200);

            seedDoc(typeIndex, OwnerType.COMPANY, bp.getId(), "사업자등록증",  null,    true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.COMPANY, bp.getId(), "통장 사본",     null,    true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.COMPANY, eq.getId(), "사업자등록증",  null,    true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.COMPANY, eq.getId(), "건설업등록증",  valid,   true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.COMPANY, eq.getId(), "4대보험증명",   expSoon, false, stubKey, uploader);
            seedDoc(typeIndex, OwnerType.COMPANY, mp.getId(), "사업자등록증",  null,    true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.COMPANY, mp.getId(), "4대보험증명",   expired, false, stubKey, uploader);

            Person p1 = savePerson(bp.getId(), "데모 김기사", "010-1000-0001", Set.of(PersonRole.OPERATOR));
            seedDoc(typeIndex, OwnerType.PERSON, p1.getId(), "운전면허증",     valid,   true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.PERSON, p1.getId(), "건강진단서",     expSoon, true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.PERSON, p1.getId(), "안전교육 이수증", expired, false, stubKey, uploader);

            Person p2 = savePerson(bp.getId(), "데모 이지휘", "010-1000-0002", Set.of(PersonRole.WORK_DIRECTOR));
            seedDoc(typeIndex, OwnerType.PERSON, p2.getId(), "신분증",         null,    true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.PERSON, p2.getId(), "안전교육 이수증", valid,   true,  stubKey, uploader);

            Person p3 = savePerson(eq.getId(), "데모 박조종", "010-2000-0001", Set.of(PersonRole.OPERATOR));
            seedDoc(typeIndex, OwnerType.PERSON, p3.getId(), "운전면허증",     expired, false, stubKey, uploader);
            seedDoc(typeIndex, OwnerType.PERSON, p3.getId(), "건강진단서",     valid,   true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.PERSON, p3.getId(), "자격증",         expSoon, true,  stubKey, uploader);

            Person p4 = savePerson(eq.getId(), "데모 최조종", "010-2000-0002", Set.of(PersonRole.OPERATOR));
            seedDoc(typeIndex, OwnerType.PERSON, p4.getId(), "운전면허증",     valid,   true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.PERSON, p4.getId(), "안전교육 이수증", valid,   true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.PERSON, p4.getId(), "건강진단서",     valid,   true,  stubKey, uploader);

            Person p5 = savePerson(mp.getId(), "데모 정신호", "010-3000-0001", Set.of(PersonRole.SIGNALER));
            seedDoc(typeIndex, OwnerType.PERSON, p5.getId(), "신분증",         null,    true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.PERSON, p5.getId(), "안전교육 이수증", expSoon, true,  stubKey, uploader);

            Person p6 = savePerson(mp.getId(), "데모 한점검", "010-3000-0002", Set.of(PersonRole.INSPECTOR));
            seedDoc(typeIndex, OwnerType.PERSON, p6.getId(), "안전교육 이수증", expired, false, stubKey, uploader);
            seedDoc(typeIndex, OwnerType.PERSON, p6.getId(), "자격증",         valid,   true,  stubKey, uploader);

            Equipment e1 = saveEquipment(eq.getId(), "12가1234", EquipmentCategory.CRANE,       "200톤 크롤러크레인", "현대", 2020);
            seedDoc(typeIndex, OwnerType.EQUIPMENT, e1.getId(), "자동차등록증", null,    true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.EQUIPMENT, e1.getId(), "정기검사증",   expSoon, true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.EQUIPMENT, e1.getId(), "보험증권",     valid,   true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.EQUIPMENT, e1.getId(), "안전인증서",   expired, false, stubKey, uploader);

            Equipment e2 = saveEquipment(eq.getId(), "23나5678", EquipmentCategory.AERIAL_LIFT, "45m 고소작업차",      "두산", 2021);
            seedDoc(typeIndex, OwnerType.EQUIPMENT, e2.getId(), "자동차등록증", null,    true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.EQUIPMENT, e2.getId(), "정기검사증",   valid,   true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.EQUIPMENT, e2.getId(), "보험증권",     expSoon, true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.EQUIPMENT, e2.getId(), "점검표",       valid,   true,  stubKey, uploader);

            Equipment e3 = saveEquipment(eq.getId(), "34다9012", EquipmentCategory.EXCAVATOR,   "30톤 굴삭기",          "현대", 2019);
            seedDoc(typeIndex, OwnerType.EQUIPMENT, e3.getId(), "자동차등록증", null,    true,  stubKey, uploader);
            seedDoc(typeIndex, OwnerType.EQUIPMENT, e3.getId(), "정기검사증",   expired, false, stubKey, uploader);
            seedDoc(typeIndex, OwnerType.EQUIPMENT, e3.getId(), "보험증권",     valid,   true,  stubKey, uploader);

            // ====== ClientOrg 자원 이력 ======
            // V33 migration 이 시드한 ClientOrg 가 있으면 사용. (삼성/SK/현대/LG/포스코/GS)
            ClientOrg samsung = clientOrgs.findByCode("SAMSUNG").orElse(null);
            ClientOrg sk = clientOrgs.findByCode("SK").orElse(null);
            ClientOrg hyundai = clientOrgs.findByCode("HYUNDAI").orElse(null);
            if (samsung != null) {
                seedEqHist(e1, samsung.getId(), LocalDate.of(2024, 1, 10), LocalDate.of(2024, 3, 20));
                seedEqHist(e1, samsung.getId(), LocalDate.of(2024, 6, 1), LocalDate.of(2024, 8, 15));
                seedEqHist(e2, samsung.getId(), LocalDate.of(2024, 4, 1), LocalDate.of(2024, 5, 30));
                seedPpHist(p1, samsung.getId(), LocalDate.of(2024, 1, 10), LocalDate.of(2024, 3, 20));
                seedPpHist(p3, samsung.getId(), LocalDate.of(2024, 7, 1), LocalDate.of(2024, 8, 30));
            }
            if (sk != null) {
                seedEqHist(e1, sk.getId(), LocalDate.of(2025, 1, 5), LocalDate.of(2025, 2, 28));
                seedEqHist(e3, sk.getId(), LocalDate.of(2024, 9, 1), LocalDate.of(2024, 11, 30));
                seedPpHist(p4, sk.getId(), LocalDate.of(2024, 9, 1), LocalDate.of(2024, 11, 30));
            }
            if (hyundai != null) {
                seedEqHist(e2, hyundai.getId(), LocalDate.of(2025, 3, 1), null); // 진행 중
                seedPpHist(p1, hyundai.getId(), LocalDate.of(2025, 3, 1), null);
            }

            log.info("demo seeder: created 6 persons, 3 equipment, ~30 documents, ~10 client-org histories");
        }

        private void seedEqHist(Equipment e, Long clientOrgId, LocalDate start, LocalDate end) {
            eqHistRepo.save(EquipmentClientOrgHistory.builder()
                    .equipmentId(e.getId()).clientOrgId(clientOrgId)
                    .periodStart(start).periodEnd(end)
                    .source(HistorySource.ADMIN).build());
        }
        private void seedEqHistById(Long equipmentId, Long clientOrgId, LocalDate start, LocalDate end) {
            eqHistRepo.save(EquipmentClientOrgHistory.builder()
                    .equipmentId(equipmentId).clientOrgId(clientOrgId)
                    .periodStart(start).periodEnd(end)
                    .source(HistorySource.ADMIN).build());
        }
        private void seedPpHist(Person p, Long clientOrgId, LocalDate start, LocalDate end) {
            ppHistRepo.save(PersonClientOrgHistory.builder()
                    .personId(p.getId()).clientOrgId(clientOrgId)
                    .periodStart(start).periodEnd(end)
                    .source(HistorySource.ADMIN).build());
        }
        private void seedPpHistById(Long personId, Long clientOrgId, LocalDate start, LocalDate end) {
            ppHistRepo.save(PersonClientOrgHistory.builder()
                    .personId(personId).clientOrgId(clientOrgId)
                    .periodStart(start).periodEnd(end)
                    .source(HistorySource.ADMIN).build());
        }

        /** 이미 시드된 데모 자원에 이력만 backfill. */
        private void seedHistoriesForExistingDemo() {
            ClientOrg samsung = clientOrgs.findByCode("SAMSUNG").orElse(null);
            ClientOrg sk = clientOrgs.findByCode("SK").orElse(null);
            ClientOrg hyundai = clientOrgs.findByCode("HYUNDAI").orElse(null);
            // 데모 vehicleNo 로 장비 찾기
            Long e1 = findEqIdByVehicle("12가1234");
            Long e2 = findEqIdByVehicle("23나5678");
            Long e3 = findEqIdByVehicle("34다9012");
            // 데모 person 이름으로 찾기
            Long p1 = findPpIdByName("데모 김기사");
            Long p3 = findPpIdByName("데모 박조종");
            Long p4 = findPpIdByName("데모 최조종");

            if (samsung != null) {
                if (e1 != null) {
                    seedEqHistById(e1, samsung.getId(), LocalDate.of(2024, 1, 10), LocalDate.of(2024, 3, 20));
                    seedEqHistById(e1, samsung.getId(), LocalDate.of(2024, 6, 1), LocalDate.of(2024, 8, 15));
                }
                if (e2 != null) seedEqHistById(e2, samsung.getId(), LocalDate.of(2024, 4, 1), LocalDate.of(2024, 5, 30));
                if (p1 != null) seedPpHistById(p1, samsung.getId(), LocalDate.of(2024, 1, 10), LocalDate.of(2024, 3, 20));
                if (p3 != null) seedPpHistById(p3, samsung.getId(), LocalDate.of(2024, 7, 1), LocalDate.of(2024, 8, 30));
            }
            if (sk != null) {
                if (e1 != null) seedEqHistById(e1, sk.getId(), LocalDate.of(2025, 1, 5), LocalDate.of(2025, 2, 28));
                if (e3 != null) seedEqHistById(e3, sk.getId(), LocalDate.of(2024, 9, 1), LocalDate.of(2024, 11, 30));
                if (p4 != null) seedPpHistById(p4, sk.getId(), LocalDate.of(2024, 9, 1), LocalDate.of(2024, 11, 30));
            }
            if (hyundai != null) {
                if (e2 != null) seedEqHistById(e2, hyundai.getId(), LocalDate.of(2025, 3, 1), null);
                if (p1 != null) seedPpHistById(p1, hyundai.getId(), LocalDate.of(2025, 3, 1), null);
            }
        }

        private Long findEqIdByVehicle(String vehicleNo) {
            return equipments.findAll().stream()
                    .filter(e -> vehicleNo.equals(e.getVehicleNo()))
                    .findFirst().map(Equipment::getId).orElse(null);
        }
        private Long findPpIdByName(String name) {
            return persons.findAll().stream()
                    .filter(p -> name.equals(p.getName()))
                    .findFirst().map(Person::getId).orElse(null);
        }

        private Person savePerson(Long supplierId, String name, String phone, Set<PersonRole> roles) {
            return persons.save(Person.builder()
                    .supplierId(supplierId).name(name).phone(phone).roles(roles).build());
        }

        private Equipment saveEquipment(Long supplierId, String vehicleNo, EquipmentCategory cat,
                                        String model, String mfg, int year) {
            return equipments.save(Equipment.builder()
                    .supplierId(supplierId).vehicleNo(vehicleNo).category(cat)
                    .model(model).manufacturer(mfg).year(year).build());
        }

        // ===== Bid-12: OPEN_BID 견적 + 제안 + 영업견적 데모 시드 =====
        //
        // 기존 데모는 BP/EQ/MP 각 1개 회사만 있어 OPEN_BID 비교 화면이 단조로움.
        // 추가 EQ 공급사 1개 (B) + 장비 2개 (CRANE/AERIAL_LIFT) 를 만들어
        //   - OPEN_BID 견적 2건 (CRANE 200톤급 / AERIAL_LIFT 45m) 에 2개 공급사가 경쟁
        //   - 다양한 상태 (SUBMITTED / FINAL_ACCEPTED / PENDING_REVIEW) 가 한 화면에 보이도록 구성.
        // 영업견적 2건: 등록 BP 발송 1건 + 외부 이메일 발송 1건.

        private static final String EQ_B_BIZ_NO = "444-44-44444";
        private static final String EQ_B_EMAIL = "equipment2@example.com";

        private Company ensureEqSupplierB() {
            return companies.findByBusinessNumber(EQ_B_BIZ_NO).orElseGet(() ->
                    companies.save(Company.builder()
                            .name("데모 장비공급(주) B")
                            .businessNumber(EQ_B_BIZ_NO)
                            .type(CompanyType.EQUIPMENT)
                            .build()));
        }

        /** 추가 공급사의 master user — TestUserSeeder 와 같은 기본 비번을 사용 (testpass123). */
        private User ensureEqSupplierBUser(Long companyId) {
            return users.findByEmail(EQ_B_EMAIL).orElseGet(() ->
                    users.save(User.builder()
                            .email(EQ_B_EMAIL)
                            .password(passwordEncoder.encode("testpass123"))
                            .name("데모 장비공급사 B")
                            .role(Role.EQUIPMENT_SUPPLIER)
                            .companyId(companyId)
                            .isCompanyAdmin(true)
                            .enabled(true)
                            .build()));
        }

        private Equipment ensureEqB(Long supplierId, String vehicleNo, EquipmentCategory cat,
                                     String model, String mfg, int year) {
            return equipments.findAll().stream()
                    .filter(e -> vehicleNo.equals(e.getVehicleNo()))
                    .findFirst()
                    .orElseGet(() -> saveEquipment(supplierId, vehicleNo, cat, model, mfg, year));
        }

        private void seedQuotationsForExistingDemo() {
            Company bp = companies.findByBusinessNumber("111-11-11111").orElse(null);
            if (bp == null) { log.warn("quotation seed: BP company missing, skip"); return; }
            // 첫 BP user 를 발주자로
            Long bpUserId = users.findAll().stream()
                    .filter(u -> u.getRole() == Role.BP && u.getCompanyId() != null
                            && u.getCompanyId().equals(bp.getId()))
                    .findFirst().map(User::getId).orElse(null);
            if (bpUserId == null) { log.warn("quotation seed: BP user missing, skip"); return; }

            // 기존 EQ 공급사 A 의 장비
            Long e1 = findEqIdByVehicle("12가1234"); // CRANE 200톤
            Long e2 = findEqIdByVehicle("23나5678"); // AERIAL_LIFT 45m
            Company eqA = companies.findByBusinessNumber("222-22-22222").orElse(null);
            Long eqAUserId = users.findAll().stream()
                    .filter(u -> u.getRole() == Role.EQUIPMENT_SUPPLIER && u.getCompanyId() != null
                            && eqA != null && u.getCompanyId().equals(eqA.getId()))
                    .findFirst().map(User::getId).orElse(null);
            if (eqA == null || eqAUserId == null || e1 == null || e2 == null) {
                log.warn("quotation seed: EQ supplier A or equipments missing, skip");
                return;
            }

            // 추가 EQ 공급사 B + master user + 장비 2개
            Company eqB = ensureEqSupplierB();
            User eqBUser = ensureEqSupplierBUser(eqB.getId());
            Equipment e4 = ensureEqB(eqB.getId(), "45라3456", EquipmentCategory.CRANE,
                    "250톤 크롤러크레인", "삼성", 2022);
            Equipment e5 = ensureEqB(eqB.getId(), "56마7890", EquipmentCategory.AERIAL_LIFT,
                    "35m 고소작업차", "현대", 2023);

            LocalDate today = LocalDate.now();
            LocalDate periodStart = today.plusDays(7);
            LocalDate periodEnd = today.plusDays(21);

            // 견적 1: CRANE 200톤급 (count 2)
            QuotationRequest qr1 = quotationRequests.save(QuotationRequest.builder()
                    .requestedByUserId(bpUserId)
                    .bpCompanyId(bp.getId())
                    .workPeriodStart(periodStart)
                    .workPeriodEnd(periodEnd)
                    .requestType(QuotationRequestType.EQUIPMENT)
                    .equipmentCategory(EquipmentCategory.CRANE)
                    .specText("200톤 이상 크롤러크레인, 붐 60m+")
                    .count(2)
                    .proposedDailyRate(1_500_000)
                    .proposedMonthlyRate(35_000_000)
                    .notes("화성 신공장 증축. 2주간 일대 정산")
                    .mode(QuotationMode.OPEN_BID)
                    .workLocationText("경기 화성시 신공장 부지")
                    .build());

            // 견적 1 에 대한 제안 3개 — 다양한 가격/상태
            quotationProposals.save(QuotationProposal.builder()
                    .requestId(qr1.getId())
                    .supplierCompanyId(eqA.getId())
                    .proposedByUserId(eqAUserId)
                    .equipmentId(e1)
                    .dailyRate(1_400_000)
                    .monthlyRate(32_000_000)
                    .note("200톤 크롤러크레인 즉시 배차 가능")
                    .build());
            quotationProposals.save(QuotationProposal.builder()
                    .requestId(qr1.getId())
                    .supplierCompanyId(eqB.getId())
                    .proposedByUserId(eqBUser.getId())
                    .equipmentId(e4.getId())
                    .dailyRate(1_800_000)
                    .monthlyRate(40_000_000)
                    .note("250톤급 — 더 큰 작업 반경 가능")
                    .build());

            // 견적 2: AERIAL_LIFT 45m (count 1)
            QuotationRequest qr2 = quotationRequests.save(QuotationRequest.builder()
                    .requestedByUserId(bpUserId)
                    .bpCompanyId(bp.getId())
                    .workPeriodStart(periodStart)
                    .workPeriodEnd(periodStart.plusDays(5))
                    .requestType(QuotationRequestType.EQUIPMENT)
                    .equipmentCategory(EquipmentCategory.AERIAL_LIFT)
                    .specText("35m 이상, 사다리식")
                    .count(1)
                    .proposedDailyRate(600_000)
                    .notes("외벽 점검 작업")
                    .mode(QuotationMode.OPEN_BID)
                    .workLocationText("서울 강남구 사옥")
                    .build());

            quotationProposals.save(QuotationProposal.builder()
                    .requestId(qr2.getId())
                    .supplierCompanyId(eqA.getId())
                    .proposedByUserId(eqAUserId)
                    .equipmentId(e2)
                    .dailyRate(580_000)
                    .note("45m 고소작업차 — 운전수 포함")
                    .build());
            quotationProposals.save(QuotationProposal.builder()
                    .requestId(qr2.getId())
                    .supplierCompanyId(eqB.getId())
                    .proposedByUserId(eqBUser.getId())
                    .equipmentId(e5.getId())
                    .dailyRate(500_000)
                    .note("35m — 가격 경쟁력")
                    .build());
        }

        private void seedOutgoingForExistingDemo() {
            Company bp = companies.findByBusinessNumber("111-11-11111").orElse(null);
            Company eqA = companies.findByBusinessNumber("222-22-22222").orElse(null);
            Company mp = companies.findByBusinessNumber("333-33-33333").orElse(null);
            if (eqA == null || mp == null) {
                log.warn("outgoing seed: supplier companies missing, skip");
                return;
            }
            Long eqAUserId = users.findAll().stream()
                    .filter(u -> u.getRole() == Role.EQUIPMENT_SUPPLIER && u.getCompanyId() != null
                            && u.getCompanyId().equals(eqA.getId()))
                    .findFirst().map(User::getId).orElse(null);
            Long mpUserId = users.findAll().stream()
                    .filter(u -> u.getRole() == Role.MANPOWER_SUPPLIER && u.getCompanyId() != null
                            && u.getCompanyId().equals(mp.getId()))
                    .findFirst().map(User::getId).orElse(null);
            Long bpUserId = bp == null ? null : users.findAll().stream()
                    .filter(u -> u.getRole() == Role.BP && u.getCompanyId() != null
                            && u.getCompanyId().equals(bp.getId()))
                    .findFirst().map(User::getId).orElse(null);

            Long e3 = findEqIdByVehicle("34다9012");           // EXCAVATOR
            Long p5 = findPpIdByName("데모 정신호");           // SIGNALER

            LocalDate today = LocalDate.now();
            LocalDate periodStart = today.plusDays(10);
            LocalDate periodEnd = today.plusDays(20);

            // 영업견적 1: EQ A → BP (등록된 발주사)
            if (eqAUserId != null && e3 != null && bp != null && bpUserId != null) {
                outgoingQuotations.save(OutgoingQuotation.builder()
                        .supplierCompanyId(eqA.getId())
                        .sentByUserId(eqAUserId)
                        .equipmentId(e3)
                        .dailyRate(700_000)
                        .monthlyRate(16_000_000)
                        .note("30톤 굴삭기 — 토공 작업용. 다음 분기 가용 가능합니다.")
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .recipientType(OutgoingQuotation.RecipientType.REGISTERED_BP)
                        .recipientUserId(bpUserId)
                        .recipientCompanyId(bp.getId())
                        .build());
            }

            // 영업견적 2: MP → 외부 이메일 (등록 안 된 BP)
            if (mpUserId != null && p5 != null) {
                outgoingQuotations.save(OutgoingQuotation.builder()
                        .supplierCompanyId(mp.getId())
                        .sentByUserId(mpUserId)
                        .personId(p5)
                        .dailyRate(250_000)
                        .note("신호수 1명 — 단가 협의 가능합니다.")
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .recipientType(OutgoingQuotation.RecipientType.EMAIL)
                        .recipientEmail("external-bp@example.com")
                        .build());
            }
        }

        private void seedDoc(Map<String, DocumentType> typeIndex, OwnerType ownerType, Long ownerId,
                             String typeName, LocalDate expiry, boolean verified, String key, Long uploader) {
            DocumentType t = typeIndex.get(ownerType + ":" + typeName);
            if (t == null) {
                log.warn("demo seeder: document type not found, skip — {} / {}", ownerType, typeName);
                return;
            }
            Document d = documents.save(Document.builder()
                    .documentTypeId(t.getId())
                    .ownerType(ownerType)
                    .ownerId(ownerId)
                    .fileKey(key)
                    .fileName("demo-stub.pdf")
                    .fileSize(STUB_PDF.length)
                    .contentType("application/pdf")
                    .expiryDate(expiry)
                    .uploadedBy(uploader)
                    .build());
            if (verified) {
                d.markVerifiedBy(uploader);
            }
        }
    }
}
