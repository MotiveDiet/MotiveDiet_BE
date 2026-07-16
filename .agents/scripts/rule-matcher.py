#!/usr/bin/env python3
"""
파일 경로에 매칭되는 룰을 찾아 에이전트 컨텍스트에 주입한다.

두 도구가 이 스크립트 하나를 공유한다:
  - Claude Code : PreToolUse:Edit|Write 훅이 stdin 으로 훅 JSON 을 넘김
                  → hookSpecificOutput.additionalContext 로 룰 본문을 반환
  - Codex       : codex-rule-hint.sh 가 파일 경로를 인자로 넘김
                  → 평문으로 출력

사용:
  echo '{"tool_input":{"file_path":"..."}}' | rule-matcher.py     # Claude 훅 모드
  rule-matcher.py --path src/main/java/.../User.java              # 평문 모드
"""

import fnmatch
import json
import os
import sys

RULES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "rules")


def parse_rule(path):
    """frontmatter 의 paths 배열과 본문을 뽑는다. PyYAML 없이 처리한다(의존성 추가 금지)."""
    with open(path, encoding="utf-8") as f:
        text = f.read()

    if not text.startswith("---"):
        return None

    end = text.find("\n---", 3)
    if end == -1:
        return None

    frontmatter = text[3:end]
    body = text[end + 4 :].strip()

    patterns = []
    for line in frontmatter.splitlines():
        line = line.strip()
        if not line.startswith("paths:"):
            continue
        raw = line[len("paths:") :].strip()
        if raw.startswith("[") and raw.endswith("]"):
            for item in raw[1:-1].split(","):
                item = item.strip().strip("\"'")
                if item:
                    patterns.append(item)

    if not patterns:
        return None

    return {"name": os.path.basename(path)[:-3], "patterns": patterns, "body": body}


def matches(file_path, pattern):
    """`**/` 는 fnmatch 가 0개 디렉토리에 매칭시키지 못하므로 접두어를 벗긴 것도 함께 시도한다."""
    norm = file_path.replace(os.sep, "/")
    if fnmatch.fnmatch(norm, pattern):
        return True
    if pattern.startswith("**/") and fnmatch.fnmatch(norm, pattern[3:]):
        return True
    # 절대경로로 들어와도 레포 상대 패턴이 맞도록 뒤에서부터 비교
    return fnmatch.fnmatch(norm, "*/" + pattern.lstrip("*/"))


def find_matching(file_path):
    if not file_path or not os.path.isdir(RULES_DIR):
        return []

    hits = []
    for name in sorted(os.listdir(RULES_DIR)):
        if not name.endswith(".md"):
            continue
        rule = parse_rule(os.path.join(RULES_DIR, name))
        if rule and any(matches(file_path, p) for p in rule["patterns"]):
            hits.append(rule)
    return hits


def render(file_path, rules):
    head = f"이 파일에 적용되는 프로젝트 룰 ({len(rules)}개) — {file_path}"
    parts = [head, ""]
    for r in rules:
        parts.append(f"### {r['name']}")
        parts.append(r["body"])
        parts.append("")
    return "\n".join(parts).strip()


def main():
    if "--path" in sys.argv:
        file_path = sys.argv[sys.argv.index("--path") + 1]
        rules = find_matching(file_path)
        if rules:
            print(render(file_path, rules))
        return

    # Claude Code 훅 모드: stdin 으로 훅 JSON 이 들어온다.
    # 훅이 죽으면 Edit/Write 가 막히므로, 어떤 실패에도 조용히 통과시킨다.
    try:
        payload = json.load(sys.stdin)
        file_path = payload.get("tool_input", {}).get("file_path", "")
        rules = find_matching(file_path)
        if not rules:
            return
        print(json.dumps({
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "additionalContext": render(file_path, rules),
            }
        }))
    except Exception:
        return


if __name__ == "__main__":
    main()
