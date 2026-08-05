#!/usr/bin/env python3
"""Unit tests for llm_client provider wiring (no live API calls)."""

from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

import llm_client  # noqa: E402


class LlmClientGoogleTest(unittest.TestCase):
    def test_provider_config_google_defaults(self) -> None:
        with patch.dict(
            os.environ,
            {
                "LLM_PROVIDER": "google",
                "GEMINI_API_KEY": "test-key",
            },
            clear=False,
        ):
            provider, api_key, model, base_url = llm_client._provider_config()
        self.assertEqual(provider, "google")
        self.assertEqual(api_key, "test-key")
        self.assertEqual(model, "gemini-3.6-flash")
        self.assertEqual(base_url, "https://generativelanguage.googleapis.com/v1beta")

    def test_provider_config_gemini_alias(self) -> None:
        with patch.dict(os.environ, {"LLM_PROVIDER": "gemini", "GOOGLE_API_KEY": "gk"}, clear=False):
            provider, api_key, model, _base_url = llm_client._provider_config()
        self.assertEqual(provider, "google")
        self.assertEqual(api_key, "gk")
        self.assertEqual(model, "gemini-3.6-flash")

    @patch("requests.post")
    def test_call_google_parses_response(self, mock_post: MagicMock) -> None:
        mock_post.return_value.json.return_value = {
            "candidates": [{"content": {"parts": [{"text": '{"save": "Speichern"}'}]}}]
        }
        mock_post.return_value.raise_for_status = MagicMock()

        result = llm_client._call_google(
            "translate save",
            "gemini-3.6-flash",
            "test-key",
            "https://generativelanguage.googleapis.com/v1beta",
            500,
        )
        self.assertEqual(result, '{"save": "Speichern"}')

        args, kwargs = mock_post.call_args
        self.assertIn("/models/gemini-3.6-flash:generateContent", args[0])
        self.assertEqual(kwargs["params"]["key"], "test-key")
        self.assertEqual(kwargs["json"]["generationConfig"]["temperature"], 0.0)
        self.assertEqual(kwargs["json"]["generationConfig"]["thinkingConfig"]["thinkingBudget"], 0)

    @patch("requests.post")
    def test_call_google_omits_thinking_when_default_requested(self, mock_post: MagicMock) -> None:
        mock_post.return_value.json.return_value = {
            "candidates": [{"content": {"parts": [{"text": "ok"}]}}]
        }
        mock_post.return_value.raise_for_status = MagicMock()

        with patch.dict(os.environ, {"LLM_GEMINI_THINKING_BUDGET": "default"}, clear=False):
            llm_client._call_google("prompt", "gemini-3.1-pro-preview", "k", "http://example", 100)

        generation_config = mock_post.call_args.kwargs["json"]["generationConfig"]
        self.assertNotIn("thinkingConfig", generation_config)


if __name__ == "__main__":
    unittest.main()
