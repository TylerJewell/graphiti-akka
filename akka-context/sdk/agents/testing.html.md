<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Testing](testing.html)

<!-- </nav> -->

# Testing the agent

Testing agents built with Generative AI involves two complementary approaches: evaluating the quality of the non-deterministic model behavior and writing deterministic unit tests for the agent’s and surrounding components' logic. Evaluations is described in [LLM evaluation](llm_eval.html), and here we will cover the deterministic testing.

## <a href="about:blank#_mocking_responses_from_the_model"></a> Mocking responses from the model

For predictable and repeatable tests of your agent’s business logic and component integrations, it’s essential to use deterministic responses. This allows you to verify that your agent behaves correctly when it receives a known model output.

Use the `TestKitSupport` and the `ComponentClient` to call the components from the test. The `ModelProvider` of the agents can be replaced with [TestModelProvider](../_attachments/testkit/akka/javasdk/testkit/TestModelProvider.html), which provides ways to mock the responses without using the real AI model.

[WeatherAgentIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/test/java/demo/multiagent/application/WeatherAgentIntegrationTest.java)
```java
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class WeatherAgentIntegrationTest extends TestKitSupport { // (1)

  private final TestModelProvider weatherModel = new TestModelProvider(); // (2)

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    ).withModelProvider(WeatherAgent.class, weatherModel); // (3)
  }

  @Test
  public void replyWithFixedResponse() {
    weatherModel.fixedResponse("The weather in Madrid is sunny, 25°C."); // (4)

    var reply = componentClient
      .forAgent()
      .inSession(UUID.randomUUID().toString())
      .method(WeatherAgent::query)
      .invoke("What is the weather in Madrid?");

    assertThat(reply).contains("sunny");
  }


  // The runtime prefixes a tool name with the simple name of the registered tool's class;
  // here the WeatherAgent receives a FakeWeatherService instance (wired by the bootstrap
  // when WEATHER_API_KEY is unset), so the tool name the model sees is "FakeWeatherService_getWeather".
  private static final String GET_WEATHER_TOOL = "FakeWeatherService_getWeather";

  @Test
  public void invokeWeatherTool() {
    // Turn 1: the mocked model asks the runtime to invoke the getWeather tool.
    weatherModel
      .whenMessage(msg -> msg.contains("Stockholm"))
      .reply(new ToolInvocationRequest(GET_WEATHER_TOOL, "{\"location\":\"Stockholm\"}")); // (5)

    // Turn 2: the mocked model receives the tool result and produces the final answer.
    // FakeWeatherService returns "It's always sunny <date> in <location>."
    weatherModel
      .whenToolResult(tr -> tr.name().equals(GET_WEATHER_TOOL))
      .thenReply(tr -> new AiResponse("Forecast: " + tr.content())); // (6)

    var reply = componentClient
      .forAgent()
      .inSession(UUID.randomUUID().toString())
      .method(WeatherAgent::query)
      .invoke("What is the weather in Stockholm?");

    assertThat(reply).startsWith("Forecast:");
    assertThat(reply).contains("sunny");
    assertThat(reply).contains("Stockholm");
  }
}
```

| **1** | Extend `TestKitSupport` to gain access to testing utilities for Akka components. |
| **2** | Create a `TestModelProvider`. Use a separate instance per agent for distinct mock behavior. |
| **3** | Register the test model provider in `testKitSettings()` to replace the agent’s real `ModelProvider`. |
| **4** | The simplest case: `fixedResponse` always returns the same string. The agent never calls its tools because the model produces a direct answer. |
| **5** | When the model should drive a tool call, reply with a `ToolInvocationRequest`. The runtime invokes the actual tool method and feeds the result back into the model loop. |
| **6** | `whenToolResult(…​).thenReply(…​)` runs when the model receives a tool result. Inspect `tr.name()` and `tr.content()` to build the next response. |
The example mocks the `WeatherAgent` from the multi-agent sample. The bootstrap wires a `FakeWeatherService` whenever the `WEATHER_API_KEY` environment variable is unset, so the tool runs deterministically against a fake. The tool name the model sees is prefixed with the simple class name of the registered tool, here `FakeWeatherService_getWeather`. For agent-local tools annotated with `@FunctionTool` directly on the agent class, the prefix is the agent’s simple class name.

You can also use `whenMessage(predicate).reply(response)` for conditional text responses that vary based on the user message. Invoke the agent through the `componentClient` and assert on the result as in any other integration test.

