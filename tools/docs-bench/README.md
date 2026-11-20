# Documentation benchmark data

The questions, tasks, answer keys and prompts of the documentation benchmark that ADR 0022 records
(`docs/adr/0022-agent-entry-point-and-documentation-lookup.md`), from issues 135, 137 and 138. They
are kept so that the runs can be checked and repeated.

**The harness is not here yet.** It ran on 19 November 2026 as about 570 lines of Python 3 and a short bash setup script.
`AGENTS.md` allows no new toolchain without a security review, so issue 135 stays open until the harness is ported to
Java on the JDK alone. When the docs tool's own search (issue 136, step 3) takes the place of the search described
below, that is a new comparison with its own version, not a rerun of these.

## Files

| File | What it holds |
|---|---|
| `lookups.json` | Issue 135: 14 single-fact lookups (`q`), each with `keys` and `forbid` patterns. `amended` says why a key changed after the discarded first run; every key was fixed before the clean run started. |
| `tasks.json` | Issue 137: 12 multi-file development tasks, written and keyed by a separate reviewer from the repository alone, with `area`, `keys`, `forbid` and `evidence` (`path:line` at the revision below). `amended_after_run` says why a key changed after the run. |
| `overrides.json` | Issue 135: the two answers marked wrong by hand after the clean run. |
| `prompts-lookups.json`, `prompts-tasks.json` | The text appended to the system prompt: `common`, then the setup's own text. The two files differ only in the answer length they ask for, five sentences for lookups and eight for tasks. `{BRAIN}` stands for the copy's `BRAIN.md`. |

## How the runs were made

- **Repository:** a fresh copy of revision `3eb06772fe07a628c62e61bbd79dbf845c177784` for each setup, made its own Git
  repository with one commit. R1 and R2 add only a root `CLAUDE.md`.
- **Setups:**

  | Setup | What the agent gets besides the repository |
  |---|---|
  | A | Nothing |
  | B | The prompt points at `BRAIN.md` |
  | C | `./docs-search WORDS` (below) |
  | D | B and C |
  | E | `site/contentIndex.json`, a Quartz-format index of every Markdown page, queried with `jq` |
  | F | `./site-search WORDS` over that index |
  | G | `BRAIN.md` preloaded in the prompt, and C |
  | H | `BRAIN.md` preloaded in the prompt |
  | R1 | A root `CLAUDE.md` holding `@AGENTS.md`, loaded by Claude Code's own import |
  | R2 | A root `CLAUDE.md` holding `@AGENTS.md` and `@BRAIN.md` |

- **Agent:** Claude Code 2.1.325 on a separate macOS host, headless:
  `claude -p QUESTION --model MODEL --effort medium --output-format stream-json --verbose --setting-sources project
  --strict-mcp-config --disable-slash-commands --no-session-persistence --tools Bash,Read,Grep,Glob --allowedTools
  TOOLS --append-system-prompt PROMPT --max-budget-usd 2`, with `CLAUDE_CONFIG_DIR` set to a directory of its own.
  TOOLS are `Read`, `Grep`, `Glob` and `Bash` limited to `cat`, `sed`, `head`, `tail`, `ls`, `find`, `grep`, `rg`,
  `jq`, `wc`, `git ls-files`, `git grep`, `./docs-search` and `./site-search`. Setups A to H ran with
  `CLAUDE_CODE_DISABLE_CLAUDE_MDS=1`. R1 and R2 instead excluded only the host's user-level `CLAUDE.md`, with
  `claudeMdExcludes` in a `--settings` file.
- **Models and repetitions:** `claude-haiku-5-5` and `claude-sonnet-5-5`, three repetitions of every question in every
  setup. The runs were shuffled with seed 135, so that models and setups met the same rate limits and time of day,
  and ran four at a time (the overhead runs two at a time). Each had a 900-second limit; every run exited 0.
- **Fixed overhead:** the question "Reply with the single word OK." in setup A, three runs per model: a median of
  7,924 total tokens for Haiku and 7,101 for Sonnet. It is reported, not subtracted.
- **Discarded run:** the first lookup run loaded the host's own user-level `CLAUDE.md` into every run and was
  discarded. The clean runs disable or exclude it as described above.

## The search commands

- **Sections:** every Git-tracked Markdown file, split at headings of levels 1 to 4 outside fenced code blocks. The
  text before the first heading is a section headed by the frontmatter `title`, or by the path. A section records its
  path, first line, heading and lines.
- **`./docs-search WORDS`:** BM25 with k1 1.2 and b 0.75 over the sections. Tokens are runs of ASCII letters and
  digits, lower-cased; a word with camel-case or digit parts also yields each part. Stop words: a, an, and, are, as,
  at, be, by, can, do, does, for, from, has, have, how, in, is, it, its, of, on, or, that, the, this, to, what, when,
  where, which, who, why, with, today, give. A section counts as its text plus its heading and its path, both twice.
  For each of the five best sections it prints the rank, `path:line` of the first line with the most distinct query
  tokens, the heading (80 characters at most), the section's line range and that line (220 characters at most).
- **Quartz-format index (E and F):** `site/contentIndex.json`, one entry per Git-tracked Markdown file, keyed by its
  path without `.md`: `slug`, `filePath`, `title` (the frontmatter title, else the first `# ` heading, else the file
  name), `links` (relative `.md` links, resolved to slugs), `tags` (from the frontmatter) and `content` (the text
  after the frontmatter). It follows Quartz's index format; it is not Quartz.
- **`./site-search WORDS`:** the same ranking over whole pages of that index. For each of the five best pages it prints
  the path, the title and the best line.

## Scoring and aggregation

- **Pass:** the run ended without an error, every `keys` pattern is found in the final answer, and no `forbid`
  pattern is. They are Python `re` patterns, matched case-insensitively; `forbid` also matches multi-line, so `^`
  matches at the start of any line. Then `overrides.json` applies.
- **Tokens:** from `modelUsage`, summed over every model the run used. Fresh tokens are input, cache creation and
  output; total tokens are fresh tokens plus cache reads.
- **Cost:** the `total_cost_usd` that Claude Code reported for the run, its API-equivalent price on 19 November 2026.
  The runs used a subscription, so it is not a bill.
- **Contrast X against Y, per model:** for each question, the median of X's runs divided by the median of Y's runs,
  then the geometric mean over the questions. The 95% interval is a two-level bootstrap with 4,000 resamples and seed
  135: resample the questions, then each setup's repetitions within every sampled question. The intervals are
  exploratory: few questions, several contrasts and no correction for multiple comparisons.

## Keys changed after a run

- **Lookups,** after the discarded first run and before the clean one: q05, q07 and q09 (see `amended`), and q11
  gained its `forbid` pattern.
- **Tasks,** after the run; ADR 0022 gives the pass counts both ways:
  - t01 accepts the rejections named in words. Before, its first two keys were `(?i)\bROUTE_NOT_SERVED\b` and
    `(?i)\bBILLABLE_WEIGHT_ABOVE_LIMIT\b`.
  - t12 accepts an answer saying the row would not expose the flag. Before, its second key had no
    `\b(would|will|does)\s+not\b[^.]{0,80}\b(expose|return)` or `\bno\b[^.]{0,20}\bwould not\b` alternative.

## Results

The tables are in ADR 0022. Raw run logs are not published (`AGENTS.md`, step 6).
