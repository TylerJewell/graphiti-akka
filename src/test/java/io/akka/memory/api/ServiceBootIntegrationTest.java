package io.akka.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.StatusCodes;
import io.akka.memory.TestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The service starts and answers.
 *
 * <p>Trivial as an assertion, load-bearing as a check: every other integration test depends on the
 * component set being wired correctly, and a component that cannot be constructed fails here with a
 * clear cause rather than inside whatever test happened to run first.
 */
class ServiceBootIntegrationTest extends TestBase {

  @Test
  @DisplayName("the service starts with every component wired and answers its health route")
  void theServiceStarts() {
    var response = httpClient.GET("/healthcheck").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
  }
}
