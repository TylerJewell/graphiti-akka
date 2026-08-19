<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Function tools](extending.html)

<!-- </nav> -->

# Function tools

Function tools are Java methods that an agent makes available to the model. The agent tells the model which tools exist, and the model determines from the prompt which tools to request and with which parameters. The agent then executes the requested tool, incorporates the result into the session context, and sends a new prompt. This continues in a loop until the model no longer requests a tool.

There are four ways to add function tools to your agent:

1. **Agent-defined function tools** — Define function tools directly within your agent class using the `@FunctionTool` annotation. These are automatically registered as available tools for the current Agent.
2. **Externally defined function tools** — Explicitly register external objects or classes containing function tools by
passing them to the `effects().tools()` method in your agent’s command handler. Objects or classes passed to `effects
().tools()` must have at least one public method annotated with `@FunctionTool`.
3. **Akka components as function tools** — Use Akka components from the same application as tools by annotating their command handlers with `@FunctionTool` and passing the component class to the `effects().tools()` method. This approach works with Event Sourced Entities, Key Value Entities, Workflows, and Views.
4. **Tools defined by remote MCP servers** – Register remote MCP servers to let the agent use tools they provide.

|  | A class (either the agent itself, Akka components, or an external tool class) can have multiple methods annotated with `@FunctionTool`. Each annotated method will be registered as a separate tool that the LLM can choose to invoke based on the task requirements. |
You can use either approach independently or combine them based on your needs. The following complete example shows both approaches:

[WeatherAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/WeatherAgent.java)
```java
public class WeatherAgent extends Agent {

  private final WeatherService weatherService;

  public WeatherAgent(WeatherService weatherService) {
    this.weatherService = weatherService; // (1)
  }

  public Effect<String> query(String request) {
    logger.info("Invoked with: {}", request);
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .tools(weatherService) // (2)
      .userMessage(request)
      .thenReply();
  }

  @FunctionTool(description = "Return current date in yyyy-MM-dd format") // (3)
  private String getCurrentDate() {
    return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
  }
}
```

