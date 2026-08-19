<!-- <nav> -->
- [Akka](index.html)
- [About Akka](about-akka.html)
- [Memory](akka-memory.html)

<!-- </nav> -->

# Memory

Akka Memory provides durable, in-memory, sharded state for AI agents. It is the foundation for context engineering: shaping what an agent can see so it performs complex, multi-step tasks reliably.

Memory is intrinsic to Akka agents. State is captured and persisted as agents run, so you do not have to hand-roll persistence, wire up a cache, or reconcile a separate data store. The runtime keeps working state in memory for fast access and durably records every change so nothing is lost across restarts, rescheduling, or hardware failure.

![Durable](_images/capabilities/transparent-memory-alt.png)

## <a href="about:blank#_why_agents_need_memory"></a> Why agents need memory

An agent is only as good as the context it can reason over. Without memory, every interaction starts from zero: the agent forgets what the user just said, cannot build on prior work, and has no access to knowledge learned in earlier sessions.

Akka Memory addresses two distinct needs:

Session memory (short-term) Maintains situational awareness and conversation history across the turns of an interaction. This is the working context an agent draws on to stay coherent within a task or a conversation.

Durable memory (long-term) Persists semantic knowledge, skills, and retrieved data across users, sessions, agents, and systems. This is what lets an agent accumulate knowledge over time and share it beyond a single conversation.

## <a href="about:blank#_durable_in_memory_state"></a> Durable in-memory state

Akka Memory keeps agent state resident in memory so reads and writes do not pay the cost of a round trip to an external database on every access. At the same time, state is durable: it survives process restarts and node failures.

There is no separate cache to provision, invalidate, or keep consistent. The in-memory tier and the durable record are the same system, so you never have to reason about a cache and a database drifting out of sync.

### <a href="about:blank#_event_sourcing"></a> Event sourcing

State is tracked as a sequential series of events and persisted transparently to an event journal. Rather than overwriting a record in place, each change is appended as an event, giving you:

- A complete, ordered history of how state reached its current value.
- Recovery by replaying events, with periodic snapshots to bound replay time.
- Transparent restoration after a network partition or hardware failure — the agent’s memory is rebuilt from the journal.
Because the journal is the source of truth, the current in-memory value is always a projection you can rebuild deterministically.

![State tracked as an appended event journal with periodic snapshots](_images/capabilities/memory-event-journal-alt.png)

### <a href="about:blank#_sharding_and_scale"></a> Sharding and scale

Agent state is sharded across in-memory, durable nodes. Each piece of state lives on exactly one node at a time, which keeps access consistent and avoids coordination overhead on every write.

As the runtime scales nodes up or down, state rebalances automatically across the cluster. You do not manually partition data or redirect clients; the runtime moves shards and routes requests to wherever the state currently lives.

This combination of in-memory placement and single-owner sharding is what delivers sub-10ms writes without a bolt-on caching layer.

![Agent state sharded across nodes with single ownership](_images/capabilities/sharded-data.png)

![Shards rebalance automatically as the cluster scales](_images/capabilities/data-rebalancing-1.png)

## <a href="about:blank#_what_memory_holds"></a> What memory holds

Akka Memory is not limited to raw conversation transcripts. The same durable, sharded foundation backs the broader context an agent needs:

- **Conversation and session history** — the recent turns that keep an agent coherent within a task.
- **Semantic knowledge** — facts, skills, and retrieved data carried across sessions and agents.
- **Retrieved knowledge** — data pulled from vector databases, transactional databases, message brokers, or APIs and folded into context.
- **Prompt management** — prompts with versioning and rollback, so you can evolve and revert instructions safely.
- **Tool schemas** — the functions and MCP server definitions an agent can call.
- **Workflow state** — a persistent call stack that guides multi-step work and survives interruptions.

## <a href="about:blank#_context_window_optimization"></a> Context window optimization

Model context windows are finite, and stuffing them degrades both cost and quality. Akka Memory supports [compaction](sdk/agents/memory.html#_compaction) to keep the working context focused, summarizing or pruning older material so the agent carries forward what matters without exceeding the window.

Because memory is durable, compaction does not lose information: detail that is compacted out of the live context remains available in the journal and long-term store for retrieval when it is needed again.

## <a href="about:blank#_reacting_to_change_in_real_time"></a> Reacting to change in real time

Agents can subscribe to any event or change in memory using [Akka Streaming](akka-streaming.html). Rather than polling for updates, an agent reacts as state changes, enabling adaptive behavior in response to real-time changes in its environment.

## <a href="about:blank#_how_it_fits_together"></a> How it fits together

Akka Memory works alongside the rest of the Akka programming model. In practice you express durable agent state through entities:

- [Event Sourced Entities](sdk/event-sourced-entities.html) persist state as a journal of events — the event-sourcing model described above.
- [Key Value Entities](sdk/key-value-entities.html) persist the latest value directly when you do not need a full event history.
Both are sharded and kept in memory by the runtime, so the durability, scaling, and low-latency access characteristics apply whichever model you choose.

To read the latest value across many entities, [Views](sdk/views.html) project entity state into a read model you can query, rather than serving as primary storage.

## <a href="about:blank#_see_also"></a> See also

- [Event Sourced Entities](sdk/event-sourced-entities.html)
- [Key Value Entities](sdk/key-value-entities.html)

<!-- <footer> -->
<!-- <nav> -->
[Orchestration](akka-orchestration.html) [Streaming](akka-streaming.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->