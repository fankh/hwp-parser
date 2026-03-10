"""HWP Parser 테스트 스크립트"""

import sys
from hwp_parser import HwpParser

def main():
    if len(sys.argv) < 2:
        print("사용법: python test_client.py <hwp-파일-경로>")
        return

    file_path = sys.argv[1]
    parser = HwpParser()

    print(f"파싱 중: {file_path}")

    # 전체 파싱
    doc = parser.parse(file_path)
    print(f"버전: {doc.version}")
    print(f"압축: {doc.compressed}")
    print(f"섹션 수: {len(doc.sections)}")

    # 텍스트 추출
    text = parser.extract_text(file_path)
    print(f"\n추출된 텍스트 (처음 500자):")
    print(text[:500])

    # 문단 목록
    paragraphs = parser.get_paragraphs(file_path)
    print(f"\n전체 문단 수: {len(paragraphs)}")


if __name__ == "__main__":
    main()
