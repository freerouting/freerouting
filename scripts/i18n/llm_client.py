"""Google Gemini LLM client with retry/backoff and batch translation support."""

from __future__ import annotations

import json
import os
import re
import time
from typing import Any, Dict, List, Optional, Tuple

from i18n_output import err

DEFAULT_BATCH_SIZE = 15
DEFAULT_GEMINI_MODEL = "gemini-3.6-flash"
DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
MAX_RETRIES = 3
RETRY_BASE_DELAY_S = 1.0


def batch_size() -> int:
    raw = os.environ.get("LLM_BATCH_SIZE", str(DEFAULT_BATCH_SIZE))
    try:
        value = int(raw)
        return max(1, min(value, 25))
    except ValueError:
        return DEFAULT_BATCH_SIZE


def gemini_api_key() -> str:
    """Return the configured Gemini API key (empty when unset)."""
    return os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY") or ""


def gemini_config() -> Tuple[str, str, str]:
    """Return (api_key, model, base_url) for Gemini generateContent."""
    return (
        gemini_api_key(),
        os.environ.get("LLM_MODEL", DEFAULT_GEMINI_MODEL),
        os.environ.get("LLM_BASE_URL", DEFAULT_GEMINI_BASE_URL),
    )


CAPACITY_MAX_RETRIES = int(os.environ.get("LLM_CAPACITY_MAX_RETRIES", "100"))
CAPACITY_DELAYS_S = [10.0, 15.0, 20.0, 30.0, 45.0, 60.0]


def _is_server_capacity_error(exc: Exception) -> bool:
    """HTTP 503 or transient network timeout on server-side."""
    msg = str(exc).lower()
    return (
        "503" in msg
        or "high demand" in msg
        or "unavailable" in msg
        or "timed out" in msg
        or "timeout" in msg
    )


def _is_quota_or_balance_error(exc: Exception) -> bool:
    """HTTP 429: client/project quota limit reached or billing balance exhausted."""
    msg = str(exc).lower()
    return "429" in msg or "resource_exhausted" in msg or "quota" in msg


def _call_with_retry(fn, description: str) -> Optional[str]:
    capacity_attempt = 0
    standard_attempt = 0
    last_error: Optional[Exception] = None

    while True:
        try:
            return fn()
        except Exception as exc:  # noqa: BLE001 - surface provider errors to user
            last_error = exc

            # 1. Quota or Account Balance Exhausted (HTTP 429) -> Action required from user
            if _is_quota_or_balance_error(exc):
                err("\n" + "=" * 68)
                err("  [ACTION REQUIRED] Gemini API Quota or Account Balance Exhausted (429)")
                err("  Google AI Studio reported: RESOURCE_EXHAUSTED.")
                err("  Action needed: Please check your Google AI Studio billing or project quota:")
                err("    -> https://aistudio.google.com/app/plan_information")
                err("  Once your quota or balance is restored, re-run the translation command.")
                err("=" * 68 + "\n")
                return None

            # 2. Server-side Capacity Spike (HTTP 503) -> Automated backoff & retry
            if _is_server_capacity_error(exc):
                if capacity_attempt < CAPACITY_MAX_RETRIES:
                    delay = CAPACITY_DELAYS_S[min(capacity_attempt, len(CAPACITY_DELAYS_S) - 1)]
                    capacity_attempt += 1
                    err(
                        f"  [Gemini Server Spike] Google's server is experiencing high demand (HTTP 503). "
                        f"Waiting {delay:.0f}s before retry (attempt {capacity_attempt}/{CAPACITY_MAX_RETRIES})..."
                    )
                    time.sleep(delay)
                    continue
                err(
                    f"\n  {description} capacity retries exhausted after "
                    f"{CAPACITY_MAX_RETRIES} attempts ({sum(CAPACITY_DELAYS_S):.0f}s total wait): {last_error}"
                )
                return None

            # 3. Other transient network or server errors -> Standard exponential retry
            if standard_attempt < MAX_RETRIES:
                delay = RETRY_BASE_DELAY_S * (2 ** standard_attempt)
                standard_attempt += 1
                err(f"  {description} failed (attempt {standard_attempt}/{MAX_RETRIES}): {exc}")
                time.sleep(delay)
                continue

            err(f"  {description} gave up after {MAX_RETRIES} attempts: {last_error}")
            return None


def call_llm(prompt: str, max_tokens: int = 2000) -> Optional[str]:
    api_key, model, base_url = gemini_config()
    return _call_with_retry(
        lambda: _call_gemini(prompt, model, api_key, base_url, max_tokens),
        "Google Gemini API",
    )


def _gemini_uses_thinking_level(model: str) -> bool:
    """Gemini 3.x uses thinkingLevel; Gemini 2.5 and earlier use thinkingBudget."""
    normalized = model.lower()
    return normalized.startswith("gemini-3") or normalized.startswith("gemini-3.")


