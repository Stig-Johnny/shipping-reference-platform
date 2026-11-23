# Documentation benchmark data

The questions, tasks, answer keys and prompts of the documentation benchmark that ADR 0022 records
(`docs/adr/0022-agent-entry-point-and-documentation-lookup.md`), from issues 135, 137 and 138. They
are kept so that the runs can be checked and repeated.

**The harness is not here yet.** It ran on 19 November 2026 as about 570 lines of Python 3 and a short bash setup
script. `AGENTS.md` allows no new toolchain without a security review, so issue 135 stays open until the harness is
ported to Java on the JDK alone. When the docs tool's own search (issue 136, step 3) takes the place of the search
described below, that is a new comparison with its own version, not a rerun of these.

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
  `jq`, `wc`, `git ls-files`, `git grep`, `./docs-search WORDS` and `./site-search WORDS`. Setups A to H ran with
  `CLAUDE_CODE_DISABLE_CLAUDE_MDS=1`. R1 and R2 instead excluded only the host's user-level `CLAUDE.md`, with
  `claudeMdExcludes` in a `--settings` file.
- **Runs:** `claude-haiku-5-5` and `claude-sonnet-5-5`, three repetitions of each question in each setup of its run:

  | Run | Questions | Setups | Prompt file | Runs |
  |---|---|---|---|---|
  | Lookups, issue 135 | `lookups.json` | A to H | `prompts-lookups.json` | 672 |
  | Fixed overhead | "Reply with the single word OK." | A | `prompts-lookups.json` | 6 |
  | Development tasks, issue 137 | `tasks.json` | A, C, G and H | `prompts-tasks.json` | 288 |
  | Real import, issue 138 | `lookups.json`, then `tasks.json` | R1 and R2 | the file of each set | 168 and 144 |
  | Discarded first run | `lookups.json` | A to G | `prompts-lookups.json` | 588, and 6 overhead |

  Within a run, the order was shuffled with seed 135, so that models and setups met the same rate limits and time of
  day, and four ran at a time (the overhead runs two at a time). Each run had a 900-second limit; every run exited 0.
- **Fixed overhead:** the question "Reply with the single word OK." in setup A, three runs per model: a median of
  7,924 total tokens for Haiku and 7,101 for Sonnet. It is reported, not subtracted.
- **Discarded run:** the first lookup run loaded the host's own user-level `CLAUDE.md` into every run and was
  discarded. The clean runs disable or exclude it as described above.

## The search commands

These are the commands as they ran. A later implementation that ranks differently is a new comparison.

- **Files:** the output of `git ls-files '*.md'` in the copy, sorted by path: every tracked Markdown file, in any
  directory.
- **Frontmatter:** when a file's first line is `---`, its frontmatter runs to the next line that is `---`, both after
  trimming. Lines of the form `key: value`, with a key matching `\w[\w-]*`, give simple values, trimmed and without
  surrounding double quotes. The body starts after the closing line.
- **Sections:** a heading is a body line matching `^(#{1,4})\s+(.*)$` outside a fenced code block; a line that starts
  with three backticks, after any leading spaces, opens or closes a fence. Each heading starts a section that includes
  the heading line and runs to the line before the next heading. The lines before the first heading form a section
  headed by the frontmatter `title`, or by the path when there is none. A section whose lines are all blank is
  dropped. A section keeps its path, the number of its first line in the file (counting from 1), its heading and its
  lines.
- **Tokens:** every match of `[A-Za-z0-9]+`, in order. Each gives itself in lower case and, when it splits into more
  than one part by `[A-Z]?[a-z]+|[A-Z]+(?![a-z])|[0-9]+`, each part in lower case as well: `CurrentTenant` gives
  `currenttenant`, `current` and `tenant`. Then the stop words are removed: a, an, and, are, as, at, be, by, can, do,
  does, for, from, has, have, how, in, is, it, its, of, on, or, that, the, this, to, what, when, where, which, who,
  why, with, today, give.
