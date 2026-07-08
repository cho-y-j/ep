package com.skep.docx;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanEquipment;
import com.skep.workplan.WorkPlanEquipmentRepository;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanService;
import com.skep.worksheet.WorksheetMailService;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 작업계획서 DOCX 렌더링 + DOCX→PDF 변환 헬퍼.
 * WorkPlanExportController (다운로드) 와 SignatureService (사인 메일 첨부) 가 공유.
 *
 * - renderDocx: 첫번째 active 템플릿 자동 선택 가능
 * - renderPdf: DOCX 렌더 후 LibreOffice 로 PDF 변환
 */
@Service
public class WorkPlanPdfService {

    private final WorkPlanService workPlanService;
    private final WorkPlanRepository workPlans;
    private final WorkPlanEquipmentRepository wpe;
    private final WorkPlanPersonRepository wpp;
    private final EquipmentRepository equipment;
    private final PersonRepository persons;
    private final SiteRepository sites;
    private final CompanyRepository companies;
    private final DocxTemplateService templates;
    private final WorkPlanDocxExporter exporter;
    private final WorksheetMailService worksheetMail;

    public WorkPlanPdfService(WorkPlanService workPlanService, WorkPlanRepository workPlans,
                              WorkPlanEquipmentRepository wpe, WorkPlanPersonRepository wpp,
                              EquipmentRepository equipment, PersonRepository persons,
                              SiteRepository sites, CompanyRepository companies,
                              DocxTemplateService templates, WorkPlanDocxExporter exporter,
                              WorksheetMailService worksheetMail) {
        this.workPlanService = workPlanService;
        this.workPlans = workPlans;
        this.wpe = wpe;
        this.wpp = wpp;
        this.equipment = equipment;
        this.persons = persons;
        this.sites = sites;
        this.companies = companies;
        this.templates = templates;
        this.exporter = exporter;
        this.worksheetMail = worksheetMail;
    }

    /** WORK_PLAN 가시 템플릿 중 첫 번째. 없으면 null. */
    public Long defaultTemplateId(AuthenticatedUser actor) {
        List<DocxTemplate> list = templates.listVisible(DocxTemplateService.TARGET_WORK_PLAN, actor);
        return list.isEmpty() ? null : list.get(0).getId();
    }

    /** actor 없이 (공개 토큰 접근) WORK_PLAN 가시 템플릿 중 첫 번째. */
    public Long defaultTemplateIdAny() {
        List<DocxTemplate> list = templates.listVisible(DocxTemplateService.TARGET_WORK_PLAN, null);
        return list.isEmpty() ? null : list.get(0).getId();
    }

    /** 사인 토큰 등 공개 컨텍스트에서 호출. 권한 체크 skip — 호출 전에 토큰 검증 완료해야 함. */
    public byte[] renderPdfPublic(Long workPlanId) {
        Long tid = defaultTemplateIdAny();
        if (tid == null) {
            throw ApiException.badRequest("NO_TEMPLATE", "사용 가능한 작업계획서 템플릿이 없습니다");
        }
        byte[] docx = renderDocxPublic(workPlanId, tid);
        try {
            return worksheetMail.convertDocxToPdf(docx, "work-plan-" + workPlanId);
        } catch (Exception e) {
            throw ApiException.badRequest("PDF_RENDER_FAILED", "PDF 변환 실패: " + e.getMessage());
        }
    }

    private byte[] renderDocxPublic(Long workPlanId, Long templateId) {
        WorkPlan wp = workPlans.findById(workPlanId)
                .orElseThrow(() -> ApiException.notFound("WORK_PLAN_NOT_FOUND", "작업계획서를 찾을 수 없습니다"));
        Site site = wp.getSiteId() != null ? sites.findById(wp.getSiteId()).orElse(null) : null;
        Company bp = companies.findById(wp.getBpCompanyId()).orElse(null);

        List<WorkPlanEquipment> wpeList = wpe.findByWorkPlanIdOrderByIdAsc(workPlanId);
        List<WorkPlanPerson> wppList = wpp.findByWorkPlanIdOrderByIdAsc(workPlanId);

        Map<Long, Equipment> eqMap = mapById(
                equipment.findAllById(wpeList.stream().map(WorkPlanEquipment::getEquipmentId).toList()),
                Equipment::getId);
        Map<Long, Person> personMap = mapById(
                persons.findAllById(wppList.stream().map(WorkPlanPerson::getPersonId).toList()),
                Person::getId);
        java.util.Set<Long> companyIds = new java.util.HashSet<>();
        wpeList.forEach(x -> companyIds.add(x.getSupplierCompanyId()));
        wppList.forEach(x -> companyIds.add(x.getSupplierCompanyId()));
        Map<Long, Company> companyMap = mapById(companies.findAllById(companyIds), Company::getId);

        var equipmentRows = wpeList.stream().map(row -> {
            Equipment e = eqMap.get(row.getEquipmentId());
            String name = e != null ? Optional.ofNullable(e.getModel())
                    .orElse(Optional.ofNullable(e.getVehicleNo()).orElse("(이름없음)")) : "(삭제됨)";
            Company c = companyMap.get(row.getSupplierCompanyId());
            return new WorkPlanDocxExporter.EquipmentRow(row, name,
                    e != null ? e.getCategory() : null,
                    c != null ? c.getName() : null);
        }).toList();

        var personRows = wppList.stream().map(row -> {
            Person p = personMap.get(row.getPersonId());
            Company c = companyMap.get(row.getSupplierCompanyId());
            return new WorkPlanDocxExporter.PersonRow(row,
                    p != null ? p.getName() : "(삭제됨)",
                    c != null ? c.getName() : null);
        }).toList();

        var ctx = new WorkPlanDocxExporter.WorkPlanContext(
                wp,
                site != null ? site.getName() : null,
                bp != null ? bp.getName() : null,
                equipmentRows, personRows);

        DocxTemplate template = templates.getByIdPublic(templateId);
        try (InputStream in = templates.loadFile(template).getInputStream()) {
            return exporter.export(in, ctx);
        } catch (Exception e) {
            throw ApiException.badRequest("DOCX_EXPORT_FAILED", "DOCX 생성 실패: " + e.getMessage());
        }
    }

