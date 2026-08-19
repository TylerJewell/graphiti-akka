<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Integrating with CI/CD tools](index.html)
- [Scanning vulnerabilities](scanning-dependencies.html)

<!-- </nav> -->

# Scanning vulnerabilities

Your Akka service ships with the libraries that you add to it. You must scan these libraries for known vulnerabilities. This page shows the recommended way to do it.

Akka scans and patches the runtime part of your service. You scan the dependencies that you introduce. This split follows the [shared responsibility model](../production-readiness/index.html).

|  | Akka patches the JVM, the container base image, the operating system, and the Akka runtime that ship inside your deployed service. Akka runs continuous scanning on these parts. Akka addresses critical and high severity findings as soon as possible. You do not need to scan the runtime. See the [shared responsibility model](../production-readiness/index.html) for the full split. |

## <a href="about:blank#_what_ships_in_your_service"></a> What ships in your service

A deployed Akka service combines two sources:

1. **Your service image** — A small image that holds your application JAR and its direct SDK dependencies. The `mvn install` command builds this image. It includes these libraries in `/opt/local-lib/` in the built image.
2. **The Akka runtime base image** — The image that actually runs. It provides the JVM, the Akka runtime, and all platform dependencies (for example gRPC, Netty, Jackson, and Logback). Akka builds, scans, and patches this image.
Scan the libraries that you add in step 1. These are the dependencies that you control. If a scan finds a vulnerability, you fix it by updating your `pom.xml`.

|  | Do not scan the whole Maven project directly. The project dependency tree includes `akka-runtime-dev`, the local development runtime. This runtime does not ship in your service image. A scan of the whole tree reports findings for libraries that Akka provides and patches. These findings are noise for you. |

## <a href="about:blank#_scan_the_shipped_libraries"></a> Scan the shipped libraries

Build your service image first. The build writes the shipped libraries to a known folder under `target/`. Then point your scanner at that folder.

The folder path follows this pattern:

```none
target/docker/<service-name>/<tag>/build/maven/
```
The following example uses [Snyk](https://snyk.io/). Replace `<service-name>` and `<tag>` with your values.

```command
mvn clean install -DskipTests
snyk test --scan-all-unmanaged -- --target-dir=target/docker/my-service/latest/build/maven/
```
The `--scan-all-unmanaged` flag scans each JAR file in the folder. Snyk matches each JAR against its vulnerability database.

|  | A scanner that matches JAR files identifies them by fingerprint. It skips a JAR that it does not recognize, for example a shaded or internally built artifact. It also reports no dependency paths, but only the vulnerable JAR. |

## <a href="about:blank#_scan_the_container_image"></a> Scan the container image

You can scan the built container image instead. Point the scanner at `/opt/local-lib/`. This folder holds the libraries that you introduce.

Use this method when your process scans images rather than folders. It reports the same libraries as the folder scan.

## <a href="about:blank#_run_the_scan_in_ci"></a> Run the scan in CI

Run the scan in your CI pipeline after the build step. This checks every change before you deploy it. See [CI/CD with GitHub Actions](github-actions.html) for how to set up a workflow.

A typical job does the following:

1. Build the service image with `mvn clean install -DskipTests`.
2. Install the scanner.
3. Scan `target/docker/<service-name>/<tag>/build/maven/`.
4. Fail the job when the scanner finds a vulnerability at or above your threshold.

## <a href="about:blank#_see_also"></a> See also

- [CI/CD with GitHub Actions](github-actions.html)
- [Production readiness](../production-readiness/index.html)
- [Deploy and manage services](../services/deploy-service.html)

<!-- <footer> -->
<!-- <nav> -->
[CI/CD with GitHub Actions](github-actions.html) [CLI](../cli/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->