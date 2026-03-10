const fs = require('fs');
const zlib = require('zlib');
const CFB = require('cfb');

// HWP 태그 타입
const TAG_PARA_HEADER = 66;
const TAG_PARA_TEXT = 67;

// 2바이트 추가 데이터를 가진 제어 문자
const CTRL_2_BYTES = new Set([0x0002, 0x0003, 0x0005, 0x0006, 0x0007, 0x0008]);

// 8바이트 추가 데이터를 가진 제어 문자
const CTRL_8_BYTES = new Set([
  0x000b, 0x000c, 0x000e, 0x000f, 0x0010, 0x0011,
  0x0012, 0x0013, 0x0014, 0x0015, 0x0016, 0x0017,
]);

/**
 * HWP 문단 텍스트 레코드에서 텍스트 추출
 */
function extractText(data) {
  if (!data || data.length === 0) return '';

  const result = [];
  let i = 0;

  while (i + 1 < data.length) {
    const ch = data.readUInt16LE(i);
    i += 2;

    if (ch === 0x0000 || ch === 0x0001) {
      continue;
    } else if (CTRL_2_BYTES.has(ch)) {
      i += 2;
    } else if (CTRL_8_BYTES.has(ch)) {
      i += 8;
    } else if (ch === 0x0009) {
      result.push('\t');
    } else if (ch === 0x000a) {
      result.push('\n');
    } else if (ch === 0x000d) {
      // 문단 끝
    } else if (ch === 0x0004) {
      // 필드 끝
    } else if (ch === 0x0018) {
      result.push('-');
    } else if (ch === 0x001e || ch === 0x001f) {
      result.push(' ');
    } else if (ch >= 0x0020) {
      result.push(String.fromCharCode(ch));
    }
  }

  return result.join('').trim();
}

/**
 * zlib 압축 해제 (raw deflate 먼저 시도)
 */
function decompress(data) {
  try {
    return zlib.inflateRawSync(Buffer.from(data));
  } catch (e) {
    try {
      return zlib.inflateSync(Buffer.from(data));
    } catch (e2) {
      throw new HwpParseError(`압축 해제 실패: ${e2.message}`);
    }
  }
}

/**
 * 태그 기반 레코드 파싱
 */
function parseRecords(data) {
  const records = [];
  let pos = 0;

  while (pos + 4 <= data.length) {
    const header = data.readUInt32LE(pos);
    pos += 4;

    const tagId = header & 0x3ff;
    const level = (header >> 10) & 0x3ff;
    let size = (header >> 20) & 0xfff;

    if (size === 0xfff) {
      if (pos + 4 > data.length) break;
      size = data.readUInt32LE(pos);
      pos += 4;
    }

    if (pos + size > data.length) break;

    const recordData = data.slice(pos, pos + size);
    pos += size;

    records.push({ tagId, level, data: recordData });
  }

  return records;
}

/**
 * 섹션 스트림 파싱
 */
function parseSection(data, sectionIndex) {
  const section = { sectionIndex, paragraphs: [] };
  const records = parseRecords(data);
  let currentPara = null;

  for (const record of records) {
    if (record.tagId === TAG_PARA_HEADER) {
      currentPara = { text: '', paraShapeId: 0, styleId: 0 };
      if (record.data.length >= 10) {
        currentPara.paraShapeId = record.data.readUInt16LE(8);
      }
      if (record.data.length >= 11) {
        currentPara.styleId = record.data[10];
      }
      section.paragraphs.push(currentPara);
    } else if (record.tagId === TAG_PARA_TEXT && currentPara) {
      currentPara.text = extractText(record.data);
    }
  }

  return section;
}

class HwpParseError extends Error {
  constructor(message) {
    super(message);
    this.name = 'HwpParseError';
  }
}

class HwpParser {
  /**
   * HWP 파일을 파싱하여 문서 구조를 반환합니다.
   * @param {string|Buffer} file - 파일 경로 또는 Buffer
   * @returns {Object} 파싱된 문서 객체
   */
  parse(file) {
    const data = typeof file === 'string' ? fs.readFileSync(file) : file;
    const cfb = CFB.read(data, { type: 'buffer' });

    const doc = {
      version: '',
      compressed: false,
      encrypted: false,
      sections: [],
      previewText: '',
    };

    // 1. FileHeader 파싱
    const fileHeaderEntry = CFB.find(cfb, '/FileHeader');
    if (!fileHeaderEntry) {
      throw new HwpParseError('FileHeader 스트림을 찾을 수 없습니다');
    }

    const headerData = Buffer.from(fileHeaderEntry.content);
    if (headerData.length < 36) {
      throw new HwpParseError('FileHeader 데이터가 너무 짧습니다');
    }

    // 시그니처 확인
    const sig = headerData.slice(0, 32).toString('ascii').replace(/\0/g, '');
    if (!sig.includes('HWP Document File')) {
      throw new HwpParseError(`잘못된 HWP 시그니처: ${sig}`);
    }

    // 버전
    const versionDword = headerData.readUInt32LE(32);
    const major = (versionDword >> 24) & 0xff;
    const minor = (versionDword >> 16) & 0xff;
    const build = (versionDword >> 8) & 0xff;
    doc.version = `${major}.${minor}.${build}`;

    // 속성 플래그
    if (headerData.length >= 40) {
      const props = headerData.readUInt32LE(36);
      doc.compressed = !!(props & 0x01);
      doc.encrypted = !!(props & 0x02);
    }

    if (doc.encrypted) {
      throw new HwpParseError('암호화된 HWP 파일은 지원하지 않습니다');
    }

    // 2. BodyText 섹션 파싱
    let sectionIdx = 0;
    while (true) {
      const entry = CFB.find(cfb, `/BodyText/Section${sectionIdx}`);
      if (!entry) break;

      let sectionData = Buffer.from(entry.content);
      if (doc.compressed) {
        sectionData = decompress(sectionData);
      }

      const section = parseSection(sectionData, sectionIdx);
      doc.sections.push(section);
      sectionIdx++;
    }

    // 3. PrvText (미리보기 텍스트)
    const prvEntry = CFB.find(cfb, '/PrvText');
    if (prvEntry) {
      let prvData = Buffer.from(prvEntry.content);
      if (doc.compressed) {
        try { prvData = decompress(prvData); } catch (e) { /* 무시 */ }
      }
      doc.previewText = prvData.toString('utf16le').trim();
    }

    return doc;
  }

  /**
   * HWP 파일에서 텍스트만 추출합니다.
   * @param {string|Buffer} file - 파일 경로 또는 Buffer
   * @returns {string} 추출된 전체 텍스트
   */
  extractText(file) {
    const doc = this.parse(file);
    const lines = [];
    for (const section of doc.sections) {
      for (const para of section.paragraphs) {
        if (para.text) lines.push(para.text);
      }
    }
    return lines.join('\n');
  }

  /**
   * HWP 파일의 섹션 목록을 반환합니다.
   * @param {string|Buffer} file
   * @returns {Array} 섹션 배열
   */
  getSections(file) {
    return this.parse(file).sections;
  }

  /**
   * HWP 파일의 문단 텍스트 목록을 반환합니다.
   * @param {string|Buffer} file
   * @returns {string[]} 문단 텍스트 배열
   */
  getParagraphs(file) {
    const doc = this.parse(file);
    const paragraphs = [];
    for (const section of doc.sections) {
      for (const para of section.paragraphs) {
        if (para.text) paragraphs.push(para.text);
      }
    }
    return paragraphs;
  }
}

module.exports = { HwpParser, HwpParseError };
