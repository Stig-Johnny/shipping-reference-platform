---
title: "Risk levels, explained"
description: "What the four risk levels mean, who must approve each, and how a change gets its level. Written for readers who do not work with code."
status: accepted
updated: 2026-11-06
---

# Risk levels, explained

Every proposed change to this repository, a pull request, gets a risk level from 0 to 3. The level decides who must
say yes before the change is merged. The rules are in `policy/risk.json`; the decision behind them is ADR 0008.

## The four levels

| Level | In plain words | Examples here | Who must approve |
|---|---|---|---|
| 0 | Wording and presentation of documents. | Fixing a sentence in a guide. | Automated checks pass and the AI reviewer records its approval. |
| 1 | How screens look, and small experiments that are switched off by default. | Changing spacing on the quote cards; a label preview shown only to tenants who have the prototype switched on. | Same as level 0. |
| 2 | Ordinary behaviour of the application. | How the audit trail is written; how the web app calls the server. | Same as level 0. |
| 3 | Anything that touches money, bookings, personal data, logins, keeping customers' data apart, the database structure, third-party software, or the controls themselves. | The price table; booking rules; recipient addresses; who can see which tenant's data; adding a library; changing these rules. | Everything above, **plus the repository owner's own approval of the exact final version**, before it is merged. |

"Tenant" means one webshop using the platform. Keeping tenants apart means one webshop can never see or change
another webshop's quotes or shipments.

## How a change gets its level

The level is the highest of three answers:

1. **Where does the change touch?** Each folder and file type has a level. A change to the price calculation is in
   a level 3 folder. A change to a screen's layout is in a level 1 folder.
2. **What does the change contain?** Some contents raise the level wherever they appear: database commands, words
   that handle logins or tenant separation, prices and rounding, booking keys, personal data fields, disabled tests,
   and a few programming techniques that hide what code does.
3. **Is anything unclear?** A file that no rule covers, a binary file, an incomplete list of changes, or a generated
   file that changed without its source all count as level 3. When the rules cannot tell, the answer is "sensitive".

Three safeguards keep this honest:

- The rules are always read from the approved version of the repository, never from the change being judged. A
  change cannot lower its own level by editing the rules.
- The AI reviewer may raise a level and must say why. Nobody and nothing can lower a level the rules computed.
- If the owner approves and then new changes are pushed, the approval no longer counts. The owner approves the exact
  version that will be merged.

## What approval means, and what it does not

> Agent-authored changes receive recorded automated review; sensitive changes additionally receive the owner’s human
> approval. This demonstrates accountable human oversight, not independent two-person review or a
> production-security certification.

- "Recorded automated review" means an AI reviewer's findings and decision are stored and tied to the exact version
  of the change. AI reviewers can be wrong.
- One person approves level 3 changes. There is no second, independent human reviewer.
- The rules recognise patterns. A change that looks harmless but has a sensitive effect can be classified too low.
  Unknown files are treated as sensitive; unknown meanings are not always caught. `docs/gaps.md` lists this and other
  limits.

## Where this runs

Today nothing computes the level automatically: the author states it in the pull request, and the owner approves level
3 changes in writing. The checks below are planned (ADR 0008):

- The `risk-approval` check computes the level, shows every rule that matched, and blocks a level 3 change until the
  owner has approved its current version.
- The `ai-review` check blocks any change without a recorded AI review of its final version.
- GitHub's code-owner rule independently requires the owner's approval for the folders listed in
  `.github/CODEOWNERS`. Every one of those folders is level 3 in the policy, and a test keeps the two lists in step.
  This needs pull requests opened under an identity other than the owner's (`docs/gaps.md`, section 5).
