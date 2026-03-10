# HWP Parser

HWP 5.0 바이너리 문서 파서 — Java, Node.js, Python SDK를 제공합니다.

한컴오피스 한글(HWP) 파일에서 텍스트, 문서 구조를 직접 파싱합니다. 서버 없이 로컬에서 바로 사용할 수 있습니다.

## 프로젝트 구조

```
hwp-parser/
├── server/          # Java Spring Boot REST API (선택사항)
├── sdk-java/        # Java SDK (직접 파싱)
├── sdk-nodejs/      # Node.js SDK (직접 파싱)
└── sdk-python/      # Python SDK (직접 파싱)
```

**SDK**는 HWP 바이너리 파일을 직접 파싱합니다 (OLE Compound File, zlib 압축 해제, 태그 기반 레코드 파싱). 서버가 필요 없습니다.

**server**는 REST API가 필요한 경우 선택적으로 사용할 수 있습니다.

## SDK 사용법

### Python

```bash
pip install olefile
```

```python
from hwp_parser import HwpParser

parser = HwpParser()

# 전체 파싱
doc = parser.parse("document.hwp")
print(f"버전: {doc.version}")
print(f"섹션 수: {len(doc.sections)}")

# 텍스트 추출
text = parser.extract_text("document.hwp")
print(text)

# 문단 목록 가져오기
paragraphs = parser.get_paragraphs("document.hwp")
for p in paragraphs:
    print(p)

# 섹션 목록 가져오기
sections = parser.get_sections("document.hwp")
for s in sections:
    print(f"섹션 {s.section_index}: {len(s.paragraphs)}개 문단")

# 바이트 배열로도 파싱 가능
with open("document.hwp", "rb") as f:
    doc = parser.parse(f.read())
```

### Node.js

```bash
cd sdk-nodejs && npm install
```

```javascript
const { HwpParser } = require('./index');

const parser = new HwpParser();

// 전체 파싱
const doc = parser.parse('document.hwp');
console.log(`버전: ${doc.version}`);
console.log(`섹션 수: ${doc.sections.length}`);

// 텍스트 추출
const text = parser.extractText('document.hwp');
console.log(text);

// 문단 목록 가져오기
const paragraphs = parser.getParagraphs('document.hwp');
paragraphs.forEach(p => console.log(p));

// Buffer로도 전달 가능
const fs = require('fs');
const buffer = fs.readFileSync('document.hwp');
const doc2 = parser.parse(buffer);
```

### Java

```xml
<dependency>
    <groupId>com.hwpparser</groupId>
    <artifactId>hwp-parser-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
import com.hwpparser.sdk.HwpParserClient;
import com.hwpparser.sdk.model.HwpDocument;
import java.nio.file.Path;
import java.util.List;

HwpParserClient parser = new HwpParserClient();

// 전체 파싱
HwpDocument doc = parser.parse(Path.of("document.hwp"));
System.out.println("섹션 수: " + doc.getSections().size());

// 텍스트 추출
String text = parser.extractText(Path.of("document.hwp"));
System.out.println(text);

// 문단 목록 가져오기
List<String> paragraphs = parser.getParagraphs(Path.of("document.hwp"));
paragraphs.forEach(System.out::println);

// 바이트 배열로도 파싱 가능
byte[] data = Files.readAllBytes(Path.of("document.hwp"));
HwpDocument doc2 = parser.parse(data);

// InputStream으로도 파싱 가능
try (InputStream is = new FileInputStream("document.hwp")) {
    HwpDocument doc3 = parser.parse(is);
}
```

## SDK API 요약

모든 SDK는 동일한 메서드를 제공합니다:

| 메서드 | 설명 | 반환값 |
|--------|------|--------|
| `parse(file)` | HWP 파일 전체 파싱 | 문서 구조 객체 |
| `extractText(file)` | 텍스트만 추출 | 문자열 |
| `getSections(file)` | 섹션 목록 반환 | 섹션 배열 |
| `getParagraphs(file)` | 문단 텍스트 목록 반환 | 문자열 배열 |

`file` 파라미터는 파일 경로(문자열), 바이트 배열, 또는 Buffer를 지원합니다.

## 서버 (선택사항)

REST API가 필요한 경우 서버를 별도로 실행할 수 있습니다.

### 요구사항

- Java 17 이상
- Maven 3.8 이상

### 실행

```bash
cd server
mvn spring-boot:run
```

서버가 `http://localhost:8080`에서 시작됩니다.

### API 엔드포인트

| 메서드 | 엔드포인트 | 설명 |
|--------|----------|------|
| `POST` | `/api/v1/hwp/parse` | HWP 파일을 파싱하여 전체 JSON 구조 반환 |
| `POST` | `/api/v1/hwp/extract-text` | HWP 파일에서 텍스트만 추출 |
| `GET`  | `/api/v1/hwp/health` | 서버 상태 확인 |

## HWP 5.0 포맷

파서는 HWP 5.0 바이너리 포맷을 처리합니다:

- **컨테이너**: OLE2 Compound File
- **압축**: 본문 스트림에 zlib 압축 적용
- **인코딩**: 한글 텍스트에 UTF-16LE 사용
- **구조**: 태그 기반 레코드 (66개 이상의 태그 타입)

### 지원 기능

| 기능 | 상태 |
|------|------|
| 텍스트 추출 | 지원 |
| 문서 구조 (섹션, 문단) | 지원 |
| 파일 헤더 및 문서 속성 | 지원 |
| 글꼴 및 글자 모양 | 지원 |
| 문단 모양 및 스타일 | 지원 |
| 표 (기본 내용) | 지원 |
| 이미지 (바이너리 데이터 추출) | 지원 |
| 번호 매기기 및 글머리 기호 | 지원 |

### 제한사항

- 암호화된 HWP 파일은 지원하지 않습니다
- 그리기 객체 및 수식은 렌더링하지 않습니다
- HWPX (XML 기반) 포맷은 지원하지 않습니다 (HWP 5.0 바이너리만 지원)

## 라이선스

MIT License — [LICENSE](./LICENSE) 파일을 참조하세요.
