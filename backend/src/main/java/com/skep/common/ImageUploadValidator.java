package com.skep.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 사진 업로드 시 content-type spoofing + SVG XSS 방어.
 *  - image/* prefix 만으로 부족 (image/svg+xml 도 통과). SVG 명시 거부.
 *  - 매직 바이트 검증으로 PNG/JPEG/GIF/WebP 만 실제 통과.
 */
public final class ImageUploadValidator {
    private ImageUploadValidator() {}

    public static void validateOrThrow(MultipartFile file) {
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw ApiException.badRequest("INVALID_IMAGE", "이미지 파일만 업로드할 수 있습니다");
        }
        if ("image/svg+xml".equalsIgnoreCase(ct)) {
            throw ApiException.badRequest("INVALID_IMAGE", "SVG 는 업로드할 수 없습니다");
        }
        try (var in = file.getInputStream()) {
            byte[] header = in.readNBytes(16);
            if (!matchesImageMagic(header)) {
                throw ApiException.badRequest("INVALID_IMAGE", "이미지 형식이 올바르지 않습니다");
            }
        } catch (IOException e) {
            throw ApiException.badRequest("INVALID_IMAGE", "이미지 파일을 읽을 수 없습니다");
        }
    }

    private static boolean matchesImageMagic(byte[] h) {
        if (h.length < 4) return false;
        // PNG: 89 50 4E 47
        if (h[0] == (byte) 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47) return true;
        // JPEG: FF D8 FF
        if (h[0] == (byte) 0xFF && h[1] == (byte) 0xD8 && h[2] == (byte) 0xFF) return true;
        // GIF: GIF8
        if (h[0] == 0x47 && h[1] == 0x49 && h[2] == 0x46 && h[3] == 0x38) return true;
        // WebP: RIFF....WEBP
        if (h.length >= 12
                && h[0] == 0x52 && h[1] == 0x49 && h[2] == 0x46 && h[3] == 0x46
                && h[8] == 0x57 && h[9] == 0x45 && h[10] == 0x42 && h[11] == 0x50) return true;
        // HEIC/HEIF: ISO BMFF — bytes 4-7 = "ftyp", bytes 8-11 = brand (heic/heix/mif1/msf1/...)
        if (h.length >= 12
                && h[4] == 0x66 && h[5] == 0x74 && h[6] == 0x79 && h[7] == 0x70) {
            char b1 = (char) (h[8] & 0xFF), b2 = (char) (h[9] & 0xFF),
                 b3 = (char) (h[10] & 0xFF), b4 = (char) (h[11] & 0xFF);
            String brand = ("" + b1 + b2 + b3 + b4).toLowerCase();
            // 일반 HEIC/HEIF brand. 영상(qt/mp4) 차단.
            return brand.equals("heic") || brand.equals("heix") || brand.equals("heim")
                    || brand.equals("heis") || brand.equals("mif1") || brand.equals("msf1");
        }
        return false;
    }
}
