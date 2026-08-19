<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Autonomous Agents](../autonomous-agents.html)
- [Tasks](tasks.html)

<!-- </nav> -->

# Tasks

An autonomous agent is a process. It runs, has a definition, and handles work, but has no result type of its own. A task is a separate persistent entity: a typed unit of work with its own identity, result schema, and lifecycle, independent of any agent.

Because tasks exist independently, they can be handed off between agents, queried by external clients, and managed with their own lifecycle. An agent can be stopped and restarted without affecting task state. Multiple tasks can be assigned to a single agent, or a single task can move through several agents via handoff. The task defines what the result should look like; the agent provides the capability to produce it.

Tasks also drive coordination. They flow between agents, and the coordination patterns of delegation, handoff, and teams all operate through task creation, assignment, and completion.

## <a href="about:blank#_defining_tasks"></a> Defining tasks

Task definitions are immutable constants, typically declared as `static final` fields. A definition specifies the task name, a description of what kind of work it represents, and the expected result type.

[ResearchTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/research/application/ResearchTasks.java)
```java
public class ResearchTasks {

  public static final Task<ResearchBrief> BRIEF = Task
    .name("Brief")
    .description("Produce a research brief on a given topic")
    .resultConformsTo(ResearchBrief.class);

  public static final Task<ResearchFindings> FINDINGS = Task
    .name("Findings")
    .description("Research a topic and produce factual findings")
    .resultConformsTo(ResearchFindings.class)
    .rules(ResearchFindingsRule.class);

  public static final Task<AnalysisReport> ANALYSIS = Task
    .name("Analysis")
    .description("Analyse a topic and produce a trend analysis report")
    .resultConformsTo(AnalysisReport.class);
}
```
The result type is a Java record. The model’s structured output is validated against this schema, and the typed result is available when querying the completed task.

[ResearchBrief.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/research/application/ResearchBrief.java)
```java
/** Typed result for research brief tasks. */
public record ResearchBrief(String title, String summary, List<String> keyFindings) {}
```
[ResearchFindings.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/research/application/ResearchFindings.java)
```java
/** Result type for the researcher agent. */
public record ResearchFindings(String topic, List<String> facts, List<String> sources) {}
```
[AnalysisReport.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/research/application/AnalysisReport.java)
```java
/** Result type for the analyst agent. */
public record AnalysisReport(String topic, String assessment, List<String> trends) {}
```
If `resultConformsTo` is not declared on a task definition, the result type defaults to `String`. The task completes with the model’s free-form text response and no schema validation is applied.

## <a href="about:blank#task-rules"></a> Task rules

A task can declare one or more rules that validate the structured result before completion is accepted. Rules implement `TaskRule<R>` where `R` is the task’s result type. When the task is about to complete, each rule’s `onComplete` method is called with the deserialized result. If any rule returns `Result.Rejected`, the task transitions to `RESULT_REJECTED`. On the next iteration the runtime injects a reminder into the model’s context explaining that the previous result was rejected and why, so the agent can correct and resubmit. If the agent never satisfies the rules within its iteration limit, the task transitions to `FAILED`.

[ResearchFindingsRule.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/research/application/ResearchFindingsRule.java)
```java
/** Validates that research findings include at least one cited source. */
public class ResearchFindingsRule implements TaskRule<ResearchFindings> {

  @Override
  public Result onComplete(ResearchFindings findings) {
    if (findings.sources() == null || findings.sources().isEmpty()) {
      return new Result.Rejected(
        "sources must not be empty — research findings must cite sources"
      );
    }
    return new Result.Accepted();
  }
}
```
Attach rules to a task definition with `.rules(…​)`:

[ResearchTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/research/application/ResearchTasks.java)
```java
public static final Task<ResearchFindings> FINDINGS = Task
  .name("Findings")
  .description("Research a topic and produce factual findings")
  .resultConformsTo(ResearchFindings.class)
  .rules(ResearchFindingsRule.class);
```
Rules are evaluated in declaration order. The first rejection short-circuits and reports its reason. Rule implementations must have a public no-arg constructor.

## <a href="about:blank#_creating_task_instances"></a> Creating task instances

Task definitions are templates. To create an actual task instance, add per-request details (instructions and optional attachments) to a definition. The definition itself is unchanged because all methods return new instances.

[DocReviewEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/docreview/api/DocReviewEndpoint.java)
```java
var task = ReviewTasks.REVIEW
  .instructions(request.reviewInstructions())
  .attach(TextMessageContent.from(request.document()));
```
Attachments carry large content, such as a document, a transcript, or a JSON payload, without inflating the instruction text. The runtime delivers attachments to the model’s context alongside the instructions. Attachments travel with the task across agent boundaries: a handoff target sees the original task’s attachments, and a delegated worker sees the subtask’s attachments.

Attachments can also be images or PDFs, referenced by URI rather than inlined. The runtime resolves `http(s)://` and `object://` references automatically, and a custom `ContentLoader` handles other sources. See [Loading attachment content](defining.html#attachment-content) for details.

## <a href="about:blank#_task_templates"></a> Task templates

A `TaskTemplate` is a task definition with a parameterized instruction string. Placeholders use `{paramName}` syntax and are filled in when the template is turned into a submittable `Task`. Templates fit cases where the structure of the instructions is fixed and only a few values change per invocation. They are particularly useful when the model itself supplies the values at delegation or task-claim time.

[DeveloperTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/devteam/application/DeveloperTasks.java)
```java
public class DeveloperTasks {

  public static final TaskTemplate<CodeDeliverable> IMPLEMENT = TaskTemplate
    .define("Implement")
    .description("Implement a feature with clean, tested code")
    .resultConformsTo(CodeDeliverable.class)
    .instructionTemplate("Implement: {feature}. Requirements: {requirements}.");
}
```
Resolve a template into a `Task` in one of two ways:

- `params(Map<String, String>)` substitutes the named placeholders. Missing parameters throw `IllegalArgumentException`.
- `instructions(String)` discards the template and uses free-form instructions instead, the same as for a regular `Task`.
[ClientApiSample.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/test/java/demo/docs/ClientApiSample.java)
```java
var task = DeveloperTasks.IMPLEMENT.params(
  Map.of(
    "feature",
    "rate limiter",
    "requirements",
    "10 requests per second per user, with 1-minute window"
  )
);
```
A `TaskTemplate` is a `TaskDefinition` and can be passed wherever a `Task` definition is accepted (`TaskAcceptance.of(…​)`, delegation tools, the team task list). When a coordinator’s or team-lead’s model creates a task from a template, the runtime presents the template parameters as structured tool arguments, so the model fills them in directly.

## <a href="about:blank#_task_lifecycle"></a> Task lifecycle

A task reaches a terminal state in one of three ways during execution. The model decides the work is done and produces a result that conforms to the task’s declared result schema, and the task transitions to `COMPLETED`. The model decides it cannot make progress and reports a reason, and the task transitions to `FAILED`. Or the iteration limit is reached without either decision, in which case the runtime terminates the task as `FAILED`. A separate `CANCELLED` state covers tasks terminated before execution begins, such as a dependency failure. The status field on the task entity records which path was taken.

A task progresses through these statuses:

| Status | Description |
| --- | --- |
| `PENDING` | Created but not yet assigned to an agent |
| `ASSIGNED` | Assigned to an agent but not yet started |
| `IN_PROGRESS` | An agent is actively working on it |
| `RESULT_REJECTED` | A [task rule](about:blank#task-rules) rejected the result. The agent retries on the next iteration. |
| `COMPLETED` | Finished successfully with a typed result |
| `FAILED` | Failed during execution (model decision or iteration limit) |
| `CANCELLED` | Terminated before execution began (for example, by a dependency failure) |

### <a href="about:blank#_task_failure"></a> Task failure

A task transitions to `FAILED` when the agent decides it cannot complete the work, or when the framework terminates it after the iteration limit. In both cases:

- Sibling tasks queued on the same agent are unaffected and continue in order.
- The agent itself keeps running; it does not stop because of a task failure. Agents started with `runSingleTask` still auto-stop once their queue drains, as that is their normal completion behavior.
- The task is terminal, there is no automatic retry. If the work needs to be reattempted, application code creates a new task.
- Tasks depending on the failed task transition to `CANCELLED` automatically.
Both failure paths (model decision and iteration-limit termination) emit `Notification.TaskFailed`; the recorded failure reason distinguishes them. `Notification.TaskCancelled` is emitted only for the separate `CANCELLED` state, when a task is terminated before it begins, for example by a dependency failure. The task snapshot reports the resulting status. See [Notifications](notifications.html) for the full event catalog.

## <a href="about:blank#_task_snapshots"></a> Task snapshots

Query a task’s current state with the `ComponentClient`. The snapshot includes the status, description, instructions, typed result (if completed), and failure reason (if failed).

[ClientApiSample.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/test/java/demo/docs/ClientApiSample.java)
```java
var snapshot = componentClient.forTask(taskId).get(ReviewTasks.REVIEW);
// PENDING, ASSIGNED, IN_PROGRESS, RESULT_REJECTED, COMPLETED, FAILED, or CANCELLED
TaskStatus status = snapshot.status();
// present if completed
Optional<ReviewResult> result = snapshot.result();
// present if failed
Optional<String> reason = snapshot.failureReason();
```

## <a href="about:blank#_task_dependencies"></a> Task dependencies

Tasks can declare dependencies on other tasks. A task with dependencies will not be started by the agent until all dependencies have completed. This enables pipeline patterns where work flows through ordered phases.

[PipelineEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/pipeline/api/PipelineEndpoint.java)
```java
// Create collect task (no dependencies)
var collectTaskId = componentClient
  .forTask(UUID.randomUUID().toString())
  .create(PipelineTasks.COLLECT.instructions("Collect data on: " + request.topic()));

// Create analyze task (depends on collect)
var analyzeTaskId = componentClient
  .forTask(UUID.randomUUID().toString())
  .create(
    PipelineTasks.ANALYZE.instructions("Analyze data for: " + request.topic()).dependsOn(
      collectTaskId
    )
  );

// Create report task (depends on analyze)
var reportTaskId = componentClient
  .forTask(UUID.randomUUID().toString())
  .create(
    PipelineTasks.REPORT.instructions("Write report for: " + request.topic()).dependsOn(
      analyzeTaskId
    )
  );
```
Dependencies are specified by task id. The depended-on task must already exist when the dependent is created or assigned. You can create it explicitly via `componentClient.forTask(taskId).create(…​)`, or implicitly through `runSingleTask(…​)` or `assignTasks(…​)`, which create the task before assigning it.

Once the tasks are created, they can be assigned to a single agent instance. The agent processes them in dependency order:

[PipelineEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/pipeline/api/PipelineEndpoint.java)
```java
componentClient
  .forAutonomousAgent(ReportAgent.class, agentInstanceId)
  .assignTasks(collectTaskId, analyzeTaskId, reportTaskId);
```
If a dependency fails or is cancelled, dependents are cancelled automatically.

## <a href="about:blank#_task_notifications"></a> Task notifications

The task entity publishes a `TaskNotification` on every terminal transition so the runtime can observe completion without polling. See [Task entity notifications](notifications.html#task-entity-notifications) for the full catalog.

Subscribe to the notification stream of a task from application code via `componentClient.forTask(taskId).notificationStream()`. The returned `Source` emits `TaskNotification` events as the task reaches its terminal state. A common pattern is to bridge the stream to the browser as Server-Sent Events from an HTTP endpoint, so a UI can react to task completion without polling:

[TaskNotificationEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/TaskNotificationEndpoint.java)
```java
@HttpEndpoint("/tasks")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TaskNotificationEndpoint {

  private final ComponentClient componentClient;

  public TaskNotificationEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/notifications/{taskId}")
  public HttpResponse notifications(String taskId) {
    var source = componentClient
      .forTask(taskId)
      .notificationStream() // (1)
      .map(Note::from); // (2)
    return HttpResponses.serverSentEvents(source);
  }

  public record Note(String kind, String taskId, String taskName, String detail) { // (3)
    static Note from(TaskNotification n) {
      return switch (n) {
        case TaskNotification.Completed c -> new Note(
          "completed",
          c.taskId(),
          c.taskName(),
          c.result()
        );
        case TaskNotification.ResultRejected r -> new Note(
          "result-rejected",
          r.taskId(),
          r.taskName(),
          r.reason()
        );
        case TaskNotification.Failed f -> new Note(
          "failed",
          f.taskId(),
          f.taskName(),
          f.reason()
        );
        case TaskNotification.Cancelled c -> new Note(
          "cancelled",
          c.taskId(),
          c.taskName(),
          c.reason()
        );
      };
    }
  }
}
```

| **1** | Subscribe to the task’s notification stream. |
| **2** | Map each `TaskNotification` to a public representation so the wire shape is decoupled from the SDK record. |
| **3** | A small wire shape with a `kind` discriminator lets clients handle the four terminal cases without depending on SDK types. |

## <a href="about:blank#_see_also"></a> See also

- [Client API](client.html)
- [Notifications](notifications.html)
- [Agent definition](defining.html)

<!-- <footer> -->
<!-- <nav> -->
[Agent definition](defining.html) [Coordination patterns](coordination.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->