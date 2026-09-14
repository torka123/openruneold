# Combat Content Rules

- Target OSRS-accurate behavior using official OpenRune APIs.
- Before editing, inspect comparable official content and provide a mechanic-to-API mapping.
- Use the shared combat pipeline for accuracy, queued hits, freezes, projectiles, and prayers.
- Do not add private combat hooks, direct engine overrides, or fork-only APIs.
- If an official API cannot implement verified OSRS behavior, stop and explain the gap before changing engine code.
- Add focused tests and cite the evidence used for timing and mechanics.

## Required Workflow

Before implementing combat content, first produce a plan that maps every mechanic to an official OpenRune API or established engine pipeline. Flag any mechanic that needs a new engine API, explain the verified OSRS behavior and supporting evidence, and wait for approval before editing. Implement only the approved mapping, avoid private hooks and engine overrides, run focused tests, and summarize any assumptions.

## Execution Discipline and Token Use

- Resume from the latest handoff, boss ledger, source and diffs. Reuse verified findings, mappings, approvals and valid test results; revisit them only when new evidence, changed code or project requirements justify it.
- Default to existing OpenRune systems. The user already approves a missing shared boss API/DSL route only after verifying that no suitable route exists. Do not request that approval again or build a parallel private system.
- The user's working premise is that almost all standard functionality is already present and correctly configured in this OpenRune setup, including drops, rewards, pickup, account saving and lifecycle handling. Start by locating the established route and a comparable caller; trace the relevant integration and reuse it. Expand investigation only for a concrete defect, failed acceptance check or demonstrated missing capability. Routine drop routing must not turn into hours of speculative persistence research or framework redesign; the prior eight-hour investigation is explicitly unacceptable as a working pattern.
- Work in small, complete increments: identify the mechanic, native route and observable acceptance criteria; implement the approved mapping, verify it, update the existing ledger, then continue the next approved increment.
- Bound research to one unresolved implementation question. Once evidence supports an approved mapping, implement it. Do not restart broad audits or expand historical research without a concrete decision it will resolve.
- If evidence remains unavailable, record the exact gap, leave dependent behavior unchanged and continue independent approved work. Do not approximate mechanics or let one blocked mechanic stall the migration.
- Do not invent stronger persistence guarantees or policy decisions as prerequisites for ordinary native migration. Preserve explicitly approved recovery requirements, save protections and deployment holds; track unfinished stronger guarantees separately.
- Run focused behavioral tests and required shared/regression checks. Reuse unaffected passing results. Repeat expensive checks only when changes, failures or requirements warrant them; keep shared edits, integration and expensive builds under one owner.
- Measure progress by implemented and verified behavior. Research volume, commits, lines changed and passing test totals are supporting evidence, not completion. Clearly separate source changes, behavior verified, build passed and deployed status; label rough percentages and their scope.
- Report limited progress or investigation drift promptly and narrow the work before spending more time or tokens. Keep updates compact, use the existing ledger, and avoid duplicate audits, repeated explanations and documentation churn. Do not wait for another "continue" to advance authorized work.

## Authorized Deployment Logout

- The user authorizes logging their account out when a tested, backed-up project deployment requires a controlled restart. Do not pause solely because their client is connected or repeatedly ask them to log out.
- Use the native logout/save flow and verify saves have completed before shutting down services. This is not permission to hard-kill the JVM, discard unsaved progress, bypass persistence failures, or disconnect unrelated users without authority.
- Preserve the canonical workspace, revision-240 SERVER/LIVE caches and `.data/postgres` authority. Verify the build marker, doctor and persistence after the required two controlled restarts.
