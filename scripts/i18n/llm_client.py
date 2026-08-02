"""LLM client with retry/backoff and batch translation support."""

from __future__ import annotations

import json
import os
import re
import time
from typing import Any, Dict, List, Optional, Tuple

from i18n_output import err

DEFAULT_BATCH_SIZE = 15
MAX_RETRIES = 3
RETRY_BASE_DELAY_S = 1.0


def batch_size() -> int:
    raw = os.environ.get("LLM_BATCH_SIZE", str(DEFAULT_BATCH_SIZE))
    try:
        value = int(raw)
        return max(1, min(value, 25))
    except ValueError:
        return DEFAULT_BATCH_SIZE


def _provider_config() -> Tuple[str, str, str, str]:
    provider = os.environ.get("LLM_PROVIDER", "openai").lower()
    api_key = os.environ.get("LLM_API_KEY") or os.environ.get("OPENAI_API_KEY", "")

    if provider == "openai":
        model = os.environ.get("LLM_MODEL", "gpt-4o-mini")
        base_url = os.environ.get("LLM_BASE_URL", "https://api.openai.com/v1")
    elif provider == "anthropic":
        model = os.environ.get("LLM_MODEL", "claude-3-haiku-20240307")
        base_url = "https://api.anthropic.com/v1"
    elif provider == "ollama":
        model = os.environ.get("LLM_MODEL", "llama3.2")
        base_url = os.environ.get("LLM_BASE_URL", "http://localhost:11434")
    else:
        raise ValueError(f"Unknown LLM provider: {provider}")

    return provider, api_key, model, base_url


def _call_with_retry(fn, description: str) -> Optional[str]:
    last_error: Optional[Exception] = None
    for attempt in range(MAX_RETRIES):
        try:
            return fn()
        except Exception as exc:  # noqa: BLE001 - surface provider errors to user
            last_error = exc
            delay = RETRY_BASE_DELAY_S * (2 ** attempt)
            err(f"  {description} failed (attempt {attempt + 1}/{MAX_RETRIES}): {exc}")
            if attempt + 1 < MAX_RETRIES:
                time.sleep(delay)
    err(f"  {description} gave up after {MAX_RETRIES} attempts: {last_error}")
    return None


def call_llm(prompt: str, max_tokens: int = 2000) -> Optional[str]:
    provider, api_key, model, base_url = _provider_config()

    if provider == "openai":
        return _call_with_retry(
            lambda: _call_openai(prompt, model, api_key, base_url, max_tokens),
            "OpenAI API",
        )
    if provider == "anthropic":
        return _call_with_retry(
            lambda: _call_anthropic(prompt, model, api_key, max_tokens),
            "Anthropic API",
        )
    if provider == "ollama":
        return _call_with_retry(
            lambda: _call_ollama(prompt, model, base_url, max_tokens),
            "Ollama API",
        )

    err(f"  Unknown LLM provider: {provider}")
    return None


def _call_openai(prompt: str, model: str, api_key: str, base_url: str, max_tokens: int) -> str:
    import requests

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0.0,
        "max_tokens": max_tokens,
    }
    resp = requests.post(
        f"{base_url.rstrip('/')}/chat/completions",
        headers=headers,
        json=payload,
        timeout=60,
    )
    resp.raise_for_status()
    content = resp.json()["choices"][0]["message"]["content"].strip()
    return content.strip("\"'")


def _call_anthropic(prompt: str, model: str, api_key: str, max_tokens: int) -> str:
    import requests

    headers = {
        "x-api-key": api_key,
        "anthropic-version": "2023-06-01",
        "Content-Type": "application/json",
    }
    payload = {
        "model": model or "claude-3-haiku-20240307",
        "max_tokens": max_tokens,
        "messages": [{"role": "user", "content": prompt}],
    }
    resp = requests.post(
        "https://api.anthropic.com/v1/messages",
        headers=headers,
        json=payload,
        timeout=60,
    )
    resp.raise_for_status()
    return resp.json()["content"][0]["text"].strip().strip("\"'")


def _call_ollama(prompt: str, model: str, base_url: str, max_tokens: int) -> str:
    import requests

    payload = {
        "model": model or "llama3.2",
        "prompt": prompt,
        "stream": False,
        "options": {"temperature": 0.0, "num_predict": max_tokens},
    }
    resp = requests.post(
        f"{base_url.rstrip('/') or 'http://localhost:11434'}/api/generate",
        json=payload,
        timeout=120,
    )
    resp.raise_for_status()
    return resp.json().get("response", "").strip().strip("\"'")


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


def translate_batch(prompt: str, expected_keys: List[str]) -> Optional[Dict[str, str]]:
    """Call LLM with a batch prompt; parse JSON map of key -> translation."""
    response = call_llm(prompt, max_tokens=max(500, 120 * len(expected_keys)))
    if response is None:
        return None

    parsed = _extract_json_object(response)
    if parsed is None:
        err("  Failed to parse batch JSON response; will retry keys individually")
        return None

    result: Dict[str, str] = {}
    for key in expected_keys:
        value = parsed.get(key)
        if isinstance(value, str) and value.strip():
            result[key] = value.strip().strip("\"'")
    return result
