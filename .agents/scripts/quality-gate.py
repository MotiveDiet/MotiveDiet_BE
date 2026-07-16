#!/usr/bin/env python3
"""
QUALITY_SCORE.md 의 하드 게이트를 기계적으로 검사한다.

Claude Code 의 Stop 훅이 호출한다. 게이트가 실패하면 decision=block 을 돌려
턴을 끝내지 못하게 막는다 — 사용자에게 미완성 결과물이 도달하지 않게 하는 게 목적이다.

문서는 강제력이 없다. 이 스크립트가 QUALITY_SCORE.md 를 실행 가능하게 만든다.

사용:
  echo '{}' | quality-gate.py            # Stop 훅 모드 (JSON 출력)
  quality-gate.py --report               # 사람이 읽는 리포트
"""

import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
MARKER = os.path.join(ROOT, ".agents", "tasks", "codex-review.json")

# 검사 대상 — 이 경로가 안 바뀌었으면 게이트를 통째로 건너뛴다(문서만 고친 턴 등)
CODE_PREFIXES = ("src/", "build.gradle", "settings.gradle")


def sh(*args, **kw):
    return subprocess.run(args, cwd=ROOT, capture_output=True, text=True, **kw)


def changed_files():
    """아직 origin/main 에 안 올라간 것 = 지금 넘기려는 산출물."""
    out = set()
    out.update(sh("git", "diff", "--name-only", "HEAD").stdout.split())
    out.update(sh("git", "diff", "--name-only", "--cached").stdout.split())
    r = sh("git", "diff", "--name-only", "origin/main...HEAD")
    if r.returncode == 0:
        out.update(r.stdout.split())
    out.update(
        line[3:] for line in sh("git", "status", "--porcelain").stdout.splitlines()
        if line.startswith("??")
    )
    return {f for f in out if f}


def touches_code(files):
    return any(f.startswith(CODE_PREFIXES) for f in files)


# ── 게이트들 ────────────────────────────────────────────────────────────
def gate_compile():
    env = dict(os.environ)
    if "JAVA_HOME" not in env:
        r = sh("/usr/libexec/java_home", "-v", "17")
        if r.returncode == 0:
            env["JAVA_HOME"] = r.stdout.strip()
    r = subprocess.run(
        ["./gradlew", "compileJava", "compileTestJava", "-q", "--offline"],
        cwd=ROOT, capture_output=True, text=True, env=env,
    )
    if r.returncode == 0:
        return True, "컴파일 통과"
    tail = (r.stdout + r.stderr).strip().splitlines()
    return False, "컴파일 실패:\n" + "\n".join(tail[-12:])


def gate_tests():
    """BUILD SUCCESSFUL 은 근거가 안 된다 — 스킵돼도 초록불이다. XML 을 직접 읽는다."""
    d = os.path.join(ROOT, "build", "test-results", "test")
    if not os.path.isdir(d):
        return False, "테스트 결과가 없다. ./gradlew test 를 실행한 적이 없다"

    total = failed = skipped = 0
    for name in os.listdir(d):
        if not name.endswith(".xml"):
            continue
        try:
            root = ET.parse(os.path.join(d, name)).getroot()
        except ET.ParseError:
            continue
        total += int(root.get("tests", 0))
        failed += int(root.get("failures", 0)) + int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))

    if total == 0:
        return False, "실행된 테스트가 0건이다"
    if failed:
        return False, f"테스트 실패 {failed}건 (전체 {total}건)"
    return True, f"테스트 {total}건 통과 (스킵 {skipped})"


SECRET_PATTERNS = [
    (re.compile(r'(?i)(password|secret|token|api[_-]?key)\s*[:=]\s*["\']?(?!\$\{)[^\s"\'$}][^\s"\']{7,}'), "비밀값처럼 보이는 리터럴"),
    (re.compile(r"GOCSPX-[A-Za-z0-9_-]{10,}"), "구글 클라이언트 시크릿"),
    (re.compile(r"(?i)jdbc:mysql://(?!\$\{)[a-z0-9.-]+:\d+"), "하드코딩된 DB 접속 정보"),
]


