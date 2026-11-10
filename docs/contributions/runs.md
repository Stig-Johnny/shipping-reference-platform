---
title: "Model runs and cost evidence"
description: "Recorded models, usage, cost bases and outcomes, with unsuccessful work and explicit limits on attribution and coverage."
status: accepted
updated: 2026-11-10
schemaVersion: 1
coverage: "Partial record set: 14 historical DeepSeek rounds for issues 69, 37 and 101, four supported historical Codex phases, and seven current issue 111 phases. Work dates and backfill dates differ. Missing phases, unallocated subscription costs and incomplete delivery costs remain unknown; this is not complete benchmark or billing coverage."
records:
  - id: "issue-69-build-round-1"
    issue: 69
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "4d2af998ccebc119bd92917e6acab7d00389a8c7"
    outcome: "partial"
    scope: "Characterize switch-pattern bytecode evidence limits; bounded worker implementation attempt."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T19:29:22.809042+00:00; CLI exit 1; stop: cost alarm (not a hard per-job billing cap). 10 model steps, 12 gateway attempts, 2 failed tool calls. Later job decision: accepted for integration; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-69-build/job.json SHA-256 aeff36041f865c86900e3d6aeb806e401f81f631cb8f9e636bfe5abc9a41b2a1; events-1.jsonl SHA-256 e3dc8f8118be4859ccf8bca96a77543b26df35bc1bcb31e5460c17eaa55678d7."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 571020
      output: 9125
      scope: "Provider-reported prompt/completion sums retained for 10 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 118 through 129 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.183759"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 10 chat and 2 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 118 through 129. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-69-build-round-2"
    issue: 69
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "4d2af998ccebc119bd92917e6acab7d00389a8c7"
    outcome: "completed"
    scope: "Characterize switch-pattern bytecode evidence limits; bounded worker implementation attempt."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T19:36:37.957975+00:00; CLI exit 0; stop: none recorded. 14 model steps, 14 gateway attempts, 0 failed tool calls. Later job decision: accepted for integration; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-69-build/job.json SHA-256 aeff36041f865c86900e3d6aeb806e401f81f631cb8f9e636bfe5abc9a41b2a1; events-2.jsonl SHA-256 4a625b8ae9857e4aa1b887c0256f35dcfcad244dc763746357b66749a9a89dfe."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 1177700
      output: 7496
      scope: "Provider-reported prompt/completion sums retained for 14 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 130 through 143 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.362311"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 14 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 130 through 143. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-37-plan-round-1"
    issue: 37
    date: "2026-11-09"
    role: "planning"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "651901d7dcc9930086f89ece002beefc712dbfa5"
    outcome: "partial"
    scope: "Plan repository source-link work within existing publication controls."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T20:59:33.981759+00:00; CLI exit 1; stop: controller model-step limit. 5 model steps, 6 gateway attempts, 0 failed tool calls. Later job decision: plan reviewed; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. USD 0.400000 remained reserved in this window, excluded from the cost amount; its actual usage and bill are unknown."
    evidence: "Backfill 2026-11-10: issue-37-plan/job.json SHA-256 a1d9d9b48bff3c3d8dfffedd7e01d6d89876414c4bb5dd33e6e0243b674a2011; events-1.jsonl SHA-256 e8046e8bb9744eccc9d3e4a8eee9608d59825cc74b5491b8ec10af8fc4e92207."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 167143
      output: 9707
      scope: "Provider-reported prompt/completion sums retained for 5 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 144 through 149 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.061794"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 5 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 144 through 149. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-37-plan-round-2"
    issue: 37
    date: "2026-11-09"
    role: "planning"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "651901d7dcc9930086f89ece002beefc712dbfa5"
    outcome: "completed"
    scope: "Plan repository source-link work within existing publication controls."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T22:19:12.639871+00:00; CLI exit 0; stop: none recorded. 2 model steps, 2 gateway attempts, 0 failed tool calls. Later job decision: plan reviewed; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-37-plan/job.json SHA-256 a1d9d9b48bff3c3d8dfffedd7e01d6d89876414c4bb5dd33e6e0243b674a2011; events-2.jsonl SHA-256 88dbf3a5face073d5852bc962fa633604e59c306214cceb00e0531f5041866ec."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 152422
      output: 11110
      scope: "Provider-reported prompt/completion sums retained for 2 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 150 through 151 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.059060"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 2 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 150 through 151. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-37-build-round-1"
    issue: 37
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "6b7405f24b472dfdfc45a866006a90dde092e5a4"
    outcome: "partial"
    scope: "Attempt repository source-link changes; the worker proposal remained unaccepted."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T22:26:57.469375+00:00; CLI exit 0; stop: controller model-step limit. 15 model steps, 15 gateway attempts, 0 failed tool calls. Later job decision: rejected as a worker proposal; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-37-build/job.json SHA-256 e537b1847032142f0879fef6d9b9155b1fc94ffa118efdb50440babeeb9af6d9; events-1.jsonl SHA-256 f0edb61f364404f916a98a6509b49dcca186123fe00f770c72d3f0d378352fe9."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 808758
      output: 4592
      scope: "Provider-reported prompt/completion sums retained for 15 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 152 through 166 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.248145"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 15 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 152 through 166. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-37-build-round-2"
    issue: 37
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "6b7405f24b472dfdfc45a866006a90dde092e5a4"
    outcome: "partial"
    scope: "Attempt repository source-link changes; the worker proposal remained unaccepted."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T22:35:04.049505+00:00; CLI exit 1; stop: controller model-step limit. 16 model steps, 18 gateway attempts, 0 failed tool calls. Later job decision: rejected as a worker proposal; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. USD 0.400000 remained reserved in this window, excluded from the cost amount; its actual usage and bill are unknown."
    evidence: "Backfill 2026-11-10: issue-37-build/job.json SHA-256 e537b1847032142f0879fef6d9b9155b1fc94ffa118efdb50440babeeb9af6d9; events-2.jsonl SHA-256 47e98619f6430afdad2c3d0b28e7f1951b4303afa2673874469437c35421428c."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 999104
      output: 29316
      scope: "Provider-reported prompt/completion sums retained for 17 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 167 through 184 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.334917"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 17 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 167 through 184. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-37-build-round-3"
    issue: 37
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "6b7405f24b472dfdfc45a866006a90dde092e5a4"
    outcome: "partial"
    scope: "Attempt repository source-link changes; the worker proposal remained unaccepted."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T22:42:27.565427+00:00; CLI exit 0; stop: controller model-step limit. 15 model steps, 15 gateway attempts, 0 failed tool calls. Later job decision: rejected as a worker proposal; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-37-build/job.json SHA-256 e537b1847032142f0879fef6d9b9155b1fc94ffa118efdb50440babeeb9af6d9; events-3.jsonl SHA-256 c6b1045433c425da3dd3fec17451aff35dfd3ea707759118f3c17af5328dd1ff."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 1237585
      output: 21333
      scope: "Provider-reported prompt/completion sums retained for 15 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 185 through 199 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.396882"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 15 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 185 through 199. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-37-build-round-4"
    issue: 37
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "6b7405f24b472dfdfc45a866006a90dde092e5a4"
    outcome: "partial"
    scope: "Attempt repository source-link changes; the worker proposal remained unaccepted."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T22:48:32.814489+00:00; CLI exit 0; stop: controller model-step limit. 16 model steps, 17 gateway attempts, 1 failed tool calls. Later job decision: rejected as a worker proposal; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-37-build/job.json SHA-256 e537b1847032142f0879fef6d9b9155b1fc94ffa118efdb50440babeeb9af6d9; events-4.jsonl SHA-256 cf68cbb10de189c7d64d5cdb066a9d71579ef62fb56d465d4ff95eaf3838382c."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 809497
      output: 30365
      scope: "Provider-reported prompt/completion sums retained for 17 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 200 through 216 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.279294"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 17 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 200 through 216. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-37-build-round-5"
    issue: 37
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "6b7405f24b472dfdfc45a866006a90dde092e5a4"
    outcome: "partial"
    scope: "Attempt repository source-link changes; the worker proposal remained unaccepted."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T22:55:59.620285+00:00; CLI exit 0; stop: controller model-step limit. 15 model steps, 15 gateway attempts, 0 failed tool calls. Later job decision: rejected as a worker proposal; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-37-build/job.json SHA-256 e537b1847032142f0879fef6d9b9155b1fc94ffa118efdb50440babeeb9af6d9; events-5.jsonl SHA-256 d1e1154f8708bf1826bb018d0f8d871d10e401e549568f3e9c96a9fbea6b64b8."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 1031371
      output: 11755
      scope: "Provider-reported prompt/completion sums retained for 15 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 217 through 231 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.323523"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 15 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 217 through 231. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-37-build-round-6"
    issue: 37
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "6b7405f24b472dfdfc45a866006a90dde092e5a4"
    outcome: "completed"
    scope: "Attempt repository source-link changes; the worker proposal remained unaccepted."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T22:59:52.400728+00:00; CLI exit 0; stop: none recorded. 8 model steps, 8 gateway attempts, 0 failed tool calls. Later job decision: rejected as a worker proposal; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-37-build/job.json SHA-256 e537b1847032142f0879fef6d9b9155b1fc94ffa118efdb50440babeeb9af6d9; events-6.jsonl SHA-256 6c9c9b3149f6eb15ab5de317170dcaec3d714e6c04c9e6ef6e7c4065b5d71c04."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 690303
      output: 3825
      scope: "Provider-reported prompt/completion sums retained for 8 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 232 through 239 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.211683"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 8 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 232 through 239. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-37-build-round-7"
    issue: 37
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "6b7405f24b472dfdfc45a866006a90dde092e5a4"
    outcome: "partial"
    scope: "Attempt repository source-link changes; the worker proposal remained unaccepted."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T23:10:34.498640+00:00; CLI exit 0; stop: controller model-step limit. 17 model steps, 18 gateway attempts, 0 failed tool calls. Later job decision: rejected as a worker proposal; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-37-build/job.json SHA-256 e537b1847032142f0879fef6d9b9155b1fc94ffa118efdb50440babeeb9af6d9; events-7.jsonl SHA-256 0e903fa7c1bd3a4253fc4d2a7f7d875269d3697456b9a53d10b69c5e70be1ce1."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 800555
      output: 29951
      scope: "Provider-reported prompt/completion sums retained for 18 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 240 through 257 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.276116"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 18 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 240 through 257. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-37-build-round-8"
    issue: 37
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "6b7405f24b472dfdfc45a866006a90dde092e5a4"
    outcome: "partial"
    scope: "Attempt repository source-link changes; the worker proposal remained unaccepted."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T23:20:25.993235+00:00; CLI exit 0; stop: controller model-step limit. 15 model steps, 15 gateway attempts, 0 failed tool calls. Later job decision: rejected as a worker proposal; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-37-build/job.json SHA-256 e537b1847032142f0879fef6d9b9155b1fc94ffa118efdb50440babeeb9af6d9; events-8.jsonl SHA-256 7588cfd09b625be08ccd8e1eeb88682a69034c9eb0664a16b436ab1c0d317c99."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 893290
      output: 13315
      scope: "Provider-reported prompt/completion sums retained for 15 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 258 through 272 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.283971"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 15 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 258 through 272. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-101-build-round-1"
    issue: 101
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "980715d0bf425dd4440a407d4aa980d5bae98a92"
    outcome: "partial"
    scope: "Attempt compact Brain details while preserving relationships, sources and statuses."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T23:27:44.109499+00:00; CLI exit 0; stop: controller model-step limit. 15 model steps, 15 gateway attempts, 1 failed tool calls. Later job decision: rejected as a worker proposal; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-101-build/job.json SHA-256 8f9fb2c919a8f4b2013f9dffb531aa8304473c72b377dd67e3ede2fce9d606b3; events-1.jsonl SHA-256 26326158564f416d3795b610257336275ed10ec4bd64e7a914477fc5111c1798."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 766793
      output: 17819
      scope: "Provider-reported prompt/completion sums retained for 15 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 273 through 287 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.251427"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 15 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 273 through 287. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-101-build-round-2"
    issue: 101
    date: "2026-11-09"
    role: "implementation"
    tool: "OpenCode bounded worker through the reviewed gateway"
    model: "deepseek-flash"
    checkpoint: "unknown"
    effort: "unknown"
    base: "980715d0bf425dd4440a407d4aa980d5bae98a92"
    outcome: "failed"
    scope: "Attempt compact Brain details while preserving relationships, sources and statuses."
    environment: "Historical bounded worker environment; controller checkout base recorded above. This backfill does not reconstruct the full supplied context or claim identical environments across tasks."
    result: "Round ended 2026-11-09T23:36:35.971894+00:00; CLI exit 1; stop: none recorded. 12 model steps, 13 gateway attempts, 0 failed tool calls. Later job decision: rejected as a worker proposal; no separate acceptance decision for this round is inferred."
    limitations: "Work occurred 2026-11-09; audited/backfilled 2026-11-10. Configured alias is recorded; checkpoint and effort were not retained. Ledger attribution is corroborated by timestamp windows, counts and amounts, but no job ID exists in ledger rows. This excludes Codex, human and infrastructure cost. No unresolved reservation was present in this window at audit time."
    evidence: "Backfill 2026-11-10: issue-101-build/job.json SHA-256 8f9fb2c919a8f4b2013f9dffb531aa8304473c72b377dd67e3ede2fce9d606b3; events-2.jsonl SHA-256 8e0da47a5ae39e21f8b8357a73c41c679cf9f4529f694ca15b1fb7cea6af55d0."
    correctionOf: "none"
    usage:
      state: "reported"
      input: 784439
      output: 12853
      scope: "Provider-reported prompt/completion sums retained for 13 usage-settled chat attempts in this round's corroborated ledger window; unresolved calls excluded. Cache/reasoning subsets are not added again."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 288 through 300 (one-based); timestamp-window count and accounted delta match the job receipt. Step-finish events can omit gateway activity."
    cost:
      basis: "metered-estimate"
      amount: "0.250761"
      currency: "USD"
      scope: "Usage-settled estimate for this round's corroborated ledger window: 13 chat and 0 web results. Reservations and other delivery phases excluded; not a provider invoice."
      method: "Rates recorded 2026-11-09: ceil((3 * prompt_tokens + 12 * completion_tokens) / 10) micro-USD per settled chat; USD 0.000750 per billable web result. Cache discounts ignored; sum each recorded charge after per-request rounding. Current rates not reverified."
      receipt: "Ledger SHA-256 a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6, entries 288 through 300. Gateway rate-source hashes appear in the backfill method. Event cost: 0 is a placeholder and is not used."
  - id: "issue-69-codex-patch-review"
    issue: 69
    date: "2026-11-09"
    role: "independent-review"
    tool: "Separate Codex setup coordinator"
    model: "unknown"
    checkpoint: "unknown"
    effort: "unknown"
    base: "unknown"
    outcome: "completed"
    scope: "Initial exported worker patch review; not final integrated-head approval."
    environment: "Codex workspace continuation under repository rules; full task environment and provider checkpoint are not established by this receipt."
    result: "Existing history phase 69-2 records patch acceptance for integration after diff review, check-log hashes and independent fixture disassembly."
    limitations: "Backfilled 2026-11-10. Runtime settings, full review base, phase usage/cost and resolved checkpoint are unknown. Acceptance of this patch is not owner merge approval."
    evidence: "docs/contributions/history.md, Issue 69 phase 69-2; sanitized review receipt at 2026-11-09 19:40:26 UTC."
    correctionOf: "none"
    usage:
      state: "unknown"
      input: "unknown"
      output: "unknown"
      scope: "This named phase only; no allocation from an account or shared session counter."
      receipt: "No phase-scoped input/output usage receipt was retained for this record; counts cannot be inferred from the patch or model name."
    cost:
      basis: "unknown"
      amount: "unknown"
      currency: "unknown"
      scope: "This named phase only; provider/subscription allocation, human and infrastructure costs are not established."
      method: "No task-specific bill or evidenced subscription/shared-session allocation is available. Missing cost is unknown, not zero; no rate is assumed."
      receipt: "Retained contribution receipts establish work and selected settings, not a phase-specific bill or monetary allocation."
  - id: "issue-69-codex-integration"
    issue: 69
    date: "2026-11-09"
    role: "integration"
    tool: "Codex integrating coordinator"
    model: "gpt-6-astra"
    checkpoint: "unknown"
    effort: "xhigh"
    base: "e1108baec90543f8a898b847eb79f46b8e8e26fa"
    outcome: "completed"
    scope: "Integrate the exact worker patch, validate it and add publication/provenance records."
    environment: "Codex workspace continuation under repository rules; full task environment and provider checkpoint are not established by this receipt."
    result: "Existing history phase 69-3 records hash verification of ten exported check logs and a 28-test focused suite with no failures/errors/skips; later integrated results have separate receipts."
    limitations: "Backfilled 2026-11-10. These historical settings do not establish any current session setting. No resolved checkpoint or phase-scoped cost/usage receipt is available."
    evidence: "docs/contributions/history.md, Issue 69 phase 69-3; historical runtime metadata at 2026-11-09 20:36:46 UTC establishes configured model and effort."
    correctionOf: "none"
    usage:
      state: "unknown"
      input: "unknown"
      output: "unknown"
      scope: "This named phase only; no allocation from an account or shared session counter."
      receipt: "No phase-scoped input/output usage receipt was retained for this record; counts cannot be inferred from the patch or model name."
    cost:
      basis: "unknown"
      amount: "unknown"
      currency: "unknown"
      scope: "This named phase only; provider/subscription allocation, human and infrastructure costs are not established."
      method: "No task-specific bill or evidenced subscription/shared-session allocation is available. Missing cost is unknown, not zero; no rate is assumed."
      receipt: "Retained contribution receipts establish work and selected settings, not a phase-specific bill or monetary allocation."
  - id: "issue-101-codex-integration"
    issue: 101
    date: "2026-11-09"
    role: "integration"
    tool: "Codex coordinator"
    model: "gpt-6-astra"
    checkpoint: "unknown"
    effort: "xhigh"
    base: "unknown"
    outcome: "completed"
    scope: "Correct worker test assumptions and integrate compact Brain details, browser behavior, layout and documentation."
    environment: "Codex workspace continuation under repository rules; full task environment and provider checkpoint are not established by this receipt."
    result: "Existing history phase 101-2 records corrected red/green tests, 4464 ordinary checks and 22 generated files; browser and final-head outcomes were recorded separately."
    limitations: "Backfilled 2026-11-10. The retained phase summary does not establish a precise integration base or phase-scoped usage/cost. Historical configuration is not current runtime introspection."
    evidence: "docs/contributions/history.md, Issue 101 phase 101-2; receipt 6754558658 records runtime settings at 2026-11-09 23:30:06 UTC."
    correctionOf: "none"
    usage:
      state: "unknown"
      input: "unknown"
      output: "unknown"
      scope: "This named phase only; no allocation from an account or shared session counter."
      receipt: "No phase-scoped input/output usage receipt was retained for this record; counts cannot be inferred from the patch or model name."
    cost:
      basis: "unknown"
      amount: "unknown"
      currency: "unknown"
      scope: "This named phase only; provider/subscription allocation, human and infrastructure costs are not established."
      method: "No task-specific bill or evidenced subscription/shared-session allocation is available. Missing cost is unknown, not zero; no rate is assumed."
      receipt: "Retained contribution receipts establish work and selected settings, not a phase-specific bill or monetary allocation."
  - id: "issue-101-codex-checkpoint-review"
    issue: 101
    date: "2026-11-09"
    role: "independent-review"
    tool: "Separate Codex reviewer"
    model: "gpt-6-astra"
    checkpoint: "unknown"
    effort: "xhigh"
    base: "unknown"
    outcome: "completed"
    scope: "Read-only checkpoint review of the partial worker patch and test chronology."
    environment: "Codex workspace continuation under repository rules; full task environment and provider checkpoint are not established by this receipt."
    result: "Existing history phase 101-3 records test defects and preservation of constructed edge semantics by inspection; this was not final-head approval."
    limitations: "Backfilled 2026-11-10. Inherited configuration is historical receipt evidence, not backend introspection. Exact review base, checkpoint and phase usage/cost are unknown."
    evidence: "docs/contributions/history.md, Issue 101 phase 101-3; receipt 6754558658 records inherited settings without override."
    correctionOf: "none"
    usage:
      state: "unknown"
      input: "unknown"
      output: "unknown"
      scope: "This named phase only; no allocation from an account or shared session counter."
      receipt: "No phase-scoped input/output usage receipt was retained for this record; counts cannot be inferred from the patch or model name."
    cost:
      basis: "unknown"
      amount: "unknown"
      currency: "unknown"
      scope: "This named phase only; provider/subscription allocation, human and infrastructure costs are not established."
      method: "No task-specific bill or evidenced subscription/shared-session allocation is available. Missing cost is unknown, not zero; no rate is assumed."
      receipt: "Retained contribution receipts establish work and selected settings, not a phase-specific bill or monetary allocation."
  - id: "issue-111-codex-implementation"
    issue: 111
    date: "2026-11-10"
    role: "implementation"
    tool: "Codex coordinator"
    model: "unknown"
    checkpoint: "unknown"
    effort: "unknown"
    base: "e67ab7a4ffa67803e9601ed5b2671248876cd847"
    outcome: "partial"
    scope: "Implement strict model-run record validation, publication and tests for issue 111."
    environment: "Codex workspace continuation under repository rules; full task environment and provider checkpoint are not established by this receipt."
    result: "Implementation and validation are in progress at this authored checkpoint. Final checks, independent review and exact-head decisions will be recorded on the PR."
    limitations: "No completion or final-head approval is claimed. Historical settings from other phases cannot establish the current configuration. DeepSeek is blocked by its exhausted request allowance; no provider calls or cap change belong to this issue."
    evidence: "Current issue 111 implementation assignment and authored diff; no fresh per-phase model-settings, usage or billing receipt accompanies this initial record."
    correctionOf: "none"
    usage:
      state: "unknown"
      input: "unknown"
      output: "unknown"
      scope: "This named phase only; no allocation from an account or shared session counter."
      receipt: "No phase-scoped input/output usage receipt was retained for this record; counts cannot be inferred from the patch or model name."
    cost:
      basis: "unknown"
      amount: "unknown"
      currency: "unknown"
      scope: "This named phase only; provider/subscription allocation, human and infrastructure costs are not established."
      method: "No task-specific bill or evidenced subscription/shared-session allocation is available. Missing cost is unknown, not zero; no rate is assumed."
      receipt: "Retained contribution receipts establish work and selected settings, not a phase-specific bill or monetary allocation."
  - id: "issue-111-codex-cost-audit"
    issue: 111
    date: "2026-11-10"
    role: "validation"
    tool: "Separate Codex evidence auditor"
    model: "unknown"
    checkpoint: "unknown"
    effort: "unknown"
    base: "unknown"
    outcome: "completed"
    scope: "Read existing receipts and ledger aggregates for the bounded historical cost backfill."
    environment: "Codex workspace continuation under repository rules; full task environment and provider checkpoint are not established by this receipt."
    result: "Audited 14 rounds across four DeepSeek jobs. Request counts and accounted deltas reconcile with nonoverlapping ledger windows; unknown delivery phases and reservations remain separate."
    limitations: "Read-only evidence audit, not a code review or billing authentication. No repository checkout base or current model settings were established for this phase; usage/cost are unallocated and unknown."
    evidence: "Public-safe cost-evidence-audit receipt SHA-256 8062d64ebb8306dc378d5f0e00fd643ba903d814f49fc1ffc5b2ba4641882d18; audited 2026-11-10 11:46:26 UTC. The backfill method below states the source and attribution limits."
    correctionOf: "none"
    usage:
      state: "unknown"
      input: "unknown"
      output: "unknown"
      scope: "This named phase only; no allocation from an account or shared session counter."
      receipt: "No phase-scoped input/output usage receipt was retained for this record; counts cannot be inferred from the patch or model name."
    cost:
      basis: "unknown"
      amount: "unknown"
      currency: "unknown"
      scope: "This named phase only; provider/subscription allocation, human and infrastructure costs are not established."
      method: "No task-specific bill or evidenced subscription/shared-session allocation is available. Missing cost is unknown, not zero; no rate is assumed."
      receipt: "Retained contribution receipts establish work and selected settings, not a phase-specific bill or monetary allocation."
  - id: "issue-111-codex-design-review"
    issue: 111
    date: "2026-11-10"
    role: "independent-review"
    tool: "Separate Codex design reviewer"
    model: "unknown"
    checkpoint: "unknown"
    effort: "unknown"
    base: "169267ee1cc5aff4f39d4984d7b1368725eb53fd"
    outcome: "completed"
    scope: "Review the recording design, accounting boundaries, validation scope and publication safeguards."
    environment: "Codex workspace continuation under repository rules; full task environment and provider checkpoint are not established by this receipt."
    result: "Recommended a bounded structured-record slice with reviewer-owned completeness. This is design advice, not approval of the implementation or final head."
    limitations: "No fresh runtime-settings or phase billing/usage receipt was obtained. No build, provider call, code change or final-head approval belongs to this design review."
    evidence: "Independent design-review receipt SHA-256 d60a4236287d60b318cc850515c3e7bbaa0ed5e8f2c610685ce6a379f92dac37; source inspected at the recorded base on 2026-11-10."
    correctionOf: "none"
    usage:
      state: "unknown"
      input: "unknown"
      output: "unknown"
      scope: "This named phase only; no allocation from an account or shared session counter."
      receipt: "No phase-scoped input/output usage receipt was retained for this record; counts cannot be inferred from the patch or model name."
    cost:
      basis: "unknown"
      amount: "unknown"
      currency: "unknown"
      scope: "This named phase only; provider/subscription allocation, human and infrastructure costs are not established."
      method: "No task-specific bill or evidenced subscription/shared-session allocation is available. Missing cost is unknown, not zero; no rate is assumed."
      receipt: "Retained contribution receipts establish work and selected settings, not a phase-specific bill or monetary allocation."
  - id: "issue-111-codex-documentation"
    issue: 111
    date: "2026-11-10"
    role: "implementation"
    tool: "Separate Codex documentation agent"
    model: "unknown"
    checkpoint: "unknown"
    effort: "unknown"
    base: "e67ab7a4ffa67803e9601ed5b2671248876cd847"
    outcome: "completed"
    scope: "Author contribution protocol, public-safe run backfill, rule/template updates and the documentation decision for issue 111."
    environment: "Codex workspace continuation under repository rules; full task environment and provider checkpoint are not established by this receipt."
    result: "Completed the bounded authored-documentation handoff; coordinator owns parser/rendering, builds, generated files, demo and final review."
    limitations: "Completion applies only to this editorial handoff. No local build or final-head approval is claimed. Fresh model/effort/checkpoint, per-phase usage and subscription allocation are unknown."
    evidence: "Current issue 111 documentation assignment and authored diff; historical facts derived from the public-safe backfill receipt and preserved contribution history."
    correctionOf: "none"
    usage:
      state: "unknown"
      input: "unknown"
      output: "unknown"
      scope: "This named phase only; no allocation from an account or shared session counter."
      receipt: "No phase-scoped input/output usage receipt was retained for this record; counts cannot be inferred from the patch or model name."
    cost:
      basis: "unknown"
      amount: "unknown"
      currency: "unknown"
      scope: "This named phase only; provider/subscription allocation, human and infrastructure costs are not established."
      method: "No task-specific bill or evidenced subscription/shared-session allocation is available. Missing cost is unknown, not zero; no rate is assumed."
      receipt: "Retained contribution receipts establish work and selected settings, not a phase-specific bill or monetary allocation."
  - id: "issue-111-codex-draft-review"
    issue: 111
    date: "2026-11-10"
    role: "independent-review"
    tool: "Separate Codex reviewer"
    model: "unknown"
    checkpoint: "unknown"
    effort: "unknown"
    base: "e67ab7a4ffa67803e9601ed5b2671248876cd847"
    outcome: "completed"
    scope: "Review the parser, accounting states, corrections and publication before the first commit."
    environment: "Codex workspace continuation under repository rules; full task environment and provider checkpoint are not established by this receipt."
    result: "Two findings: known numeric values accepted missing-marker receipts/methods; correction labels were hidden inside disclosures. Both required regression tests. This is draft advice, not exact-head approval."
    limitations: "This receipt covers the named bounded phase, not complete issue delivery or final-head approval. Fresh model, effort, checkpoint, per-phase usage and subscription allocation are unknown."
    evidence: "Issue 111 authored changes and retained red/green validation receipts; the pull request will carry the sanitized results and exact-head decisions."
    correctionOf: "none"
    usage:
      state: "unknown"
      input: "unknown"
      output: "unknown"
      scope: "This named phase only; no allocation from an account or shared session counter."
      receipt: "No phase-scoped input/output usage receipt was retained for this record; counts cannot be inferred from the patch or model name."
    cost:
      basis: "unknown"
      amount: "unknown"
      currency: "unknown"
      scope: "This named phase only; provider/subscription allocation, human and infrastructure costs are not established."
      method: "No task-specific bill or evidenced subscription/shared-session allocation is available. Missing cost is unknown, not zero; no rate is assumed."
      receipt: "Retained contribution receipts establish work and selected settings, not a phase-specific bill or monetary allocation."
  - id: "issue-111-codex-remediation"
    issue: 111
    date: "2026-11-10"
    role: "implementation"
    tool: "Codex coordinator"
    model: "unknown"
    checkpoint: "unknown"
    effort: "unknown"
    base: "e67ab7a4ffa67803e9601ed5b2671248876cd847"
    outcome: "completed"
    scope: "Correct both independent draft-review findings while preserving accounting records."
    environment: "Codex workspace continuation under repository rules; full task environment and provider checkpoint are not established by this receipt."
    result: "Two new regression tests failed before fixes. Known measurements now reject missing-marker receipts and methods; closed summaries label corrections and amended originals with reciprocal links. The focused suite then passed eight tests."
    limitations: "This receipt covers the named bounded phase, not complete issue delivery or final-head approval. Fresh model, effort, checkpoint, per-phase usage and subscription allocation are unknown."
    evidence: "Issue 111 authored changes and retained red/green validation receipts; the pull request will carry the sanitized results and exact-head decisions."
    correctionOf: "none"
    usage:
      state: "unknown"
      input: "unknown"
      output: "unknown"
      scope: "This named phase only; no allocation from an account or shared session counter."
      receipt: "No phase-scoped input/output usage receipt was retained for this record; counts cannot be inferred from the patch or model name."
    cost:
      basis: "unknown"
      amount: "unknown"
      currency: "unknown"
      scope: "This named phase only; provider/subscription allocation, human and infrastructure costs are not established."
      method: "No task-specific bill or evidenced subscription/shared-session allocation is available. Missing cost is unknown, not zero; no rate is assumed."
      receipt: "Retained contribution receipts establish work and selected settings, not a phase-specific bill or monetary allocation."
  - id: "issue-111-codex-validation"
    issue: 111
    date: "2026-11-10"
    role: "validation"
    tool: "Codex coordinator"
    model: "unknown"
    checkpoint: "unknown"
    effort: "unknown"
    base: "e67ab7a4ffa67803e9601ed5b2671248876cd847"
    outcome: "completed"
    scope: "Run ordinary Java tests and regenerate documentation from the structured source."
    environment: "Codex workspace continuation under repository rules; full task environment and provider checkpoint are not established by this receipt."
    result: "Offline generation passed with 22 generated files and 4491 ordinary tests, zero failures/errors/skips. Local database and mutation tasks were not run; full checks, rendered inspection and final-head review remain pending in the PR receipt."
    limitations: "This receipt covers the named bounded phase, not complete issue delivery or final-head approval. Fresh model, effort, checkpoint, per-phase usage and subscription allocation are unknown."
    evidence: "Issue 111 authored changes and retained red/green validation receipts; the pull request will carry the sanitized results and exact-head decisions."
    correctionOf: "none"
    usage:
      state: "unknown"
      input: "unknown"
      output: "unknown"
      scope: "This named phase only; no allocation from an account or shared session counter."
      receipt: "No phase-scoped input/output usage receipt was retained for this record; counts cannot be inferred from the patch or model name."
    cost:
      basis: "unknown"
      amount: "unknown"
      currency: "unknown"
      scope: "This named phase only; provider/subscription allocation, human and infrastructure costs are not established."
      method: "No task-specific bill or evidenced subscription/shared-session allocation is available. Missing cost is unknown, not zero; no rate is assumed."
      receipt: "Retained contribution receipts establish work and selected settings, not a phase-specific bill or monetary allocation."
