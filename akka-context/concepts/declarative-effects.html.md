<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- State and execution
- [Declarative effects](declarative-effects.html)

<!-- </nav> -->

# Declarative effects

In Akka, the behavior of your services is decoupled from the execution. This decoupling allows Akka to determine how a service is executed without being constrained by how your system’s behavior is defined. Delegation removes you from worrying about distributed systems, persistence, elasticity, or networking. With Akka’s hosted services, we use delegation to enable swapping out new, improved runtimes while your services are running without a recompilation or redeployment!

In Akka, you specify *what* the system should do, while the Akka runtime decides *how* it should be executed. For example, you define an agent by specifying the model it uses, its session memory, and the user prompt. This represents the *what*. The Akka runtime then determines the *how* by managing processes, virtual threads, persistence, and actor-based concurrency.

![Akka Agentic Platform](_images/component-effects.png)


Your services define the *what* using `Effects`, which are Application Programming Interfaces (APIs) provided by each Akka component. When you write a component method, you return an `Effect<…​>` object that describes, in a declarative way, what you want Akka to do.

For example, when using Akka’s [Agent](../sdk/agents.html) component, you might return an `Effect` that tells the runtime to execute the agent with a system message, a user message, and then send the AI model’s response back to the requester:

```java
public Effect<String> query(String question) {
  return effects()
    .systemMessage("You are a helpful...")
    .userMessage(question)
    .thenReply();
}
```
Each component defines its own Effect API offering predefined operations tailored to the component’s specific semantics. For example, [Event Sourced Entities](../sdk/event-sourced-entities.html) provide an Effect for persisting events, while a [Workflow](../sdk/workflows.html) Effect defines both what needs to be executed and how to handle the result to transition to the next step.

This model simplifies development by removing the need to handle persistence, distribution, serialization, cache management, replication, and other distributed system concerns. Developers can focus on business logic — defining what needs to be persisted, how to respond to the caller, transitioning to different steps, rejecting commands, and more — while the Akka runtime takes care of the rest.

For example, with our Workflow component at the end of each step, you return an Effect that indicates how the Workflow should persist the call stack and which stage it should transition to next.

```java
return effects()
 .updateState(currentState().withStatus(WITHDRAW_SUCCEED))
 .transitionTo("deposit", depositInput);
```
For details on the specific Effect types, refer to the documentation for each component.

| Component | Available Effects |
| --- | --- |
| [Agents](../sdk/agents.html#_effect_api) | Model, Memory, Tools, System and User Message,  Reply, Error |
| [Event Sourced Entities](../sdk/event-sourced-entities.html#_effect_api) | Persist Events, Reply, Delete Entity, Error |
| [Key Value Entities](../sdk/key-value-entities.html#_effect_api) | Update State, Reply, Delete State, Error |
| [Views](../sdk/views.html#_effect_api) | Update State, Delete State, Ignore |
| [Workflows](../sdk/workflows.html#_effect_api) | Update State, Transition, Pause, End, Reject Command, Reply |
| [Timers](../sdk/timed-actions.html#_effect_api) | Confirm Scheduled Call, Error |
| [Consumers](../sdk/consuming-producing.html#_effect_api) | Publish to Topic, Confirm Message, Ignore |

## <a href="about:blank#_background_execution"></a> Background execution

Because effects are declarative, the runtime can carry them out on your behalf as background execution rather than requiring you to manage the work directly.

In Akka, [effects](../reference/glossary.html#effect) are processed in the background. When you call a component or a service within Akka, the default mode is synchronous, but you can opt-in to asynchronous for more control. You do not need to implement any asynchronous libraries, queues, promises, callbacks, await/async, futures, or event loops for Akka to behave this way.

Akka handles background processing using actors. Actors offer a lightweight model for concurrency, relying on asynchronous messaging rather than locks. This helps avoid shared mutable state and sidesteps many of the typical issues seen in multi-threaded programming, such as blocking, deadlocks, and race conditions.

Because the Actor runtime manages concurrency, you can write simple, synchronous code within your Akka components. There is little need to worry about performance or resource contention.

This "share nothing" approach also makes it easier to reason about concurrent systems. It helps reduce the chance of deadlocks and supports the creation of systems that are more stable and easier to scale. Akka also includes built-in supervision and fault tolerance, so if an actor fails, the issue is contained and resolved locally. This avoids broader system failure and reduces the need for complex manual error handling, which is often required elsewhere.

When you use the [component client](../reference/glossary.html#component_client) to call another component, your code remains synchronous and returns regular objects. If those calls involve effects, the Akka runtime takes care of them in the background. Depending on where the component is located, the runtime may even do so across different locations.

<!-- <footer> -->
<!-- <nav> -->
[State model](state-model.html) [Multi-region operations](multi-region.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->