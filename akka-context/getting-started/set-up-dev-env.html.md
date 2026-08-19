<!-- <nav> -->
- [Akka](../index.html)
- [Getting Started](index.html)
- [Set up your dev env](set-up-dev-env.html)

<!-- </nav> -->

# Set up your dev env

There are three ways to set up an Akka development environment. Pick the one that fits how you want to work. Each path is self-contained and lists its own prerequisites.

- [Set up your AI harness](about:blank#_set_up_your_ai_harness) — install the Akka Specify Plugin and let it install and configure everything else.
- [Set up your env without AI](about:blank#_set_up_your_env_without_ai) — install the Akka CLI, Java, and Maven directly.
- [Containerized dev environment](about:blank#_containerized_dev_environment) — run everything in a Docker container, with no local Java, Maven, or CLI install.
Every tutorial and sample links back to the path it needs, so you set up once here and refer to it from everywhere else.

## <a href="about:blank#_set_up_your_ai_harness"></a> Set up your AI harness

Install the Akka Specify Plugin in your AI coding assistant. From there the plugin installs and configures the rest of the tools for you.

Prerequisites
- A supported AI coding assistant (Claude Code recommended; Akka supports 30 AI-assist tools).
- An [Akka download token](https://account.akka.io/token) (free).

### <a href="about:blank#_install_the_plugin"></a> Install the plugin

Akka installs into your AI coding harness through that harness’s own plugin mechanism. Select your harness:

- Claude Code
- Gemini CLI
- Codex CLI
- Cursor, VS Code + Copilot

```none
/plugin marketplace add akka/ai-marketplace
/plugin install akka@ai-marketplace
/reload-plugins
```
Can’t add the marketplace? Clone the repository and add it as a local marketplace instead:

```none
git clone https://github.com/akka/ai-marketplace.git
/plugin marketplace add /path/to/ai-marketplace
```

```none
gemini extensions install https://github.com/akka/ai-marketplace
```

```none
codex plugin marketplace add akka/ai-marketplace
codex plugin add akka@akka
```
Codex exposes the commands as skills. Where this documentation shows `/akka:specify`, use the `akka-specify` skill (`@akka-specify`, or ask Codex to run it).

```none
akka specify init --agent cursor
```
Use `--agent vscode-copilot` for VS Code with Copilot. Cursor has no slash commands — the Akka tools are available over MCP; where this documentation shows `/akka:specify`, ask the agent to run that step.

This installs the Akka commands and registers the Akka MCP server, giving your harness the full Akka toolset (CLI, MCP server, AI coding assistant, and project scaffolding).

### <a href="about:blank#_update_the_plugin"></a> Update the plugin

Keep the Akka Specify Plugin up to date with the latest commands and fixes.

Update the Akka Specify Plugin through that harness’s own plugin mechanism. Select your harness:

- Claude Code
- Gemini CLI
- Codex CLI
- Cursor, VS Code + Copilot

```none
/plugin marketplace update
/plugin update akka
```

```none
gemini extensions update ai-marketplace
```

```none
codex plugin marketplace upgrade ai-marketplace
```
Cursor and VS Code + Copilot use the Akka CLI directly over MCP, with no plugin marketplace to update. Update the Akka CLI itself to get the latest version; see [Install the Akka CLI](../operations/cli/installation.html) for the upgrade command on your platform.

### <a href="about:blank#_configure_your_environment"></a> Configure your environment

```none
/akka:setup
```
This ensures the Akka CLI is installed, Java and Maven are available, and your Akka download token is configured.

### <a href="about:blank#_choose_your_mode"></a> Choose your mode

The Akka Specify plugin runs in one of two modes:

- **À la carte** (the default) lets you run the specification commands individually, with nothing blocking you — faster and lighter.
- **Enforced** makes you define "done" as machine-checkable exit conditions before code is written, and gates shipping until they are met — a cleaner, auditable result. Opt in when you want that rigor.
New installs start in **À la carte** mode. Switch at any time:

```none
/akka:mode a-la-carte
/akka:mode enforced
```
See [Choosing your mode](../sdk/spec-driven-development.html#choosing-your-mode) for the trade-offs. Then build your first agent with the [Spec-first hello agent](spec-your-first-agent.html) tutorial, which walks the same build in either mode.

## <a href="about:blank#_set_up_your_env_without_ai"></a> Set up your env without AI

Install the tools directly on your machine. Use this path if you do not want an AI harness, or your AI-assist tool does not support plugins.

Prerequisites
- Java 25 (see [Adoptium](https://adoptium.net/marketplace/)).
- Apache Maven 3.9 or later.
- An [Akka download token](https://account.akka.io/token) (free).

### <a href="about:blank#_install_the_akka_cli"></a> Install the Akka CLI

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

### <a href="about:blank#_create_a_project"></a> Create a project

Create a new project from a sample and start building:

```bash
akka code init --name helloworld-agent --repo akka-samples/helloworld-agent.git
```
To use spec-driven development without an AI plugin, use `akka specify init <dir>` instead — see [Spec-driven development](../sdk/spec-driven-development.html). Then continue with [Code-first hello agent](author-your-first-service.html).

## <a href="about:blank#_containerized_dev_environment"></a> Containerized dev environment

Run Akka inside a pre-built Docker container. Nothing but Docker is installed on your machine — the container bundles Java 25, Maven, the Akka CLI, and pre-cached SDK dependencies.

Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/).
- An [Akka download token](https://account.akka.io/token) (free).

### <a href="about:blank#_set_environment_variables"></a> Set environment variables

The download token is required. AI provider keys are optional and only needed for agent samples.

```bash
export AKKA_RESOLVER_TOKEN=<your-token>       # required
export GOOGLE_AI_GEMINI_API_KEY=<your-key>    # optional, for agent samples
export ANTHROPIC_API_KEY=<your-key>           # optional, for agent samples
export OPENAI_API_KEY=<your-key>              # optional, for agent samples
```

### <a href="about:blank#_create_a_project_and_start_the_container"></a> Create a project and start the container

1. Create a new project from a sample:

```bash
akka code init --name helloworld-agent --repo akka-samples/helloworld-agent.git
```
2. Start the dev container with the project directory mounted:

```bash
docker run -d --name akka-dev \
  -v "$(pwd)/helloworld-agent":/workspace \
  -p 9000:9000 \
  -p 9889:9889 \
  -e AKKA_RESOLVER_TOKEN \
  -e GOOGLE_AI_GEMINI_API_KEY \
  -e ANTHROPIC_API_KEY \
  -e OPENAI_API_KEY \
  registry.akka.io/akka-dev-container:latest
```
This forwards `localhost:9000` for your service endpoint and `localhost:9889` for the Akka local console.

### <a href="about:blank#_run_the_service"></a> Run the service

Build commands run inside the container with `docker exec`; your files stay on the host through the bind mount.

```bash
docker exec -w /workspace akka-dev mvn compile exec:java
```
Test it from your host:

```bash
curl -i -XPOST http://localhost:9000/hello \
  --header "Content-Type: application/json" \
  --data '{"user": "alice", "text": "Hello, I am Alice"}'
```
Start the local console with `docker exec -t -w /workspace akka-dev akka local console --bind-address 0.0.0.0`, then open [localhost:9889](http://localhost:9889/).

## <a href="about:blank#_see_also"></a> See also

- [Getting started](index.html)
- [Spec-driven development](../sdk/spec-driven-development.html)

<!-- <footer> -->
<!-- <nav> -->
[Getting Started](index.html) [Spec-first hello agent](spec-your-first-agent.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->