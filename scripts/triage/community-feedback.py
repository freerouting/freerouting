#!/usr/bin/env python3
"""Automated community feedback script using Google Gemini.

Provides immediate, polite feedback on PR merges and issue closures for Freerouting.
"""

import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

GEMINI_MODEL = "gemini-flash-latest"
API_URL_TEMPLATE = (
    "https://generativelanguage.googleapis.com/v1beta/models/"
    "{model}:generateContent?key={api_key}"
)
SNAPSHOT_URL = "https://github.com/freerouting/freerouting/releases/tag/SNAPSHOT"


def run_gh_cmd(cmd: list[str]) -> str:
    """Run a GitHub CLI command and return stdout."""
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return res.stdout.strip()


def post_comment(target_type: str, number: int, comment_text: str) -> None:
    """Post a comment using gh CLI with --body-file to avoid shell mangling."""
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as tf:
        tf.write(comment_text)
        temp_path = tf.name

    try:
        if target_type == "pr":
            cmd = ["gh", "pr", "comment", str(number), "--body-file", temp_path]
        else:
            cmd = ["gh", "issue", "comment", str(number), "--body-file", temp_path]
        run_gh_cmd(cmd)
        print(f"[OK] Posted comment to {target_type} #{number}")
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)


def call_gemini(prompt: str, api_key: str) -> str:
    """Call Google Gemini API with thinking disabled (budget=0) using urllib."""
    url = API_URL_TEMPLATE.format(model=GEMINI_MODEL, api_key=api_key)
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.3,
            "maxOutputTokens": 800,
            "thinkingConfig": {"thinking_budget": 0},
        },
    }

    req_data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=req_data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            resp_json = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        print(f"[ERROR] Gemini API returned HTTP {e.code}: {err_body}", file=sys.stderr)
        raise
    except Exception as e:
        print(f"[ERROR] Failed to call Gemini API: {e}", file=sys.stderr)
        raise

    candidates = resp_json.get("candidates", [])
    if not candidates:
        raise ValueError("Gemini returned no candidates.")

    parts = candidates[0].get("content", {}).get("parts", [])
    # Filter out thought parts, keeping only output text
    text_parts = [
        p.get("text", "")
        for p in parts
        if "text" in p and not p.get("thought", False)
    ]
    result = "".join(text_parts).strip()
    if not result:
        raise ValueError("Gemini returned empty text response.")
    return result


def find_linked_issues(text: str) -> list[int]:
    """Find issue numbers referenced by keywords like fixes #123, closes #456."""
    pattern = r"(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s+#(\d+)"
    matches = re.findall(pattern, text, re.IGNORECASE)
    return sorted(list({int(m) for m in matches}))


def handle_pr_merged(pr: dict, api_key: str) -> None:
    """Handle pull request merge event."""
    pr_number = pr["number"]
    pr_title = pr.get("title", "").strip()
    pr_body = pr.get("body", "") or ""
    author = pr.get("user", {}).get("login", "")

    print(f"[INFO] Processing merged PR #{pr_number} by @{author}: {pr_title}")

    prompt = f"""You are drafting a GitHub comment on behalf of Andras, the maintainer of the Freerouting project.
Rules:
- Write in the first-person singular ("I", "my", "me"), NOT plural ("we", "our").
- Be warm, professional, concise, and genuinely grateful.
- Thank @{author} for their contribution.
- In 1-3 bullet points, briefly summarize what was accomplished in this PR based strictly on the title and description provided.
- Mention that the changes are merged into `master` and will be available in the upcoming release and testable immediately in the nightly SNAPSHOT build ({SNAPSHOT_URL}).
- Do NOT make up any unmentioned technical claims.
- Do NOT include any intro or conversational filler outside the comment itself.

PR Title: {pr_title}
PR Description:
{pr_body[:2000]}
"""

    try:
        reply = call_gemini(prompt, api_key)
        reply += "\n\n---\n*— Generated with Gemini on behalf of the Freerouting project.*"
        post_comment("pr", pr_number, reply)
    except Exception as e:
        print(f"[WARN] Failed to generate/post PR comment: {e}", file=sys.stderr)

    # Check for linked issues
    linked_issues = find_linked_issues(f"{pr_title}\n{pr_body}")
    for issue_num in linked_issues:
        print(f"[INFO] Notifying linked issue #{issue_num} from PR #{pr_number}")
        issue_prompt = f"""You are drafting a GitHub comment on an issue on behalf of Andras, the maintainer of the Freerouting project.
Rules:
- Write in the first-person singular ("I", "my", "me").
- Inform the reporter that this issue has been resolved and merged into `master` via PR #{pr_number} ("{pr_title}").
- Provide a 1-sentence summary of the fix.
- Mention that the fix can be tested right away in the latest nightly [SNAPSHOT build]({SNAPSHOT_URL}) or will be included in the next official release.
- Be concise (3-4 sentences max), polite, and professional.
- Do NOT include any intro or conversational filler outside the comment.
"""
        try:
            issue_reply = call_gemini(issue_prompt, api_key)
            issue_reply += "\n\n---\n*— Generated with Gemini on behalf of the Freerouting project.*"
            post_comment("issue", issue_num, issue_reply)
        except Exception as e:
            print(f"[WARN] Failed to notify issue #{issue_num}: {e}", file=sys.stderr)


