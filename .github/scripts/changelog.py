"""Release notes from CHANGELOG.md for the Release workflow.

  changelog.py release VERSION DATE
      Moves the [Unreleased] entries of CHANGELOG.md under a "VERSION - DATE" heading and
      prints them.
  changelog.py play-notes URL
      Reads release notes on stdin and prints them as Google Play "What's new" text.
"""
import re
import sys

UNRELEASED = "## [Unreleased]"
PLAY_LIMIT = 500


def split_release(changelog, version, date):
    """Returns the changelog with its unreleased entries under a version heading, and the entries."""
    start = changelog.index(UNRELEASED) + len(UNRELEASED)
    next_version = changelog.find("\n## [", start)
    end = len(changelog) if next_version == -1 else next_version + 1
    notes = changelog[start:end].strip()
    if not notes:
        raise SystemExit("CHANGELOG.md has nothing under [Unreleased]")
    older = changelog[end:]
    updated = f"{changelog[:start]}\n\n## [{version}] - {date}\n\n{notes}\n"
    return (updated + "\n" + older if older else updated), notes


def entries(notes):
    """Yields (heading, entry) for each list entry, with wrapped lines joined."""
    heading, entry = None, None
    for line in notes.splitlines():
        if line.startswith("### "):
            if entry:
                yield heading, entry
            heading, entry = line[4:].strip(), None
        elif line.startswith("- "):
            if entry:
                yield heading, entry
            entry = line[2:].strip()
        elif line.strip() and entry is not None:
            entry += " " + line.strip()
    if entry:
        yield heading, entry


def plain(text):
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    text = re.sub(r"`([^`]*)`", r"\1", text)
    return re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)


def play_notes(notes, url, limit=PLAY_LIMIT):
    """Plain-text notes: leading whole entries that fit within limit, then a link to url."""
    footer = f"Full release notes: {url}"
    lines, last_heading = [], None
    for heading, entry in entries(notes):
        added = [heading] if heading and heading != last_heading else []
        added.append("• " + plain(entry))
        if len("\n".join(lines + added + ["", footer])) > limit:
            break
        lines += added
        last_heading = heading
    return "\n".join(lines + ["", footer]) if lines else footer


def main(argv):
    if argv[:1] == ["release"] and len(argv) == 3:
        with open("CHANGELOG.md", encoding="utf-8") as f:
            updated, notes = split_release(f.read(), argv[1], argv[2])
        with open("CHANGELOG.md", "w", encoding="utf-8") as f:
            f.write(updated)
        print(notes)
    elif argv[:1] == ["play-notes"] and len(argv) == 2:
        sys.stdout.write(play_notes(sys.stdin.read(), argv[1]))
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
