<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Web applications](web-applications.html)

<!-- </nav> -->

# Web applications

An Akka service can serve a web application to a browser: the HTML, JavaScript, and CSS files of a single-page application, individual files such as images, or HTML generated when the request arrives. The same service serves the API that the application calls, so a browser-facing system can be a single deployable unit.

## <a href="about:blank#_overview"></a> Overview

Web application files are packaged with the service and returned from [HTTP endpoints](http-endpoints.html). There is no separate web server to configure. The endpoint that returns a page and the endpoint that returns JSON live in the same codebase. Both run in the same service, so access control, traces, and metrics cover the pages and the API together.

Use this when the user interface belongs to the service:

- An operator console for a service that otherwise exposes only an API.
- An administrative panel over [Event Sourced Entities](event-sourced-entities.html) and [Views](views.html).
- A chat interface for an [Agent](agents.html) that displays model output as it is generated.
- A dashboard that redraws as new data arrives.
- A demo or evaluation application that ships with the service it exercises.
Files are part of the service artifact. Changing a page means deploying the service again. The [Scope and trade-offs](about:blank#scope) section describes the boundaries in full.

## <a href="about:blank#_in_service_or_separately_hosted"></a> In-service or separately hosted

The application can run inside the service or on a separate host.

Host the application inside the service for:

- User interfaces released together with the service.
- Operator consoles, administrative panels, agent chat interfaces, dashboards, demo applications.
- One origin, one set of ACLs, and one trace and metric surface across pages and API.
- Delivery to every region the service runs in, with no extra hosting to configure.
Host the application on a separate static site host or content delivery network for:

- User interfaces released on their own cadence, independent of the service.
- Traffic patterns that need edge caching, `ETag`, `Cache-Control`, or 304 Not Modified responses. The in-service path does not provide these. See [Scope and trade-offs](about:blank#scope).
- Image resizing, format conversion, or delivery from a point of presence close to the browser.
- One user interface shared by more than one service or more than one client application.

## <a href="about:blank#_serving_a_single_page_application"></a> Serving a single-page application

Place the application files in `src/main/resources/static-resources`. Map one endpoint method to a path prefix ending in `\**`, and pass the request to `HttpResponses.staticResource` together with the prefix to remove:

[StaticResourcesEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/StaticResourcesEndpoint.java)
```java
@Get("/pages/**") // (1)
public HttpResponse webPageResources(HttpRequest request) { // (2)
  return HttpResponses.staticResource(request, "/pages/"); // (3)
}
```

| **1** | Match any request path under `/pages/`. |
| **2** | Accept `akka.http.javadsl.model.HttpRequest` so the method can read the requested path. |
| **3** | Remove the `/pages/` prefix and serve the matching file from `static-resources`. |
A request for `/pages/app.css` returns `static-resources/app.css`. Because `\**` also matches deeper paths, `/pages/images/logo.png` returns `static-resources/images/logo.png`.

When the remaining path is empty or ends with `/`, `index.html` from that directory is served. A request to `/pages` or `/pages/` therefore returns the application shell, which is the behavior a single-page application needs at the root of its subtree.

The response content type is set from the file extension. HTML, CSS, JavaScript, JSON, PNG, SVG, JPEG, GIF, ICO, PDF, XML, and YAML are recognized. Any other extension is served as `application/octet-stream`.

## <a href="about:blank#_serving_individual_files"></a> Serving individual files

Map a method to one path when you want to control each file explicitly:

[StaticResourcesEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/StaticResourcesEndpoint.java)
```java
@Get("/") // (1)
public HttpResponse index() {
  return HttpResponses.staticResource("index.html"); // (2)
}

@Get("/favicon.ico") // (3)
public HttpResponse favicon() {
  return HttpResponses.staticResource("favicon.ico"); // (4)
}
```

| **1** | The specific path `/`. |
| **2** | Serve `src/main/resources/static-resources/index.html`. |
| **3** | Another specific path. |
| **4** | The file to serve for that path. |
Paths passed to `HttpResponses.staticResource` are relative to `static-resources` and must not start with `/`. A path containing `..` returns 403 Forbidden. A path with no matching file returns 404 Not Found.

## <a href="about:blank#_generating_html_in_an_endpoint"></a> Generating HTML in an endpoint

An endpoint method can build a response body itself instead of reading a packaged file. Return `HttpResponses.of` with an HTML content type:

[StaticResourcesEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/StaticResourcesEndpoint.java)
```java
@Get("/status")
public HttpResponse status() {
  var html = "<html><body><h1>Service is running</h1></body></html>"; // (1)
  return HttpResponses.of(
    StatusCodes.OK,
    ContentTypes.TEXT_HTML_UTF8,
    html.getBytes(StandardCharsets.UTF_8)
  ); // (2)
}
```

| **1** | Build the markup. A template library can be used here, since an endpoint is ordinary Java. |
| **2** | Return the bytes with `text/html` so the browser renders them as a page. |
This suits pages whose content depends on the request or on current service state.

## <a href="about:blank#_updating_the_page_as_data_changes"></a> Updating the page as data changes

A page does not have to poll for new data. An endpoint can hold a connection open and push updates to the browser.

Server-sent events carry a one-way stream from the service to the page. The browser reconnects automatically, and the SDK can resume the stream from the last event the client saw. This is how a chat interface displays an agent’s response as the model produces it. See [Server-sent events](http-endpoints.html#sse) and [Streaming responses](agents/streaming.html).

WebSockets carry traffic in both directions over one connection. Use them when the page sends messages to the service as well as receiving them. See [Streaming with WebSockets](http-endpoints.html#websocket).

|  | A deployed service needs route configuration before WebSocket connections succeed. Without it, the connection fails with 403 Forbidden. See [WebSocket support](../operations/services/invoke-service.html#websockets). |

## <a href="about:blank#_serving_the_application_and_its_api_on_one_hostname"></a> Serving the application and its API on one hostname

The application and the API it calls can live in one service, which keeps them on the same origin and avoids cross-origin configuration.

They can also live in separate services behind one hostname. A route maps path prefixes to different services, so `/` can reach a service that serves the pages while `/api` reaches a service that serves JSON. Akka does not rewrite the path before passing the request on, so each service must handle the full path the client sent. See [Exposing services to the internet](../operations/services/invoke-service.html#exposing-internet).

When the page and the API are served from different hostnames, the browser applies its cross-origin rules. Enable Cross-Origin Resource Sharing (CORS) on the route and name the origins that are allowed to call it. See [Enabling CORS](../operations/services/invoke-service.html#_enabling_cors).

## <a href="about:blank#_using_your_own_domain_name"></a> Using your own domain name

Register the hostname with your project, then create a `CNAME` record at your DNS provider pointing to the value Akka returns. Akka provisions a TLS certificate through Let’s Encrypt and renews it, for hostnames you bring and for hostnames Akka generates. All traffic uses TLS.

See [Exposing services to the internet](../operations/services/invoke-service.html#exposing-internet) for the commands.

## <a href="about:blank#_restricting_who_can_load_a_page"></a> Restricting who can load a page

Endpoint methods that serve pages are subject to the same access control as any other endpoint method. An endpoint reachable from a browser needs an Access Control List (ACL) that admits internet traffic, which is not the default.

For pages that require a signed-in user, validate a JSON Web Token (JWT) on the endpoint. See [Access control lists](access-control.html) and [Authentication with JWTs](auth-with-jwts.html).

## <a href="about:blank#_serving_from_more_than_one_region"></a> Serving from more than one region

Routes are global by default. When a project runs in more than one region, a route replicates to each new region so any region can serve the application. The application files are packaged with the service, so every region serves them without extra configuration. Which region a given request reaches is determined by the DNS resolution in front of Akka, not by Akka itself.

See [Multi-region operations](../concepts/multi-region.html).

## <a href="about:blank#scope"></a> Scope and trade-offs

Files are served by the service itself. Akka does not run a content delivery network, and the following characteristics follow from that:

- Responses carry a `Last-Modified` header. They do not carry `ETag` or `Cache-Control`, and conditional requests are not answered with 304 Not Modified, so each request transfers the full file.
- Files are read from the service classpath on each request rather than from an edge cache.
- Images are served as packaged. There is no resizing or format conversion.
- Files are part of the service artifact, so a change to the user interface requires deploying the service.
- There is no build integration for server-side rendering frameworks. Akka serves the output of a front-end build; it does not run the build.
For a user interface with heavy asset traffic, or one that is released independently of the service, deploy it separately and call the service API across the network.

## <a href="about:blank#_testing"></a> Testing

Endpoint methods that serve pages are tested like any other HTTP endpoint method. Extend `TestKitSupport` and call the endpoint through its `httpClient`, then assert on the status code and the content type of the response.

For WebSocket endpoint methods, the testkit provides `akka.javasdk.testkit.WebSocketRouteTester`, reached through `TestKit#getSelfWebSocketRouteTester`. See [Testing WebSocket endpoints](http-endpoints.html#_testing_websocket_endpoints).

Files under `src/main/resources/static-resources` are on the test classpath, so a test can verify that a request for a packaged file returns it.

## <a href="about:blank#_see_also"></a> See also

- [HTTP Endpoints](http-endpoints.html)
- [Streaming responses](agents/streaming.html)
- [Access control lists](access-control.html)
- [Invoking Akka services](../operations/services/invoke-service.html)

<!-- <footer> -->
<!-- <nav> -->
[Consumers](consuming-producing.html) [Integrations](integrations/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->