---

# Model runs and cost evidence

[Contribution protocol](../contributions.md) · [Preserved issue history](history.md) · [Decision and limits](../adr/0020-model-run-evidence-and-cost-boundaries.md)

This is a partial, receipt-backed record of model-assisted work. The build validates the structure of submitted
records; reviewers check missing changes, history preservation and receipt credibility. No complete delivery
total, model ranking or provider-billing claim follows from a valid record. Amounts retain their cost basis and scope.

## Backfill method

The historical work occurred on **2026-11-09**. The read-only accounting audit and this public backfill were
recorded on **2026-11-10**. The audit examined 14 rounds from four named jobs and preserved unsuccessful work:

| Job receipt | Rounds | Later worker-job decision | Job receipt SHA-256 |
|---|---:|---|---|
| issue-69-build | 2 | accepted | `aeff36041f865c86900e3d6aeb806e401f81f631cb8f9e636bfe5abc9a41b2a1` |
| issue-37-plan | 2 | plan-reviewed | `a1d9d9b48bff3c3d8dfffedd7e01d6d89876414c4bb5dd33e6e0243b674a2011` |
| issue-37-build | 8 | rejected | `e537b1847032142f0879fef6d9b9155b1fc94ffa118efdb50440babeeb9af6d9` |
| issue-101-build | 2 | rejected | `8f9fb2c919a8f4b2013f9dffb531aa8304473c72b377dd67e3ede2fce9d606b3` |

