# Combat Content Rules

- Target OSRS-accurate behavior using official OpenRune APIs.
- Before editing, inspect comparable official content and provide a mechanic-to-API mapping.
- Use the shared combat pipeline for accuracy, queued hits, freezes, projectiles, and prayers.
- Do not add private combat hooks, direct engine overrides, or fork-only APIs.
- If an official API cannot implement verified OSRS behavior, stop and explain the gap before changing engine code.
- Add focused tests and cite the evidence used for timing and mechanics.

## Required Workflow

Before implementing combat content, first produce a plan that maps every mechanic to an official OpenRune API or established engine pipeline. Flag any mechanic that needs a new engine API, explain the verified OSRS behavior and supporting evidence, and wait for approval before editing. Implement only the approved mapping, avoid private hooks and engine overrides, run focused tests, and summarize any assumptions.

## Authorized Deployment Logout

- The user authorizes logging their account out when a tested, backed-up project deployment requires a controlled restart. Do not pause solely because their client is connected or repeatedly ask them to log out.
- Use the native logout/save flow and verify saves have completed before shutting down services. This is not permission to hard-kill the JVM, discard unsaved progress, bypass persistence failures, or disconnect unrelated users without authority.
- Preserve the canonical workspace, revision-240 SERVER/LIVE caches and `.data/postgres` authority. Verify the build marker, doctor and persistence after the required two controlled restarts.
