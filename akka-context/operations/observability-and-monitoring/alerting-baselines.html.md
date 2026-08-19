<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Observability and monitoring](index.html)
- [Alerting baselines](alerting-baselines.html)

<!-- </nav> -->

# Alerting baselines

Setting up alerts on key metrics helps detect issues before they impact users. The following are suggested baselines for common Akka components.

|  | These thresholds are starting points — tune them based on your workload characteristics and operational experience. |

|  | For a full list of available metrics and their attributes, see the [Telemetry metrics reference](../../reference/telemetry/metrics.html). To set up metric exports to your monitoring system, see [Exporting metrics, logs, and traces](observability-exports.html). |

## <a href="about:blank#_http_endpoints"></a> HTTP Endpoints

| Alert condition | Metric | Suggested threshold |
| --- | --- | --- |
| Failing requests rate | `http.server.request.duration` (filter where `error.type` attribute is present) | > 1 req/s |
| Processing time p99 | `http.server.request.duration` (p99) | > 200 ms |

|  | For streaming HTTP endpoints, the duration metric measures the full stream lifetime, so the p99 threshold above may not be meaningful. Focus on the failing requests rate for streaming endpoints instead. |

## <a href="about:blank#_grpc_endpoints"></a> gRPC Endpoints

| Alert condition | Metric | Suggested threshold |
| --- | --- | --- |
| Failing requests rate | `rpc.server.call.duration` (filter where `error.type` attribute is present) | > 1 req/s |
| Call duration p99 | `rpc.server.call.duration` (p99) | > 200 ms |

|  | For gRPC streaming calls, the duration metric measures the full stream lifetime, so the p99 threshold above may not be meaningful. Focus on the failing requests rate for streaming endpoints instead. |

## <a href="about:blank#_agent_components"></a> Agent components

| Alert condition | Metric | Suggested threshold |
| --- | --- | --- |
| Commands failed rate | `akka.agent.command.duration` (filter where `error.type` attribute is present) | > 1 cmd/s |
| Tools failed rate | `gen_ai.client.operation.duration` (filter where `gen_ai.operation.name="execute_tool"` and `error.type` is present) | > 1 cmd/s |
| Content load duration p99 | `akka.agent.content_load.duration` (p99) | > 2 s (depends on content source) |

## <a href="about:blank#_event_sourced_entities"></a> Event Sourced Entities

|  | Event Sourced Entity metrics use `akka.component.type="event-sourced-entity"` to distinguish them from Key Value Entity metrics, which share the same metric names. |

| Alert condition | Metric | Suggested threshold |
| --- | --- | --- |
| Persist time p99 | `akka.entity.persist.duration` (p99, filter `akka.component.type="event-sourced-entity"`) | > 100 ms (average over 60s) |
| Processing time p99 | `akka.entity.command.processing.duration` (p99, filter `akka.component.type="event-sourced-entity"`) | > 100 ms (average over 60s) |

|  | For Event Sourced Entities, occasional spikes in persist or processing time are expected (for example, during snapshot creation). Using a 60-second averaging window helps filter out these transient peaks. |

## <a href="about:blank#_key_value_entities"></a> Key Value Entities

|  | Key Value Entity metrics use `akka.component.type="key-value-entity"` to distinguish them from Event Sourced Entity metrics, which share the same metric names. |

| Alert condition | Metric | Suggested threshold |
| --- | --- | --- |
| Persist time p99 | `akka.entity.persist.duration` (p99, filter `akka.component.type="key-value-entity"`) | > 100 ms (average over 60s) |
| Processing time p99 | `akka.entity.command.processing.duration` (p99, filter `akka.component.type="key-value-entity"`) | > 100 ms (average over 60s) |

## <a href="about:blank#_workflows"></a> Workflows

| Alert condition | Metric | Suggested threshold |
| --- | --- | --- |
| Commands failed rate | `akka.workflow.command.duration` (filter where `error.type` attribute is present) | > 1 req/s |
| Steps failed rate | `akka.workflow.step.duration` (filter where `error.type` attribute is present) | > 1 req/s |
| Active workflow count | `akka.workflow.active` | Set a reasonable max for your workload (sustained counts above the expected maximum may indicate stuck workflows) |
| Recovery duration p99 | `akka.workflow.recovery.duration` (p99) | > 1 s |

## <a href="about:blank#_views"></a> Views

| Alert condition | Metric | Suggested threshold |
| --- | --- | --- |
| Query duration p99 | `akka.view.query.duration` (p99) | > 500 ms |
| Update duration p99 | `akka.view.update.duration` (p99) | > 500 ms |

## <a href="about:blank#_consumers"></a> Consumers

| Alert condition | Metric | Suggested threshold |
| --- | --- | --- |
| Events consumption lag (max) | `akka.projection.consumer.lag` (max) | > 1 min |
| Events processing time (avg) | `akka.consumer.message.duration` (average) | > 1 s |
| Subscriptions failed | `akka.eventing.stream.failed` (counter) | > 0 |

## <a href="about:blank#_replication"></a> Replication

|  | These metrics are only applicable to multi-region deployments. |

| Alert condition | Metric | Suggested threshold |
| --- | --- | --- |
| Replication lag (avg) | `akka.projection.consumer.lag` (average, filter `akka.projection.type="replicated-event-sourced-projection"`) | > 10 s |

## <a href="about:blank#_timers_and_timed_actions"></a> Timers and Timed Actions

| Alert condition | Metric | Suggested threshold |
| --- | --- | --- |
| Timer call failures | `akka.timer.call.duration` (filter where `error.type` attribute is present) | > 0 |
| Timed action failures | `akka.timed_action.message.duration` (filter where `error.type` attribute is present) | > 0 |

<!-- <footer> -->
<!-- <nav> -->
[Exporting metrics, logs, and traces](observability-exports.html) [Integrating with CI/CD tools](../integrating-cicd/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->