A round stopped by a controller limit is labelled partial; a nonzero exit without a recorded limit is failed.
A natural finish is completed only as a run. Later job decisions remain in each result: neither a completed round
nor an accepted worker patch establishes final issue delivery, independent final-head review or owner approval.
Historical Codex phases come only from the linked contribution history; their unknown phase costs are preserved.

### Accounting boundary

Per-round usage is the retained provider-reported prompt/completion count in a corroborated ledger window.
The ledger has timestamps but no job/request/session IDs. Windows reconstructed from recorded finish times and
elapsed durations do not overlap; every selected window matches its controller request count and accounted delta.
All 24 known job-receipt windows were checked for overlap. This supports attribution to the named rounds but is
not invoice-grade proof of exclusive ownership. Shared earlier ledger work is not allocated to these issues.

The rate-derived cost amount includes only usage-settled charges in that window. Unresolved reservations stay in
limitations and are excluded from the amount; their actual charge and tokens are unknown. No missing Codex,
reviewer, human or infrastructure cost is treated as zero. Subscription cost is unallocated unless a receipt
establishes an allocation. This page computes no totals across phases or currencies.

### Rates and source integrity

Rates recorded by the gateway on **2026-11-09** were USD 0.30 per million prompt tokens and USD 1.20 per million
completion tokens. For each settled chat, the controller rounds up `(3 * prompt + 12 * completion) / 10` to integer
micro-USD before summing. Cache discounts are deliberately ignored. A billable web result adds USD 0.000750;
nonbillable web results settle to zero. These are historical accounting assumptions, not reverified current prices
or provider invoices. Every usage-settled chat charge in the audited ledger recomputes from this formula.