def handle_issue_closed(issue: dict, api_key: str) -> None:
    """Handle issue closure event."""
    issue_number = issue["number"]
    issue_title = issue.get("title", "").strip()
    issue_body = issue.get("body", "") or ""
    state_reason = issue.get("state_reason", "completed")  # completed or not_planned
    author = issue.get("user", {}).get("login", "")

    print(f"[INFO] Processing closed issue #{issue_number}: {issue_title} (reason: {state_reason})")

    if state_reason == "completed":
        prompt = f"""You are drafting a closing GitHub comment on an issue on behalf of Andras, the maintainer of Freerouting.
Rules:
- Write in first-person singular ("I", "my").
- Thank @{author} for reporting this issue.
- State that the issue has been resolved.
- Mention that the fix is available to test in the nightly [SNAPSHOT build]({SNAPSHOT_URL}) or can be expected in the upcoming official release.
- Keep it brief (under 4 sentences), direct, and polite.
- Do NOT include conversational filler outside the comment.

Issue Title: {issue_title}
Issue Description:
{issue_body[:1500]}
"""
    else:
        prompt = f"""You are drafting a closing GitHub comment on an issue on behalf of Andras, the maintainer of Freerouting.
Rules:
- Write in first-person singular ("I", "my").
- Thank @{author} for taking the time to report / discuss this.
- Politely acknowledge that this issue has been closed as not planned / superseded.
- Keep it warm, polite, and brief (2-3 sentences).
- Do NOT include conversational filler outside the comment.

Issue Title: {issue_title}
Issue Description:
{issue_body[:1500]}
"""

    try:
        reply = call_gemini(prompt, api_key)
        reply += "\n\n---\n*— Generated with Gemini on behalf of the Freerouting project.*"
        post_comment("issue", issue_number, reply)
    except Exception as e:
        print(f"[WARN] Failed to post closing comment on issue #{issue_number}: {e}", file=sys.stderr)


def main() -> None:
    """Entry point for community feedback automation."""
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        print("[NOTICE] GEMINI_API_KEY is not set. Skipping community feedback automation.")
        sys.exit(0)

    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if not event_path or not os.path.exists(event_path):
        print(f"[ERROR] Event file not found: {event_path}", file=sys.stderr)
        sys.exit(1)

    with open(event_path, "r", encoding="utf-8") as f:
        event = json.load(f)

    event_name = os.environ.get("GITHUB_EVENT_NAME", "")
    action = event.get("action", "")

    if event_name in ("pull_request", "pull_request_target"):
        pr = event.get("pull_request", {})
        merged = pr.get("merged", False)
        if action == "closed" and merged:
            handle_pr_merged(pr, api_key)
        else:
            print(f"[INFO] Skipping PR event: action={action}, merged={merged}")
    elif event_name == "issues":
        if action == "closed":
            issue = event.get("issue", {})
            handle_issue_closed(issue, api_key)
        else:
            print(f"[INFO] Skipping issue event: action={action}")
    else:
        print(f"[INFO] Unsupported event name: {event_name}")


if __name__ == "__main__":
    main()
