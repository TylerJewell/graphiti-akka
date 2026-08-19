<!-- <nav> -->
- [Akka](../../index.html)
- [Getting Started](../index.html)
- [Multi-agent tutorial](index.html)

<!-- </nav> -->

# Build an AI multi-agent planner

|  | **New to Akka? Start here:**

Use the [Spec-first hello agent](../spec-your-first-agent.html) guide to use your AI assistant for implementing a simple agentic service, running it locally and interacting with it. |
This guide starts with creating an agent that suggests real-world activities. We will incorporate more components in separate parts of the guide, and at the end we will have a multi-agent system with dynamic planning and orchestration capabilities.

1. [Activity agent](activity.html) — An Agent (with session memory) that suggests real-world activities using an LLM.
2. [User preferences](preferences.html) — An Entity (long-term memory) to personalize the suggestions.
3. [Weather agent](weather.html) — A weather forecasting Agent that uses an external service as an agent tool.
4. [Orchestrate the agents](team.html) — A Workflow that coordinates long-running calls across the agents.
5. [Dynamic orchestration](dynamic-team.html) — An Autonomous Agent coordinator that delegates dynamically to the worker agents using the built-in Delegation capability.
6. [Evaluating task results](eval.html) — A Consumer subscribed to task-completion events runs LLM-as-judge and toxicity evaluators.

<!-- <footer> -->
<!-- <nav> -->
[Code-first hello agent](../author-your-first-service.html) [Activity agent](activity.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->