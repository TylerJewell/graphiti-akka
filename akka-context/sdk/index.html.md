<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)

<!-- </nav> -->

# Developing

|  | **New to Akka? Start here:**

Use the [Spec-first hello agent](../getting-started/spec-your-first-agent.html) guide to use your AI assistant for implementing a simple agentic service, running it locally and interacting with it. |
The Akka SDK provides you proven design patterns that enable your apps to remain responsive to change. It frees you from infrastructure concerns and lets you focus on the application logic.

With its few, concise components, the Akka SDK is easy to learn. You can develop services in quick, iterative steps by running your code locally with full insight through Akka’s console. Or even better, you can use [Spec-driven development](spec-driven-development.html) to turn natural-language specifications into code.

Akka services let you build REST endpoints with flexible access control and multiple ways to expose these endpoints to their consuming systems or applications. Akka is secure by default, and you explicitly express the desired access through code and configuration.

Akka encapsulates data together with the logic to access and modify it. The data itself is expressed in regular Java records (plain old Java objects). The same goes for the events that change the data, these are expressed in pure Java to reflect business events that lead to data updates. Akka enables you to build fully event-driven services by combining logic and data into one thing: entities.

Data and changes to it are managed by Akka’s runtime without the need to manage database storage. Changes to your data can be automatically replicated to multiple places, not only within a single service, but also across applications and even cloud providers. An SQL-like language lets you design read access that ensures the data is properly indexed for your application needs.

Integrations with message systems like Kafka are already built-in and the Akka SDK enables message consumers to listen to topics and queues.

## <a href="about:blank#_getting_started"></a> Getting Started

Create your first Akka agent with your AI assistant using Spec-driven development following our instructions to [Spec-first hello agent](../getting-started/spec-your-first-agent.html).

For manually coding your first Akka agent, follow [Code-first hello agent](../getting-started/author-your-first-service.html) to implement your first agentic service. If you prefer to first explore working example code, you can check out [A simple shopping cart service](../getting-started/shopping-cart/build-and-deploy-shopping-cart.html) or our other [samples](../getting-started/samples.html).

On the other hand, if you would rather spend some time exploring our documentation, here are some main features you will find in this section:

- [Spec-driven development](spec-driven-development.html)
- [Agents](agents.html)
- [Event Sourced Entities](event-sourced-entities.html)
- [Key Value Entities](key-value-entities.html)
- [HTTP Endpoints](http-endpoints.html)
- [gRPC Endpoints](grpc-endpoints.html)
- [MCP Endpoints](mcp-endpoints.html)
- [Views](views.html)
- [Workflows](workflows.html)
- [Timed Actions](timed-actions.html)
- [Consuming and Producing](consuming-producing.html)

<!-- <footer> -->
<!-- <nav> -->
[Resources](../resources.html) [Spec-driven development](spec-driven-development.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->