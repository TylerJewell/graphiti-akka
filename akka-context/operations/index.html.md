<!-- <nav> -->
- [Akka](../index.html)
- [Operating](index.html)

<!-- </nav> -->

# Operating

Akka offers two distinct operational approaches:

- <a href="configuring.html">**Self-managed operations**</a>: For teams that prefer to operate Akka on their own infrastructure. This provides full control over runtime and operational details.
- <a href="akka-platform.html">**Akka Automated Operations**</a>: For teams seeking a managed experience with built-in automation, observability, and scalability. Services are deployed either in our serverless cloud or your VPC. AAO gives you a single entry point to all of your Akka regions.

## Feature comparison

| Feature | Self-managed Operations | Akka Automated Operations (in your VPC) | Akka Automated Operations (serverless) |
| --- | --- | --- | --- |
| Akka runtime | ✅ | ✅ | ✅ |
| Akka clustering | ✅ | ✅ | ✅ |
| Elasticity | ✅ | ✅ | ✅ |
| Resilience | ✅ | ✅ | ✅ |
| Durable memory | ✅ | ✅ | ✅ |
| Akka Orchestration | ✅ | ✅ | ✅ |
| Akka Agents | ✅ | ✅ | ✅ |
| Akka Memory | ✅ | ✅ | ✅ |
| Akka Streaming | ✅ | ✅ | ✅ |
| Metrics, logs, and traces | ✅ | ✅ | ✅ |
| User management: OpenID Connect (OIDC) | ❌ | ✅ | ✅ |
| Deploy: Bare metal | ✅ | ❌ | ❌ |
| Deploy: VMs | ✅ | ❌ | ❌ |
| Deploy: Edge | ✅ | ❌ | ❌ |
| Deploy: Containers | ✅ | ❌ | ❌ |
| Deploy: PaaS | ✅ | ❌ | ❌ |
| Deploy: Serverless | ❌ | ❌ | ✅ |
| Deploy: Your VPC | ❌ | ✅ | ❌ |
| Deploy: Your Edge VPC | ❌ | ✅ | ❌ |
| Custom network setup | ✅ | ✅ | ❌ |
| Auto-elasticity | ❌ | ✅ | ✅ |
| Multi-tenant services | ❌ | ✅ | ❌ |
| Multi-region operations | ❌ | ✅ | ✅ |
| Persistence oversight | ❌ | ✅ | ✅ |
| Certificate and key rotation | ❌ | ✅ | ✅ |
| Multi-org access controls | ❌ | ✅ | ❌ |
| No downtime updates | ❌ | ✅ | ✅ |

## Service packaging

The services you build with Akka components are composable, which can be combined to design agentic, transactional, analytics, edge, and digital twin systems. You can create services with one component or many.

Your services are packed into a single binary. You create instances of Akka that you can operate on any infrastructure: Platform as a Service (PaaS), Kubernetes, Docker Compose, virtual machines (VMs), bare metal, or edge.

Akka services self-cluster without you needing to install a service mesh. Akka clustering provides elasticity and resilience to your agentic services. In addition to data sharding, data rebalancing, and traffic routing, Akka clustering has built-in support for addressing split brain networking disruptions.

Optionally, you can deploy your agentic services into [Akka Automated Operations](akka-platform.html), which provides a global control plane, multi-tenancy, multi-region operations (for compliance data pinning, failover, and disaster recovery), auto-elasticity based upon traffic load, and persistence management (memory auto-scaling).

![Akka Packaging](../concepts/_images/packed-services.png)


<!-- <footer> -->
<!-- <nav> -->
[Design considerations](../sdk/dev-best-practices.html) [Self-managed operations](configuring.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->