## <a href="about:blank#_mocked_model_in_a_deployed_service"></a> Mocked model in a deployed service

In some scenarios it can be useful to run the service deployed but without interacting with an actual agent. For example,
a load test that exercises the service with heavy load to verify scalability could quickly consume a large number of tokens
when the exact answer from the model is not very important, one or a few different predefined responses and responding with
a slight delay to simulate model processing time could be good enough.

It is possible to implement a custom model provider using `akka.javasdk.agent.ModelProvider.Custom`, such a mock provider
however, side steps quite a bit of the infrastructure involved in agent interactions, a more realistic mock model can be implemented
by building a separate Akka service with a single [HTTP endpoint](../http-endpoints.html) mimicking the model endpoint and
configuring the deployed agentic service to use that.

Here is an example endpoint returning a static response over the OpenAI protocol:

[MockOpenAI.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/MockOpenAI.java)
```java
@HttpEndpoint
@Acl(allow = { @Acl.Matcher(service = "*") })
public class MockOpenAI extends AbstractHttpEndpoint {

  private static final long MIN_DELAY_MILLIS = 2000;
  private static final long MAX_DELAY_MILLIS = 3000;
  private static final long DELAY_SPAN = MAX_DELAY_MILLIS - MIN_DELAY_MILLIS;

  private static final HttpResponse staticResponse = HttpResponse.create()
    .withStatus(StatusCodes.OK)
    .withEntity(
      HttpEntities.create(
        ContentTypes.APPLICATION_JSON,
        """
        { "id": "chatcmpl-Byz9msOuInWGiYmFJR8eH7ei2S3d0",
          "object": "chat.completion",
          "created": 1753874466,
          "model": "gpt-4o-mini-2024-07-18",
           "choices": [
           {
             "index": 0,
             "message": {
               "role": "assistant",
               "content": "Some hardcoded result",
               "refusal": null,
               "annotations": []
             },
             "logprobs": null,
             "finish_reason": "stop"
           }],
           "usage": {
             "prompt_tokens": 29,
             "completion_tokens": 264,
             "total_tokens": 293,
             "prompt_tokens_details": {
               "cached_tokens": 0,
               "audio_tokens": 0
             },
             "completion_tokens_details": {
               "reasoning_tokens": 0,
               "audio_tokens": 0,
               "accepted_prediction_tokens": 0,
               "rejected_prediction_tokens": 0
             }
           },
           "service_tier": "default",
           "system_fingerprint": "fp_197a02a720"
        }"""
      )
    )
    .withHeaders(
      Arrays.asList(
        RawHeader.create("x-request-id", "537dc248-255e-49eb-8799-fcc11a8b6cf0"),
        RawHeader.create("x-ratelimit-limit-tokens", "2000000"),
        RawHeader.create("openai-organization", "abc-123123"),
        RawHeader.create("openai-version", "20200-01"),
        RawHeader.create("openai-processing-ms", "5916"),
        RawHeader.create("openai-project", "proj_1234567abcdef")
      )
    );

  @Post("/chat/completions")
  public HttpResponse completion(HttpEntity.Strict ignoredRequestBody) throws Exception {
    var delay = MIN_DELAY_MILLIS + ThreadLocalRandom.current().nextLong(DELAY_SPAN);
    Thread.sleep(delay);
    return staticResponse;
  }
}
```
For more elaborate scenarios, the mock model endpoint may have to parse the request to decide which hard coded answer out of a few
or to create a reply in a more dynamic fashion.

Deploying this service as `mock-openai` allows other services containing agents in the same [Akka project](../../operations/projects/index.html).
Using the deployed mock service from an agent in another service can be done with a config like this:

application.conf
```hocon
akka.javasdk {
  agent {
    model-provider = openai

    openai {
      model-name = "gpt-4o-mini"
      base-url = "http://mock-openai" // (1)
    }
  }
}
```

1. The service name the mock was deployed as.
Note that you should use `http`, and not `https`, the connection will be encrypted with TLS, but that is handled by the platform.

## <a href="about:blank#_log_model_request_and_response"></a> Log model request and response

To see exactly what is sent to and received from the AI model, you can enable the following logger in `include-dev-loggers.xml`:

```none
<logger name="kalix.runtime.agent.AkkaLangChain4jHttpClient" level="TRACE"/>
```

<!-- <footer> -->
<!-- <nav> -->
[LLM evaluation](llm_eval.html) [Autonomous Agents](../autonomous-agents.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->