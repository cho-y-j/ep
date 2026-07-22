package com.skep.collection;

import com.skep.common.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 서류 업로드 어댑터 — 파일 1개면 그대로, 2개 이상이면 올린 순서대로 1개 PDF로 병합.
 * 병합 결과를 단일 MultipartFile 로 감싸 각 컨트롤러의 기존 '단일 저장' 로직을 그대로 재사용한다.
 * (병합 자체는 {@link PdfMergeService} 재사용 — 이미지/PDF 혼합 지원.)
 */
@Component
public class DocumentUploadMerger {

    private final PdfMergeService pdfMerge;

    public DocumentUploadMerger(PdfMergeService pdfMerge) {
        this.pdfMerge = pdfMerge;
    }

    /** files 가 1개면 그대로 반환, 2개 이상이면 순서대로 병합한 PDF 를 단일 MultipartFile 로 반환. */
    public MultipartFile mergeToSingle(MultipartFile[] files) {
        List<MultipartFile> present = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) present.add(f);
            }
        }
        if (present.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "업로드된 파일이 없습니다");
        if (present.size() == 1) return present.get(0);

        List<PdfMergeService.Part> parts = new ArrayList<>(present.size());
        for (MultipartFile f : present) {
            try {
                parts.add(new PdfMergeService.Part(f.getBytes(), f.getContentType(), f.getOriginalFilename()));
            } catch (IOException e) {
                throw ApiException.badRequest("FILE_READ_FAILED", "파일을 읽지 못했습니다");
            }
        }
        byte[] pdf = pdfMerge.merge(parts);
        return new InMemoryMultipartFile(mergedName(present), "application/pdf", pdf);
    }

    /** "{첫 파일명} 외 N건.pdf" — 병합 저장 파일명. */
    private static String mergedName(List<MultipartFile> files) {
        String first = files.get(0).getOriginalFilename();
        String base = (first == null || first.isBlank()) ? "서류" : first.replaceAll("(?i)\\.[a-z0-9]+$", "");
        return base + " 외 " + (files.size() - 1) + "건.pdf";
    }

    /** 병합 PDF 를 담는 메모리 MultipartFile — 저장 파이프라인이 MultipartFile 을 요구하므로. */
    private static final class InMemoryMultipartFile implements MultipartFile {
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String originalFilename, String contentType, byte[] content) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
