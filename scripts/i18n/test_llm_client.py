#!/usr/bin/env python3
"""Unit tests for Gemini llm_client wiring (no live API calls)."""

from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

import llm_client  # noqa: E402


class LlmClientGeminiTest(unittest.TestCase):
    def test_gemini_config_defaults(self) -> None:
        with patch.dict(os.environ, {"GEMINI_API_KEY": "test-key"}, clear=True):
            api_key, model, base_url = llm_client.gemini_config()
        self.assertEqual(api_key, "test-key")
        self.assertEqual(model, "gemini-3.6-flash")
        self.assertEqual(base_url, "https://generativelanguage.googleapis.com/v1beta")

    def test_gemini_api_key_accepts_google_api_key_alias(self) -> None:
        with patch.dict(os.environ, {"GOOGLE_API_KEY": "gk"}, clear=True):
            self.assertEqual(llm_client.gemini_api_key(), "gk")

    @patch("requests.post")
    def test_call_gemini_parses_response(self, mock_post: MagicMock) -> None:
        mock_post.return_value.json.return_value = {
            "candidates": [{"content": {"parts": [{"text": '{"save": "Speichern"}'}]}}]
        }
        mock_post.return_value.ok = True

        result = llm_client._call_gemini(
            "translate save",
            "gemini-3.7-flash",
            "test-key",
            "https://generativelanguage.googleapis.com/v1beta",
            500,
        )
        self.assertEqual(result, '{"save": "Speichern"}')

        args, kwargs = mock_post.call_args
        self.assertIn("/models/gemini-3.7-flash:generateContent", args[0])  # codespell:ignore
        self.assertEqual(kwargs["headers"]["x-goog-api-key"], "test-key")
        generation_config = kwargs["json"]["generationConfig"]
        self.assertEqual(generation_config["thinkingConfig"]["thinkingLevel"], "low")
        self.assertNotIn("thinkingBudget", generation_config.get("thinkingConfig", {}))

    @patch("requests.post")
    def test_call_gemini_preserves_surrounding_quotes(self, mock_post: MagicMock) -> None:
        mock_post.return_value.json.return_value = {
            "candidates": [{"content": {"parts": [{"text": '"en" for English, "de" for German'}]}}]
        }
        mock_post.return_value.ok = True

        result = llm_client._call_gemini(
            "translate language help",
            "gemini-3.7-flash",
            "test-key",
            "https://generativelanguage.googleapis.com/v1beta",
            500,
        )
        self.assertEqual(result, '"en" for English, "de" for German')

    def test_call_gemini_requires_api_key(self) -> None:
        with self.assertRaisesRegex(ValueError, "GEMINI_API_KEY is not set"):
            llm_client._call_gemini("prompt", "gemini-3.7-flash", "", "http://example", 100)

    @patch("requests.post")
    def test_call_gemini_gemini_25_uses_thinking_budget(self, mock_post: MagicMock) -> None:
        mock_post.return_value.json.return_value = {
            "candidates": [{"content": {"parts": [{"text": "ok"}]}}]
        }
        mock_post.return_value.ok = True

        llm_client._call_gemini("prompt", "gemini-2.5-flash", "AIza-test", "http://example", 100)

        generation_config = mock_post.call_args.kwargs["json"]["generationConfig"]
        self.assertEqual(generation_config["thinkingConfig"]["thinkingBudget"], 0)
        self.assertNotIn("thinkingLevel", generation_config["thinkingConfig"])

    @patch("requests.post")
    def test_call_gemini_gemini_3_respects_thinking_level_env(self, mock_post: MagicMock) -> None:
        mock_post.return_value.json.return_value = {
            "candidates": [{"content": {"parts": [{"text": "ok"}]}}]
        }
        mock_post.return_value.ok = True

        with patch.dict(os.environ, {"LLM_GEMINI_THINKING_LEVEL": "low"}, clear=False):
            llm_client._call_gemini("prompt", "gemini-3.1-pro-preview", "AIza-test", "http://example", 100)

        generation_config = mock_post.call_args.kwargs["json"]["generationConfig"]
        self.assertEqual(generation_config["thinkingConfig"]["thinkingLevel"], "low")
        self.assertNotIn("thinkingBudget", generation_config.get("thinkingConfig", {}))

    def test_is_server_capacity_error(self) -> None:
        self.assertTrue(llm_client._is_server_capacity_error(ValueError("503 Service Unavailable")))
        self.assertTrue(llm_client._is_server_capacity_error(ValueError("This model is currently experiencing high demand")))
        self.assertFalse(llm_client._is_server_capacity_error(ValueError("429 Resource Exhausted")))

    def test_is_quota_or_balance_error(self) -> None:
        self.assertTrue(llm_client._is_quota_or_balance_error(ValueError("429 RESOURCE_EXHAUSTED")))
        self.assertTrue(llm_client._is_quota_or_balance_error(ValueError("Quota exceeded for quota metric")))
        self.assertFalse(llm_client._is_quota_or_balance_error(ValueError("503 Service Unavailable")))



class ExtractJsonObjectTests(unittest.TestCase):
    """Responses that used to fail to parse and were then written to locale files verbatim."""

    def test_properties_escapes_inside_json_string(self) -> None:
        # \# and \: are .properties escapes, not JSON escapes; json.loads rejects them.
        response = '```json\n{ "36": " \\#_global_optimal_passes\\:\\#_prioritized_passes." }\n```'
        self.assertEqual(
            llm_client._extract_json_object(response),
            {"36": " \\#_global_optimal_passes\\:\\#_prioritized_passes."},
        )

    def test_bare_integer_key(self) -> None:
        response = '```json { 0: "The autorouter is about to start." } ```'
        self.assertEqual(llm_client._extract_json_object(response), {"0": "The autorouter is about to start."})

    def test_valid_json_escapes_are_left_alone(self) -> None:
        self.assertEqual(llm_client._extract_json_object('{"1": "a\\tb \\"q\\""}'), {"1": 'a\tb "q"'})

    def test_translate_batch_keeps_leading_quote(self) -> None:
        response = '{"0": "\\"en\\" für Englisch, \\"de\\" für Deutsch."}'
        with patch.object(llm_client, "call_llm", return_value=response):
            result, ok = llm_client.translate_batch(
                "prompt", ["0"], english_values=['"en" for English, "de" for German.']
            )
        self.assertTrue(ok)
        self.assertEqual(result, {"0": '"en" für Englisch, "de" für Deutsch.'})


if __name__ == "__main__":
    unittest.main()
