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
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SCRIPTS = os.path.join(ROOT, ".agents", "scripts")

# 이 파일들이 바뀌면 API 계약이 바뀌었을 가능성이 높다 → API.md 갱신 필요.
# src/main 으로 한정한다 — 테스트의 controller 패키지까지 잡으면 테스트만 고쳐도 막힌다
# (2026-07-16 Codex 리뷰 지적).
CONTRACT_HINTS = ("src/main/java/", ("/controller/", "/dto/"))
API_DOC = "docs/design-docs/API.md"

# MCP 정본과 생성물. 정본만 고치고 sync 를 안 하면 둘이 어긋난다 (검사는 sync-mcp.sh 공유).
MCP_FILES = (".agents/mcp/servers.json", ".mcp.json")


def sh(*args, **kw):
    return subprocess.run(args, cwd=ROOT, capture_output=True, text=True, **kw)


def code_prefixes():
    """검사 대상 경로. check-review.py 의 CODE_PATHS 를 그대로 받아 쓴다.

    두 곳에 손으로 유지하면 반드시 어긋난다 — 실제로 gradlew.bat 이 check-review.py 에만
    있어서, 그 파일만 고치면 로컬 게이트가 "코드 변경 없음"으로 생략되는 구멍이 있었다.
    """
    r = subprocess.run(
        [sys.executable, os.path.join(SCRIPTS, "check-review.py"), "--paths"],
        cwd=ROOT, capture_output=True, text=True,
    )
    paths = [p for p in r.stdout.split() if p]
    # 디렉토리는 하위 전체가 대상이므로 슬래시를 붙여 접두어로 만든다
    return tuple(
        p + "/" if os.path.isdir(os.path.join(ROOT, p)) else p
        for p in paths
    )


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
    return any(f.startswith(code_prefixes()) for f in files)


def touches_mcp(files):
    return any(f in MCP_FILES for f in files)


# ── 게이트들 ────────────────────────────────────────────────────────────
def gradle_env():
    env = dict(os.environ)
    if "JAVA_HOME" not in env:
        r = sh("/usr/libexec/java_home", "-v", "17")
        if r.returncode == 0:
            env["JAVA_HOME"] = r.stdout.strip()
    return env


def gradle(*args):
    """--offline 우선, 실패하면 온라인 재시도.

    새 의존성을 추가하면 로컬 캐시에 없어 --offline 이 실패하는데, CI 는 받아와서 통과한다.
    로컬과 CI 가 다른 판정을 내리는 것을 막는다 (2026-07-16 Codex 리뷰 지적).
    """
    env = gradle_env()
    r = subprocess.run(["./gradlew", *args, "--offline"], cwd=ROOT,
                       capture_output=True, text=True, env=env)
    if r.returncode == 0:
        return r
    return subprocess.run(["./gradlew", *args], cwd=ROOT,
                          capture_output=True, text=True, env=env)


def gate_compile():
    r = gradle("compileJava", "compileTestJava", "-q")
    if r.returncode == 0:
        return True, "컴파일 통과"
    tail = (r.stdout + r.stderr).strip().splitlines()
    return False, "컴파일 실패:\n" + "\n".join(tail[-12:])


def gate_tests():
    """결과 디렉토리를 지우고 gradle test 를 돌린 뒤, 종료 코드와 XML 을 함께 본다.

    셋 다 필요한 이유 — 하나라도 빼면 거짓 통과가 생긴다:
    - 종료 코드만: `BUILD SUCCESSFUL` 은 테스트가 전부 스킵돼도 나온다
    - XML 만: 컴파일이 깨지면 gradle 이 XML 을 안 써서 옛 결과를 읽고 통과시킨다
    - 결과를 안 지우면: test 태스크를 꺼도 지난 XML 이 남아 통과한다

    낡음 판정은 gradle 의 up-to-date 검사에 맡긴다. mtime 을 직접 비교해봤지만 gradle 이
    이미 하는 일을 다시 만드는 것이었고, 파이썬 스크립트를 고쳐도 Java 테스트가 낡았다고
    오판해 정상 상태까지 막았다. up-to-date 면 1~2초라 매번 돌려도 싸다.
    """
    d = os.path.join(ROOT, "build", "test-results", "test")

    # 지난 실행 결과를 지우고 시작한다. 안 그러면 이번 실행이 테스트를 안 돌려도
    # (test 태스크를 꺼놓는 등) 낡은 XML 을 읽어 통과시킨다 (2026-07-16 Codex 리뷰 지적).
    shutil.rmtree(d, ignore_errors=True)

    r = gradle("test")

    total = failed = skipped = 0
    if os.path.isdir(d):
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

    if r.returncode != 0:
        if failed:
            return False, f"테스트 실패 {failed}건 (전체 {total}건)"
        # 테스트 실패가 아닌데 gradle 이 죽었다 = 컴파일 에러 등
        tail = (r.stdout + r.stderr).strip().splitlines()
        return False, "테스트를 실행하지 못했다:\n" + "\n".join(tail[-12:])

    # 스킵을 빼고 실제로 실행된 것만 센다. @Disabled 를 전부 붙이면 tests 는 늘어나지만
    # 실행된 건 0건인데, total>0 만 보면 통과한다 (2026-07-16 Codex 리뷰 지적).
    executed = total - skipped
    if executed <= 0:
        return False, f"실행된 테스트가 0건이다 (수집 {total}건, 전부 스킵)"
    if failed:
        return False, f"테스트 실패 {failed}건 (실행 {executed}건)"
    return True, f"테스트 {executed}건 통과 (스킵 {skipped})"


