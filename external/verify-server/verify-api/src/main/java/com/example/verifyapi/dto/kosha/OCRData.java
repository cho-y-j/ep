package com.example.verifyapi.dto.kosha;

import java.util.List;

/**
 * OCR로 추출한 교육이수증 데이터
 * - name, birthDate, registrationNumber: 비교용 핵심 필드
 * - fullText: Google Vision OCR 전체 텍스트 (디버깅/추가 파싱용)
 * - textAnnotations: 개별 텍스트 요소 + boundingPoly 좌표 (마스킹용)
 */
public class OCRData {

    private String name;
    private String birthDate;
    private String registrationNumber;
    private String fullText;
    private List<TextAnnotation> textAnnotations;

    public OCRData() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }

    public List<TextAnnotation> getTextAnnotations() {
        return textAnnotations;
    }

    public void setTextAnnotations(List<TextAnnotation> textAnnotations) {
        this.textAnnotations = textAnnotations;
    }

    @Override
    public String toString() {
        return "OCRData{name='" + name + "', birthDate='" + birthDate + "', registrationNumber='" + registrationNumber + "'}";
    }

    /**
     * Google Vision API textAnnotation 개별 요소
     */
    public static class TextAnnotation {
        private String description;
        private int[][] vertices; // [[x,y], [x,y], [x,y], [x,y]]

        public TextAnnotation() {}

        public TextAnnotation(String description, int[][] vertices) {
            this.description = description;
            this.vertices = vertices;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int[][] getVertices() {
            return vertices;
        }

        public void setVertices(int[][] vertices) {
            this.vertices = vertices;
        }

        public int getMinX() {
            int min = Integer.MAX_VALUE;
            for (int[] v : vertices) min = Math.min(min, v[0]);
            return min;
        }

        public int getMinY() {
            int min = Integer.MAX_VALUE;
            for (int[] v : vertices) min = Math.min(min, v[1]);
            return min;
        }

        public int getMaxX() {
            int max = 0;
            for (int[] v : vertices) max = Math.max(max, v[0]);
            return max;
        }

        public int getMaxY() {
            int max = 0;
            for (int[] v : vertices) max = Math.max(max, v[1]);
            return max;
        }
    }
}
