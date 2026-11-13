#!/usr/bin/env python3
"""PreToolUse hook that keeps Droid from merging, releasing or deploying.

Droid passes the pending tool call as JSON on stdin. For Execute calls, this script
exits with status 2, which blocks the call, when the shell command would merge or
approve a pull request, push to a protected branch, rewrite remote history, publish a
release or deploy. Any other command, and any input it cannot read, exits 0.

This is a guardrail for agents, not a security boundary. It matches command text, so
it can be bypassed on purpose, and it also blocks commands that merely mention a
blocked action, such as an echo. Branch protection on the main branch is still needed.
"""

import json
import os
import re
import shlex
import subprocess
import sys

PROTECTED_BRANCHES = {"main", "master"}

# Stops a match at the next shell separator, so one command cannot borrow
# arguments from the next.
ARGS = r"[^\n;&|]*"

RULES = [
    (re.compile(r"\bgh\s+pr\s+merge\b"), "merging a pull request"),
    (re.compile(r"\bgh\s+pr\s+review\b" + ARGS + r"\s(--approve|-a)(?=\s|$)"), "approving a pull request"),
    (re.compile(r"\bgh\s+api\b" + ARGS + r"pulls/\d+/merge\b"), "merging a pull request through the GitHub API"),
    (re.compile(r"\bgh\s+api\b" + ARGS + r"/merges\b"), "merging branches through the GitHub API"),
    (re.compile(r"\bgh\s+api\b(?=" + ARGS + r"branches/[^/\s]+/protection\b)"
                r"(?=" + ARGS + r"(?:-X|--method)[\s=]*(?:PUT|POST|PATCH|DELETE)\b)", re.IGNORECASE),
     "changing branch protection"),
    (re.compile(r"\bgh\s+release\s+(create|delete|edit|upload)\b"), "publishing a release"),
    (re.compile(r"\bkubectl\b" + ARGS
                + r"\s(apply|create|replace|patch|delete|edit|scale|set|annotate|label|expose|run|drain|cordon|taint"
                + r"|rollout\s+(restart|undo|pause|resume))(?=\s|$)"), "changing a Kubernetes cluster"),
    (re.compile(r"\bhelm\b" + ARGS + r"\s(install|upgrade|uninstall|delete|rollback)(?=\s|$)"), "changing a Helm release"),
    (re.compile(r"\bdocker\s+(image\s+)?push(?=\s|$)"), "pushing a container image"),
    (re.compile(r"\bdocker\s+buildx\s+build\b" + ARGS + r"\s--push\b"), "pushing a container image"),
    (re.compile(r"\bmvn(w|w\.cmd)?\b" + ARGS + r"\s(deploy|deploy:deploy)(?=\s|$)"), "deploying Maven artifacts"),
    (re.compile(r"\bmvn(w|w\.cmd)?\b" + ARGS + r"spring-boot\.build-image\.publish=true"), "publishing a container image"),
    (re.compile(r"\bgradlew?\b" + ARGS + r"\spublish(?!ToMavenLocal\b)\w*(?=\s|$)"), "publishing Gradle artifacts"),
]

GIT_PUSH = re.compile(r"\bgit((?:\s+-[Cc]\s+\S+|\s+--?[\w-]+(?:=\S+)?)*)\s+push\b(" + ARGS + ")")
CD = re.compile(r"(?:^|[\s;&|(])cd\s+(\"[^\"]*\"|'[^']*'|[^\s;&|)]+)")
PUSH_OPTIONS_WITH_VALUE = {"-o", "--push-option", "--repo", "--receive-pack", "--exec"}


def split_args(text):
    try:
        return shlex.split(text)
    except ValueError:
        return [part.strip("'\"") for part in text.split()]


def current_branch(directory):
    try:
        # symbolic-ref also works on a branch with no commits yet; rev-parse does not.
        result = subprocess.run(["git", "-C", directory, "symbolic-ref", "--short", "-q", "HEAD"],
                                capture_output=True, text=True, timeout=5, check=False)
    except (OSError, subprocess.SubprocessError):
        return None
    return result.stdout.strip() if result.returncode == 0 else None


def push_directory(command, match, cwd):
    """Directory the push runs in: git -C wins, then the last preceding cd."""
    options = split_args(match.group(1))
    directory = cwd
    for found in CD.finditer(command, 0, match.start()):
        directory = os.path.join(directory, os.path.expanduser(found.group(1).strip("'\"")))
    for index, option in enumerate(options[:-1]):
        if option == "-C":
            directory = os.path.join(directory, os.path.expanduser(options[index + 1]))
    return directory


def branch_name(ref):
    ref = ref.lstrip("+")
    return ref[len("refs/heads/"):] if ref.startswith("refs/heads/") else ref


def git_push_reason(args, directory):
    positional = []
    skip_next = False
    for arg in args:
        arg = arg.strip("'\"")
        if skip_next:
            skip_next = False
            continue
        if arg in PUSH_OPTIONS_WITH_VALUE:
            skip_next = True
        elif arg.startswith("--force") or (re.fullmatch(r"-[46dnquv]*f[46dnfquv]*", arg) is not None):
            return "force-pushing"
        elif arg in ("--mirror", "--all", "--branches"):
            return "pushing every branch"
        elif arg in ("--tags", "--follow-tags"):
            return "pushing tags"
        elif not arg.startswith("-"):
            positional.append(arg)
    refspecs = positional[1:]
    for refspec in refspecs:
        if refspec.startswith("+"):
            return "force-pushing"
        if "refs/tags/" in refspec:
            return "pushing tags"
        destination = branch_name(refspec.split(":", 1)[1] if ":" in refspec else refspec)
        if destination in ("HEAD", "@"):
            destination = current_branch(directory)
        if destination in PROTECTED_BRANCHES:
            return f"pushing to the protected branch '{destination}'"
    if not refspecs and current_branch(directory) in PROTECTED_BRANCHES:
        return f"pushing to the protected branch '{current_branch(directory)}'"
    return None


def blocked_action(command, cwd):
    for pattern, reason in RULES:
        if pattern.search(command):
            return reason
    for match in GIT_PUSH.finditer(command):
        reason = git_push_reason(split_args(match.group(2)), push_directory(command, match, cwd))
        if reason:
            return reason
    return None


def main():
    try:
        event = json.load(sys.stdin)
    except ValueError:
        return 0
    if not isinstance(event, dict) or event.get("tool_name") != "Execute":
        return 0
    command = (event.get("tool_input") or {}).get("command") or ""
    cwd = event.get("cwd") or os.environ.get("FACTORY_PROJECT_DIR") or os.getcwd()
    reason = blocked_action(command, cwd)
    if reason is None:
        return 0
    print(f"Blocked by .factory/hooks/guard_release_actions.py: {reason}. In this repository, "
          "people merge, release and deploy. Push a feature branch and open a pull request "
          "for review instead.", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
