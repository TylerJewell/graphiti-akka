<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Integrations](index.html)

<!-- </nav> -->

# Integrations

Akka runs on the JVM. All integrations use industry-standard Java APIs and client libraries. No proprietary adapters or Akka-specific wrappers are needed. If a Java client exists for it, you can use it in Akka.

## <a href="about:blank#_ai_models"></a> AI & models

LLM providers are supported natively — 9 providers built into the SDK: Anthropic, OpenAI, Google AI Gemini, Google Cloud Vertex AI, AWS Bedrock, Ollama, LocalAI, Hugging Face, plus custom providers via the `ModelProvider.Custom` interface.

- [Agents](../agents.html) — how Agents interact with AI models
- [Model provider details](../model-provider-details.html) — configuration for each provider

## <a href="about:blank#_data_knowledge"></a> Data & knowledge

Akka connects to any data store with a Java client library.

- **Vector databases:** Pinecone, Weaviate, Qdrant, Chroma, pgvector — via their Java client libraries
- **Relational databases:** PostgreSQL, MySQL — via R2DBC or JDBC
- **NoSQL databases:** MongoDB, DynamoDB, Cassandra — via their Java client libraries
- **Search engines:** Elasticsearch, OpenSearch — via their Java client libraries
- [Retrieval-Augmented Generation (RAG)](../../getting-started/ask-akka-agent/index.html) — semantic search on vector databases to enrich AI model requests

### <a href="about:blank#_no_caching_layer_needed"></a> No caching layer needed

Akka’s Entity components (Event Sourced Entities, Key Value Entities) are in-memory systems of durable record. All data is durable, immutable, and in-memory with sub-10ms access. There is no need for a separate cache like Redis. Your Entities *are* your cache — with persistence guarantees that Redis cannot provide.

## <a href="about:blank#_messaging_events"></a> Messaging & events

**Akka-native first.** Akka has built-in messaging capabilities that should be your primary path. These are durable, integrated with the runtime, and require no external infrastructure:

- [Component and service calls](../component-and-service-calls.html) — invoke other services and components directly
- [Consumers](../consuming-producing.html) — brokerless pub/sub, service-to-service eventing, event-driven consumption
- [Streaming](../streaming.html) — built-in stream processing
When you need to integrate with systems outside Akka, use external message brokers via their Java client libraries:

- [Message brokers](messaging-and-events.html#external) — Kafka, Google Pub/Sub, Azure Event Hubs
- Webhooks for external system callbacks — implement via [HTTP Endpoints](../http-endpoints.html)

## <a href="about:blank#_apis_protocols"></a> APIs & protocols

Akka provides built-in support for exposing your services:

- [HTTP Endpoints](../http-endpoints.html) — REST APIs with JSON
- [OpenAPI](../http-endpoints.html#_openapi_endpoint_schema) — schema generation (built-in)
- [WebSockets](../http-endpoints.html#websocket) — real-time bidirectional communication (built-in)
- [gRPC Endpoints](../grpc-endpoints.html) — high-performance APIs
- [MCP Endpoints](../mcp-endpoints.html) — expose Agent functions to remote LLMs via `@FunctionTool` annotations

### <a href="about:blank#_agent_protocol_support"></a> Agent protocol support

A2A, ACP, and MCP clients are baked into Akka’s Agent component. Agents natively consume tools and communicate with other Agents via all three protocols. No external libraries needed.

The Akka CLI also provides a built-in MCP server (`akka mcp serve`) that exposes CLI operations as MCP tools for Claude Desktop, VS Code, and Cursor.

- [MCP](https://akka.io/blog/mcp-a2a-acp-what-does-it-all-mean)

## <a href="about:blank#_identity_security"></a> Identity & security

- **Akka-native secret management** (built-in) — project-level secrets injected as environment variables, supporting generic secrets, symmetric/asymmetric keys, TLS certificates, and CA bundles. Key rotation without environment variable changes. Values are never exposed in CLI or Console. See [Manage secrets](../../operations/projects/secrets.html).
- **External secrets:** Azure KeyVault integration (currently documented). See [Manage external secrets](../../operations/projects/external-secrets.html).
- **Other external providers** (AWS Secrets Manager, GCP Secret Manager, HashiCorp Vault) — via standard Java APIs.

## <a href="about:blank#_observability"></a> Observability

- **OpenTelemetry metrics and logs exporting** (built-in) — feed into any OTEL-compatible backend: Grafana, Datadog, New Relic, and others. See [Observability and monitoring](../../operations/observability-and-monitoring/index.html).

<!-- <footer> -->
<!-- <nav> -->
[Web applications](../web-applications.html) [Component and service calls](../component-and-service-calls.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->