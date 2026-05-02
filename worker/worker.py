#!/usr/bin/env python3
"""
Workflow step worker: runs STEP_COMMAND, then reports result to the Java orchestrator callback API.
"""
from __future__ import annotations

import os
import sys
import time
import subprocess
from typing import Any

import requests


def _require_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        print(f"error: missing required environment variable: {name}", file=sys.stderr)
        sys.exit(1)
    return value


def _int_env(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or raw == "":
        return default
    try:
        return int(raw)
    except ValueError:
        print(f"error: {name} must be an integer, got {raw!r}", file=sys.stderr)
        sys.exit(1)


def _print_streams(result: subprocess.CompletedProcess[str] | None, timeout_exc: subprocess.TimeoutExpired | None) -> None:
    if result is not None:
        if result.stdout:
            print(result.stdout, end="")
        if result.stderr:
            print(result.stderr, end="", file=sys.stderr)
    if timeout_exc is not None:
        out = getattr(timeout_exc, "stdout", None)
        err = getattr(timeout_exc, "stderr", None)
        if out:
            text = out.decode(errors="replace") if isinstance(out, bytes) else str(out)
            print(text, end="")
        if err:
            text = err.decode(errors="replace") if isinstance(err, bytes) else str(err)
            print(text, end="", file=sys.stderr)


def _build_message(
    returncode: int | None,
    result: subprocess.CompletedProcess[str] | None,
    timeout_exc: subprocess.TimeoutExpired | None,
) -> str:
    if timeout_exc is not None:
        return f"Command timed out after {timeout_exc.timeout} seconds"
    if result is None:
        return "No process result"
    parts = [f"exitCode={returncode}"]
    if result.stdout:
        out = result.stdout.strip()
        if out:
            parts.append(f"stdout={out[:4000]}")
    if result.stderr:
        err = result.stderr.strip()
        if err:
            parts.append(f"stderr={err[:4000]}")
    return "; ".join(parts)


def _post_callback(url: str, token: str, payload: dict[str, Any]) -> bool:
    headers = {
        "Content-Type": "application/json",
        "X-Callback-Token": token,
    }
    resp = requests.post(url, headers=headers, json=payload, timeout=60)
    if not (200 <= resp.status_code < 300):
        print(f"callback: HTTP {resp.status_code}: {resp.text[:500]}", file=sys.stderr)
        return False
    return True


def _callback_with_retries(url: str, token: str, payload: dict[str, Any]) -> bool:
    for attempt in range(3):
        try:
            if _post_callback(url, token, payload):
                print(f"callback: succeeded on attempt {attempt + 1}", file=sys.stderr)
                return True
        except requests.RequestException as e:
            print(f"callback: attempt {attempt + 1} failed: {e}", file=sys.stderr)
        if attempt < 2:
            time.sleep(0.5 * (2**attempt))
    return False


def main() -> None:
    execution_id = _require_env("EXECUTION_ID")
    step_execution_id = _require_env("STEP_EXECUTION_ID")
    callback_url = _require_env("CALLBACK_URL")
    callback_token = _require_env("CALLBACK_TOKEN")
    step_command = _require_env("STEP_COMMAND")
    timeout_sec = _int_env("STEP_TIMEOUT_SECONDS", 300)

    result: subprocess.CompletedProcess[str] | None = None
    timeout_exc: subprocess.TimeoutExpired | None = None
    returncode: int | None = None

    try:
        result = subprocess.run(
            step_command,
            shell=True,
            capture_output=True,
            text=True,
            timeout=timeout_sec,
        )
        returncode = result.returncode
    except subprocess.TimeoutExpired as e:
        timeout_exc = e
        returncode = None

    _print_streams(result, timeout_exc)

    if timeout_exc is not None:
        status = "FAILED"
        msg = _build_message(None, None, timeout_exc)
        exit_after_callback = 1
    elif result is not None:
        if result.returncode == 0:
            status = "SUCCESS"
            msg = _build_message(result.returncode, result, None)
            exit_after_callback = result.returncode
        else:
            status = "FAILED"
            msg = _build_message(result.returncode, result, None)
            exit_after_callback = result.returncode
    else:
        status = "FAILED"
        msg = "Internal worker error: no subprocess result"
        exit_after_callback = 1

    payload = {
        "executionId": int(execution_id),
        "stepExecutionId": int(step_execution_id),
        "status": status,
        "message": msg,
        "logsRef": None,
    }

    print(f"callback: posting status={status} to {callback_url}", file=sys.stderr)
    if not _callback_with_retries(callback_url, callback_token, payload):
        print("callback: failed after retries; cannot deliver result", file=sys.stderr)
        sys.exit(1)

    sys.exit(exit_after_callback)


if __name__ == "__main__":
    main()
