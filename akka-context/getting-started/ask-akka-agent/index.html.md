<!-- <nav> -->
- [Akka](../../index.html)
- [Getting Started](../index.html)
- [RAG chat tutorial](index.html)

<!-- </nav> -->

# Build a RAG chat agent

|  | **New to Akka? Start here:**

Use the [Spec-first hello agent](../spec-your-first-agent.html) guide to use your AI assistant for implementing a simple agentic service, running it locally and interacting with it. |
This tutorial walks through building a Retrieval-Augmented Generation (RAG) chat agent. We start with a simple agent that streams responses from a large language model (LLM), and add retrieval functionality in separate parts of the tutorial. By the end, we will have an agent that uses the latest Akka documentation as its knowledge base, accessible through a web UI.

1. [Creating the agent](the-agent.html) — A streaming Agent that answers questions using an LLM and session memory.
2. [Knowledge indexing with a workflow](indexer.html) — A Workflow that indexes local documentation into a vector database.
3. [Executing RAG queries](rag.html) — A helper class that performs RAG queries by combining vector search with the LLM.
4. [Adding UI endpoints](endpoints.html) — Endpoints that expose a UI, support multiple sessions, and allow users to query the system.

<!-- <footer> -->
<!-- <nav> -->
[Evaluating task results](../planner-agent/eval.html) [Creating the agent](the-agent.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->