#!/usr/bin/env python3
"""
Automated issue and pull request triage script for Freerouting.
Runs in GitHub Actions when an issue or pull request is opened.
"""

import json
import os
import re
import subprocess
import sys
import time

TYPE_MAP = {
    "Bug": "IT_kwDOAHpJfc4ADZap",
    "Feature": "IT_kwDOAHpJfc4ADZar",
    "Task": "IT_kwDOAHpJfc4ADZam",
}

PRIO_FIELD_ID = "IFSS_kgDOABL39A"
PRIO_OPTS = {
    "Urgent": "IFSSO_kgDOACEcrA",
    "High": "IFSSO_kgDOACEcrQ",
    "Medium": "IFSSO_kgDOACEcrg",
    "Low": "IFSSO_kgDOACEcrw",
}

EFFORT_FIELD_ID = "IFSS_kgDOABL39w"
EFFORT_OPTS = {
    "High": "IFSSO_kgDOACEcsA",
    "Medium": "IFSSO_kgDOACEcsQ",
    "Low": "IFSSO_kgDOACEcsg",
}


def run_cmd(cmd, retries=3, delay=2):
    """Executes a command with retry logic."""
    for attempt in range(1, retries + 1):
        try:
            res = subprocess.run(cmd, capture_output=True, text=True, check=True)
            return res.stdout.strip()
        except subprocess.CalledProcessError as e:
            err = (e.stderr or str(e)).strip()
            if attempt == retries:
                print(f"[ERROR] Command {' '.join(cmd)} failed: {err}", file=sys.stderr)
                raise
            time.sleep(delay * attempt)


def run_graphql(query):
    """Executes a GraphQL mutation/query."""
    cmd = ["gh", "api", "graphql", "-f", f"query={query}"]
    raw = run_cmd(cmd)
    return json.loads(raw) if raw else {}


def triage_issue(issue):
    """Triages a newly created issue."""
    num = issue["number"]
    node_id = issue["node_id"]
    title = (issue.get("title") or "").strip()
    body = (issue.get("body") or "").strip()
    existing_labels = {lbl["name"] for lbl in issue.get("labels", [])}

    print(f"--- Triaging Issue #{num}: '{title}' ---")

    labels_to_add = set()

    # 1. Detect Component
    if re.search(r"routing-engine", body, re.IGNORECASE) or "routing-engine" in existing_labels:
        labels_to_add.add("routing-engine")
    if re.search(r"\bGUI\b", body) or "GUI" in existing_labels:
        labels_to_add.add("GUI")
    if re.search(r"\bCLI\b", body) or "CLI" in existing_labels:
        labels_to_add.add("CLI")
    if re.search(r"\bAPI\b", body) or "API" in existing_labels:
        labels_to_add.add("API")
    if re.search(r"\bMCP\b", body) or "MCP" in existing_labels:
        labels_to_add.add("MCP")

    # 2. Detect OS / Platform
    if re.search(r"\bWindows\b", body, re.IGNORECASE):
        labels_to_add.add("Windows")
    if re.search(r"\bmacOS\b|\bOSX\b|\bApple Silicon\b", body, re.IGNORECASE):
        labels_to_add.add("macOS")
    if re.search(r"\bLinux\b|\bUbuntu\b|\bDebian\b|\bArch\b|\bFedora\b", body, re.IGNORECASE):
        labels_to_add.add("Linux")
    if re.search(r"\bDocker\b|\bContainer\b", body, re.IGNORECASE):
        labels_to_add.add("Docker")

    # 3. Detect EDA Tool
    if re.search(r"\bKiCad\b", body, re.IGNORECASE):
        labels_to_add.add("KiCad")
    if re.search(r"\bAutodesk Fusion\b|\bEAGLE\b|\bFusion 360\b", body, re.IGNORECASE):
        labels_to_add.add("Autodesk Fusion")

    # 4. Detect Type & Tech Stack
    is_bug = title.startswith("[Bug]") or "bug" in existing_labels or re.search(r"bug|error|crash|regression|exception|fail", title, re.IGNORECASE)
    is_doc = re.search(r"\bdocumentation\b|\bguide\b|\btutorial\b", title, re.IGNORECASE) or "documentation" in existing_labels

    if is_bug:
        labels_to_add.add("bug")
        issue_type = "Bug"
    elif is_doc:
        labels_to_add.add("documentation")
        issue_type = "Task"
    else:
        labels_to_add.add("enhancement")
        issue_type = "Feature"

    # Core codebase is Java by default unless solely documentation/Python
    if not is_doc:
        labels_to_add.add("Java")

    # 5. Determine Priority & Effort
    if re.search(r"regression|data loss|deadlock|freeze|crash|corrupt|security", body + " " + title, re.IGNORECASE):
        priority = "High"
    else:
        priority = "Medium"
    effort = "Medium"

    # 6. Apply Milestone and Labels via gh issue edit
    new_labels = labels_to_add - existing_labels
    edit_cmd = ["gh", "issue", "edit", str(num)]
    if not issue.get("milestone"):
        edit_cmd.extend(["--milestone", "Future"])
    for l in new_labels:
        edit_cmd.extend(["--add-label", l])

    # Clean title prefix if needed (e.g. [Bug]: -> Subsystem: ...)
    clean_title = title
    if title.startswith("[Bug]:"):
        clean_title = title.replace("[Bug]:", "").strip()
    elif title.startswith("[Feat]:"):
        clean_title = title.replace("[Feat]:", "").strip()

    if clean_title != title:
        edit_cmd.extend(["--title", clean_title])

    print(f"Executing: {' '.join(edit_cmd)}")
    run_cmd(edit_cmd)

    # 7. Set Native Issue Type
    t_id = TYPE_MAP[issue_type]
    m_type = f'''mutation {{
      updateIssue(input: {{ id: "{node_id}", issueTypeId: "{t_id}" }}) {{
        issue {{ id }}
      }}
    }}'''
    try:
        run_graphql(m_type)
        print(f"Set native Issue Type -> {issue_type}")
    except Exception as e:
        print(f"[WARN] Failed to set issue type: {e}", file=sys.stderr)

    # 8. Set Native Priority & Effort
    p_opt = PRIO_OPTS[priority]
    e_opt = EFFORT_OPTS[effort]
    m_fields = f'''mutation {{
      setIssueFieldValue(input: {{
        issueId: "{node_id}",
        issueFields: [
          {{ fieldId: "{PRIO_FIELD_ID}", singleSelectOptionId: "{p_opt}" }},
          {{ fieldId: "{EFFORT_FIELD_ID}", singleSelectOptionId: "{e_opt}" }}
        ]
      }}) {{ clientMutationId }}
    }}'''
    try:
        run_graphql(m_fields)
        print(f"Set native Priority -> {priority}, Effort -> {effort}")
    except Exception as e:
        print(f"[WARN] Failed to set native fields: {e}", file=sys.stderr)

    print(f"Successfully triaged Issue #{num}!\n")


