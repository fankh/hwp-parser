const { HwpParser } = require('./index');

const filePath = process.argv[2];
if (!filePath) {
  console.log('사용법: node test.js <hwp-파일-경로>');
  process.exit(0);
}

const parser = new HwpParser();

console.log(`파싱 중: ${filePath}`);

const doc = parser.parse(filePath);
console.log(`버전: ${doc.version}`);
console.log(`압축: ${doc.compressed}`);
console.log(`섹션 수: ${doc.sections.length}`);

const text = parser.extractText(filePath);
console.log(`\n추출된 텍스트 (처음 500자):`);
console.log(text.substring(0, 500));

const paragraphs = parser.getParagraphs(filePath);
console.log(`\n전체 문단 수: ${paragraphs.length}`);
