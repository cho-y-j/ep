package com.skep.docx;

import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanEquipment;
import com.skep.workplan.WorkPlanPerson;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DOCX 템플릿에 작업계획서 데이터를 채워 출력 byte 배열로 반환.
 *
 * <h3>지원 placeholder ({key} 문법)</h3>
 * <ul>
 *   <li>{title}, {site_name}, {bp_company_name}, {work_date}, {start_time}, {end_time},
 *       {work_location}, {description}, {status}</li>
 *   <li>{equipment_list} — 줄바꿈 구분 텍스트 (장비명 / 분류 / 공급사 / 용도)</li>
 *   <li>{person_list} — 줄바꿈 구분 텍스트 (이름 / 역할 / 공급사)</li>
 *   <li>{equipment_count}, {person_count}</li>
 *   <li>{printed_at} — 출력 일시</li>
 * </ul>
 *
 * <h3>제약</h3>
 * <ul>
 *   <li>placeholder 가 여러 run 으로 split 되면 못 찾는다 (Word 의 "field" 가 아닌 단순 텍스트 가정).
 *       템플릿 작성 시 placeholder 한 단위로 한 번에 입력 권장.</li>
 *   <li>표 행 반복(`{#equipment}...{/equipment}`)은 미지원 — 첫 번째 단순 placeholder 만 지원. 후속 phase 로.</li>
 * </ul>
 */
@Component
public class WorkPlanDocxExporter {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] export(InputStream templateStream, WorkPlanContext ctx) throws Exception {
        // ZIP 레벨 직접 조작 — Apache POI 의 OOXML 변환이 senkore 같은 복잡한 template 의 일부 namespace/extension 을
        // 손상시키는 문제 회피. 모든 entries 그대로 복사하고 word/*.xml 내의 placeholder 만 치환.
        Map<String, String> values = buildValues(ctx);
        byte[] template = templateStream.readAllBytes();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(template));
             java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(out)) {
            java.util.zip.ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                java.io.ByteArrayOutputStream entryOut = new java.io.ByteArrayOutputStream();
                int r; while ((r = zin.read(buf)) >= 0) entryOut.write(buf, 0, r);
                byte[] content = entryOut.toByteArray();
                java.util.zip.ZipEntry n = new java.util.zip.ZipEntry(e.getName());
                // 내용이 변경될 가능성이 있는 XML 은 DEFLATED, 그 외도 DEFLATED 로 통일 (STORED size 재계산 회피)
                n.setMethod(java.util.zip.ZipEntry.DEFLATED);
                zout.putNextEntry(n);
                String name = e.getName();
                boolean isTextXml = name.endsWith(".xml")
                        && (name.equals("word/document.xml")
                            || name.startsWith("word/header")
                            || name.startsWith("word/footer"));
                if (isTextXml) {
                    String xml = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                    xml = mergeSplitRuns(xml); // split 된 placeholder 합치기 시도
                    for (var v : values.entrySet()) {
                        xml = xml.replace("{" + v.getKey() + "}",
                                escapeXml(v.getValue() == null ? "" : v.getValue()));
                    }
                    content = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                zout.write(content);
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /**
     * Word 가 placeholder 를 여러 run 으로 분할 저장한 경우 ({, site, })
     * 단순 string replace 가 fail. 인접 run 사이의 closing/opening tag 를 합쳐 단일 텍스트로.
     * 단, run 의 rPr 이 다르면 스타일 손실 위험이 있어 텍스트 노드 사이의 간단 case 만 처리.
     */
    private static String mergeSplitRuns(String xml) {
        // </w:t>...</w:r>...<w:r ...>...<w:t...> 사이의 닫고/여는 태그를 한 번에 제거.
        // rPr 없는 단순 케이스만 안전하게 합침.
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "</w:t></w:r><w:r(?:\\s+[^>]*)?><w:t(?:\\s+[^>]*)?>");
        return p.matcher(xml).replaceAll("");
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void replaceInTable(XWPFTable table, Map<String, String> values) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) replacePlaceholders(p, values);
                for (XWPFTable nested : cell.getTables()) replaceInTable(nested, values);
            }
        }
    }

    /**
     * paragraph 의 모든 run 텍스트를 합친 뒤 {key} 치환하고,
     * 첫 run 에 결과를 넣고 나머지 run 은 비운다 (스타일 일부 손실 트레이드오프).
     */
    private void replacePlaceholders(XWPFParagraph paragraph, Map<String, String> values) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) return;
        StringBuilder all = new StringBuilder();
        for (XWPFRun r : runs) {
            String t = r.text();
            if (t != null) all.append(t);
        }
        String original = all.toString();
        if (!original.contains("{")) return;
        String replaced = original;
        for (Map.Entry<String, String> e : values.entrySet()) {
            replaced = replaced.replace("{" + e.getKey() + "}", e.getValue());
        }
        if (replaced.equals(original)) return;
        runs.get(0).setText(replaced, 0);
        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).setText("", 0);
        }
    }

    private Map<String, String> buildValues(WorkPlanContext ctx) {
        WorkPlan wp = ctx.workPlan;
        Map<String, String> v = new HashMap<>();
        v.put("title", nz(wp.getTitle()));
        v.put("site_name", nz(ctx.siteName));
        v.put("bp_company_name", nz(ctx.bpCompanyName));
        v.put("work_date", wp.getWorkDate() != null ? wp.getWorkDate().toString() : "");
        v.put("start_time", wp.getStartTime() != null ? wp.getStartTime().toString().substring(0, 5) : "");
        v.put("end_time", wp.getEndTime() != null ? wp.getEndTime().toString().substring(0, 5) : "");
        v.put("work_location", nz(wp.getWorkLocation()));
        v.put("description", nz(wp.getDescription()));
        v.put("status", wp.getStatus().name());

        StringBuilder eqList = new StringBuilder();
        for (var pair : ctx.equipmentRows) {
            WorkPlanEquipment e = pair.row();
            eqList.append("• ").append(nz(pair.name()));
            if (pair.category() != null) eqList.append(" [").append(pair.category()).append("]");
            eqList.append(" / ").append(nz(pair.supplierName()));
            if (e.getPurpose() != null && !e.getPurpose().isBlank()) {
                eqList.append(" — ").append(e.getPurpose());
            }
            eqList.append('\n');
        }
        v.put("equipment_list", eqList.toString().trim());
        v.put("equipment_count", String.valueOf(ctx.equipmentRows.size()));

        StringBuilder pList = new StringBuilder();
        for (var pair : ctx.personRows) {
            WorkPlanPerson p = pair.row();
            pList.append("• ").append(nz(pair.name()));
            if (p.getRole() != null && !p.getRole().isBlank()) pList.append(" (").append(p.getRole()).append(")");
            pList.append(" / ").append(nz(pair.supplierName()));
            pList.append('\n');
        }
        v.put("person_list", pList.toString().trim());
        v.put("person_count", String.valueOf(ctx.personRows.size()));

        v.put("printed_at", java.time.LocalDateTime.now().format(DT));
        return v;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public record EquipmentRow(WorkPlanEquipment row, String name, String category, String supplierName) {}
    public record PersonRow(WorkPlanPerson row, String name, String supplierName) {}

    public record WorkPlanContext(
            WorkPlan workPlan,
            String siteName,
            String bpCompanyName,
            List<EquipmentRow> equipmentRows,
            List<PersonRow> personRows
    ) {}
}