def gate_secrets(files):
    hits = []
    for f in sorted(files):
        path = os.path.join(ROOT, f)
        if not os.path.isfile(path):
            continue
        # 추적되지 않는 파일은 커밋되지 않으므로 검사 대상이 아니다
        if sh("git", "check-ignore", "-q", f).returncode == 0:
            continue
        try:
            with open(path, encoding="utf-8", errors="ignore") as fh:
                text = fh.read()
        except OSError:
            continue
        for pat, label in SECRET_PATTERNS:
            m = pat.search(text)
            if m:
                hits.append(f"{f}: {label}")
                break
    if hits:
        return False, "비밀값 의심:\n  " + "\n  ".join(hits)
    return True, "비밀값 없음"


def gate_schema(files):
    entities = [f for f in files if "/domain/" in f and f.endswith(".java")]
    if not entities:
        return True, "엔티티 변경 없음"
    if "schema-changes.sql" in files:
        return True, "엔티티 변경 + DDL 동반"
    return False, (
        "엔티티를 바꿨는데 schema-changes.sql 이 없다: " + ", ".join(entities) +
        "\n  prod 는 ddl-auto: validate 다. 컬럼이 DB 에 없으면 머지 즉시 앱이 기동하지 못한다"
    )


def diff_hash():
    r = sh("git", "diff", "origin/main")
    r2 = sh("git", "diff", "HEAD")
    import hashlib
    return hashlib.sha256((r.stdout + r2.stdout).encode()).hexdigest()[:16]


def gate_codex(files):
    """2중 검증 루프 3단계 강제. 같은 모델이 만든 것을 같은 모델이 검증하지 않는다."""
    if not touches_code(files):
        return True, "코드 변경 없음 — 교차 검증 불필요"
    if not os.path.isfile(MARKER):
        return False, (
            "Codex 교차 검증 기록이 없다.\n"
            "  실행: codex exec -s workspace-write --add-dir ~/.gradle \\\n"
            "          -c sandbox_workspace_write.network_access=true -C . -o <출력> \"<리뷰 프롬프트>\"\n"
            "  끝나면: .agents/scripts/mark-codex-review.sh <발견건수>"
        )
    try:
        with open(MARKER, encoding="utf-8") as fh:
            marker = json.load(fh)
    except (OSError, ValueError):
        return False, "Codex 검증 기록이 깨졌다. 다시 실행할 것"

    if marker.get("diff_hash") != diff_hash():
        return False, (
            "Codex 검증 이후 코드가 또 바뀌었다. 현재 diff 는 교차 검증을 거치지 않았다.\n"
            f"  검증된 diff: {marker.get('diff_hash')}\n  현재 diff:   {diff_hash()}"
        )
    return True, f"Codex 교차 검증됨 (발견 {marker.get('findings', '?')}건)"


# ── 실행 ────────────────────────────────────────────────────────────────
def run():
    files = changed_files()
    if not touches_code(files):
        return None  # 코드 변경이 없으면 게이트를 돌리지 않는다

    results = [
        ("컴파일", *gate_compile()),
        ("테스트 실행+통과", *gate_tests()),
        ("비밀값 없음", *gate_secrets(files)),
        ("스키마/DDL 정합", *gate_schema(files)),
        ("Codex 교차 검증", *gate_codex(files)),
    ]
    passed = sum(1 for _, ok, _ in results if ok)
    return results, passed, len(results)


def main():
    report_mode = "--report" in sys.argv

    if not report_mode:
        # Stop 훅이 재발동하며 무한 루프 도는 것을 막는다
        try:
            payload = json.load(sys.stdin)
        except ValueError:
            payload = {}
        if payload.get("stop_hook_active"):
            return

    outcome = run()
    if outcome is None:
        if report_mode:
            print("코드 변경 없음 — 게이트 생략")
        return

    results, passed, total = outcome

    if report_mode:
        print(f"품질 게이트 {passed}/{total}\n")
        for name, ok, detail in results:
            print(f"  [{'PASS' if ok else 'FAIL'}] {name}: {detail}")
        sys.exit(0 if passed == total else 1)

    if passed == total:
        return  # 전부 통과 — 조용히 턴을 끝내게 둔다

    lines = [f"품질 게이트 미통과 ({passed}/{total}). 결과물을 넘기기 전에 아래를 해결할 것:", ""]
    for name, ok, detail in results:
        if not ok:
            lines.append(f"[FAIL] {name}: {detail}")
    lines.append("")
    lines.append("기준: QUALITY_SCORE.md 의 하드 게이트. 하나라도 걸리면 머지 불가다.")

    print(json.dumps({"decision": "block", "reason": "\n".join(lines)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