- Gateway rate-source SHA-256: `c623e0ec8812824d52fc684d230812a589ca36ac50bb0e09ffb3a47549879a76`.
- Web-rate-source SHA-256: `d3e884eca8dfe15cbabfa6cdd4f9ba02c2ed5c5936f3580b164ac04eb1f033d2`.
- Audited ledger SHA-256: `a44f98380dccc97f5e19a04b9f1df2fe85ad386956aa73ba17453e8bc7c05ac6`.
- Public-safe audit receipt SHA-256: `8062d64ebb8306dc378d5f0e00fd643ba903d814f49fc1ffc5b2ba4641882d18`.

Each round names the hash of its job/event receipts and the one-based ledger window. Raw prompts, event logs,
responses, credentials, private names and machine paths are not published. These hashes identify retained
receipts; the publishing build does not authenticate their contents.

All retained model-step events report a cost field of zero. That field is not used as a billing measurement. Some
settled gateway calls have no retained step-finish event; ledger token counts include the settled calls rather than
only visible model steps. Cached input and reasoning subdivisions are not added to provider totals again.
A configured model alias does not establish a provider checkpoint or reasoning effort.

## Current coverage and next records

The DeepSeek request allowance is exhausted; a proposed increase remains unapproved. Current issue111 work is
performed with Codex, whose fresh per-phase settings, usage and allocated cost are unknown where no receipt exists.
This feature performs no provider calls, billing fetches, controller restart or allowance change.

Append planning, implementation, remediation, integration, validation, independent review and actual human
decision records as the work occurs. Preserve failed, rejected, blocked and abandoned attempts. Corrections name
an earlier same-issue ID and leave the earlier assertion visible. Final-head review and approval receipts stay in
durable PR comments; a later reviewed backfill may append them without changing an already approved head.
