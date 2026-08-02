"""Windows-safe console output for i18n scripts."""

from __future__ import annotations

import sys


def _supports_unicode() -> bool:
    encoding = getattr(sys.stdout, "encoding", None) or "utf-8"
    try:
        "✅".encode(encoding)
        return True
    except (LookupError, UnicodeEncodeError):
        return False


_USE_UNICODE = _supports_unicode()

_SYMBOLS = {
    "ok": ("✅", "[OK]"),
    "fail": ("❌", "[FAIL]"),
    "warn": ("⚠️", "[WARN]"),
    "info": ("📖", "[INFO]"),
    "stats": ("📊", "[STATS]"),
    "world": ("🌍", "[LOCALE]"),
    "bundle": ("📦", "[BUNDLE]"),
    "search": ("🔍", "[CHECK]"),
    "sync": ("🔄", "[TRANSLATE]"),
}


def symbol(name: str) -> str:
    pair = _SYMBOLS.get(name, ("", ""))
    return pair[0] if _USE_UNICODE else pair[1]


def out(message: str = "", *, file=None, end: str = "\n", flush: bool = False) -> None:
    target = file or sys.stdout
    print(message, file=target, end=end, flush=flush)


def err(message: str) -> None:
    print(message, file=sys.stderr)
