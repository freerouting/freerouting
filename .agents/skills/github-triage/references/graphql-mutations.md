# GitHub GraphQL Mutations & API Reference for Freerouting

This reference provides the exact GraphQL queries, mutations, and field IDs used to automate issue and pull request management on the `freerouting/freerouting` repository.

---

## 1. Node IDs & Field Identifiers

### Repository Native Issue Types
- **Bug:** `IT_kwDOAHpJfc4ADZap`
- **Feature:** `IT_kwDOAHpJfc4ADZar`
- **Task:** `IT_kwDOAHpJfc4ADZam`

### Repository Native Issue Fields
- **Priority Field ID:** `IFSS_kgDOABL39A`
  - `Urgent`: `IFSSO_kgDOACEcrA`
  - `High`: `IFSSO_kgDOACEcrQ`
  - `Medium`: `IFSSO_kgDOACEcrg`
  - `Low`: `IFSSO_kgDOACEcrw`
- **Effort Field ID:** `IFSS_kgDOABL39w`
  - `High`: `IFSSO_kgDOACEcsA`
  - `Medium`: `IFSSO_kgDOACEcsQ`
  - `Low`: `IFSSO_kgDOACEcsg`

---

## 2. Common Queries

### Query Open Issues with Comments, Labels, Milestones & Project Items
```graphql
query {
  repository(owner: "freerouting", name: "freerouting") {
    issues(first: 100, states: OPEN) {
      nodes {
        id
        number
        title
        body
        author { login }
        createdAt
        milestone { number title }
        labels(first: 10) { nodes { name } }
        projectItems(first: 5) { nodes { id project { title } } }
        comments(first: 30) {
          nodes {
            author { login }
            body
            createdAt
          }
        }
      }
    }
  }
}
```

### Query Open Pull Requests with Labels, Milestones & Diff Details
```graphql
query {
  repository(owner: "freerouting", name: "freerouting") {
    pullRequests(first: 100, states: OPEN) {
      nodes {
        id
        number
        title
        body
        milestone { number title }
        labels(first: 10) { nodes { name } }
        projectItems(first: 5) { nodes { id project { title } } }
      }
    }
  }
}
```

---

## 3. Mutations

### Update Issue Type (Bug, Feature, Task)
```graphql
mutation {
  updateIssue(input: {
    id: "ISSUE_NODE_ID",
    issueTypeId: "IT_kwDOAHpJfc4ADZap" # Bug
  }) {
    issue {
      id
      issueType { name }
    }
  }
}
```

### Set Repository Native Issue Fields (Priority & Effort)
```graphql
mutation {
  setIssueFieldValue(input: {
    issueId: "ISSUE_NODE_ID",
    issueFields: [
      { fieldId: "IFSS_kgDOABL39A", singleSelectOptionId: "IFSSO_kgDOACEcrQ" }, # Priority: High
      { fieldId: "IFSS_kgDOABL39w", singleSelectOptionId: "IFSSO_kgDOACEcsQ" }  # Effort: Medium
    ]
  }) {
    clientMutationId
  }
}
```

---

## 4. Python Retry Pattern for CLI & GraphQL Operations

Network calls on Windows systems can encounter transient socket resets or TLS timeouts. Always use an exponential-backoff retry wrapper:

```python
import json
import subprocess
import time

def run_gh_cmd(cmd, retries=4, delay=2):
    """Executes a gh CLI command with automatic retry on network errors."""
    for attempt in range(1, retries + 1):
        try:
            res = subprocess.run(cmd, capture_output=True, text=True, check=True)
            return res.stdout.strip()
        except subprocess.CalledProcessError as e:
            err = (e.stderr or str(e)).strip()
            if attempt == retries:
                raise RuntimeError(f"Command {' '.join(cmd)} failed permanently: {err}")
            time.sleep(delay * attempt)

def run_graphql_query(query, retries=4, delay=2):
    """Executes a GraphQL query/mutation and returns parsed JSON data."""
    cmd = ["gh", "api", "graphql", "-f", f"query={query}"]
    raw = run_gh_cmd(cmd, retries=retries, delay=delay)
    return json.loads(raw)
```
