"""Tests for guard_release_actions.py. Run: python3 -B -m unittest discover -s .factory/hooks"""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

HOOK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "guard_release_actions.py")


def run_hook(command, cwd, tool_name="Execute"):
    event = {"hook_event_name": "PreToolUse", "tool_name": tool_name, "cwd": cwd,
             "tool_input": {"command": command}}
    return subprocess.run([sys.executable, "-B", HOOK], input=json.dumps(event), capture_output=True,
                          text=True, timeout=30, check=False)


def git_repository(branch):
    directory = tempfile.mkdtemp()
    subprocess.run(["git", "init", "-q", "-b", branch, directory], check=True)
    return directory


class GuardReleaseActionsTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.feature = git_repository("security/fix")
        cls.main = git_repository("main")

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.feature, ignore_errors=True)
        shutil.rmtree(cls.main, ignore_errors=True)

    def assertBlocked(self, command, cwd=None):
        result = run_hook(command, cwd or self.feature)
        self.assertEqual(result.returncode, 2, f"expected {command!r} to be blocked")
        self.assertIn("open a pull request", result.stderr)

    def assertAllowed(self, command, cwd=None):
        result = run_hook(command, cwd or self.feature)
        self.assertEqual(result.returncode, 0, f"expected {command!r} to be allowed: {result.stderr}")

    def test_blocks_merging_and_approving(self):
        for command in ("gh pr merge 12 --squash", "cd repo && gh pr merge --auto --merge",
                        "gh pr review 3 --approve", "gh pr review 3 -a",
                        "gh api -X PUT repos/o/r/pulls/3/merge", "gh api repos/o/r/merges -f base=main -f head=x",
                        "gh api --method DELETE repos/o/r/branches/main/protection"):
            with self.subTest(command=command):
                self.assertBlocked(command)

    def test_blocks_force_pushes_tags_and_protected_branches(self):
        for command in ("git push --force origin security/fix", "git push -f origin security/fix",
                        "git push -uf origin security/fix", "git push --force-with-lease",
                        "git push origin +security/fix", "git push origin main", "git push origin HEAD:main",
                        "git push origin security/fix:refs/heads/master", "git push origin :main",
                        "git push --tags", "git push origin refs/tags/v1.0.0", "git push --mirror backup",
                        "git -c http.extraHeader=x push origin main", 'bash -c "git push --force origin x"'):
            with self.subTest(command=command):
                self.assertBlocked(command)

    def test_blocks_a_plain_push_from_a_protected_branch(self):
        self.assertBlocked("git push", self.main)
        self.assertBlocked("git push origin HEAD", self.main)
        self.assertBlocked(f"git -C {self.main} push origin", self.feature)
        self.assertBlocked(f"cd {self.main} && git push", self.feature)

    def test_blocks_releases_and_deployments(self):
        for command in ("gh release create v1.0.0", "kubectl apply -f k8s/",
                        "kubectl --context kind-kind delete pod petclinic-1", "kubectl rollout restart deploy/petclinic",
                        "helm upgrade --install petclinic ./chart", "docker push example/petclinic:1.0",
                        "docker buildx build --push -t example/petclinic .", "./mvnw deploy",
                        "./mvnw -B spring-boot:build-image -Dspring-boot.build-image.publish=true", "./gradlew publish"):
            with self.subTest(command=command):
                self.assertBlocked(command)

    def test_allows_everyday_commands(self):
        for command in ("git push -u origin security/fix", "git push origin HEAD", "git push",
                        "git push origin --delete old-branch", "gh pr create --draft --title Fix --body x",
                        "gh pr view 3", "gh pr review 3 --comment -b 'looks good'", "gh api repos/o/r/branches/main/protection",
                        "kubectl get pods", "kubectl rollout status deploy/petclinic", "kubectl get configmap apply-settings",
                        "helm list", "docker pull postgres:18.4", "./mvnw verify", "./gradlew build publishToMavenLocal",
                        "git commit -s -m 'Prepare the release notes'", "git log --oneline -5"):
            with self.subTest(command=command):
                self.assertAllowed(command)

    def test_ignores_other_tools_and_unreadable_input(self):
        self.assertEqual(run_hook("gh pr merge 1", self.feature, tool_name="Read").returncode, 0)
        result = subprocess.run([sys.executable, "-B", HOOK], input="not json", capture_output=True, text=True,
                                timeout=30, check=False)
        self.assertEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