- **Ranking, `./docs-search WORDS`:** a section's document is the tokens of its lines joined by newlines, followed
  twice by the tokens of its heading and then of its path. The query is the set of distinct tokens of the words given.
  With N sections, `avgdl` their mean length in tokens, `df(t)` the number of sections whose document contains `t` and
  `tf` the count of `t` in the document, a section scores the sum, over the query tokens it contains, of
  `idf(t) × tf × 2.2 / (tf + 1.2 × (0.25 + 0.75 × length / avgdl))`, where
  `idf(t) = ln(1 + (N − df(t) + 0.5) / (df(t) + 0.5))`: BM25 with k1 1.2 and b 0.75. Sections that score 0 are left
  out. Among equal scores, the section that comes later in file and line order ranks first.
- **Output:** for each of the five best sections, `RANK. PATH:LINE  [HEADING, lines FIRST-LAST]`, then the chosen
  line indented by three spaces. The chosen line is the first of the section's lines with the most distinct query
  tokens, and LINE is its number in the file. HEADING is cut to 80 characters; the chosen line is trimmed, and when it
  is longer than 220 characters it is cut to 217 followed by `...`. Without a match the command prints `no matches`
  and exits 1; without query tokens it prints a usage line and exits 2.
- **Quartz-format index (E and F):** `site/contentIndex.json`, an object with one entry per file above, keyed by its
  path without `.md` (spaces would become hyphens): `slug` (the same key), `filePath`, `title` (the frontmatter
  `title`, else the text after the first body line that starts with `# `, else the file name), `links` (each target of
  a Markdown link matching `\]\(([^)#\s]+\.md)(?:#[^)]*)?\)` without `://`, resolved against the file's directory and
  turned into a key, sorted and without duplicates), `tags` (the frontmatter `tags` value without its brackets, split
  at commas) and `content` (the body). It follows Quartz's index format; it is not Quartz.
- **`./site-search WORDS`:** the same tokens, ranking and choice of line over one document per entry of that index, in
  the index's order: the tokens of its `content`, followed twice by the tokens of its `title` and then of its
  `filePath`. For each of the five best pages it prints `RANK. PATH  [TITLE]`, with the title cut to 80 characters,
  then the chosen line.

## Scoring and aggregation

- **Pass:** the run ended without an error, every `keys` pattern is found in the final answer, and no `forbid`
  pattern is. They are Python `re` patterns, matched case-insensitively; `forbid` also matches multi-line, so `^`
  matches at the start of any line. Then `overrides.json` applies.
- **Tokens:** from `modelUsage`, summed over every model the run used. Fresh tokens are input, cache creation and
  output; total tokens are fresh tokens plus cache reads.
- **Cost:** the `total_cost_usd` that Claude Code reported for the run, its API-equivalent price on 19 November 2026.
  The runs used a subscription, so it is not a bill.
- **Contrast X against Y, per model:** for each question, the median of X's runs divided by the median of Y's runs;
  the estimate is the geometric mean of those ratios over the questions. The 95% interval is a percentile bootstrap
  with 4,000 resamples, drawn for each contrast from Python's `random.Random(135)`. A resample draws as many questions
  as there are, with replacement. For each drawn question it draws X's runs again with replacement, as many as X has,
  then Y's runs likewise, and takes the log of the ratio of their medians. The resample's value is the exponential of
  the mean of those logs. The 4,000 values are sorted, and the bounds are the values at positions 100 and 3,900,
  counting from 0. Another random generator gives slightly different bounds. The intervals are exploratory: few
  questions, several contrasts and no correction for multiple comparisons.

## Keys changed after a run

- **Lookups,** after the discarded first run and before the clean one: q05, q07 and q09 (see `amended`), and q11
  gained its `forbid` pattern.
- **Tasks,** after the run; ADR 0022 gives the pass counts both ways:
  - t01 accepts the rejections named in words. Before, its first two keys were `(?i)\bROUTE_NOT_SERVED\b` and
    `(?i)\bBILLABLE_WEIGHT_ABOVE_LIMIT\b`.
  - t12 accepts an answer saying the row would not expose the flag. Before, its second key had no
    `\b(would|will|does)\s+not\b[^.]{0,80}\b(expose|return)` or `\bno\b[^.]{0,20}\bwould not\b` alternative.

## Results

The tables are in ADR 0022. The per-run results behind them, such as each run's pass, tokens and cost, are not in the
repository, so the tables cannot be recomputed from it. Raw run logs are not published (`AGENTS.md`, step 6).
