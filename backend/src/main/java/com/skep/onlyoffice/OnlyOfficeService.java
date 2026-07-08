package com.skep.onlyoffice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.docx.DocxTemplate;
import com.skep.docx.DocxTemplateService;
import com.skep.docx.WorkPlanDocxExporter;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.storage.FileStorage;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanEquipment;
import com.skep.workplan.WorkPlanEquipmentRepository;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanService;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OnlyOffice Document Server 통합.
 *
 * <h3>플로우</h3>
 * <ol>
 *   <li>프론트가 {@code /api/onlyoffice/work-plan/{id}/config?templateId=N} 호출</li>
 *   <li>백엔드: 첫 호출이면 템플릿에서 DOCX 생성 + storage 저장 + work_plans.current_docx_key 기록.
 *       OnlyOffice DocsAPI 용 config (document.url, editorConfig.callbackUrl, token=JWT) 반환</li>
 *   <li>프론트: <code>DocsAPI.DocEditor()</code> 로 에디터 렌더 (회사 OnlyOffice 도메인 JS 로드)</li>
 *   <li>OnlyOffice 가 document.url 로 우리 서버에 GET 요청 → 파일 응답</li>
 *   <li>편집 후 OnlyOffice 가 callbackUrl 에 POST → status=2(저장요청) 또는 6(강제저장) 시 url 의 파일을
 *       다운로드해 같은 storage key 에 덮어씀</li>
 *   <li>{@code {error: 0}} 응답 필수 — 비표준 응답이면 OnlyOffice 가 변경사항 잃었다고 판단</li>
 * </ol>
 *
 * <h3>JWT 서명</h3>
 * Document Server 가 JWT 활성화되어 있으면 모든 config + 콜백 본문이 signed JWT 로 wrapping 된다.
 * HS256, secret 은 양쪽 일치해야 함.
 */
@Service
@Transactional
public class OnlyOfficeService {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OnlyOfficeProperties props;
    private final WorkPlanRepository workPlans;
    private final WorkPlanEquipmentRepository wpe;
    private final WorkPlanPersonRepository wpp;
    private final EquipmentRepository equipment;
    private final PersonRepository persons;
    private final SiteRepository sites;
    private final CompanyRepository companies;
    private final WorkPlanService workPlanService;
    private final DocxTemplateService templates;
    private final WorkPlanDocxExporter exporter;
    private final FileStorage storage;
    private final WebClient webClient;

