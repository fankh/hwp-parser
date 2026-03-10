"""HWP 5.0 바이너리 파서 — 서버 없이 직접 HWP 파일을 파싱합니다."""

from __future__ import annotations

import struct
import zlib
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, BinaryIO, Union

import olefile


@dataclass
class HwpParagraph:
    text: str = ""
    para_shape_id: int = 0
    style_id: int = 0


@dataclass
class HwpSection:
    section_index: int = 0
    paragraphs: list[HwpParagraph] = field(default_factory=list)


@dataclass
class HwpDocument:
    version: str = ""
    compressed: bool = False
    encrypted: bool = False
    sections: list[HwpSection] = field(default_factory=list)
    preview_text: str = ""


class HwpParseError(Exception):
    """HWP 파싱 오류"""
    pass


# HWP 태그 타입
_TAG_PARA_HEADER = 66
_TAG_PARA_TEXT = 67

# 2바이트 추가 데이터를 가진 제어 문자
_CTRL_2_BYTES = {0x0002, 0x0003, 0x0005, 0x0006, 0x0007, 0x0008}

# 8바이트 추가 데이터를 가진 제어 문자
_CTRL_8_BYTES = {
    0x000B, 0x000C, 0x000E, 0x000F, 0x0010, 0x0011,
    0x0012, 0x0013, 0x0014, 0x0015, 0x0016, 0x0017,
}


def _extract_text(data: bytes) -> str:
    """HWP 문단 텍스트 레코드에서 텍스트를 추출합니다."""
    if not data:
        return ""

    result = []
    i = 0
    length = len(data)

    while i + 1 < length:
        ch = struct.unpack_from("<H", data, i)[0]
        i += 2

        if ch in (0x0000, 0x0001):
            continue
        elif ch in _CTRL_2_BYTES:
            i += 2
        elif ch in _CTRL_8_BYTES:
            i += 8
        elif ch == 0x0009:
            result.append("\t")
        elif ch == 0x000A:
            result.append("\n")
        elif ch == 0x000D:
            pass  # 문단 끝
        elif ch == 0x0004:
            pass  # 필드 끝
        elif ch == 0x0018:
            result.append("-")
        elif ch in (0x001E, 0x001F):
            result.append(" ")
        elif ch >= 0x0020:
            result.append(chr(ch))

    return "".join(result).strip()


def _decompress(data: bytes) -> bytes:
    """zlib 압축 해제 (raw deflate 먼저 시도, 실패 시 zlib 헤더 포함으로 재시도)."""
    try:
        return zlib.decompress(data, -15)  # raw deflate
    except zlib.error:
        try:
            return zlib.decompress(data)  # zlib 헤더 포함
        except zlib.error as e:
            raise HwpParseError(f"압축 해제 실패: {e}")


def _parse_records(data: bytes):
    """태그 기반 레코드를 파싱합니다."""
    records = []
    pos = 0
    length = len(data)

    while pos + 4 <= length:
        header = struct.unpack_from("<I", data, pos)[0]
        pos += 4

        tag_id = header & 0x3FF
        level = (header >> 10) & 0x3FF
        size = (header >> 20) & 0xFFF

        if size == 0xFFF:
            if pos + 4 > length:
                break
            size = struct.unpack_from("<I", data, pos)[0]
            pos += 4

        if pos + size > length:
            break

        record_data = data[pos:pos + size]
        pos += size

        records.append((tag_id, level, record_data))

    return records


def _parse_section(data: bytes, section_index: int) -> HwpSection:
    """섹션 스트림을 파싱합니다."""
    section = HwpSection(section_index=section_index)
    records = _parse_records(data)
    current_para = None

    for tag_id, level, record_data in records:
        if tag_id == _TAG_PARA_HEADER:
            current_para = HwpParagraph()
            if len(record_data) >= 10:
                current_para.para_shape_id = struct.unpack_from("<H", record_data, 8)[0]
            if len(record_data) >= 11:
                current_para.style_id = record_data[10]
            section.paragraphs.append(current_para)

        elif tag_id == _TAG_PARA_TEXT and current_para is not None:
            current_para.text = _extract_text(record_data)

    return section


