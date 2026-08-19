<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Production readiness](index.html)

<!-- </nav> -->

# Production readiness

## <a href="about:blank#_overview"></a> Overview

Production readiness is a partnership with Akka, not something you do alone. Akka provides the platform and years of experience operating distributed systems in production at scale; your team owns a clearly defined set of activities. How much of the running and the reliability each side owns is set out in a Shared Responsibility Model, and shifts with the [deployment model](../../concepts/deployment-model.html) you choose.

Akka’s team embeds with yours from onboarding through ongoing operations. Getting to production is demanding, and it is where that experience earns its place: the disciplines below are the shape of what it has shown to be necessary, worked through with you rather than discovered under pressure. If you want this experience behind your own path to production, [talk to Akka](https://akka.io/contact-us).

|  | **Reaching the reliability and availability your deployment is built for is part of this partnership.** It assumes both sides have met their responsibilities in the Shared Responsibility Model, and the readiness items on your side are how those guarantees are met, worked through with your Akka team. Your agreement, and the Federation Plane SLA Policy it references, remain the authoritative statement of what is committed and the conditions attached. |

## <a href="about:blank#_production_readiness"></a> Production readiness

Production readiness spans a lot, and that breadth is the point: these are the areas that decide whether getting to production, and staying there, is smooth rather than fraught. Take the list as the high-level shape; the full, tailored checklist is the larger piece your Akka team works through with you. This ends up as a version customized to your deployment.

Sizing and capacity Forecast peak load and size compute and persistence to meet it.

Observability Application logs, metrics, and traces flowing to your own tooling, with the monitoring and alerting to act on them, including database and persistence health.

Delivery and rollback A pipeline that ships and rolls back safely, covering configuration and data as well as container images.

Backups and recovery Backups and recovery for your data and cluster state, sized to your recovery objectives.

Multi-region resilience Replication and failover behaviour chosen per service to meet your availability needs.

Certificates Valid TLS end to end: Akka rotates platform-managed certificates, and you manage any you provide.

Ownership A single named owner (an individual or a designated body such as a go-live board) accountable for production readiness, with the authority to declare it or hold the launch.

## <a href="about:blank#_how_the_split_shifts_by_deployment_model"></a> How the split shifts by deployment model

Akka behaves identically across [deployment models](../../concepts/deployment-model.html). What changes is how much of the operational surface you run:

- **Dedicated**: Akka hosts and operates the platform and connects to you over a private data link, leaving your team the least to run.

[Shared Responsibility Model](dedicated/shared-responsibility.html) · [Production Readiness Checklist](dedicated/checklist.html)
- **BYOC (Bring Your Own Cloud)**: Akka operates the platform inside your own cloud account. You own the account, networking, and data boundary.

[Shared Responsibility Model](byoc/shared-responsibility.html) · [Production Readiness Checklist](byoc/checklist.html)
- **BYOK8s (Bring Your Own Kubernetes)**: You run Akka on the Kubernetes cluster you operate and own more of the stack, including the cluster, its upgrades, and backups.

[Shared Responsibility Model](byok8s/shared-responsibility.html) · [Production Readiness Checklist](byok8s/checklist.html)
The further you move toward BYOK8s, the more of the readiness items fall to your team.

<!-- <footer> -->
<!-- <nav> -->
[Operator best practices](../operator-best-practices.html) [Shared responsibility model](dedicated/shared-responsibility.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->