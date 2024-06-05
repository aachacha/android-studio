"""A module providing a BazelCmd object."""
from typing import List
import subprocess
import os


class BuildEnv:
  """Represents the build environment."""
  build_number: str
  build_target_name: str
  dist_dir: str
  tmp_dir: str
  bazel_path: str

  def __init__(self, bazel_path: str):
    self.build_number = os.environ.get("BUILD_NUMBER", "SNAPSHOT")
    self.build_target_name = os.environ.get("BUILD_TARGET_NAME", "")
    self.dist_dir = os.environ.get("DIST_DIR", "")
    self.tmp_dir = os.environ.get("TMPDIR", "")
    self.bazel_path = bazel_path

  def is_ci(self) -> bool:
    """Returns true if in a continuous integration environment."""
    return self.build_target_name != ""


class BazelCmd:
  """Represents a Bazel command and arguments."""

  build_env: BuildEnv
  startup_options: List[str] = []

  def __init__(self, build_env: BuildEnv):
    self.build_env = build_env

  def build(self, *build_args) -> subprocess.CompletedProcess:
    """Runs a 'bazel build' command."""
    return self._run(True, False, "build", *build_args)

  def test(self, *test_args) -> subprocess.CompletedProcess:
    """Runs a 'bazel test' command."""
    return self._run(False, False, "test", *test_args)

  def run(self, *run_args) -> subprocess.CompletedProcess:
    """Runs a 'bazel run' command.

    Raises:
      CalledProcessError: If the command fails.
    """
    return self._run(False, True, "run", *run_args)

  def query(self, *query_args) -> subprocess.CompletedProcess:
    """Runs a 'bazel query' command.

    Raises:
      CalledProcessError: If the query fails.
    """
    return self._run(True, True, "query", *query_args)

  def info(self, *info_args) -> subprocess.CompletedProcess:
    """Runs a 'bazel info' command.

    Raises:
      CalledProcessError: If the command fails.
    """
    return self._run(True, True, "info", *info_args)

  def _run(self, capture_output: bool, check: bool, *args: List[str]) -> subprocess.CompletedProcess:
    """Runs a Bazel command with the given args."""
    cmd = [self.build_env.bazel_path]
    cmd.extend(self.startup_options)
    cmd.extend(args)
    return subprocess.run(
        cmd,
        capture_output=capture_output,
        check=check,
        cwd=os.environ.get('BUILD_WORKSPACE_DIRECTORY'),
    )
