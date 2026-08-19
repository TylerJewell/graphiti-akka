<!-- <nav> -->
- [Akka](index.html)
- [About Akka](about-akka.html)
- [What is Akka?](what-is-akka.html)

<!-- </nav> -->

# What is Akka?

Akka is an agentic AI platform for enterprises: a full-stack system for building, running, and governing production-grade agentic AI. It brings developer experience, runtime, and governance together in one integrated system. You describe what you need. Akka generates a production-ready system, and that system runs as a durable, event-driven, distributed application.

![The Akka agentic platform — build](_images/capabilities/akka-platform-intro.png)

## <a href="about:blank#_the_platform_layers"></a> The platform layers

The graphic above shows how Akka is organized into four layers:

- **Components** — the building blocks you compose into a system: agents, workflows, entities, endpoints, views, and consumers.
- **Runtime** — the engine that handles the hard distributed-systems problems for you: clustering, resilience, zero-trust networking, data sharding, and traffic steering. It keeps state in memory, persists every change, routes messages, and recovers from failure.
- **Cloud Stack** — the infrastructure behind [Automated Operations](akka-automated-operations.html): active-active HA/DR, runtime patching, rolling updates, elastic scaling, and shared compute.
- **Harnesses** — the tooling to build, test, and evaluate agentic AI: the dev sandbox, operations console, TestKit, EvalKit, [spec-driven development](sdk/spec-driven-development.html), infosec controls, and cost management.

## <a href="about:blank#_the_capabilities"></a> The capabilities

Akka brings the building blocks of agentic systems together under one model:

- [Agents](akka-agents.html) — model-backed components with memory, tools, and multi-agent collaboration.
- [Orchestration](akka-orchestration.html) — durable, multi-step coordination of agents and workflows.
- [Memory](akka-memory.html) — durable, in-memory, sharded state for agents and entities.
- [Streaming](akka-streaming.html) — real-time stream processing for event-driven and AI workloads.
- [Automated Operations](akka-automated-operations.html) — run services with elastic scaling, resilience, and no-downtime updates.
For how these pieces fit together under the hood, see [Akka Internals](akka-internals.html).

## <a href="about:blank#_next_steps"></a> Next steps

- [Getting started](getting-started/index.html) — build your first Akka service.
- [Spec-driven development](sdk/spec-driven-development.html) — the primary way to build with Akka.

<!-- <footer> -->
<!-- <nav> -->
[About Akka](about-akka.html) [Agents](akka-agents.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->