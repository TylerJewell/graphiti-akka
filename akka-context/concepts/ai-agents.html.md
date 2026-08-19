<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- Agentic concepts
- [AI agents](ai-agents.html)

<!-- </nav> -->

# AI agents

AI agents are components that integrate with AI to perceive their environment, make decisions, and take actions toward a specific goal. Agents can have varying degrees of human intervention from none (completely autonomous) to requiring a human to approve each action the agent takes.

## <a href="about:blank#_overview"></a> Overview

In Akka, an AI Agent is a lightweight, single-purpose component that interacts with one or more AI models to accomplish a discrete goal.

Use AI Agents in Akka when you need to:

- Interact with models as part of a larger application.
- Compose multiple Agents under platform-mediated coordination: a Workflow supervisor when the orchestration steps are fixed in code, or an [Autonomous Agent](../sdk/autonomous-agents.html) when the model should decide which agent runs next through delegation, handoff, teams, or moderation.
- Maintain session memory, context, and audit trails across Agent interactions.
- Enforce cost controls, governance policies, and risk boundaries on model usage.
Agents relate to other Akka components in a direct way: [Workflows or Autonomous Agent coordination capabilities](ai-orchestration-patterns.html) orchestrate Agents, [Entities](state-model.html) store the durable state Agents depend on, and [agent-to-agent communication](ai-orchestration-patterns.html) connects Agents to each other and to external systems via protocols like MCP, A2A, and ACP.

## <a href="about:blank#_tokens_and_streaming"></a> Tokens and streaming

Agents interact with AI, most commonly in the form of Large Language Models (LLMs). LLMs are what is known as *predictive text*. This means that every word streamed to the agent is actually just the next word predicted to be in the output. Regardless of platform or language, agents need the ability to stream tokens bi-directionally.

If your agent consumes an LLM as a service, you could be paying some amount of money per bundle of tokens. In cases like this, it is crucial to ensure that you have control over how frequently and how many tokens the agent "spends."

## <a href="about:blank#_different_types_of_ai"></a> Different types of AI

LLMs are everywhere these days and it is impossible to escape all of their related news. It would be easy to assume that all agents interact with LLMs whether they are self-hosted or provided as an external service. This idea does a disservice to the rest of machine learning and AI in particular.

As you develop your teams of collaborative agents, keep in mind that not everything needs to be an LLM and look for opportunities to use smaller, more efficient, task-specific models. This can not only save you money, but can improve the overall performance of your application.

## <a href="about:blank#_prompts_session_memory_and_context"></a> Prompts, session memory, and context

Agents interact with LLMs through prompts. A prompt is the input to an LLM in the form of natural language text. The quality and detail of your agents' prompts can make the difference between a great application experience and a terrible one. The prompt sent to an LLM typically tells the model the role it is supposed to play, how it should respond (e.g. you can tell a model to respond with JSON).

Take a look at the following sample prompt:

```none
You are a friendly and cheerful question answerer.
Answer the question based on the context below.
Keep the answer short and concise. Respond "Unsure about answer"
if not sure about the answer.

If asked for a single item and multiple pieces of information
are acceptable answers, choose one at random.

Context:
 Here is a summary of all the action movies you know of. Each one is rated from 1 to 5 stars.

Question:
  What is the most highly rated action movie?
```
Everything except the **question** above would have been supplied by the agent. Working with and honing prompts is such an important activity in agentic development that a whole new discipline called [prompt engineering](https://www.promptingguide.ai/) has sprung up around it.

The context in the preceding prompt is how agents can augment the knowledge of an LLM. This is how Retrieval Augmented Generation (RAG) works. Agents can participate in sessions where the conversation history is stored. You see this in action whenever you use an AI assistant and it shows you a history of all of your chats. Session management and persistence is a task every agent developer needs to tackle.

## <a href="about:blank#_agent_orchestration_and_collaboration"></a> Agent orchestration and collaboration

Each agent should do *one thing*. Agents should have a single goal and they can use any form of knowledge and model inference to accomplish that goal. However, real applications rarely do only one thing. One of the super powers of agents is in *collaboration*.

There are protocols and standards rapidly evolving for ways agents can communicate with each other directly, but agents also benefit from indirect communication.

Whether you have 1 agent or 50, you still need to handle things like recovery from network failure, timeouts, failure responses, broken streams, and much more. Even for individual agents you need an orchestrator if you want that agent to be resilient at scale. With dozens of agents working together with shared and isolated sessions, they need to be managed by supervisors.

Akka offers two forms of supervision, both running on the Akka runtime with the same durable-execution, retry, and audit guarantees. A [Workflow](../sdk/workflows.html) supervises agents from outside, with explicit steps written by the developer; this is the right choice when the orchestration sequence is fixed in code. An [Autonomous Agent](../sdk/autonomous-agents.html) supervises through declared coordination capabilities (delegation, handoff, teams, moderation), with the framework driving the loop and the model deciding which agent runs next; this is the right choice when the orchestration sequence itself is a model judgment.

For more detail on orchestration, check out the [agentic orchestration patterns](ai-orchestration-patterns.html) section.

## <a href="about:blank#_agent_evaluation"></a> Agent evaluation

The answers your agents get from models are *non-deterministic*. They can seem random at times. Since you cannot predict the model output you cannot use traditional testing practices. Instead, you need to do what is called **evaluation**. Evaluation involves iteratively refining a prompt.

Submit the prompt to the model and get an answer back. Then, use *another model* to derive metrics from that response like confidence ratings. This is often called the "LLM-as-judge" pattern.

Rather than a unit test, you often have entire suites of evaluation runs where you submit a large number of prompts, derive analytics and metrics from the replies, and then score the model-generated data as a whole. This is tricky because you can very easily have an evaluation run that has a high confidence score but still somehow manages to contain [hallucinations](https://www.ibm.com/think/topics/ai-hallucinations).

## <a href="about:blank#_foundational_ai_concepts_video"></a> Foundational AI Concepts (video)

Vectors, embeddings, and Retrieval-Augmented Generation (RAG) are core concepts behind modern AI systems, especially those involving large language models (LLMs). Whether you are just beginning your journey into AI or brushing up on terminology that is increasingly appearing in development workflows, this is a great place to start.

The following video is an informal walkthrough of foundational AI concepts that underpin tools like ChatGPT, RAG, and semantic search.

Topics covered in the video include:

- What vectors are and why they are foundational to AI
- How embeddings turn human input into machine-readable vectors
- The role of vector distance and similarity metrics (e.g., Euclidean vs. cosine)
- How vector databases support semantic search
- The RAG pattern for enriching LLM prompts
- Why prompt structure, token count, and caching all matter
- How concepts like agency and stateful workflows connect to agentic AI and Akka

<!-- <footer> -->
<!-- <nav> -->
[Multi-region operations](multi-region.html) [AI orchestration patterns](ai-orchestration-patterns.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->