#!/usr/bin/env python3
"""
비밀값 스캔. Stop 훅의 quality-gate.py 와 CI 가 이 스크립트 하나를 공유한다.

  --tracked        git 이 추적하는 전체 파일 (CI 용)
  --files a b c    지정한 파일만 (게이트 용)
  --selftest       탐지/오탐 자가 점검

exit 0 = 깨끗함 / 1 = 발견

설계: **정밀도 우선.** 오탐이 나면 CI 가 상시 빨간불이 되고 아무도 안 보게 된다.
넓게 잡으려다 `String token = tokenProvider.resolveToken()` 같은 평범한 코드까지
걸어서 못 쓰게 된 전례가 있다. recall 은 사람의 리뷰와 SECURITY.md 가 담당한다.
"""

import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))

# 값이 들어가는 설정 파일. 여기서만 일반 key/value 패턴을 본다.
# .java/.gradle/.md 는 제외 — `String token = ...` 이나 `io.jsonwebtoken:jjwt` 가 전부 걸린다.
CONFIG_EXT = (".yml", ".yaml", ".properties", ".env", ".toml")

# 어디서든 걸어야 하는 것들. 전부 고유 접두어나 구조가 있어 오탐이 사실상 0이다.
ALWAYS = [
    (re.compile(r"GOCSPX-[A-Za-z0-9_-]{10,}"),
     "구글 클라이언트 시크릿"),
    (re.compile(r'(?i)export\s+[A-Z_]*(?:PASSWORD|SECRET|TOKEN|APIKEY|API_KEY)[A-Z_]*\s*=\s*\\?["\']?(?!\$)[^\s"\'\\]{6,}'),
     "export 로 노출된 비밀값"),
    (re.compile(r"(?i)jdbc:[a-z]+://(?!\$\{)[a-z0-9.-]+:\d+"),
     "하드코딩된 DB 접속 정보"),
]

# 설정 파일 전용: `password: 값` 에서 값이 ${ENV} 플레이스홀더가 아니면 의심.
CONFIG_KV = re.compile(
    r'(?im)^\s*[\w.-]*\b(password|secret|token|api[_-]?key|credential)\b\s*[:=]\s*'
    r'["\']?(?!\$\{|\$[A-Z_]|["\']?\s*$)([^\s"\'#]{8,})'
)

# 이 스크립트와 게이트는 패턴 문자열을 품고 있어 자기 자신에 걸린다
SELF = {".agents/scripts/scan-secrets.py"}


def tracked_files():
    r = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True)
    return r.stdout.split()


def ignored(path):
    return subprocess.run(["git", "check-ignore", "-q", path], cwd=ROOT).returncode == 0


def scan_text(path, text):
    hits = []
    for pat, label in ALWAYS:
        m = pat.search(text)
        if m:
            hits.append((text[: m.start()].count("\n") + 1, label))
    if path.endswith(CONFIG_EXT):
        m = CONFIG_KV.search(text)
        if m:
            hits.append((text[: m.start()].count("\n") + 1, f"설정에 비밀값 리터럴 ({m.group(1)})"))
    return hits


def scan(files):
    out = []
    for f in sorted(set(files)):
        if f in SELF:
            continue
        full = os.path.join(ROOT, f)
        if not os.path.isfile(full):
            continue
        # gitignore 된 파일은 커밋되지 않으므로 검사 대상이 아니다
        if ignored(f):
            continue
        try:
            with open(full, encoding="utf-8", errors="ignore") as fh:
                text = fh.read()
        except OSError:
            continue
        for line, label in scan_text(f, text):
            out.append(f"{f}:{line}: {label}")
    return out


SELFTEST = [
    # (파일명, 내용, 걸려야 하나)
    ("application-prod.yml", "spring:\n  datasource:\n    password: ${DB_PASSWORD}\n", False),
    ("application-local.yml", "spring:\n  datasource:\n    password: sh0308141star\n", True),
    ("settings.local.json", '["Bash(export DB_PASSWORD=\\"sh0308141*\\")"]', True),
    ("a.yml", "google:\n  client-secret: GOCSPX-xWI3R1uU7eEDBzGiO01nCn2yeJx\n", True),
    ("build.gradle", "implementation 'io.jsonwebtoken:jjwt-api:0.12.6'\n", False),
    ("JwtFilter.java", "String token = tokenProvider.resolveToken(request);\n", False),
    ("TokenProvider.java", 'String accessToken = Jwts.builder().setSubject(id).compact();\n', False),
    ("SECURITY.md", "`${JWT_SECRET:changeme}` 처럼 쓰면 그 기본값이 곧 유출이다\n", False),
    ("app.yml", "url: jdbc:mysql://${DB_HOST}:3306/db\n", False),
    ("app.yml", "url: jdbc:mysql://tokaido.proxy.rlwy.net:11532/railway\n", True),
]


def selftest():
    ok = True
    for name, text, should_hit in SELFTEST:
        hits = scan_text(name, text)
        got = bool(hits)
        mark = "OK  " if got == should_hit else "FAIL"
        if got != should_hit:
            ok = False
        kind = "탐지" if should_hit else "오탐없음"
        print(f"  [{mark}] {kind:8} {name:24} {text.strip().splitlines()[-1][:44]}")
    print("자가점검 " + ("통과" if ok else "실패"))
    sys.exit(0 if ok else 1)


def main():
    if "--selftest" in sys.argv:
        selftest()
    if "--tracked" in sys.argv:
        files = tracked_files()
    elif "--files" in sys.argv:
        files = sys.argv[sys.argv.index("--files") + 1:]
    else:
        sys.exit("사용: scan-secrets.py --tracked | --files <파일...> | --selftest")

    hits = scan(files)
    if hits:
        print("비밀값 의심:")
        for h in hits:
            print("  " + h)
        sys.exit(1)
    print(f"비밀값 없음 ({len(files)}개 검사)")


if __name__ == "__main__":
    main()
