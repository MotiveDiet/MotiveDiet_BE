#!/usr/bin/env python3
"""
Codex 교차 검증(2중 검증 루프 3단계)이 실제로 이뤄졌는지 확인한다.
Stop 훅의 quality-gate.py 와 CI 가 이 스크립트 하나를 공유한다.

  --hash              현재 코드 상태 해시 출력
  --paths             검증 대상 경로 목록 출력 (quality-gate.py 가 이걸 읽어 공유한다)
  --verify            해시에 맞는 리뷰 기록이 있는지 확인 (exit 0/1)
  --base <ref>        base 대비 코드가 안 바뀌었으면 검증 불필요로 통과 (CI 용)

## 왜 diff 해시가 아니라 코드 상태 해시인가

diff 를 해시하면 리뷰 파일을 커밋하는 순간 diff 가 바뀌어 해시가 어긋난다(닭-달걀).
또 diff 는 base ref 에 따라 달라져서 로컬과 CI 가 다른 값을 낸다.
코드 파일의 **내용**을 해시하면 "검증된 그 코드가 지금 나가는 그 코드인가"를
로컬·CI 어디서든 같은 값으로 답할 수 있다.

## 한계 (정직하게)

이 검사는 리뷰 기록의 **존재와 코드 일치**만 증명한다. Codex 가 실제로 돌았는지는
증명하지 못한다 — 기록을 손으로 지어낼 수 있다. 다만 기록에는 Codex 의 실제 출력이
그대로 들어가고 git 에 영구히 남아 감사 가능하다. 우연히 건너뛰는 것은 막고,
작정한 위조는 흔적을 남긴다.
"""

import hashlib
import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
REVIEWS = os.path.join(ROOT, ".agents", "reviews")

# 이 경로가 바뀌면 Codex 교차 검증이 필요하다.
# 원칙: **검사를 무력화할 수 있는 모든 것**이 들어간다. 애플리케이션 코드만 넣으면
# 검사 장치 자체를 고쳐서 우회할 수 있다 (2026-07-16 Codex 리뷰가 지적한 High 2건).
#   .agents/scripts   게이트를 강제하는 스크립트. 여기가 깨지면 모든 검사가 무력화된다
#   .github/workflows CI 정의. 잡을 지우면 우회된다
#   gradlew, gradle/wrapper, .java-version, railpack.json
#                     빌드·배포 실행면. gradlew 를 조작하면 컴파일·테스트가 거짓말을 한다
# .agents/reviews/ 는 여기 없다 — 리뷰를 커밋해도 해시가 안 변해야 하기 때문이다(닭-달걀).
CODE_PATHS = [
    "src",
    "build.gradle",
    "settings.gradle",
    "gradlew",
    "gradlew.bat",
    "gradle/wrapper",
    ".java-version",
    "railpack.json",
    ".agents/scripts",
    ".github/workflows",
]


def sh(*args):
    return subprocess.run(args, cwd=ROOT, capture_output=True, text=True)


def code_files():
    """추적 중인 코드 파일 + 아직 add 하지 않은 새 코드 파일.

    untracked 를 포함하는 이유: `git ls-files` 만 쓰면 새로 만든 파일이 해시에 안 잡혀서,
    새 파일을 add 하기 전에는 예전 리뷰 기록이 그대로 인정된다. CI 는 커밋 후를 보므로
    로컬과 CI 판정이 갈린다 (2026-07-16 Codex 리뷰 지적).
    """
    tracked = sh("git", "ls-files", *CODE_PATHS).stdout.split()
    untracked = sh(
        "git", "ls-files", "--others", "--exclude-standard", *CODE_PATHS
    ).stdout.split()
    return sorted(set(tracked) | set(untracked))


def code_hash():
    """코드 파일의 워킹트리 내용을 해시한다.

    CI 는 클린 트리라 커밋된 내용과 같고, 로컬은 미커밋 변경까지 반영된다 —
    양쪽이 같은 코드를 보고 있으면 같은 값이 나온다.

    워킹트리에 없는 파일(삭제 예정)은 아예 건너뛴다. `<deleted>` 같은 표식을 넣으면
    커밋 후 CI 에서는 `git ls-files` 에 그 파일이 아예 없어 해시가 달라지고, 정상적인
    파일 삭제 작업이 막힌다 (2026-07-16 Codex 리뷰 지적).
    """
    h = hashlib.sha256()
    for f in code_files():
        full = os.path.join(ROOT, f)
        if not os.path.isfile(full):
            continue
        h.update(f.encode())
        with open(full, "rb") as fh:
            h.update(hashlib.sha256(fh.read()).digest())
    return h.hexdigest()[:16]



def code_changed_since(base):
    """base 대비 코드 파일이 바뀌었나. 못 판단하면 안전하게 True."""
    r = sh("git", "diff", "--name-only", base, "--", *CODE_PATHS)
    if r.returncode != 0:
        return True
    return bool(r.stdout.split())


def find_record(h):
    """해당 코드 해시를 **코드 리뷰**로 검증한 기록을 찾는다.

    stage 를 함께 확인하는 이유: 계획 리뷰(stage: plan) 기록이 코드 검증을 대신 통과시키면
    안 된다 — 계획을 검토한 것과 구현된 코드를 검토한 것은 다르다 (2026-07-16 Codex 리뷰 지적).
    """
    if not os.path.isdir(REVIEWS):
        return None
    for name in sorted(os.listdir(REVIEWS)):
        if not name.endswith(".md"):
            continue
        path = os.path.join(REVIEWS, name)
        with open(path, encoding="utf-8") as fh:
            head = fh.read(2000)
        if not re.search(r"^code_hash:\s*%s\s*$" % re.escape(h), head, re.M):
            continue
        if not re.search(r"^stage:\s*code\s*$", head, re.M):
            continue
        if not re.search(r"^reviewer:\s*codex\s*$", head, re.M):
            continue
        return path
    return None


HOWTO = """  실행: .agents/scripts/codex-review.sh "<리뷰 프롬프트>"
        (플래그와 기록까지 스크립트가 처리한다. 프롬프트에 결론을 주지 마라 —
         동조만 하고 교차 검증이 무의미해진다)"""


def main():
    if "--hash" in sys.argv:
        print(code_hash())
        return

    if "--paths" in sys.argv:
        # quality-gate.py 가 이 목록을 읽어 쓴다. 두 곳에 손으로 유지하면 어긋난다 —
        # 실제로 gradlew.bat 이 한쪽에만 있어 로컬 게이트가 생략되는 구멍이 있었다.
        print("\n".join(CODE_PATHS))
        return


    if "--verify" not in sys.argv:
        sys.exit("사용: check-review.py --hash | --verify [--base <ref>]")

    if "--base" in sys.argv:
        base = sys.argv[sys.argv.index("--base") + 1]
        if not code_changed_since(base):
            print(f"코드 변경 없음 ({base} 대비) — 교차 검증 불필요")
            return

    h = code_hash()
    rec = find_record(h)
    if rec:
        rel = os.path.relpath(rec, ROOT)
        print(f"Codex 교차 검증됨: {rel} (코드 {h})")
        return

    print(f"Codex 교차 검증 기록이 없다 (현재 코드 {h}).")
    have = sorted(os.listdir(REVIEWS)) if os.path.isdir(REVIEWS) else []
    if have:
        print("  기존 기록은 지금 코드와 맞지 않는다 — 리뷰 이후 코드가 바뀌었다:")
        for n in have:
            print(f"    {n}")
    print(HOWTO)
    sys.exit(1)


if __name__ == "__main__":
    main()