def gate_secrets(files):
    """스캔 로직은 scan-secrets.py 하나를 CI 와 공유한다 — 두 곳이 다른 판정을 하지 않게."""
    if not files:
        return True, "검사할 파일 없음"
    script = os.path.join(ROOT, ".agents", "scripts", "scan-secrets.py")
    r = subprocess.run(
        [sys.executable, script, "--files", *sorted(files)],
        cwd=ROOT, capture_output=True, text=True,
    )
    if r.returncode == 0:
        return True, "비밀값 없음"
    return False, r.stdout.strip() or "비밀값 발견"


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


def gate_codex(files):
    """2중 검증 루프 3단계 강제. 검사 로직은 CI 와 check-review.py 하나를 공유한다."""
    if not touches_code(files):
        return True, "코드 변경 없음 — 교차 검증 불필요"
    r = subprocess.run(
        [sys.executable, os.path.join(SCRIPTS, "check-review.py"), "--verify"],
        cwd=ROOT, capture_output=True, text=True,
    )
    return r.returncode == 0, r.stdout.strip()


def gate_mcp_sync(files):
    """정본(.agents/mcp/servers.json) 을 고치고 sync 를 안 해서 .mcp.json 이 어긋난 채
    넘어가는 것을 막는다. 검사는 sync-mcp.sh --check 하나를 CI 와 공유한다.
    (CI 엔 ~/.codex/config.toml 이 없어 .mcp.json 만 대상이 된다.)"""
    if not touches_mcp(files):
        return True, "MCP 설정 변경 없음"
    # 게이트는 커밋되는 산출물(.mcp.json)만 본다. sync-mcp.sh 는 ~/.codex/config.toml 도
    # 검사하는데, 그건 머신 로컬이라 어긋나 있으면 .mcp.json 이 최신이어도 거짓 실패한다
    # (Codex 리뷰 지적). CODEX_HOME 을 없는 곳으로 돌려 config.toml 검사를 건너뛴다 —
    # CI 도 config.toml 이 없어 .mcp.json 만 보므로 로컬·CI 판정이 일치한다.
    r = sh("bash", os.path.join(SCRIPTS, "sync-mcp.sh"), "--check",
           env=dict(os.environ, CODEX_HOME="/nonexistent"))
    if r.returncode == 0:
        return True, "정본 ↔ .mcp.json 동기화됨"
    detail = (r.stdout + r.stderr).strip()
    return False, detail + "\n  정본을 고쳤으면 실행: .agents/scripts/sync-mcp.sh"


def gate_api_doc(files):
    """계약이 바뀌었는데 API.md 가 그대로면 FE 가 거짓 문서를 보게 된다.

    "계약이 실제로 바뀌었나"는 기계가 판정할 수 없다(컨트롤러를 고쳐도 순수 리팩터링일 수 있다).
    그래서 이 게이트는 로컬 전용이고 탈출구가 있다 — CI 로 올리면 가짜 API.md 수정을 강요하게 된다.
    결정적인 것은 CI 가, 판단이 필요한 것은 여기가 맡는다.
    """
    root_prefix, pkg_hints = CONTRACT_HINTS
    touched = [
        f for f in files
        if f.startswith(root_prefix) and f.endswith(".java")
        and any(h in f for h in pkg_hints)
    ]
    if not touched:
        return True, "계약 관련 파일 변경 없음"
    if API_DOC in files:
        return True, "계약 변경 + API.md 갱신됨"
    if os.path.exists(os.path.join(ROOT, ".agents", "tasks", "skip-api-doc")):
        return True, "계약 변경 없음으로 판단됨 (skip-api-doc)"
    return False, (
        f"{API_DOC} 가 갱신되지 않았다. 계약이 바뀐 것으로 보이는 파일:\n  "
        + "\n  ".join(touched)
        + f"\n  계약이 바뀌었으면 {API_DOC} 를 갱신할 것.\n"
        "  순수 리팩터링이라 계약이 그대로면: touch .agents/tasks/skip-api-doc"
    )


# ── 실행 ────────────────────────────────────────────────────────────────
def run():
    files = changed_files()
    code = touches_code(files)
    if not code and not touches_mcp(files):
        return None  # 코드도 MCP 설정도 안 바뀌면 게이트를 돌리지 않는다

    results = []
    if code:  # 컴파일·테스트는 코드가 바뀐 경우에만 (MCP 설정만 고쳤는데 Java 를 돌리지 않게)
        results += [
            ("컴파일", *gate_compile()),
            ("테스트 실행+통과", *gate_tests()),
        ]
    results += [
        ("비밀값 없음", *gate_secrets(files)),
        ("스키마/DDL 정합", *gate_schema(files)),
        ("API 계약 문서", *gate_api_doc(files)),
        ("Codex 교차 검증", *gate_codex(files)),
        ("MCP 동기화", *gate_mcp_sync(files)),
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
