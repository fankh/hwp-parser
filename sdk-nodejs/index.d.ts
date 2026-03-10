export interface HwpDocument {
  version: string;
  compressed: boolean;
  encrypted: boolean;
  sections: HwpSection[];
  previewText: string;
}

export interface HwpSection {
  sectionIndex: number;
  paragraphs: HwpParagraph[];
}

export interface HwpParagraph {
  text: string;
  paraShapeId: number;
  styleId: number;
}

export class HwpParser {
  /** HWP 파일을 파싱하여 문서 구조를 반환합니다. */
  parse(file: string | Buffer): HwpDocument;
  /** HWP 파일에서 텍스트만 추출합니다. */
  extractText(file: string | Buffer): string;
  /** HWP 파일의 섹션 목록을 반환합니다. */
  getSections(file: string | Buffer): HwpSection[];
  /** HWP 파일의 문단 텍스트 목록을 반환합니다. */
  getParagraphs(file: string | Buffer): string[];
}

export class HwpParseError extends Error {
  constructor(message: string);
}
