<!-- <nav> -->
- [Akka](index.html)
- [About Akka](about-akka.html)
- [Orchestration](akka-orchestration.html)

<!-- </nav> -->

# Orchestration

Akka Orchestration guides and controls long-running processes so that agents and services reliably reach their goals despite failures, delays, or runs that stretch from seconds to months. It gives you a durable execution engine for coordinating the steps of a business process, a multi-step agent plan, or a distributed transaction. You never write the retry loops, state machines, or recovery logic yourself.

Orchestration is the coordination layer of the platform. Where an individual agent decides *what* to do next, orchestration governs *how* that work is sequenced, retried, compensated, and resumed. It is exposed through the Workflow component in the SDK.

![Durable orchestration coordinating multi-step agent work](_images/capabilities/agentic-workflows.png)

## <a href="about:blank#_why_orchestration_matters_for_agentic_systems"></a> Why orchestration matters for agentic systems

Multi-agent and multi-step systems fail in ways that simple request/response services do not. A plan may call several tools, wait on a human approval, invoke an external API that times out, and then need to undo earlier work when a later step fails. Coordinating this correctly by hand means reinventing durable state, idempotency, retries, and rollback for every workflow.

Akka Orchestration provides these guarantees as first-class building blocks:

- **Always completes** — Workflows persist through crashes, restarts, and downtime. Once started, a workflow runs to completion or to a defined failure outcome; work is never silently abandoned.
- **Exactly-once actions** — Each step executes once and only once, preventing duplicate messages, repeated API calls, and corrupted downstream state caused by redundant writes.
- **Instant recovery** — After a failure, a workflow resumes from its last durable point, with no manual intervention and no lost progress.
- **Reactive coordination** — Workflows respond to events and data as they arrive, staying responsive under load while adapting to changing conditions.
- **Safe evolution** — Workflow logic and schema can change while instances are still in flight, so you can ship updates to long-running processes without disrupting them.

## <a href="about:blank#_how_the_orchestration_engine_works"></a> How the orchestration engine works

### <a href="about:blank#_durable_exactly_once_execution"></a> Durable, exactly-once execution

The workflow engine executes each step sequentially with strong consistency. State is made durable through event sourcing with periodic snapshots, so the exact position of every in-flight workflow survives process restarts and node failures. When a workflow resumes, it continues from where it left off rather than re-running completed steps.

### <a href="about:blank#_external_calls_as_workflow_steps"></a> External calls as workflow steps

Calls to external APIs, models, and services are modeled as native workflow steps. The engine applies built-in retry logic, flow control, and error handling to each call, so transient failures in downstream systems are absorbed by the orchestration layer instead of surfacing as workflow failures.

![External API calls modeled as durable workflow steps](_images/capabilities/integrate-with-external-apis.png)

### <a href="about:blank#_error_handling_and_compensation"></a> Error handling and compensation

Each step can define timeouts, retry policies, and recovery strategies. When a later step fails, *compensation handlers* reverse the effects of earlier completed steps. This is the saga pattern, and it lets a partially executed workflow unwind to a consistent state. This is what makes it safe to orchestrate distributed transactions across systems that have no shared transaction boundary.

![Compensation handlers unwind completed steps when a later step fails](_images/capabilities/error-handling-compensation.png)

### <a href="about:blank#_multi_region_replication"></a> Multi-region replication

Workflows can run in active-active deployments across cloud regions. Execution is distributed transparently, with failover between regions that preserves progress and prevents duplicate side effects, so a regional outage does not restart or corrupt in-flight work.

![Workflows replicated active-active across regions](_images/capabilities/multi-region-replication-map-alt1.png)

## <a href="about:blank#_orchestration_patterns"></a> Orchestration patterns

Akka Orchestration supports the range of coordination shapes that agentic and business processes require:

- **Sequential plans** — an ordered series of steps where each depends on the result of the previous one.
- **Branching decisions** — conditional paths chosen at runtime based on step results, model output, or external data.
- **Human-in-the-loop checkpoints** — pauses where a workflow waits, potentially for hours or days, on human approval or input before continuing, without holding resources.
- **Dynamic agent goals** — complex plans where the sequence of steps is determined as the workflow runs, letting an agent adapt its approach while the engine keeps every step durable and recoverable.

## <a href="about:blank#_where_orchestration_fits_in_the_platform"></a> Where orchestration fits in the platform

Orchestration works together with the other components: [Agents](akka-agents.html) provide the reasoning for each step, [Memory](akka-memory.html) provides durable state that steps read from and write to, and [Streaming](akka-streaming.html) provides the flow of events that workflows react to. Orchestration ties these together, turning individual agent actions into durable, long-running processes.

## <a href="about:blank#_see_also"></a> See also

- [Workflows component reference](sdk/workflows.html)
- [AI agents](concepts/ai-agents.html)

<!-- <footer> -->
<!-- <nav> -->
[Agents](akka-agents.html) [Memory](akka-memory.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->