# Correction flags

Every behaviour here looks like a defect and is reproduced on purpose (D-006). Each is listed
with the flag that would correct it, and **every flag defaults to the reproduced behaviour**
(D-007) — so a default build stays comparable to the source system, and a correction can be
measured rather than asserted.

**None of these flags is implemented yet.** They are Phase 2. Listing them now is not
documentation of features that exist; it is a record of which behaviours are deliberate, so that
a later reader does not "fix" one and quietly break equivalence, and so the work of correcting
them is scoped rather than discovered.

| # | Reproduced behaviour | Why it looks wrong | Correction, when flagged | Specified in |
|---|---|---|---|---|
| C-1 | Two contradicting facts starting at the **same instant** both stay open | A contradiction is a contradiction whether it arrives a second later or at the same moment | Treat an equal start as a contradiction, closing the earlier fact at that instant | SPEC-001 §3 |
| C-2 | A fact arriving **out of order** closes nothing in either direction | The later-dated fact is still the more current claim, whichever order they arrived in | Order the decision by validity rather than arrival | SPEC-001 §3 |
| C-3 | An over-long entity attribute is **dropped**, not shortened | Losing a field entirely is worse than losing its tail | Truncate to the limit instead of discarding | SPEC-006 §8 |
| C-4 | The length limit **does not apply at all** to a field the caller's schema marks required | A caller's schema choice silently disables a system safety limit, with no signal back | Apply the limit to every field, and report rather than drop | SPEC-006 §8 |
| C-5 | An unparseable date leaves a fact **inert** — it can neither close another nor be closed | A fact with an unreadable date is still a fact | Surface the parse failure instead of degrading to absent | SPEC-001 §3.2 |
| C-6 | A step that fails **fails the episode**; the runtime's durable retry is switched off | The platform offers recovery for free and it is deliberately suppressed | Enable step recovery, accepting that the port then survives failures the source cannot | RENDER-001 OD-19 |
| C-7 | Ingest acknowledges, then everything else fails **silently in the background** | Success and total failure look identical on the wire | Report post-acknowledgement failures on a status route | SPEC-007 §3.0 |
| C-8 | The extraction guardrails widen an entity's attribution to **every** episode when no index is usable | Unusable input producing wider attribution is the opposite of what "invalid" suggests | Attribute to the primary episode only | SPEC-003 |

## Two that are not on this list, and why

**The acknowledgement latency miss** (see `budget-report.md`) is not a correction flag. It is the
port being *stricter* than the source — it acknowledges after durably recording, where the source
acknowledges after an in-memory enqueue. Reaching the 50 ms budget would mean adopting the
source's loss window deliberately. That is a decision about what "accepted" promises, and it
belongs with these flags only if someone decides the budget outranks the guarantee.

**The mutation in the invalidation decision** (question-log row 45) is not flagged either. The
source's decision function closes its input in place; the port separates predicate from mutation.
The two are equivalent given the pipeline evaluates each candidate once per incoming fact, which
both do, so there is nothing to correct — only something to keep true.
