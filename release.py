#!/usr/bin/env python3
"""Release Fim.

Runs the whole release from one place: set the version, tag it, build it, package what goes on the
GitHub release page, build the Docker image from the tag, render the documentation and prepare the
gh-pages commit that publishes it.

Nothing leaves this machine unless --push is given. Every step is reported as it runs, so a release
that stops half way tells you where it stopped.

Usage:
    python release.py 2.0.0
    python release.py 2.0.0 --next 2.1.0-SNAPSHOT
    python release.py 2.0.0 --push
    python release.py 2.0.0 --skip-docker --skip-docs
    python release.py --help

Requires Python 3.8 or later, a JDK for the Gradle wrapper, and Docker unless --skip-docker.
"""

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parent
BUILD_FILE = REPOSITORY_ROOT / "build.gradle.kts"
VERSION_PATTERN = re.compile(r'^version\s*=\s*"([^"]+)"\s*$', re.MULTILINE)

GRADLE = "gradlew.bat" if os.name == "nt" else "./gradlew"

DOCKER_IMAGE = "evrignaud/fim"
GH_PAGES_BRANCH = "gh-pages"

# The rendered site, and the part of it that maps onto the root of the gh-pages branch.
GENERATED_SITE = REPOSITORY_ROOT / "build" / "docs" / "asciidoc"
GENERATED_DOCS = GENERATED_SITE / "docs"

RELEASE_VERSION_PATTERN = re.compile(r"^\d+\.\d+\.\d+$")


class ReleaseError(Exception):
    """Something that means the release must not continue."""


# ---------------------------------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------------------------------

_step_number = 0


def step(message):
    global _step_number
    _step_number += 1
    print(f"\n=== [{_step_number}] {message} " + "=" * max(0, 68 - len(message)), flush=True)


def info(message):
    print(f"    {message}", flush=True)


def warn(message):
    print(f"    WARNING: {message}", flush=True)


# ---------------------------------------------------------------------------------------------------
# Running commands
# ---------------------------------------------------------------------------------------------------

def run(command, cwd=None, capture=False, stdin=None):
    """Run a command, failing the release if it fails."""
    where = cwd or REPOSITORY_ROOT
    info("$ " + " ".join(str(part) for part in command))
    result = subprocess.run(
        command,
        cwd=str(where),
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
        stdin=stdin,
        text=capture,
    )
    if result.returncode != 0:
        if capture and result.stdout:
            print(result.stdout)
        raise ReleaseError(f"command failed with status {result.returncode}: {' '.join(map(str, command))}")
    return result.stdout.strip() if capture else ""


def git(*arguments, capture=True, cwd=None):
    return run(["git", *arguments], cwd=cwd, capture=capture)


