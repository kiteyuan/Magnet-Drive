#!/usr/bin/env python3
"""Patch a generated Tauri Android project so HTML5 video can go fullscreen.

Wry's RustWebChromeClient immediately rejects onShowCustomView. This script:
  1. Copies WebViewFullscreen.kt next to MainActivity
  2. Makes back / destroy exit fullscreen
  3. Hooks Gradle so the stub is re-patched after wry regenerates Kotlin
"""

from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path

DESKTOP = Path(__file__).resolve().parent.parent
ASSETS = Path(__file__).resolve().parent / "android-fullscreen"
GEN_APP = DESKTOP / "src-tauri" / "gen" / "android" / "app"

SHOW_RE = re.compile(
    r"override fun onShowCustomView\(\s*view:\s*View,\s*callback:\s*CustomViewCallback\s*\)\s*\{[^}]*}",
    re.DOTALL,
)
HIDE_RE = re.compile(
    r"override fun onHideCustomView\(\)\s*\{[^}]*}",
    re.DOTALL,
)

SHOW_REPL = """override fun onShowCustomView(view: View, callback: CustomViewCallback) {
    WebViewFullscreen.show(activity, view, callback)
  }"""
HIDE_REPL = """override fun onHideCustomView() {
    WebViewFullscreen.hide()
  }"""


def die(msg: str) -> None:
    print(f"error: {msg}", file=sys.stderr)
    raise SystemExit(1)


def find_main_activity() -> Path:
    matches = list(GEN_APP.rglob("MainActivity.kt"))
    if not matches:
        die(f"MainActivity.kt not found under {GEN_APP} (run tauri android init first)")
    return matches[0]


def package_of(kt: Path) -> str:
    for line in kt.read_text(encoding="utf-8").splitlines():
        if line.startswith("package "):
            return line[len("package ") :].strip()
    die(f"no package declaration in {kt}")
    raise AssertionError


def write_helper(dest_dir: Path, pkg: str) -> Path:
    src = ASSETS / "WebViewFullscreen.kt"
    dest = dest_dir / "WebViewFullscreen.kt"
    dest.write_text(src.read_text(encoding="utf-8").replace("{{package}}", pkg), encoding="utf-8")
    print(f"wrote {dest.relative_to(DESKTOP)}")
    return dest


def patch_main_activity(main: Path, pkg: str) -> None:
    original = main.read_text(encoding="utf-8")
    use_edge = "enableEdgeToEdge" in original
    edge_import = "import androidx.activity.enableEdgeToEdge\n" if use_edge else ""
    edge_call = "    enableEdgeToEdge()\n" if use_edge else ""
    main.write_text(
        f"""package {pkg}

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
{edge_import}
class MainActivity : TauriActivity() {{
  override fun onCreate(savedInstanceState: Bundle?) {{
{edge_call}    super.onCreate(savedInstanceState)
  }}

  override fun onWebViewCreate(webView: WebView) {{
    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {{
        override fun handleOnBackPressed() {{
          if (WebViewFullscreen.isShowing) {{
            WebViewFullscreen.hide()
          }} else {{
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
          }}
        }}
      }},
    )
  }}

  override fun onDestroy() {{
    if (WebViewFullscreen.isShowing) {{
      WebViewFullscreen.hide()
    }}
    super.onDestroy()
  }}
}}
""",
        encoding="utf-8",
    )
    print(f"patched {main.relative_to(DESKTOP)}")


def insert_import(text: str, import_line: str) -> str:
    if import_line in text:
        return text
    lines = text.splitlines(keepends=True)
    last_import = max((i for i, line in enumerate(lines) if line.startswith("import ")), default=None)
    insert_at = last_import + 1 if last_import is not None else 1
    nl = "\n" if not lines or lines[insert_at - 1].endswith("\n") else "\n"
    lines.insert(insert_at, import_line + nl)
    return "".join(lines)


def patch_chrome_client(pkg: str) -> int:
    count = 0
    helper_plain = pkg.replace("`", "")
    import_line = f"import {helper_plain}.WebViewFullscreen"
    for path in GEN_APP.rglob("RustWebChromeClient.kt"):
        text = path.read_text(encoding="utf-8")
        if "WebViewFullscreen.show" in text:
            print(f"already patched {path.relative_to(DESKTOP)}")
            continue
        if not SHOW_RE.search(text) or not HIDE_RE.search(text):
            print(f"warning: fullscreen stubs not found in {path}")
            continue
        text = SHOW_RE.sub(SHOW_REPL, text, count=1)
        text = HIDE_RE.sub(HIDE_REPL, text, count=1)
        chrome_pkg = package_of(path).replace("`", "")
        if chrome_pkg != helper_plain:
            text = insert_import(text, import_line)
        path.write_text(text, encoding="utf-8")
        print(f"patched {path.relative_to(DESKTOP)}")
        count += 1
    return count


def inject_gradle() -> None:
    gradle = GEN_APP / "build.gradle.kts"
    if not gradle.is_file():
        die(f"missing {gradle}")
    dest = GEN_APP / "webview-fullscreen.gradle.kts"
    shutil.copyfile(ASSETS / "webview-fullscreen.gradle.kts", dest)
    print(f"wrote {dest.relative_to(DESKTOP)}")

    apply_line = 'apply(from = "webview-fullscreen.gradle.kts")'
    text = gradle.read_text(encoding="utf-8")
    if apply_line in text:
        print(f"gradle already applies {dest.name}")
        return
    gradle.write_text(text.rstrip() + "\n\n" + apply_line + "\n", encoding="utf-8")
    print(f"updated {gradle.relative_to(DESKTOP)}")


def main() -> None:
    if not GEN_APP.is_dir():
        die(f"{GEN_APP} does not exist (run tauri android init first)")
    main_activity = find_main_activity()
    pkg = package_of(main_activity)
    write_helper(main_activity.parent, pkg)
    patch_main_activity(main_activity, pkg)
    inject_gradle()
    patch_chrome_client(pkg)
    print("Android WebView fullscreen patch applied.")


if __name__ == "__main__":
    main()