| **1** | The `WeatherService` providing a function tool is injected into the agent (see [DependencyProvider](../setup-and-dependency-injection.html#_custom_dependency_injection)). |
| **2** | We explicitly register the `weatherService` using the `tools()` method to make its method available as a tool for
the current Agent. |
| **3** | We define a simple tool directly in the agent class using the `@FunctionTool` annotation, which is implicitly registered. Note that since this method is defined in the agent itself, it can even be a private method. |
The `WeatherService` is an interface with a method annotated with `@FunctionTool`. A concrete implementation of this
interface is provided by `WeatherServiceImpl` class.
This class is made available for injection in the service setup using a [DependencyProvider](../setup-and-dependency-injection.html#_custom_dependency_injection).

[WeatherService.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/WeatherService.java)
```java
public interface WeatherService {
  @FunctionTool(description = "Returns the weather forecast for a given city.") // (1)
  String getWeather(
    @Description("A location or city name.") String location, // (2)
    @Description("Forecast for a given date, in yyyy-MM-dd format.") Optional<String> date
  ); // (3)
}
```

| **1** | Annotate method with `@FunctionTool` and provide a clear description of what it does. |
| **2** | Parameters can be documented with the `@Description` annotation to help the LLM understand how to use them. |
| **3** | The date parameter is optional. The LLM may call `getCurrentDate` first or call this method without a date, depending on the user query. |

|  | LLMs are all about context. The more context you can provide, the better the results.
Both `@FunctionTool` and `@Description` annotations are used to provide context to the LLM about the tool function and its parameters.
The better the context, the better the LLM can understand what the tool function does and how to use it. |
In this example, the agent has access to both:

- The `getCurrentDate()` method defined within the agent class (implicitly registered via annotation)
- The `getWeather()` method defined in the `WeatherService` interface (explicitly registered via the `.tools()` method)

## <a href="about:blank#_sharing_function_tools_across_agents"></a> Sharing function tools across agents

Function tools defined in external classes can be shared and reused across multiple agents. This approach promotes code reusability and helps maintain a consistent behavior for common functionalities.

When a tool like `WeatherService` is shared across multiple agents:

- Each agent can register the same tool but use it in different contexts
- The tool behavior remains consistent, but how and when agents invoke it may differ based on their specific tasks
- Agents provide different system prompts that influence how the LLM decides to use the shared tool

## <a href="about:blank#_lazy_initialization_of_tool_classes"></a> Lazy initialization of tool classes

In the example above, we pass an instance of `WeatherService` to the `tools()` method. Alternatively, you can pass the `Class` object instead:

java]
```java
public Effect<AgentResponse> query(String message) {
  return effects()
    .systemMessage(SYSTEM_MESSAGE)
    .tools(WeatherService.class) // (1)
    .userMessage(message)
    .responseAs(AgentResponse.class)
    .thenReply();
}
```

| **1** | The WeatherService is passed as a `Class` instead of an instance. It will be instantiated when the agent needs to use it. |
When you pass a `Class` instead of an instance, the class is only instantiated when the agent actually needs to use the tool.

For this approach to work, you must register the class with a [DependencyProvider](../setup-and-dependency-injection.html#_custom_dependency_injection) in your service setup. The DependencyProvider is responsible for creating and managing instances of these classes when they are needed. This gives you complete control over how tool dependencies are instantiated and managed throughout your application.

## <a href="about:blank#component_tools"></a> Using Akka components as function tools

Akka components within the same application can be used as function tools for agents. This allows agents to interact with your domain model directly by invoking command handlers on Event Sourced Entities, Key Value Entities, Workflows, and Views.

To use an Akka component as a tool:

1. Annotate the appropriate methods with `@FunctionTool` (just like with external tools)
2. Pass the component class to the agent using the `effects().tools()` method
The following Akka component types can be used as function tools:

- **Event Sourced Entities (ESE)** — Command handlers that return `Effect` or `ReadOnlyEffect` can be exposed as tools to create, update, or query entity state
- **Key Value Entities (KVE)** — Command handlers that return `Effect` or `ReadOnlyEffect` can be exposed as tools to create, update, or query entity state
- **Workflows** — Command handlers that return `Effect` or `ReadOnlyEffect` can be exposed as tools to trigger or interact with workflows
- **Views** — Query methods that return `QueryEffect` can be exposed as tools to retrieve aggregated or transformed data

|  | **Agents cannot be used as tools for other agents.** While an agent can define its own tools by annotating methods with `@FunctionTool`, you cannot pass an agent class to another agent’s `effects().tools()` method.

Agent chaining (where one agent calls another agent) is not a recommended pattern. Instead, use Workflows to orchestrate multiple agents. Workflows provide better control over the execution flow, error handling, and state management when coordinating between multiple agents. |

|  | When using Akka components as tools, the agent can directly modify your application state or trigger workflows. Ensure that your `@FunctionTool` descriptions clearly communicate the impact of these operations to help the LLM make appropriate decisions. |
This approach is particularly useful when you want an agent to orchestrate operations across multiple components in your application, or when an agent needs to access and manipulate your domain model based on user requests.

## <a href="about:blank#mcp_tools"></a> Using tools from remote MCP servers

[Akka MCP endpoints](../mcp-endpoints.html) declared in other services, or third party MCP services can be added to
the agent. By default, all tools provided by each added remote MCP server are included, but it is possible to filter
available tools from each server based on their name.

It is also possible to intercept, modify, or deny MCP tool requests, or their responses by defining a `RemoteMcpTools.ToolInterceptor`.

[RemoteMcpWeatherAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/RemoteMcpWeatherAgent.java)
```java
public Effect<AgentResponse> query(String message) {
  return effects()
    .systemMessage(SYSTEM_MESSAGE)
    .mcpTools(
      RemoteMcpTools.fromService("weather-service"), // (1)
      RemoteMcpTools.fromServer("https://weather.example.com/mcp") // (2)
        .addClientHeader(Authorization.oauth2(System.getenv("WEATHER_API_TOKEN"))) // (3)
        .withAllowedToolNames(Set.of("get_weather")) // (4)
    )
    .userMessage(message)
    .responseAs(AgentResponse.class)
    .thenReply();
}
```

| **1** | For MCP endpoints in other Akka services, use HTTP and the deployed service name |
| **2** | For third party MCP servers use the fully qualified host name and make sure to use HTTPS as the requests will
go over the public internet. |
| **3** | Custom headers to pass along can be defined |
| **4** | As well as filters of what tools to allow. |
When using MCP endpoints in other Akka services, the service ACLs apply just like for [HTTP endpoints](../http-endpoints.html) and [gRPC endpoints](../grpc-endpoints.html).

## <a href="about:blank#returning_images_or_pdfs"></a> Returning images or PDFs from a function tool

A function tool usually returns text (or a value that is serialized to JSON), but it can also return an image or a PDF that the agent forwards to the model as part of the conversation. This is useful when the tool dynamically produces media — for example fetching a Street View image for a location the user just mentioned.

To do this, declare the tool’s return type as `MessageContent` and return inline bytes built with `ImageMessageContent.fromBytes(…​)` or `PdfMessageContent.fromBytes(…​)`:

[StreetViewAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/StreetViewAgent.java)
```java
@Component(id = "street-view-agent")
public class StreetViewAgent extends Agent {

  public static class StreetViewService {

    @FunctionTool(description = "Fetches a Street View image for the given location")
    public MessageContent getStreetView(String location) { // (1)
      byte[] image = fetchStreetViewImage(location); // (2)
      return MessageContent.ImageMessageContent.fromBytes(image, "image/jpeg"); // (3)
    }

    private byte[] fetchStreetViewImage(String location) {
      // call the Street View API and return the raw image bytes
      return new byte[0];
    }
  }

  public Effect<String> ask(String question) {
    return effects()
      .systemMessage("You can look up Street View images to answer questions about places.")
      .tools(new StreetViewService())
      .userMessage(question)
      .thenReply();
  }
}
```

| **1** | Declare the return type as `MessageContent` — this signals that the result is multimodal. |
| **2** | Produce the binary content (here, the raw image bytes). |
| **3** | Wrap the bytes with their media type; the framework hands them to the model as image content. |
The model receives the image directly, with no need to upload it to object storage first.

A tool that declares a single `MessageContent` return type must always return a non-null value; returning `null` results in an `IllegalArgumentException`. If the tool has no media to return, return a `TextMessageContent` instead.

### <a href="about:blank#returning_a_list_of_contents"></a> Returning multiple contents

A tool can also declare its return type as `List<MessageContent>` to send several pieces of content in one tool result — for example a caption plus an image, or text plus a PDF. Every element is delivered to the model in list order. A subtype element (`List<ImageMessageContent>`) or a bounded wildcard (`List<? extends MessageContent>`) is accepted as well; any other element type makes the result a plain JSON tool response.

[ChartAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/ChartAgent.java)
```java
@Component(id = "chart-agent")
public class ChartAgent extends Agent {

  public static class ChartService {

    @FunctionTool(description = "Renders a chart for the given metric, with a caption")
    public List<MessageContent> renderChart(String metric) { // (1)
      byte[] image = renderChartImage(metric);
      return List.of(
        MessageContent.TextMessageContent.from("Chart for " + metric), // (2)
        MessageContent.ImageMessageContent.fromBytes(image, "image/png")
      ); // (3)
    }

    private byte[] renderChartImage(String metric) {
      // render the chart and return the raw image bytes
      return new byte[0];
    }
  }

  public Effect<String> ask(String question) {
    return effects()
      .systemMessage("You can render charts to help answer questions about metrics.")
      .tools(new ChartService())
      .userMessage(question)
      .thenReply();
  }
}
```

| **1** | Declare the return type as `List<MessageContent>`. |
| **2** | The first element — a text caption. |
| **3** | The second element — the rendered image, delivered right after the caption. |
Individual `null` elements are filtered out, but the tool must return at least one non-null `MessageContent`: returning `null`, an empty list, or a list containing only `null` elements results in an `IllegalArgumentException`, just like the single- `MessageContent` form. Each element follows the same persistence rules described below: text and URI-referenced contents are kept in session memory, while inline bytes become a `[image]` / `[pdf]` placeholder.

|  | Inline bytes returned from a tool are **not persisted**. They are forwarded to the model for the current request only — they are never written to [session memory](memory.html) (the session records a `[image]` or `[pdf]` placeholder in their place) and they are never uploaded to [object storage](../integrations/object-storage.html).

If you need the media to survive across requests — so the model can refer back to it later, or so it is available
outside the conversation — persist it yourself in the tool: inject an `ObjectStorageProvider`, store the bytes in a bucket, and return an `ImageUrlMessageContent` or `PdfUrlMessageContent` referencing the stored object (for example via `ImageUrlMessageContent.create(bucket, key)`). The `object://` reference **is** persisted in session memory, and the SDK resolves it back to the bytes on each request  (see [object storage](../integrations/object-storage.html) for more details). |

|  | Inline bytes are only supported as a function tool **return** value. To send an image or PDF as part of a user message, reference it by URI instead (see [multimodal user messages](prompt.html)) — for example an `object://bucket/key` reference backed by [object storage](../integrations/object-storage.html).

Only some model providers accept multimodal tool results (currently Anthropic, Bedrock, and Google AI Gemini). |

## <a href="about:blank#configuring_tool_call_limits"></a> Configuring tool call limits

Inside a single request/response cycle, an LLM can successively request the agent to call functions tools or MCP tools. After analyzing the result of a call, the LLM might decide to request another call to gather more context. The `akka.javasdk.agent.max-tool-call-steps` setting limits how many such steps may occur between a user request and the final AI response.

By default, this value is set to 100. You can adjust this in your configuration:

application.conf
```hocon
# Increase the limit to allow more tool calls
akka.javasdk.agent.max-tool-call-steps = 150
```

## <a href="about:blank#_see_also"></a> See also

- [Agents](../agents.html)
- [MCP Endpoints](../mcp-endpoints.html)
- [Testing the agent](testing.html)

<!-- <footer> -->
<!-- <nav> -->
[Failure handling](failures.html) [Streaming responses](streaming.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->