def triage_pull_request(pr):
    """Triages a newly created pull request."""
    num = pr["number"]
    title = (pr.get("title") or "").strip()
    existing_labels = {lbl["name"] for lbl in pr.get("labels", [])}

    print(f"--- Triaging Pull Request #{num}: '{title}' ---")

    labels_to_add = set()

    # 1. Type from title prefix
    if re.match(r"^fix(\(.*\))?:", title, re.IGNORECASE):
        labels_to_add.add("bug")
    elif re.match(r"^feat(\(.*\))?:", title, re.IGNORECASE):
        labels_to_add.add("enhancement")
    elif re.match(r"^docs(\(.*\))?:", title, re.IGNORECASE):
        labels_to_add.add("documentation")
    elif re.match(r"^ci(\(.*\))?:|^build(\(.*\))?:", title, re.IGNORECASE):
        labels_to_add.add("CI")
    elif re.search(r"bump|dependencies", title, re.IGNORECASE):
        labels_to_add.add("dependencies")

    # 2. Inspect modified files
    try:
        diff_files = run_cmd(["gh", "pr", "diff", str(num), "--name-only"]).splitlines()
    except Exception:
        diff_files = []

    for f in diff_files:
        f_norm = f.replace("\\", "/")
        if "src/main/java/app/freerouting/gui" in f_norm:
            labels_to_add.add("GUI")
        if any(pkg in f_norm for pkg in ["autoroute", "board", "geometry", "drc"]):
            labels_to_add.add("routing-engine")
        if "src/main/java/app/freerouting/api" in f_norm:
            labels_to_add.add("API")
        if "integrations/KiCad" in f_norm:
            labels_to_add.add("KiCad")
        if ".github/workflows" in f_norm:
            labels_to_add.add("CI")
        if f_norm.endswith(".java"):
            labels_to_add.add("Java")
        if f_norm.endswith(".py"):
            labels_to_add.add("Python")

    # 3. Apply Milestone and Labels via gh pr edit
    new_labels = labels_to_add - existing_labels
    edit_cmd = ["gh", "pr", "edit", str(num)]
    if not pr.get("milestone"):
        edit_cmd.extend(["--milestone", "Future"])
    for l in new_labels:
        edit_cmd.extend(["--add-label", l])

    if len(edit_cmd) > 3:
        print(f"Executing: {' '.join(edit_cmd)}")
        run_cmd(edit_cmd)

    print(f"Successfully triaged PR #{num}!\n")


def main():
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    event_name = os.environ.get("GITHUB_EVENT_NAME")

    if not event_path or not os.path.exists(event_path):
        print("GITHUB_EVENT_PATH not found or invalid. Exiting.")
        sys.exit(0)

    with open(event_path, "r", encoding="utf-8") as f:
        event = json.load(f)

    if event_name == "issues" and "issue" in event:
        triage_issue(event["issue"])
    elif event_name in ("pull_request", "pull_request_target") and "pull_request" in event:
        triage_pull_request(event["pull_request"])
    else:
        print(f"Event {event_name} not handled for automated triage.")


if __name__ == "__main__":
    main()
