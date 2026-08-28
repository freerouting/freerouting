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
        self.assertEqual(model, "gemini-3.7-flash")
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
        self.assertNotIn("temperature", generation_config)
        self.assertEqual(generation_config["thinkingConfig"]["thinkingLevel"], "minimal")

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


if __name__ == "__main__":
    unittest.main()
