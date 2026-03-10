package com.hwpparser.sdk;

import com.hwpparser.sdk.model.HwpDocument;
import com.hwpparser.sdk.model.HwpParagraph;
import com.hwpparser.sdk.model.HwpSection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * HWP 5.0 바이너리 파일 파서.
 * 서버 없이 직접 HWP 파일을 파싱합니다.
 *
 * <pre>
 * HwpParserClient parser = new HwpParserClient();
 * HwpDocument doc = parser.parse(Path.of("document.hwp"));
 * String text = parser.extractText(Path.of("document.hwp"));
 * </pre>
 */
public class HwpParserClient {

    private final HwpParser parser;

    public HwpParserClient() {
        this.parser = new HwpParser();
    }

    /**
     * HWP 파일을 파싱하여 문서 구조를 반환합니다.
     *
     * @param filePath HWP 파일 경로
     * @return 파싱된 HWP 문서 객체
     */
    public HwpDocument parse(Path filePath) throws IOException {
        return parser.parse(Files.readAllBytes(filePath));
    }

    /**
     * HWP 바이트 배열을 파싱합니다.
     *
     * @param data HWP 파일 바이트 배열
     * @return 파싱된 HWP 문서 객체
     */
    public HwpDocument parse(byte[] data) {
        return parser.parse(data);
    }

    /**
     * InputStream에서 HWP 파일을 파싱합니다.
     *
     * @param inputStream HWP 파일 입력 스트림
     * @return 파싱된 HWP 문서 객체
     */
    public HwpDocument parse(InputStream inputStream) throws IOException {
        return parser.parse(inputStream);
    }

    /**
     * HWP 파일에서 텍스트만 추출합니다.
     *
     * @param filePath HWP 파일 경로
     * @return 추출된 전체 텍스트
     */
    public String extractText(Path filePath) throws IOException {
        HwpDocument doc = parse(filePath);
        return extractPlainText(doc);
    }

    /**
     * HWP 파일의 섹션 목록을 반환합니다.
     *
     * @param filePath HWP 파일 경로
     * @return 섹션 목록
     */
    public List<HwpSection> getSections(Path filePath) throws IOException {
        return parse(filePath).getSections();
    }

    /**
     * HWP 파일의 문단 텍스트 목록을 반환합니다.
     *
     * @param filePath HWP 파일 경로
     * @return 문단 텍스트 목록
     */
    public List<String> getParagraphs(Path filePath) throws IOException {
        HwpDocument doc = parse(filePath);
        List<String> paragraphs = new ArrayList<>();
        for (HwpSection section : doc.getSections()) {
            for (HwpParagraph para : section.getParagraphs()) {
                if (para.getText() != null && !para.getText().isEmpty()) {
                    paragraphs.add(para.getText());
                }
            }
        }
        return paragraphs;
    }

    private String extractPlainText(HwpDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (HwpSection section : doc.getSections()) {
            for (HwpParagraph para : section.getParagraphs()) {
                if (para.getText() != null) {
                    sb.append(para.getText()).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
