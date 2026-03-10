package com.hwpparser.sdk;

import com.hwpparser.sdk.model.HwpDocument;
import com.hwpparser.sdk.model.HwpParagraph;
import com.hwpparser.sdk.model.HwpSection;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * HWP 5.0 바이너리 파일 파서.
 * OLE2 Compound File을 직접 파싱합니다.
 */
public class HwpParser {

    private static final int TAG_PARA_HEADER = 66;
    private static final int TAG_PARA_TEXT = 67;

    private static final Set<Integer> CTRL_2_BYTES = Set.of(
            0x0002, 0x0003, 0x0005, 0x0006, 0x0007, 0x0008
    );

    private static final Set<Integer> CTRL_8_BYTES = Set.of(
            0x000B, 0x000C, 0x000E, 0x000F, 0x0010, 0x0011,
            0x0012, 0x0013, 0x0014, 0x0015, 0x0016, 0x0017
    );

    public HwpDocument parse(byte[] data) {
        try {
            return parse(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new HwpParseError("파싱 오류: " + e.getMessage(), e);
        }
    }

    public HwpDocument parse(InputStream inputStream) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem(inputStream)) {
            return parseOle(fs);
        }
    }

    private HwpDocument parseOle(POIFSFileSystem fs) throws IOException {
        HwpDocument doc = new HwpDocument();
        DirectoryEntry root = fs.getRoot();

        // 1. FileHeader 파싱
        if (!root.hasEntry("FileHeader")) {
            throw new HwpParseError("FileHeader 스트림을 찾을 수 없습니다");
        }

        byte[] headerData = readEntry(root, "FileHeader");
        if (headerData.length < 36) {
            throw new HwpParseError("FileHeader 데이터가 너무 짧습니다");
        }

        // 시그니처 확인
        String sig = new String(headerData, 0, 32, StandardCharsets.US_ASCII).replace("\0", "");
        if (!sig.contains("HWP Document File")) {
            throw new HwpParseError("잘못된 HWP 시그니처: " + sig);
        }

        // 버전
        int versionDword = readInt32LE(headerData, 32);
        int major = (versionDword >> 24) & 0xFF;
        int minor = (versionDword >> 16) & 0xFF;
        int build = (versionDword >> 8) & 0xFF;
        doc.setVersion(major + "." + minor + "." + build);

        // 속성 플래그
        if (headerData.length >= 40) {
            int props = readInt32LE(headerData, 36);
            doc.setCompressed((props & 0x01) != 0);
            doc.setEncrypted((props & 0x02) != 0);
        }

        if (doc.isEncrypted()) {
            throw new HwpParseError("암호화된 HWP 파일은 지원하지 않습니다");
        }

        // 2. BodyText 섹션 파싱
        DirectoryEntry bodyText = null;
        try {
            bodyText = (DirectoryEntry) root.getEntry("BodyText");
        } catch (Exception e) {
            // BodyText 없음
        }

        if (bodyText != null) {
            int sectionIdx = 0;
            while (true) {
                String sectionName = "Section" + sectionIdx;
                if (!bodyText.hasEntry(sectionName)) break;

                byte[] sectionData = readEntry(bodyText, sectionName);
                if (doc.isCompressed()) {
                    sectionData = decompress(sectionData);
                }

                HwpSection section = parseSection(sectionData, sectionIdx);
                doc.getSections().add(section);
                sectionIdx++;
            }
        }

        // 3. PrvText (미리보기 텍스트)
        if (root.hasEntry("PrvText")) {
            byte[] prvData = readEntry(root, "PrvText");
            if (doc.isCompressed()) {
                try {
                    prvData = decompress(prvData);
                } catch (HwpParseError ignored) {
                }
            }
            doc.setPreviewText(new String(prvData, StandardCharsets.UTF_16LE).trim());
        }

        return doc;
    }

    private byte[] readEntry(DirectoryEntry dir, String name) throws IOException {
        DocumentEntry entry = (DocumentEntry) dir.getEntry(name);
        try (DocumentInputStream dis = new DocumentInputStream(entry)) {
            byte[] data = new byte[entry.getSize()];
            dis.readFully(data);
            return data;
        }
    }

    private byte[] decompress(byte[] data) {
        try {
            return inflate(data, true);
        } catch (DataFormatException e) {
            try {
                return inflate(data, false);
            } catch (DataFormatException e2) {
                throw new HwpParseError("압축 해제 실패: " + e2.getMessage());
            }
        }
    }

    private byte[] inflate(byte[] data, boolean nowrap) throws DataFormatException {
        Inflater inflater = new Inflater(nowrap);
        inflater.setInput(data);
        List<byte[]> chunks = new ArrayList<>();
        int totalSize = 0;
        byte[] buffer = new byte[8192];

        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0 && inflater.needsInput()) break;
            byte[] chunk = new byte[count];
            System.arraycopy(buffer, 0, chunk, 0, count);
            chunks.add(chunk);
            totalSize += count;
        }
        inflater.end();

        byte[] result = new byte[totalSize];
        int pos = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, pos, chunk.length);
            pos += chunk.length;
        }
        return result;
    }

    private HwpSection parseSection(byte[] data, int sectionIndex) {
        HwpSection section = new HwpSection(sectionIndex);
        List<int[]> records = parseRecords(data);
        HwpParagraph currentPara = null;

        for (int[] record : records) {
            int tagId = record[0];
            int offset = record[2];
            int size = record[3];

            if (tagId == TAG_PARA_HEADER) {
                currentPara = new HwpParagraph();
                if (size >= 10) {
                    currentPara.setParaShapeId(readUInt16LE(data, offset + 8));
                }
                if (size >= 11) {
                    currentPara.setStyleId(data[offset + 10] & 0xFF);
                }
                section.getParagraphs().add(currentPara);
            } else if (tagId == TAG_PARA_TEXT && currentPara != null) {
                currentPara.setText(extractText(data, offset, size));
            }
        }

        return section;
    }

    private List<int[]> parseRecords(byte[] data) {
        List<int[]> records = new ArrayList<>();
        int pos = 0;

        while (pos + 4 <= data.length) {
            int header = readInt32LE(data, pos);
            pos += 4;

            int tagId = header & 0x3FF;
            int level = (header >> 10) & 0x3FF;
            int size = (header >> 20) & 0xFFF;

            if (size == 0xFFF) {
                if (pos + 4 > data.length) break;
                size = readInt32LE(data, pos);
                pos += 4;
            }

            if (pos + size > data.length) break;

            records.add(new int[]{tagId, level, pos, size});
            pos += size;
        }

        return records;
    }

    private String extractText(byte[] data, int offset, int size) {
        if (size == 0) return "";

        StringBuilder sb = new StringBuilder();
        int i = offset;
        int end = offset + size;

        while (i + 1 < end && i + 1 < data.length) {
            int ch = readUInt16LE(data, i);
            i += 2;

            if (ch == 0x0000 || ch == 0x0001) {
                continue;
            } else if (CTRL_2_BYTES.contains(ch)) {
                i += 2;
            } else if (CTRL_8_BYTES.contains(ch)) {
                i += 8;
            } else if (ch == 0x0009) {
                sb.append('\t');
            } else if (ch == 0x000A) {
                sb.append('\n');
            } else if (ch == 0x000D) {
                // 문단 끝
            } else if (ch == 0x0004) {
                // 필드 끝
            } else if (ch == 0x0018) {
                sb.append('-');
            } else if (ch == 0x001E || ch == 0x001F) {
                sb.append(' ');
            } else if (ch >= 0x0020) {
                sb.appendCodePoint(ch);
            }
        }

        return sb.toString().trim();
    }

    private static int readInt32LE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static int readUInt16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }
}
