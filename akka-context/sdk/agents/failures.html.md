<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Failure handling](failures.html)

<!-- </nav> -->

# Failure handling

The `onFailure` method in the agent’s effect API handles failures that occur during model processing. Use it to implement fallback strategies and return a meaningful response when a request fails.

## <a href="about:blank#_types_of_exceptions_handled"></a> Types of exceptions handled

The `onFailure` method can handle the following types of exceptions:

- **Model-related exceptions:**

  - `ModelException` - General model processing failures
  - `RateLimitException` - API rate limiting exceeded
  - `ModelTimeoutException` - Model request timeout
  - `UnsupportedFeatureException` - Unsupported model features
  - `InternalServerException` - Internal service errors
- **Tool execution exceptions:**

  - `ToolCallExecutionException` - Function tool execution errors
  - `McpToolCallExecutionException` - MCP tool execution errors
  - `ToolCallLimitReachedException` - Tool call limit exceeded
- **Response processing exceptions:**

  - `JsonParsingException` - Response parsing failures (as shown in structured responses)
- **Unknown exceptions:**

  - `RuntimeException` - For any unexpected errors that do not fall into the above categories
Apart from the listed specific exceptions, users can still encounter `RuntimeException` instances that wrap unexpected errors. Therefore, when handling errors in the `onFailure` method, it is recommended to always include a `default` case to handle any unknown exception types gracefully.

## <a href="about:blank#_implementing_fallback_strategies"></a> Implementing fallback strategies

You can use the `onFailure` method to implement different recovery strategies based on the type of exception:

```java
public Effect<String> query(String message) {
  return effects()
    .systemMessage(SYSTEM_MESSAGE)
    .userMessage(message)
    .onFailure(exception -> {
      // Handle different types of exceptions with appropriate fallback responses
      return switch (exception) { // (1)
        case RateLimitException exc -> "Rate limit exceeded, try again later"; // (2)
        case ModelTimeoutException exc -> "Request timeout, service is delayed";
        case ToolCallExecutionException exc -> "Tool error: " + exc.getToolName();
        default -> "Unexpected error occurred"; // (3)
      };
    })
    .thenReply();
```

| **1** | Use pattern matching to handle different exception types appropriately. |
| **2** | Handle specific known exceptions with meaningful fallback responses. |
| **3** | For unknown or unexpected exceptions, define a default matching branch providing a generic fallback response. |
This approach ensures your agents remain resilient and can provide meaningful responses even when encountering various types of failures during model interaction.

## <a href="about:blank#_see_also"></a> See also

- [Agents](../agents.html)
- [Errors and failures](../errors-and-failures.html)
- [Guardrails](guardrails.html)

<!-- <footer> -->
<!-- <nav> -->
[Structured responses](structured.html) [Function tools](extending.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->