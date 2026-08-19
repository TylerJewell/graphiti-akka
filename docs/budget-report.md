# Budget report

Measured against a locally deployed build on 2026-08-19, with the store running alongside it.
Every figure below is an observation. Where a budget is missed it is reported as a miss and
explained; RENDER-001 §5 is explicit that a missed budget is a finding, not a number to restate.

## Results

| Budget | Target | Measured | |
|---|---|---|---|
| Cold start | ≤ 5 s | **3.55 s** | met |
| Resident memory | ≤ 600 MB | **425 MB** | met |
| Store processes | 1 | **1** | met |
| Read latency, p95 | < 500 ms excluding model time | **24.6 ms** | met |
| Acknowledgement latency, p95 | < 50 ms | **116.4 ms** | **missed** |

## How each was measured

**Cold start** is from the runtime's first log line to the line reporting it is serving —
`07:28:42.073` to `07:28:45.626`. Build time is excluded deliberately: it is not what a restarting
service pays.

**Resident memory** is the working set of the JVM hosting the service, sampled once it was
serving. It is an over-estimate, because this process also hosts the build tool that launched it;
the service alone is smaller than the number that passed.

**Latency** is 50 sequential requests each, over loopback, reported at p50 and p95. Two baselines
were taken alongside so the numbers can be read: the health route measures at p50 7.1 ms / p95
8.9 ms, which is transport and nothing else, and the rejection path — a request that fails
validation and starts no work — measures p50 7.5 ms / p95 23.7 ms.

**Store processes** is a count of running store processes, which is 1. That was the point of
putting graph, vector and full text in one store rather than three.

## The miss

Acknowledgement is **116.4 ms at p95 against a 50 ms budget** — between two and three times over.

The cause is visible in the baselines. Rejecting a request costs 23.7 ms at p95; accepting one
costs 116.4 ms. The difference is not validation, serialisation or transport, all of which both
paths pay. It is that accepting an episode **durably creates the orchestration** before replying,
and that write is a round trip the budget did not account for.

This is a real behavioural difference rather than a slow implementation. The source system
acknowledges after putting a closure on an in-process queue: fast, and lost entirely if the
process dies before the work runs. The port acknowledges after the work is recorded, so an
acknowledged episode survives a restart. The port's "accepted" is a stronger claim than the
source's, and it costs about 90 ms to make.

Two things follow, and neither is "adjust the target":

- **The budget was set against the wrong precedent.** It came from a prior port whose
  acknowledgement enqueued rather than persisted. A 50 ms target is reachable here only by
  acknowledging before recording, which would reintroduce the loss the durable path exists to
  avoid.
- **If the target is the requirement**, the way to meet it is to acknowledge before the durable
  write and accept the same loss window the source has. That is a decision about what "accepted"
  promises, not an optimisation, and it belongs with the correction flags of D-007 rather than in
  a performance pass.

Recorded for the decision register. Nothing has been changed to make the number look better.

## What these numbers do not cover

End-to-end ingest, which is dominated by model latency and is not comparable without frozen
model responses. The read latency above excludes model time by construction — it is a retrieval
against the projection, which is what SPEC-004 SC-006 bounds.