def _gemini_thinking_level() -> str:
    raw = os.environ.get("LLM_GEMINI_THINKING_LEVEL", "").strip().lower()
    allowed = {"low", "medium", "high"}
    return raw if raw in allowed else "low"


def _gemini_thinking_budget() -> Optional[int]:
    raw = os.environ.get("LLM_GEMINI_THINKING_BUDGET")
    if raw is None:
        # Translation prompts are deterministic; skip reasoning tokens by default.
        return 0
    raw = raw.strip()
    if raw.lower() in ("default", "none", "-1"):
        return None
    try:
        return int(raw)
    except ValueError:
        return 0


def _gemini_generation_config(model: str, max_tokens: int) -> Dict[str, Any]:
    """Build generationConfig for Gemini REST generateContent."""
    generation_config: Dict[str, Any] = {"maxOutputTokens": max_tokens}

    if _gemini_uses_thinking_level(model):
        # Gemini 3.x models require thinkingLevel ("low", "medium", "high"). Default is "low".
        generation_config["thinkingConfig"] = {"thinkingLevel": _gemini_thinking_level()}
    else:
        budget = _gemini_thinking_budget()
        if budget is not None:
            generation_config["thinkingConfig"] = {"thinkingBudget": budget}

    return generation_config


def _google_api_error_message(resp: Any) -> str:
    try:
        body = resp.json()
    except ValueError:
        return resp.text or str(resp.status_code)
    message = body.get("error", {}).get("message")
    if message:
        return message
    return json.dumps(body, ensure_ascii=False)


def _call_gemini(prompt: str, model: str, api_key: str, base_url: str, max_tokens: int) -> str:
    import requests

    if not api_key:
        raise ValueError(
            "GEMINI_API_KEY is not set. Export your Google AI Studio key, then restart "
            "Cursor (or your terminal) so the environment variable is visible to Python."
        )

    payload: Dict[str, Any] = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": _gemini_generation_config(model, max_tokens),
    }

    url = f"{base_url.rstrip('/')}/models/{model}:generateContent"
    resp = requests.post(
        url,
        headers={
            "Content-Type": "application/json",
            "x-goog-api-key": api_key,
        },
        json=payload,
        timeout=60,
    )
    if not resp.ok:
        raise ValueError(
            f"{resp.status_code} {resp.reason}: {_google_api_error_message(resp)}"
        )
    body = resp.json()

    candidates = body.get("candidates") or []
    if not candidates:
        block_reason = body.get("promptFeedback", {}).get("blockReason")
        raise ValueError(f"Gemini returned no candidates (blockReason={block_reason})")

    parts = candidates[0].get("content", {}).get("parts") or []
    text_parts = [part.get("text", "") for part in parts if isinstance(part.get("text"), str)]
    content = "".join(text_parts).strip()
    if not content:
        finish_reason = candidates[0].get("finishReason")
        raise ValueError(f"Gemini returned empty text (finishReason={finish_reason})")

    return content.strip("\"'")


def _extract_json_object(text: str) -> Optional[Dict[str, Any]]:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    try:
        parsed = json.loads(text)
        if isinstance(parsed, dict):
            return parsed
    except json.JSONDecodeError:
        pass

    match = re.search(r"\{.*\}", text, re.DOTALL)
    if match:
        try:
            parsed = json.loads(match.group(0))
            if isinstance(parsed, dict):
                return parsed
        except json.JSONDecodeError:
            return None
    return None


def max_tokens_for_values(values: List[str]) -> int:
    """Size Gemini output budget from English string length (long help text needs more)."""
    if not values:
        return 500
    longest = max(len(value) for value in values)
    # Translations can be longer; ~2 chars/token with headroom for JSON wrapping.
    return max(500, min(8192, longest * 2))


def parse_json_response(text: str) -> Optional[Dict[str, Any]]:
    """Parse a JSON object from an LLM response."""
    return _extract_json_object(text)


def translate_batch(
    prompt: str, expected_keys: List[str], *, english_values: Optional[List[str]] = None
) -> Tuple[Optional[Dict[str, str]], bool]:
    """
    Call Gemini with a batch prompt; returns (parsed_dict, api_success).
    If api_success is False, the API call itself failed (e.g. 503/429/network).
    If api_success is True but parsed_dict is None, JSON parsing failed.
    """
    values = english_values or []
    response = call_llm(prompt, max_tokens=max_tokens_for_values(values))
    if response is None:
        return None, False

    parsed = _extract_json_object(response)
    if parsed is None:
        err("  Failed to parse batch JSON response; will retry keys individually")
        return None, True

    result: Dict[str, str] = {}
    for key in expected_keys:
        value = parsed.get(key)
        if isinstance(value, str) and value.strip():
            result[key] = value.strip().strip("\"'")
    return result, True
