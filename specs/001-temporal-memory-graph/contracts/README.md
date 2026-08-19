# Interface contracts

## `surface-inventory.json` — generated, never transcribed

Produced by the source project's surface probe, which AST-parses the system being replaced.
Regenerate rather than edit:

```bash
cd graphiti-port/probes
../.venv/Scripts/python.exe probe_21_surface_inventory.py --json > \
  ../../graphiti-akka/specs/001-temporal-memory-graph/contracts/surface-inventory.json
```

It carries **11 request/response routes, 13 agent tools, 9 wire types**, plus the reachability
map and the one library-only capability.

The generation rule is not fussiness. A hand-written version of this map was wrong about two of
three capabilities, and a probe that asserts a hand-written answer has automated the assertion
rather than the verification. The diff between two generations *is* the surface change report.

## What the port must match

Everything a caller can observe, byte for byte:

- route paths, methods and status codes — including the non-obvious ones: episode ingest
  answers **202 Accepted**, entity creation answers **201 Created**
- path and query parameter names
- JSON field names, nesting, and **enum literal values**
- **defaults**, which are contract: `max_facts=10`, `max_episodes=10`, `max_nodes=10`,
  `name=''`, `source='text'`
- datetime encoding — UTC ISO-8601
- agent-tool names, parameter names, **parameter order**, defaults and return shapes

## What may differ

Anything a caller cannot observe: packages, classes, methods, fields, file layout, component
names, deployment identifiers. The test is mechanical — **if a caller can see the identifier,
it is frozen; if only a maintainer can, it is free.**

## Two traps this inventory encodes

**Ingest is asynchronous on both surfaces.** Each accepts the episode, queues it, and returns
immediately. Exactly one field — the reference time — is validated before that acknowledgement;
every other failure happens afterwards with no channel back to the caller. A synchronous
implementation is not equivalent however fast it runs.

**One reranker name does not mean what it says.** A configuration value named for a
well-known diversity algorithm implements a one-shot variant of it, not the greedy form in the
literature. The name is frozen by the wire contract; implementing the algorithm the name
suggests produces different rankings under the same configuration.

## Not portable

The source also exposes an in-process library API. It cannot be reproduced by a service in
another runtime, and callers of it are out of scope. One capability — bulk ingest — is reachable
*only* that way and therefore has no external surface to match. It is deliberately not built,
and recorded so that a future bulk endpoint does not silently reuse the wrong extraction path.
