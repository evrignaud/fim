#!/usr/bin/env python3
"""Turn the JaCoCo report into coverage badges.

Reads the xml JaCoCo writes, works out the coverage, and draws an svg badge for it. No account, no
token and no upload: the badges are files in the repository, served by GitHub like any other file,
which is what lets the coverage figure be published without depending on a hosted service.

Also writes the figures where a GitHub workflow can pick them up, when it is running inside one:
the job summary through GITHUB_STEP_SUMMARY, and the step outputs through GITHUB_OUTPUT.

Usage:
    python coverage_badge.py
    python coverage_badge.py --check          # report the figures, write nothing
    python coverage_badge.py --output-dir doc/badges
    python coverage_badge.py --help

Requires Python 3.8 or later, and a JaCoCo xml report:

    ./gradlew test jacocoTestReport
"""

import argparse
import os
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parent
DEFAULT_REPORT = REPOSITORY_ROOT / "build" / "reports" / "jacoco" / "test" / "jacocoTestReport.xml"
DEFAULT_OUTPUT_DIR = REPOSITORY_ROOT / ".github" / "badges"

# The counters worth a badge, and what to call them on it. JaCoCo produces several others, but
# instructions is the figure people mean by "coverage" and branches is the one that says whether the
# decisions in the code were actually exercised.
BADGES = {
    "INSTRUCTION": ("coverage", "coverage.svg"),
    "BRANCH": ("branches", "branches.svg"),
}

# Worst first. The first band the percentage reaches decides the colour, following the same steps
# shields.io uses so that these badges sit next to others without looking out of place.
COLOUR_BANDS = (
    (50, "#e05d44"),  # red
    (60, "#fe7d37"),  # orange
    (70, "#dfb317"),  # yellow
    (80, "#a4a61d"),  # yellowgreen
    (90, "#97ca00"),  # green
    (101, "#4c1"),    # brightgreen
)

# Verdana at 11px, near enough. The badge only has to look right, and measuring properly would mean
# carrying font metrics around for the sake of a pixel or two.
CHARACTER_WIDTH = 7.0
SIDE_PADDING = 10.0


class BadgeError(Exception):
    """Something that means no badge can be produced."""


# ---------------------------------------------------------------------------------------------------
# Reading the report
# ---------------------------------------------------------------------------------------------------

def read_coverage(report_file):
    """Return {counter type: percentage} from the totals at the end of a JaCoCo xml report."""
    if not report_file.is_file():
        raise BadgeError(
            f"No JaCoCo report at {report_file}.\n"
            f"Run './gradlew test jacocoTestReport' first."
        )

    try:
        # JaCoCo names a DTD that is not always reachable, and resolving external entities in a file
        # like this one is something to avoid regardless. ElementTree does not fetch it.
        root = ElementTree.parse(report_file).getroot()
    except ElementTree.ParseError as ex:
        raise BadgeError(f"{report_file} is not readable as xml: {ex}") from ex

    # Only the counters that are direct children of <report> are the totals. The ones nested inside a
    # package or a class are that package's or that class's, and adding them in would count twice.
    coverage = {}
    for counter in root.findall("counter"):
        covered = int(counter.get("covered", 0))
        missed = int(counter.get("missed", 0))
        total = covered + missed
        if total > 0:
            coverage[counter.get("type")] = 100.0 * covered / total

    if not coverage:
        raise BadgeError(f"{report_file} holds no coverage totals. Did any test run?")

    return coverage


# ---------------------------------------------------------------------------------------------------
# Drawing the badge
# ---------------------------------------------------------------------------------------------------

def colour_for(percentage):
    for upper_bound, colour in COLOUR_BANDS:
        if percentage < upper_bound:
            return colour
    return COLOUR_BANDS[-1][1]


def text_width(text):
    return len(text) * CHARACTER_WIDTH + SIDE_PADDING