class HwpParser:
    """HWP 5.0 바이너리 파일 파서.

    서버 없이 직접 HWP 파일을 파싱합니다.

    사용법:
        parser = HwpParser()
        doc = parser.parse("document.hwp")
        print(doc.sections[0].paragraphs[0].text)
    """

    def parse(self, file: Union[str, Path, bytes, BinaryIO]) -> HwpDocument:
        """HWP 파일을 파싱하여 문서 구조를 반환합니다.

        Args:
            file: 파일 경로, 바이트, 또는 파일 객체

        Returns:
            파싱된 HWP 문서 객체
        """
        if isinstance(file, (str, Path)):
            ole = olefile.OleFileIO(str(file))
        elif isinstance(file, bytes):
            ole = olefile.OleFileIO(file)
        else:
            ole = olefile.OleFileIO(file)

        try:
            return self._parse_ole(ole)
        finally:
            ole.close()

    def extract_text(self, file: Union[str, Path, bytes, BinaryIO]) -> str:
        """HWP 파일에서 텍스트만 추출합니다.

        Args:
            file: 파일 경로, 바이트, 또는 파일 객체

        Returns:
            추출된 전체 텍스트
        """
        doc = self.parse(file)
        lines = []
        for section in doc.sections:
            for para in section.paragraphs:
                if para.text:
                    lines.append(para.text)
        return "\n".join(lines)

    def get_sections(self, file: Union[str, Path, bytes, BinaryIO]) -> list[HwpSection]:
        """HWP 파일의 섹션 목록을 반환합니다."""
        return self.parse(file).sections

    def get_paragraphs(self, file: Union[str, Path, bytes, BinaryIO]) -> list[str]:
        """HWP 파일의 문단 텍스트 목록을 반환합니다."""
        doc = self.parse(file)
        paragraphs = []
        for section in doc.sections:
            for para in section.paragraphs:
                if para.text:
                    paragraphs.append(para.text)
        return paragraphs

    def _parse_ole(self, ole: olefile.OleFileIO) -> HwpDocument:
        doc = HwpDocument()

        # 1. FileHeader 파싱
        if not ole.exists("FileHeader"):
            raise HwpParseError("FileHeader 스트림을 찾을 수 없습니다")

        header_data = ole.openstream("FileHeader").read()
        if len(header_data) < 36:
            raise HwpParseError("FileHeader 데이터가 너무 짧습니다")

        # 시그니처 확인 (처음 32바이트)
        signature = header_data[:32].rstrip(b"\x00").decode("ascii", errors="ignore")
        if "HWP Document File" not in signature:
            raise HwpParseError(f"잘못된 HWP 시그니처: {signature}")

        # 버전
        version_dword = struct.unpack_from("<I", header_data, 32)[0]
        major = (version_dword >> 24) & 0xFF
        minor = (version_dword >> 16) & 0xFF
        build = (version_dword >> 8) & 0xFF
        doc.version = f"{major}.{minor}.{build}"

        # 속성 플래그
        if len(header_data) >= 40:
            props = struct.unpack_from("<I", header_data, 36)[0]
            doc.compressed = bool(props & 0x01)
            doc.encrypted = bool(props & 0x02)

        if doc.encrypted:
            raise HwpParseError("암호화된 HWP 파일은 지원하지 않습니다")

        # 2. BodyText 섹션 파싱
        section_idx = 0
        while True:
            stream_name = f"BodyText/Section{section_idx}"
            if not ole.exists(stream_name):
                break

            section_data = ole.openstream(stream_name).read()
            if doc.compressed:
                section_data = _decompress(section_data)

            section = _parse_section(section_data, section_idx)
            doc.sections.append(section)
            section_idx += 1

        # 3. PrvText (미리보기 텍스트)
        if ole.exists("PrvText"):
            prv_data = ole.openstream("PrvText").read()
            if doc.compressed:
                try:
                    prv_data = _decompress(prv_data)
                except HwpParseError:
                    pass
            doc.preview_text = prv_data.decode("utf-16-le", errors="ignore").strip()

        return doc