    public byte[] renderDocx(Long workPlanId, Long templateId, AuthenticatedUser actor) {
        workPlanService.get(workPlanId, actor);
        WorkPlan wp = workPlans.findById(workPlanId)
                .orElseThrow(() -> ApiException.notFound("WORK_PLAN_NOT_FOUND", "작업계획서를 찾을 수 없습니다"));
        Site site = sites.findById(wp.getSiteId()).orElse(null);
        Company bp = companies.findById(wp.getBpCompanyId()).orElse(null);

        List<WorkPlanEquipment> wpeList = wpe.findByWorkPlanIdOrderByIdAsc(workPlanId);
        List<WorkPlanPerson> wppList = wpp.findByWorkPlanIdOrderByIdAsc(workPlanId);

        Map<Long, Equipment> eqMap = mapById(
                equipment.findAllById(wpeList.stream().map(WorkPlanEquipment::getEquipmentId).toList()),
                Equipment::getId);
        Map<Long, Person> personMap = mapById(
                persons.findAllById(wppList.stream().map(WorkPlanPerson::getPersonId).toList()),
                Person::getId);
        java.util.Set<Long> companyIds = new java.util.HashSet<>();
        wpeList.forEach(x -> companyIds.add(x.getSupplierCompanyId()));
        wppList.forEach(x -> companyIds.add(x.getSupplierCompanyId()));
        Map<Long, Company> companyMap = mapById(companies.findAllById(companyIds), Company::getId);

        var equipmentRows = wpeList.stream().map(row -> {
            Equipment e = eqMap.get(row.getEquipmentId());
            String name = e != null ? Optional.ofNullable(e.getModel())
                    .orElse(Optional.ofNullable(e.getVehicleNo()).orElse("(이름없음)")) : "(삭제됨)";
            Company c = companyMap.get(row.getSupplierCompanyId());
            return new WorkPlanDocxExporter.EquipmentRow(row, name,
                    e != null ? e.getCategory() : null,
                    c != null ? c.getName() : null);
        }).toList();

        var personRows = wppList.stream().map(row -> {
            Person p = personMap.get(row.getPersonId());
            Company c = companyMap.get(row.getSupplierCompanyId());
            return new WorkPlanDocxExporter.PersonRow(row,
                    p != null ? p.getName() : "(삭제됨)",
                    c != null ? c.getName() : null);
        }).toList();

        var ctx = new WorkPlanDocxExporter.WorkPlanContext(
                wp,
                site != null ? site.getName() : null,
                bp != null ? bp.getName() : null,
                equipmentRows, personRows);

        DocxTemplate template = templates.getForExport(templateId, actor);
        try (InputStream in = templates.loadFile(template).getInputStream()) {
            return exporter.export(in, ctx);
        } catch (Exception e) {
            throw ApiException.badRequest("DOCX_EXPORT_FAILED", "DOCX 생성 실패: " + e.getMessage());
        }
    }

    /** DOCX → PDF. baseName 은 LibreOffice 임시 파일명. */
    public byte[] renderPdf(Long workPlanId, Long templateId, AuthenticatedUser actor) {
        byte[] docx = renderDocx(workPlanId, templateId, actor);
        try {
            return worksheetMail.convertDocxToPdf(docx, "work-plan-" + workPlanId);
        } catch (Exception e) {
            throw ApiException.badRequest("PDF_RENDER_FAILED", "PDF 변환 실패: " + e.getMessage());
        }
    }

    private static <T> Map<Long, T> mapById(Iterable<T> items, java.util.function.Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>();
        items.forEach(t -> m.put(idFn.apply(t), t));
        return m;
    }
}