def draw_badge(label, value, colour):
    """A flat svg badge, laid out like the ones shields.io serves."""
    label_width = text_width(label)
    value_width = text_width(value)
    total_width = label_width + value_width

    # Text sits in the middle of its half. The x10 scale is what lets the positions be whole numbers
    # while still landing on half pixels, which is how the reference badges avoid blurry text.
    label_middle = label_width / 2 * 10
    value_middle = (label_width + value_width / 2) * 10
    description = f"{label}: {value}"

    return f"""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" \
width="{total_width:.0f}" height="20" role="img" aria-label="{description}">
  <title>{description}</title>
  <linearGradient id="shine" x2="0" y2="100%">
    <stop offset="0" stop-color="#bbb" stop-opacity=".1"/>
    <stop offset="1" stop-opacity=".1"/>
  </linearGradient>
  <clipPath id="rounded">
    <rect width="{total_width:.0f}" height="20" rx="3" fill="#fff"/>
  </clipPath>
  <g clip-path="url(#rounded)">
    <rect width="{label_width:.0f}" height="20" fill="#555"/>
    <rect x="{label_width:.0f}" width="{value_width:.0f}" height="20" fill="{colour}"/>
    <rect width="{total_width:.0f}" height="20" fill="url(#shine)"/>
  </g>
  <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" \
font-size="110" text-rendering="geometricPrecision" transform="scale(.1)">
    <text x="{label_middle:.0f}" y="150" fill="#010101" fill-opacity=".3">{label}</text>
    <text x="{label_middle:.0f}" y="140">{label}</text>
    <text x="{value_middle:.0f}" y="150" fill="#010101" fill-opacity=".3">{value}</text>
    <text x="{value_middle:.0f}" y="140">{value}</text>
  </g>
</svg>
"""


# ---------------------------------------------------------------------------------------------------
# Reporting back to the workflow
# ---------------------------------------------------------------------------------------------------

def append_to_environment_file(variable, lines):
    """Add to one of the files a GitHub workflow reads afterwards, if we are running in one."""
    path = os.environ.get(variable)
    if not path:
        return

    try:
        with open(path, "a", encoding="utf-8") as handle:
            handle.write("\n".join(lines) + "\n")
    except OSError as ex:
        # Not being able to decorate the workflow is not a reason to fail it.
        print(f"WARNING: could not write to {variable}: {ex}", file=sys.stderr)


def report_to_workflow(coverage):
    summary = ["### Coverage", "", "| Measure | Covered |", "| --- | --- |"]
    for counter in sorted(coverage):
        summary.append(f"| {counter.capitalize()} | {coverage[counter]:.2f}% |")
    append_to_environment_file("GITHUB_STEP_SUMMARY", summary)

    outputs = [f"{counter.lower()}={coverage[counter]:.2f}" for counter in sorted(coverage)]
    append_to_environment_file("GITHUB_OUTPUT", outputs)


# ---------------------------------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Draw coverage badges from the JaCoCo report.")
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT,
                        help="the JaCoCo xml report to read")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR,
                        help="where to write the badges")
    parser.add_argument("--check", action="store_true",
                        help="report the figures without writing any badge")
    arguments = parser.parse_args()

    try:
        coverage = read_coverage(arguments.report)
    except BadgeError as ex:
        print(f"ERROR: {ex}", file=sys.stderr)
        return 1

    for counter in sorted(coverage):
        print(f"{counter.lower():<12} {coverage[counter]:6.2f}%")

    reported = {counter: coverage[counter] for counter in BADGES if counter in coverage}
    if not reported:
        print(f"ERROR: {arguments.report} has none of {', '.join(BADGES)}", file=sys.stderr)
        return 1

    report_to_workflow(reported)

    if arguments.check:
        return 0

    arguments.output_dir.mkdir(parents=True, exist_ok=True)
    for counter, (label, file_name) in BADGES.items():
        if counter not in reported:
            continue
        percentage = reported[counter]
        badge_file = arguments.output_dir / file_name
        # Truncated rather than rounded, so a badge never claims a percentage that was not reached.
        value = f"{int(percentage * 10) / 10:.1f}%"
        badge_file.write_text(draw_badge(label, value, colour_for(percentage)), encoding="utf-8")
        print(f"wrote {badge_file.relative_to(REPOSITORY_ROOT)}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
