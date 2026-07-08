package com.skep.docx;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanEquipment;
import com.skep.workplan.WorkPlanEquipmentRepository;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 작업계획서 DOCX 출력 endpoint.
 * - GET /api/work-plans/{id}/export/docx?templateId=N
 * - WorkPlanService.get() 권한 검사를 그대로 통과해야 함 (BP/공급사 가시성).
 * - 템플릿은 DocxTemplateService.getForExport() 권한.
 */
@RestController
@RequestMapping("/api/work-plans")
public class WorkPlanExportController {

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

    public WorkPlanExportController(WorkPlanService workPlanService,
                                    WorkPlanRepository workPlans,
                                    WorkPlanEquipmentRepository wpe,
                                    WorkPlanPersonRepository wpp,
                                    EquipmentRepository equipment,
                                    PersonRepository persons,
                                    SiteRepository sites,
                                    CompanyRepository companies,
                                    DocxTemplateService templates,
                                    WorkPlanDocxExporter exporter) {
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
    }

    @GetMapping("/{id}/export/docx")
    public ResponseEntity<ByteArrayResource> exportDocx(@PathVariable Long id,
                                                        @RequestParam Long templateId,
                                                        @CurrentUser AuthenticatedUser actor) {
        // 권한: 우선 WorkPlanService.get() 으로 가시성 확인 (BP/공급사/ADMIN scope).
        workPlanService.get(id, actor);

        WorkPlan wp = workPlans.findById(id)
                .orElseThrow(() -> ApiException.notFound("WORK_PLAN_NOT_FOUND", "작업계획서를 찾을 수 없습니다"));
        Site site = sites.findById(wp.getSiteId()).orElse(null);
        Company bp = companies.findById(wp.getBpCompanyId()).orElse(null);

        List<WorkPlanEquipment> wpeList = wpe.findByWorkPlanIdOrderByIdAsc(id);
        List<WorkPlanPerson> wppList = wpp.findByWorkPlanIdOrderByIdAsc(id);

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
            byte[] bytes = exporter.export(in, ctx);
            String fname = URLEncoder.encode(safeFilename(wp.getTitle()) + ".docx", StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fname)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(new ByteArrayResource(bytes));
        } catch (Exception e) {
            throw ApiException.badRequest("DOCX_EXPORT_FAILED", "DOCX 생성 실패: " + e.getMessage());
        }
    }

    private static <T> Map<Long, T> mapById(Iterable<T> items, java.util.function.Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>();
        items.forEach(t -> m.put(idFn.apply(t), t));
        return m;
    }

    private static String safeFilename(String s) {
        if (s == null) return "work-plan";
        return com.skep.common.SafeText.sanitizeFileName(s);
    }
}