def git_quiet(*arguments):
    """Run git without echoing it, for the many small queries."""
    result = subprocess.run(
        ["git", *arguments],
        cwd=str(REPOSITORY_ROOT),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.returncode, result.stdout.strip()


def tool_available(name):
    return shutil.which(name) is not None


# ---------------------------------------------------------------------------------------------------
# The version in build.gradle.kts
# ---------------------------------------------------------------------------------------------------

def read_version():
    match = VERSION_PATTERN.search(BUILD_FILE.read_text(encoding="utf-8"))
    if not match:
        raise ReleaseError(f"no `version = \"...\"` line found in {BUILD_FILE.name}")
    return match.group(1)


def write_version(version):
    content = BUILD_FILE.read_text(encoding="utf-8")
    updated, count = VERSION_PATTERN.subn(f'version = "{version}"', content, count=1)
    if count != 1:
        raise ReleaseError(f"not able to set the version in {BUILD_FILE.name}")
    BUILD_FILE.write_text(updated, encoding="utf-8", newline="")


def next_development_version(release_version):
    """2.0.0 -> 2.0.1-SNAPSHOT, following what the previous releases did."""
    major, minor, patch = (int(part) for part in release_version.split("."))
    return f"{major}.{minor}.{patch + 1}-SNAPSHOT"


# ---------------------------------------------------------------------------------------------------
# Steps
# ---------------------------------------------------------------------------------------------------

def check_preconditions(version, options):
    step("Checking the repository is ready to be released")

    if not (REPOSITORY_ROOT / ".git").exists() or not BUILD_FILE.exists():
        raise ReleaseError(f"{REPOSITORY_ROOT} does not look like the Fim repository")

    if not RELEASE_VERSION_PATTERN.match(version):
        raise ReleaseError(f"'{version}' is not a release version, expected something like 2.0.0")

    if not tool_available("git"):
        raise ReleaseError("git was not found on the PATH")

    if not options.skip_docker and not tool_available("docker"):
        raise ReleaseError("docker was not found on the PATH. Use --skip-docker to release without the image")

    _, status = git_quiet("status", "--porcelain")
    if status:
        raise ReleaseError(
            "the working tree has uncommitted changes. Commit or stash them first:\n"
            + "\n".join("      " + line for line in status.splitlines())
        )

    _, branch = git_quiet("rev-parse", "--abbrev-ref", "HEAD")
    if branch != options.branch:
        raise ReleaseError(
            f"on branch '{branch}' rather than '{options.branch}'."
            f" Use --branch {branch} if that is deliberate"
        )

    code, _ = git_quiet("rev-parse", "--verify", "--quiet", f"refs/tags/{version}")
    if code == 0:
        raise ReleaseError(f"tag {version} already exists")

    current = read_version()
    info(f"current version    : {current}")
    info(f"releasing          : {version}")
    info(f"next development   : {options.next_version}")
    if not current.endswith("-SNAPSHOT"):
        warn(f"the current version '{current}' is not a SNAPSHOT, which is unusual before a release")

    if not options.skip_docs:
        code, _ = git_quiet("rev-parse", "--verify", "--quiet", f"refs/remotes/origin/{GH_PAGES_BRANCH}")
        if code != 0:
            raise ReleaseError(
                f"origin/{GH_PAGES_BRANCH} was not found. Run `git fetch origin` first,"
                " or use --skip-docs to release without publishing the documentation"
            )
        code, local_gh_pages = git_quiet("rev-parse", "--verify", "--quiet", f"refs/heads/{GH_PAGES_BRANCH}")
        if code == 0:
            _, remote_gh_pages = git_quiet("rev-parse", f"refs/remotes/origin/{GH_PAGES_BRANCH}")
            if local_gh_pages != remote_gh_pages:
                raise ReleaseError(
                    f"the local {GH_PAGES_BRANCH} branch is not the same as origin/{GH_PAGES_BRANCH}."
                    " It would be reset, so deal with it first or use --skip-docs"
                )

    info("ready")


def create_release_commit_and_tag(version):
    step(f"Setting the version to {version} and tagging it")

    write_version(version)
    git("add", "--", BUILD_FILE.name, capture=False)
    git("commit", "-m", f":package: Release version {version}", capture=False)
    git("tag", "-a", version, "-m", f"Release version {version}", capture=False)

    _, tagged = git_quiet("rev-parse", "--short", version)
    info(f"tag {version} created at {tagged}")


def run_clean_build():
    step("Running a clean build")
    # jacocoTestReport is asked for on top of build, because build only enforces the coverage floor and
    # does not write the xml the badges are drawn from.
    run([GRADLE, "clean", "build", "slowTest", "jacocoTestReport", "--console=plain"])
    info("compiled, tested, style checked, analysed, both test tiers run")


def refresh_coverage_badges():
    """Redraw the badges the README points at, from the coverage this build measured.

    They are refreshed here, by the person doing the release, rather than by a workflow committing to the
    repository on its own. The figure cannot drift far in the meantime: the coverage floor in
    build.gradle.kts fails the build long before it would.
    """
    step("Refreshing the coverage badges")
    run([sys.executable, str(REPOSITORY_ROOT / "coverage_badge.py")])


def collect_release_artifacts(version):
    step("Collecting the artifacts for the GitHub release page")

    distributions = REPOSITORY_ROOT / "build" / "distributions"
    libraries = REPOSITORY_ROOT / "build" / "libs"

    wanted = [
        distributions / f"fim-shadow-{version}.zip",
        distributions / f"fim-shadow-{version}.tar",
        distributions / f"fim-{version}.zip",
        distributions / f"fim-{version}.tar",
        libraries / "fim-fat.jar",
    ]

    target = REPOSITORY_ROOT / "build" / "release" / version
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)

    checksums = []
    for artifact in wanted:
        if not artifact.exists():
            warn(f"missing, so not published: {artifact.name}")
            continue
        shutil.copy2(artifact, target / artifact.name)
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
        checksums.append(f"{digest}  {artifact.name}")
        info(f"{artifact.name}  ({artifact.stat().st_size / 1_048_576:.1f} MB)")

    if not checksums:
        raise ReleaseError(f"no artifact was produced in {distributions}")

    (target / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", encoding="utf-8")
    info(f"SHA256SUMS.txt written next to them")
    info(f"upload the contents of {target.relative_to(REPOSITORY_ROOT)} to the release page")
    return target


def build_docker_image(version):
    step(f"Building the Docker image from the {version} tag")

    # The build context is the tagged tree itself, streamed straight out of git. Building from the
    # working tree instead would let anything uncommitted, or left over from an earlier build, end up
    # in a released image. This way the image can only contain what the tag contains, and the working
    # tree does not have to be checked out at the tag to do it.
    archive = subprocess.Popen(
        ["git", "archive", "--format=tar", version],
        cwd=str(REPOSITORY_ROOT),
        stdout=subprocess.PIPE,
    )
    try:
        command = [
            "docker", "build",
            "-t", f"{DOCKER_IMAGE}:{version}",
            "-t", f"{DOCKER_IMAGE}:latest",
            "-",
        ]
        info("$ git archive --format=tar " + version + " | " + " ".join(command))
        docker = subprocess.run(command, cwd=str(REPOSITORY_ROOT), stdin=archive.stdout)
    finally:
        if archive.stdout:
            archive.stdout.close()
        archive.wait()

    if archive.returncode != 0:
        raise ReleaseError(f"git archive failed with status {archive.returncode}")
    if docker.returncode != 0:
        raise ReleaseError(f"docker build failed with status {docker.returncode}")

    info(f"tagged {DOCKER_IMAGE}:{version} and {DOCKER_IMAGE}:latest")
    info("push it with: docker push " + f"{DOCKER_IMAGE}:{version} && docker push {DOCKER_IMAGE}:latest")


def build_documentation():
    step("Rendering the documentation")
    run([GRADLE, "asciidoctor", "--console=plain"])

    if not (GENERATED_DOCS / "index.html").exists():
        raise ReleaseError(f"the documentation was not produced in {GENERATED_DOCS}")

    pages = len(list(GENERATED_DOCS.glob("*.html")))
    info(f"{pages} pages, with their css and images, in {GENERATED_DOCS.relative_to(REPOSITORY_ROOT)}")


def publish_documentation(version):
    step(f"Preparing the {GH_PAGES_BRANCH} commit that publishes the documentation")

    with tempfile.TemporaryDirectory(prefix="fim-gh-pages-") as temporary:
        worktree = Path(temporary) / GH_PAGES_BRANCH

        # A separate worktree keeps the main one exactly as it is: releasing must not check another
        # branch out from under the person running it.
        git("worktree", "add", "-B", GH_PAGES_BRANCH, str(worktree), f"origin/{GH_PAGES_BRANCH}",
            capture=False)
        try:
            copied = overlay_documentation(worktree)
            info(f"{copied} files written over the published site")

            _, pending = git_quiet_in(worktree, "status", "--porcelain")
            if not pending:
                info("the published documentation is already up to date, nothing to commit")
                return None

            git("add", "-A", capture=False, cwd=worktree)
            git("commit", "-m", f":green_book: Update documentation for version {version}",
                capture=False, cwd=worktree)
            commit = git("rev-parse", "--short", "HEAD", cwd=worktree)
            info(f"{GH_PAGES_BRANCH} now at {commit}")
            return commit
        finally:
            git("worktree", "remove", "--force", str(worktree), capture=False)


def git_quiet_in(cwd, *arguments):
    result = subprocess.run(
        ["git", *arguments],
        cwd=str(cwd),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.returncode, result.stdout.strip()


def overlay_documentation(worktree):
    """Write the rendered pages over the published site, leaving everything else in place.

    The site is overlaid rather than replaced so that whatever is published but not generated here
    survives, which today means the vendored reveal.js and the README on the branch.

    The slides are deliberately left alone. They are meant to be reveal.js decks and the published
    ones are, but this build renders them as plain HTML because the reveal.js backend is not
    configured. Publishing what is generated would replace working decks with flat pages, so until
    that backend is restored the slides are not touched from here.
    """
    copied = 0
    for source in sorted(GENERATED_DOCS.rglob("*")):
        if source.is_dir():
            continue
        relative = source.relative_to(GENERATED_DOCS)
        destination = worktree / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        copied += 1
    return copied


def prepare_next_development_version(next_version):
    step(f"Setting the version to {next_version} for further development")
    write_version(next_version)
    git("add", "--", BUILD_FILE.name, capture=False)
    # The redrawn badges travel with this commit rather than one of their own, so a release does not leave
    # a trail of commits that only change a percentage.
    git("add", "--", ".github/badges", capture=False)
    git("commit", "-m", f"Prepare dev for {next_version}", capture=False)
    info("done")


def push_everything(version, branch, published_documentation):
    step("Pushing to origin")
    git("push", "origin", branch, capture=False)
    git("push", "origin", f"refs/tags/{version}", capture=False)
    if published_documentation:
        git("push", "origin", GH_PAGES_BRANCH, capture=False)
    info("pushed")


def report_what_is_left(version, artifacts, options, published_documentation):
    step("Release prepared")
    print(f"""
    Version {version} is tagged and built.

    Done locally:
      - commit ':package: Release version {version}' and tag {version}
      - clean build, with tests, checkstyle and static analysis
      - artifacts in {artifacts.relative_to(REPOSITORY_ROOT)}
      - {'Docker image ' + DOCKER_IMAGE + ':' + version if not options.skip_docker else 'Docker image skipped'}
      - {'documentation commit on ' + GH_PAGES_BRANCH if published_documentation else 'documentation not committed'}
      - commit 'Prepare dev for {options.next_version}'
""")
    if options.push:
        print("    Pushed to origin.")
    else:
        print("    Nothing was pushed. To publish it:")
        print(f"      git push origin {options.branch}")
        print(f"      git push origin refs/tags/{version}")
        if published_documentation:
            print(f"      git push origin {GH_PAGES_BRANCH}")
    if not options.skip_docker:
        print(f"      docker push {DOCKER_IMAGE}:{version}")
        print(f"      docker push {DOCKER_IMAGE}:latest")
    print(f"\n    Then create the release at https://github.com/evrignaud/fim/releases/new?tag={version}")
    print(f"    and upload every file from {artifacts.relative_to(REPOSITORY_ROOT)}\n")


# ---------------------------------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------------------------------

def parse_arguments(argv):
    parser = argparse.ArgumentParser(
        description="Release Fim: tag, build, package, image, documentation.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Nothing is pushed unless --push is given.",
    )
    parser.add_argument("version", help="the version being released, for example 2.0.0")
    parser.add_argument("--next", dest="next_version", default=None,
                        help="the version to continue development on. Defaults to the next patch SNAPSHOT")
    parser.add_argument("--branch", default="master", help="the branch being released from. Defaults to master")
    parser.add_argument("--push", action="store_true",
                        help="push the branch, the tag and the documentation to origin")
    parser.add_argument("--skip-docker", action="store_true", help="do not build the Docker image")
    parser.add_argument("--skip-docs", action="store_true",
                        help="do not render or publish the documentation")
    options = parser.parse_args(argv)

    # Validated here rather than in the preconditions, because deriving the next version below
    # depends on the shape of this one and would otherwise fail with a stack trace.
    if not RELEASE_VERSION_PATTERN.match(options.version):
        parser.error(f"'{options.version}' is not a release version, expected something like 2.0.0")

    if options.next_version is None:
        options.next_version = next_development_version(options.version)
    return options


def main(argv):
    options = parse_arguments(argv)
    version = options.version

    try:
        check_preconditions(version, options)
        create_release_commit_and_tag(version)
        run_clean_build()
        refresh_coverage_badges()
        artifacts = collect_release_artifacts(version)

        if options.skip_docker:
            step("Docker image skipped")
        else:
            build_docker_image(version)

        published_documentation = None
        if options.skip_docs:
            step("Documentation skipped")
        else:
            build_documentation()
            published_documentation = publish_documentation(version)

        prepare_next_development_version(options.next_version)

        if options.push:
            push_everything(version, options.branch, published_documentation)

        report_what_is_left(version, artifacts, options, published_documentation)
        return 0
    except ReleaseError as failure:
        print(f"\nRelease stopped: {failure}\n", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\nRelease interrupted.\n", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
