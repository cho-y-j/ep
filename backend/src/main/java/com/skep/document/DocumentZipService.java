package com.skep.document;

import com.skep.common.SafeText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 자원(장비/인원)의 서류를 zip 으로 묶는 공용 로직.
 * 이메일 발송은 자원별 단일 zip, BP 수신함 다운로드는 자원별 폴더로 합친 1개 zip 으로 재사용한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentZipService {

    private final DocumentRepository docs;
    private final DocumentService documentService;

    public record ZipResult(byte[] bytes, int docCount) {}

    /** 자원 1건의 서류를 단일 zip 으로. 서류가 하나도 없으면 null. */
    public ZipResult zipSingleResource(OwnerType type, Long ownerId) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            int included = writeEntries(zos, type, ownerId, "");
            zos.finish();
            if (included == 0) return null;
            return new ZipResult(baos.toByteArray(), included);
        } catch (Exception e) {
            log.warn("zip build fail {} {}: {}", type, ownerId, e.getMessage());
            return null;
        }
    }

    /** 자원의 서류를 열려있는 zip 스트림에 folderPrefix 아래로 기록. 포함된 서류 수 반환. */
    public int writeEntries(ZipOutputStream zos, OwnerType type, Long ownerId, String folderPrefix) {
        List<Document> docList = docs.findByOwnerTypeAndOwnerIdOrderByIdDesc(type, ownerId);
        int included = 0;
        int idx = 1;
        for (Document doc : docList) {
            try (InputStream in = documentService.loadFile(doc).getInputStream()) {
                String raw = doc.getFileName() != null ? doc.getFileName() : "file";
                String entryName = folderPrefix + idx + "-" + SafeText.sanitizeFileName(raw);
                zos.putNextEntry(new ZipEntry(entryName));
                in.transferTo(zos);
                zos.closeEntry();
                idx++;
                included++;
            } catch (Exception ex) {
                log.warn("zip entry fail doc {} {}", doc.getId(), ex.getMessage());
            }
        }
        return included;
    }
}
