<!-- <nav> -->
- [Akka](../index.html)
- [Getting Started](index.html)
- [Code-first hello agent](author-your-first-service.html)

<!-- </nav> -->

# Code-first hello agent

|  | The recommended way to build your first agent is the [Spec-first hello agent](spec-your-first-agent.html) tutorial. The tutorial below walks through the manual, hands-on Java approach — ideal for understanding how Akka components work under the hood. |

## <a href="about:blank#_introduction"></a> Introduction

In this guide, you will:

- Set up your development environment.
- Clone a simple project that follows the [recommended structure](../concepts/architecture-model.html).
- Explore a basic AI Agent that acts as a creative greeter.
- Explore a basic HTTP Endpoint to interact with the agent.
- Add a request body to the Endpoint.
- Run your service locally.
- Explore the local console to observe your running service.

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Complete [Set up your env without AI](set-up-dev-env.html#_set_up_your_env_without_ai) — install the Akka CLI, Java, and Maven.
- Git.
- An [OpenAI API key](https://platform.openai.com/api-keys). This sample uses OpenAI; sign up for free at [platform.openai.com/api-keys](https://platform.openai.com/api-keys).

## <a href="about:blank#clone_sample"></a> Clone the sample project

1. From a command line, use the Akka CLI to create a new project. See [installation instructions](quick-install-cli.html) if you have not installed the CLI yet.

```command
akka code init --name helloworld-agent --repo akka-samples/helloworld-agent.git
```
2. Navigate to the new project directory.
3. Open it in your preferred IDE / Editor.
Alternatively, you can clone the [GitHub Repository](https://github.com/akka-samples/helloworld-agent) directly:

```command
git clone https://github.com/akka-samples/helloworld-agent.git --depth 1
```
Then navigate to the new project directory and open it in your preferred IDE / Editor, making sure to add [your Akka token](https://account.akka.io/token) to the pom.xml.

## <a href="about:blank#_explore_the_agent"></a> Explore the Agent

An *Agent* interacts with an AI model and maintains contextual history in a session memory.

1. Open the `src/main/java/com/example/application/HelloWorldAgent.java` file.
The *Agent* is implemented with:

HelloWorldAgent.java
```java
@Component(id = "hello-world-agent")
public class HelloWorldAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
    You are a cheerful AI assistant with a passion for teaching greetings in new language.

    Guidelines for your responses:
    - Start the response with a greeting in a specific language
    - Always append the language you're using in parenthesis in English. E.g. "Hola (Spanish)"
    - The first greeting should be in English
    - In subsequent interactions the greeting should be in a different language than
      the ones used before
    - After the greeting phrase, add one or a few sentences in English
    - Try to relate the response to previous interactions to make it a meaningful conversation
    - Always respond with enthusiasm and warmth
    - Add a touch of humor or wordplay when appropriate
    - At the end, append a list of previous greetings
    """.stripIndent();

  public Effect<String> greet(String userGreeting) {
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(userGreeting)
      .thenReply();
  }
}
```
The system message provides system-level instructions to the AI model that defines its behavior and context. The system message acts as a foundational prompt that establishes the AI’s role, constraints, and operational parameters. It is processed before user messages and helps maintain consistent behavior throughout the interactions.

The user message represents the specific query, instruction, or input that will be processed by the model to generate a response.

## <a href="about:blank#_explore_the_http_endpoint"></a> Explore the HTTP Endpoint

An *Endpoint* is a component that creates an externally accessible API. Endpoints are how you expose your services to the outside world. Endpoints can have different protocols, such as HTTP and gRPC.

HTTP Endpoint components make it possible to conveniently define such APIs accepting and responding in JSON, or dropping down to lower level APIs for ultimate flexibility in what types of data is accepted and returned.

1. Open the `src/main/java/com/example/api/HelloWorldEndpoint.java` file.
The *Endpoint* is implemented with:

HelloWorldEndpoint.java
```java
/**
 * This is a simple Akka Endpoint that uses an agent and LLM to generate
 * greetings in different languages.
 */
// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered,
// and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class HelloWorldEndpoint {

  public record Request(String user, String text) {}

  private final ComponentClient componentClient;

  public HelloWorldEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/hello")
  public String hello(Request request) {
    return componentClient
      .forAgent()
      .inSession(request.user)
      .method(HelloWorldAgent::greet)
      .invoke(request.text);
  }
}
```
The `ComponentClient` is the way to call the agent or other components. The agent may participate in a session, which is used for the agent’s memory and can also be shared between multiple agents that are collaborating on the same goal.

This Endpoint exposes an HTTP POST operation on `/hello`.

You can also see that there is an *Access Control List* (ACL) on this Endpoint that allows all traffic from the Internet. Without this ACL the service would be unreachable, but you can be very expressive with these ACLs.

## <a href="about:blank#_run_locally"></a> Run locally

Set your [OpenAI API key](https://platform.openai.com/api-keys) as an environment variable:

Linux or macOS
```command
export OPENAI_API_KEY=your-openai-api-key
```
Windows 10+
```command
set OPENAI_API_KEY=your-openai-api-key
```
Start your service locally:

```command
mvn compile exec:java
```
Once successfully started, any defined Endpoints become available at `localhost:9000` and you will see an INFO message that the Akka Runtime has started.

Your "Hello World" service is now running.

In another shell, you can now use `curl` to send requests to this Endpoint.

```command
curl -i -XPOST --location "http://localhost:9000/hello" \
    --header "Content-Type: application/json" \
    --data '{"user": "alice", "text": "Hello, I am Alice"}'
```
Which will reply with an AI-generated greeting, such as:

```none
Hello (English)! So great to meet you, Alice! I'm here to add some zest to our conversation
with greetings from around the world. Let's have some fun learning them together!
Feel free to ask about anything else too!

Previous greetings:
- Hello (English)
```
Try it a few more times with different text messages, for example:

```command
curl -i -XPOST --location "http://localhost:9000/hello" \
    --header "Content-Type: application/json" \
    --data '{"user": "alice", "text": "I live in New York"}'
```
The AI-generated reply might be:

```none
Bonjour (French)! Ah, New York, the city that never sleeps! It's almost like you need a
coffee the size of the Eiffel Tower to keep up with it.
What's your favorite thing about living in such a vibrant city?

Previous greetings:
- Hello (English)
- Bonjour (French)
```

```command
curl -i -XPOST --location "http://localhost:9000/hello" \
    --header "Content-Type: application/json" \
    --data '{"user": "alice", "text": "I like the botanical garden"}'
```

```none
¡Hola (Spanish)! The botanical garden in New York must be a refreshing oasis amidst the
hustle and bustle of the city. It's like taking a nature-themed vacation with just
subway ride! Do you have a favorite plant or flower that you like to see there?

Previous greetings:
- Hello (English)
- Bonjour (French)
- ¡Hola (Spanish)
```

|  | What just happened?

The greetings will be in different languages each time. The AI model itself is stateless, so it would not know what languages it had used previously unless we included that information in each request to the model. Akka Agents automatically track context using **session memory**. In this case, the Agent is able to remember all the past messages and languages that were used in this session.

Here we use the user `alice` as the session identifier. Give it a try to change the user field in the HTTP request, and you will see that it starts over without previous knowledge about Alice or the used languages. |

## <a href="about:blank#_change_the_agent_prompt"></a> Change the agent prompt

In this section, you will modify the instructions for the agent and see how it changes behavior. Open the `HelloWorldAgent.java` file and edit the `SYSTEM_MESSAGE`. For example, you can add the following to the guidelines:

HelloWorldAgent.java
```java
- Include some interesting facts
```
Restart the service and use curl again:

```command
curl -i -XPOST --location "http://localhost:9000/hello" \
    --header "Content-Type: application/json" \
    --data '{"user": "blackbeard", "text": "Ahoy there, matey! My name is Blackbeard"}'
```
Does it recognize the pirate greeting and include some facts about Blackbeard?

Something like:

```none
Hello, Blackbeard! (English)

What a fantastic name you have! It's not every day I get to chat with a legendary pirate.
So tell me, do you sail the high seas or do you prefer to dock at the local coffee shop
for a pirate-themed chai latte?

Previous greetings:
1. Hello (English)

Did you know that the famous pirate Blackbeard has a fascinating history? He was known for
his fearsome appearance, often lighting slow-burning fuses in his beard during battles to
create an intimidating aura! Arrr!
```

## <a href="about:blank#_explore_the_local_console"></a> Explore the local console

The Akka local console is a web-based tool that comes bundled with the Akka CLI. It provides a convenient way to view and interact with your running service.

### <a href="about:blank#_install_the_akka_cli"></a> Install the Akka CLI

Starting the local console requires using the Akka CLI.

|  | In case there is any trouble with installing the CLI when following these instructions, please check the [detailed CLI installation instructions](../operations/cli/installation.html). |
Linux Install the `akka` CLI using the Debian package repository:

```bash
curl -1sLf \
  'https://downloads.akka.io/setup.deb.sh' \
  | sudo -E bash
sudo apt install akka
```
macOS The recommended approach to install `akka` on macOS, is using [brew](https://brew.sh/)

```bash
brew install akka/brew/akka
```
Windows Install the `akka` CLI using [winget](https://learn.microsoft.com/en-us/windows/package-manager/winget/):

```powershell
winget install Akka.Cli
```

|  | By downloading and using this software you agree to Akka’s [Privacy Policy](https://akka.io/legal/privacy) and [Software Terms of Use](https://trust.akka.io/cloud-terms-of-service). |
Verify that the Akka CLI has been installed successfully by running the following to list all available commands:

```command
akka help
```

### <a href="about:blank#_start_the_local_console"></a> Start the local console

1. Start the local console.

```bash
akka local console
```

```bash
⠸ Waiting for services to come online...

────────────────────────────────────────────────────────────
Local console: http://localhost:9889
(use Ctrl+C to quit)
```
2. Once the console and service is running, you will see a message like this:

```bash
───────────────────────────────────────────────────────────────────────
│ SERVICE                      │ STATE    │ ADDRESS                   |
───────────────────────────────────────────────────────────────────────
│ helloworld-agent             │ Running  │ localhost:9000            │
───────────────────────────────────────────────────────────────────────
Local console: http://localhost:9889
(use Ctrl+C to quit)
```
3. You can then access the local console in your browser at:

[http://localhost:9889](http://localhost:9889/)
4. Navigate to your service’s Endpoint, which will be available [here](http://localhost:9889/services/helloworld-agent/components/com.example.api.HelloWorldEndpoint).

![Hello World](_images/hello-world-local-console.png)


You can also see the details of the session in the `SessionMemoryEntity`.

![Session memory in the Local Console](_images/hello-world-session-memory.png)


If you are curious about which components are called and for how long when you make a request of this agent, you can use the request creation console feature. Simply click on the `HelloworldEndpoint` and you will see the *Create a request* box. Fill in the fields the way you might if you were manually using `curl`. After the request executes (it could take a few seconds), you will see an analysis of the request, as shown in this screenshot:

![An HTTP post calls the agent](_images/flowview_helloworld.png)


A key insight to take away from this image is that 99.9% of the request’s execution time was spent talking to OpenAI.

This is a simple Hello World service, so there is not much to see here yet. However, as you build more complex services, the console will become a more valuable tool for monitoring and debugging.

## <a href="about:blank#_next_steps"></a> Next steps

Now that you have a basic service running, it is time to learn more about building real services in Akka.

- See the [Spec-first greeting agent](spec-your-first-agent.html) sample if you use an AI assistant and want to see a [Spec-Driven Development](../sdk/spec-driven-development.html) alternative to this example.
- See [multi-agent planner](planner-agent/index.html) to build a more realistic application.
- [Deploy to akka.io](quick-deploy.html)

## <a href="about:blank#_see_also"></a> See also

- [Spec-driven development](../sdk/spec-driven-development.html)
- [Build an AI multi-agent planner](planner-agent/index.html)

<!-- <footer> -->
<!-- <nav> -->
[Spec-first hello agent](spec-your-first-agent.html) [Multi-agent tutorial](planner-agent/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->