    public OnlyOfficeService(OnlyOfficeProperties props,
                             WorkPlanRepository workPlans,
                             WorkPlanEquipmentRepository wpe,
                             WorkPlanPersonRepository wpp,
                             EquipmentRepository equipment,
                             PersonRepository persons,
                             SiteRepository sites,
                             CompanyRepository companies,
                             WorkPlanService workPlanService,
                             DocxTemplateService templates,
                             WorkPlanDocxExporter exporter,
                             FileStorage storage) {
        this.props = props;
        this.workPlans = workPlans;
        this.wpe = wpe;
        this.wpp = wpp;
        this.equipment = equipment;
        this.persons = persons;
        this.sites = sites;
        this.companies = companies;
        this.workPlanService = workPlanService;
        this.templates = templates;
        this.exporter = exporter;
        this.storage = storage;
        // OnlyOffice 콜백에서 DOCX (수 MB 가능) 다운로드. 기본 256KB 버퍼는 부족해서 16MB 로 확장.
        this.webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    public Map<String, Object> status() {
        Map<String, Object> r = new HashMap<>();
        r.put("enabled", props.enabled());
        r.put("server_url", props.getServerUrl());
        return r;
    }

    /**
     * OnlyOffice DocsAPI 에 넘길 config 빌드. token 까지 포함.
     *
     * 권한: {@link WorkPlanService#get} 으로 plan 조회 권한 + 가시성을 먼저 검증한다.
     * 통과하지 못하면 throw — config 가 아예 발급되지 않으므로 token 도 새지 않는다.
     */
    public Map<String, Object> buildEditorConfig(Long planId, Long templateId, AuthenticatedUser actor) {
        if (!props.enabled()) {
            throw ApiException.badRequest("ONLYOFFICE_DISABLED",
                    "OnlyOffice 가 설정되지 않았습니다 (ONLYOFFICE_URL 미설정)");
        }
        workPlanService.get(planId, actor);

        WorkPlan wp = workPlans.findById(planId)
                .orElseThrow(() -> ApiException.notFound("WORK_PLAN_NOT_FOUND", "작업계획서 없음"));

        // 첫 진입이거나 다른 템플릿 선택이면 새로 빌드.
        if (wp.getCurrentDocxKey() == null
                || (templateId != null && !templateId.equals(wp.getCurrentDocxTemplateId()))) {
            DocxTemplate t = templates.getForExport(
                    templateId != null ? templateId : firstTemplateIdOrThrow(actor),
                    actor);
            byte[] bytes = generateDocx(wp, t);
            if (wp.getCurrentDocxKey() != null) {
                // 기존 키 덮어쓰기 (key 보존)
                storage.overwrite(wp.getCurrentDocxKey(), bytes);
                wp.setDocxState(wp.getCurrentDocxKey(), t.getId());
            } else {
                String key = storage.storeBytes(bytes, "docx");
                wp.setDocxState(key, t.getId());
            }
        }

        String publicBackend = props.getPublicBackendUrl() != null
                ? props.getPublicBackendUrl()
                : "";
        // 단일 plan 의 docKey 는 변경 시 캐시 무효화를 위해 업데이트 시각 hash 사용.
        String docKey = "wp-" + wp.getId() + "-" + wp.getUpdatedAt().toString().replaceAll("[^0-9]", "");
        String fileToken = signFileAccessToken(wp.getId());

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", "docx");
        document.put("key", docKey);
        document.put("title", wp.getTitle() + ".docx");
        // OnlyOffice 가 URL 의 확장자로 파일 타입을 추정하므로 .docx 명시.
        document.put("url", publicBackend + "/api/onlyoffice/work-plan/" + wp.getId() + "/file.docx?token=" + fileToken);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", "edit");
        editorConfig.put("lang", "ko");
        editorConfig.put("callbackUrl", publicBackend + "/api/onlyoffice/work-plan/" + wp.getId() + "/callback?token=" + fileToken);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", String.valueOf(actor.id()));
        user.put("name", actor.name());
        editorConfig.put("user", user);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("documentType", "word");
        config.put("document", document);
        config.put("editorConfig", editorConfig);

        // JWT 서명 (Document Server 와 동일 secret).
        String jwt = signConfigToken(config);
        if (jwt != null) config.put("token", jwt);
        return config;
    }

    /** OnlyOffice 가 document.url 로 fetch 해 갈 때 응답할 파일 (token 검증 후). */
    public Resource loadFile(Long planId, String token) {
        verifyFileAccessToken(planId, token);
        WorkPlan wp = workPlans.findById(planId)
                .orElseThrow(() -> ApiException.notFound("WORK_PLAN_NOT_FOUND", "작업계획서 없음"));
        if (wp.getCurrentDocxKey() == null) {
            throw ApiException.notFound("NO_DOCX", "편집 세션이 시작되지 않았습니다");
        }
        return storage.load(wp.getCurrentDocxKey());
    }

    /**
     * OnlyOffice 콜백 처리.
     * status: 0=찾을수없음 1=편집중 2=저장준비됨 3=저장에러 4=닫힘(저장불필요) 6=강제저장 7=강제저장에러.
     * 저장은 status 2 또는 6 에서 처리.
     */
    public Map<String, Object> handleCallback(Long planId, String token, Map<String, Object> body) {
        verifyFileAccessToken(planId, token);
        // 본문이 JWT signed 라면 verify 후 payload 사용.
        Object signedToken = body.get("token");
        if (signedToken instanceof String s && props.getJwtSecret() != null && !props.getJwtSecret().isBlank()) {
            try {
                var claims = Jwts.parser()
                        .verifyWith(hmacKey())
                        .build()
                        .parseSignedClaims(s)
                        .getPayload();
                // body 의 payload 부분으로 사용.
                Object payload = claims.get("payload");
                if (payload instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mp = (Map<String, Object>) m;
                    body = mp;
                }
            } catch (Exception e) {
                log.warn("OnlyOffice callback JWT verify failed: {}", e.getMessage());
                return Map.of("error", 1);
            }
        }

        Object statusObj = body.get("status");
        int status = statusObj instanceof Number ? ((Number) statusObj).intValue() : -1;
        if (status == 2 || status == 6) {
            String url = (String) body.get("url");
            if (url == null) return Map.of("error", 1);
            // S-Audit P1-C: SSRF 차단 — callback url 의 host 가 OnlyOffice server-url 의 host 와 같아야 fetch.
            if (!isAllowedCallbackUrl(url)) {
                log.warn("OnlyOffice callback url rejected (host mismatch): {}", url);
                return Map.of("error", 1);
            }
            try {
                byte[] bytes = webClient.get().uri(url)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block(Duration.ofSeconds(60));
                if (bytes == null) return Map.of("error", 1);
                WorkPlan wp = workPlans.findById(planId)
                        .orElseThrow(() -> ApiException.notFound("WORK_PLAN_NOT_FOUND", "작업계획서 없음"));
                if (wp.getCurrentDocxKey() == null) {
                    String key = storage.storeBytes(bytes, "docx");
                    wp.setDocxState(key, wp.getCurrentDocxTemplateId());
                } else {
                    storage.overwrite(wp.getCurrentDocxKey(), bytes);
                }
                log.info("OnlyOffice saved DOCX for work_plan #{} ({} bytes)", planId, bytes.length);
            } catch (Exception e) {
                log.error("OnlyOffice callback save failed for plan #{}", planId, e);
                return Map.of("error", 1);
            }
        }
        return Map.of("error", 0);
    }

    // ===== 헬퍼 =====

    private byte[] generateDocx(WorkPlan wp, DocxTemplate template) {
        Site site = sites.findById(wp.getSiteId()).orElse(null);
        Company bp = companies.findById(wp.getBpCompanyId()).orElse(null);
        List<WorkPlanEquipment> wpeList = wpe.findByWorkPlanIdOrderByIdAsc(wp.getId());
        List<WorkPlanPerson> wppList = wpp.findByWorkPlanIdOrderByIdAsc(wp.getId());

        Map<Long, Equipment> eqMap = new HashMap<>();
        equipment.findAllById(wpeList.stream().map(WorkPlanEquipment::getEquipmentId).toList())
                .forEach(e -> eqMap.put(e.getId(), e));
        Map<Long, Person> personMap = new HashMap<>();
        persons.findAllById(wppList.stream().map(WorkPlanPerson::getPersonId).toList())
                .forEach(p -> personMap.put(p.getId(), p));
        java.util.Set<Long> companyIds = new java.util.HashSet<>();
        wpeList.forEach(x -> companyIds.add(x.getSupplierCompanyId()));
        wppList.forEach(x -> companyIds.add(x.getSupplierCompanyId()));
        Map<Long, Company> companyMap = new HashMap<>();
        companies.findAllById(companyIds).forEach(c -> companyMap.put(c.getId(), c));

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
        try (InputStream in = templates.loadFile(template).getInputStream()) {
            return exporter.export(in, ctx);
        } catch (Exception e) {
            throw ApiException.badRequest("DOCX_EXPORT_FAILED", "DOCX 생성 실패: " + e.getMessage());
        }
    }

    private Long firstTemplateIdOrThrow(AuthenticatedUser actor) {
        var list = templates.listVisible(DocxTemplateService.TARGET_WORK_PLAN, actor);
        if (list.isEmpty()) {
            throw ApiException.badRequest("NO_TEMPLATE",
                    "OnlyOffice 편집을 시작할 DOCX 템플릿이 없습니다. 먼저 업로드하세요.");
        }
        return list.get(0).getId();
    }

    /**
     * plan 별 file 접근 토큰 (1시간 유효). callback 검증에도 사용.
     *
     * S-Audit P1-B: jwt-secret 이 비어있으면 fallback 으로 plan id 만 토큰으로 쓰던 경로가 있었으나,
     * 예측 가능한 토큰으로 인증을 우회할 수 있어 secret 미설정 시 OnlyOffice 자체를 사용 불가로 한다.
     */
    private String signFileAccessToken(Long planId) {
        if (props.getJwtSecret() == null || props.getJwtSecret().length() < 16) {
            throw ApiException.badRequest("ONLYOFFICE_NOT_CONFIGURED",
                    "OnlyOffice 토큰 시크릿이 설정되지 않아 편집 세션을 시작할 수 없습니다 (ONLYOFFICE_JWT_SECRET 16자 이상 필요)");
        }
        return Jwts.builder()
                .subject("oo-file")
                .claim("plan_id", planId)
                .expiration(new java.util.Date(System.currentTimeMillis() + 60 * 60 * 1000L))
                .signWith(hmacKey())
                .compact();
    }

    private void verifyFileAccessToken(Long planId, String token) {
        if (token == null || token.isBlank()) throw ApiException.unauthorized("BAD_TOKEN", "토큰 누락");
        if (props.getJwtSecret() == null || props.getJwtSecret().length() < 16) {
            // S-Audit P1-B: dev fallback 제거 — 시크릿 없으면 무조건 거부.
            throw ApiException.unauthorized("ONLYOFFICE_NOT_CONFIGURED",
                    "OnlyOffice 시크릿 미설정 — 토큰 검증 불가");
        }
        try {
            var claims = Jwts.parser().verifyWith(hmacKey()).build()
                    .parseSignedClaims(token).getPayload();
            Number pid = (Number) claims.get("plan_id");
            if (pid == null || pid.longValue() != planId) {
                throw ApiException.unauthorized("BAD_TOKEN", "plan_id 불일치");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.unauthorized("BAD_TOKEN", "토큰 검증 실패");
        }
    }

    /** OnlyOffice config JWT 서명 (HS256). secret 미설정이면 null 반환 — Document Server 도 JWT 비활성화 가정. */
    private String signConfigToken(Map<String, Object> config) {
        if (props.getJwtSecret() == null || props.getJwtSecret().isBlank()) return null;
        try {
            String json = MAPPER.writeValueAsString(config);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = MAPPER.readValue(json, Map.class);
            // 1시간 만료 — 편집 세션 토큰 재발급 강제. JWT 유출 시 영구 사용 방지.
            return Jwts.builder().claims(claims)
                    .expiration(new java.util.Date(System.currentTimeMillis() + 60 * 60 * 1000L))
                    .signWith(hmacKey())
                    .compact();
        } catch (Exception e) {
            throw new IllegalStateException("OnlyOffice config 서명 실패", e);
        }
    }

    private SecretKeySpec hmacKey() {
        return new SecretKeySpec(props.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * S-Audit P1-C: callback url 이 OnlyOffice Document Server 와 같은 host 인지 확인.
     * SSRF (내부망/메타데이터 endpoint) 호출 차단.
     *
     * - server-url 의 host 와 같거나, server-url 이 빈 host 면 거부.
     * - localhost / 127.0.0.1 / 169.254.x.x 등은 server-url 자체가 그렇지 않으면 자동 거부 (host 불일치).
     */
    private boolean isAllowedCallbackUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            java.net.URI server = java.net.URI.create(props.getServerUrl());
            String h = uri.getHost();
            String sh = server.getHost();
            if (h == null || sh == null) return false;
            // host + port 둘 다 일치해야 통과 — server-url 이 localhost 라도 다른 포트(backend/redis 등)
            // 로의 SSRF 차단. https only 강제하지 않음 — Document Server 가 보내는 url scheme 을 그대로 따름.
            return h.equalsIgnoreCase(sh) && uri.getPort() == server.getPort();
        } catch (Exception e) {
            return false;
        }
    }
}
