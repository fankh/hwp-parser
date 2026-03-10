"""HWP Parser REST API 서버."""

import os
import sys
from dataclasses import asdict
from datetime import datetime, timezone

from flask import Flask, jsonify, request

# sdk-python 모듈을 import 경로에 추가
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "sdk-python"))

from hwp_parser import HwpParser, HwpParseError

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 50 * 1024 * 1024  # 50MB

parser = HwpParser()


def _error_response(message: str, status_code: int):
    return jsonify({
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "status": status_code,
        "error": message,
    }), status_code


@app.route("/api/v1/hwp/health", methods=["GET"])
def health():
    """서버 상태 확인."""
    return jsonify({"status": "UP", "service": "HWP Parser API"})


@app.route("/api/v1/hwp/parse", methods=["POST"])
def parse():
    """HWP 파일을 파싱하여 전체 JSON 구조를 반환합니다."""
    if "file" not in request.files:
        return _error_response("파일이 필요합니다 (form field: 'file')", 400)

    file = request.files["file"]
    if not file.filename:
        return _error_response("파일이 비어 있습니다", 400)

    if not file.filename.lower().endswith(".hwp"):
        return _error_response("HWP 파일만 지원합니다 (.hwp)", 400)

    try:
        data = file.read()
        doc = parser.parse(data)
        return jsonify(asdict(doc))
    except HwpParseError as e:
        return _error_response(str(e), 400)
    except Exception as e:
        return _error_response(f"파싱 오류: {e}", 500)


@app.route("/api/v1/hwp/extract-text", methods=["POST"])
def extract_text():
    """HWP 파일에서 텍스트만 추출합니다."""
    if "file" not in request.files:
        return _error_response("파일이 필요합니다 (form field: 'file')", 400)

    file = request.files["file"]
    if not file.filename:
        return _error_response("파일이 비어 있습니다", 400)

    if not file.filename.lower().endswith(".hwp"):
        return _error_response("HWP 파일만 지원합니다 (.hwp)", 400)

    try:
        data = file.read()
        doc = parser.parse(data)
        text = "\n".join(
            para.text
            for section in doc.sections
            for para in section.paragraphs
            if para.text
        )
        return jsonify({
            "filename": file.filename,
            "text": text,
            "sectionCount": len(doc.sections),
        })
    except HwpParseError as e:
        return _error_response(str(e), 400)
    except Exception as e:
        return _error_response(f"파싱 오류: {e}", 500)


@app.errorhandler(413)
def too_large(e):
    return _error_response("파일 크기가 너무 큽니다 (최대 50MB)", 413)


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8080))
    app.run(host="0.0.0.0", port=port)
