package io.akka.memory.api;

import akka.javasdk.http.HttpException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Optional bearer-token authentication, <b>off unless a token is configured</b>.
 *
 * <p>The source system has none, so a default build must have none either — a caller written
 * against the source has to work here unchanged. But this service is network-reachable in a way the
 * source's usual deployment is not, and shipping no way at all to close that exposure would leave
 * the operator with nothing to reach for. Turning it on is a deliberate, recorded divergence
 * (D-008), and it is theirs to make.
 *
 * <p>Kept to one shared token. Not OAuth, not users, not roles — none of that closes the exposure
 * any better, and none of it exists in the source to be equivalent to.
 */
public final class BearerAuth {

  private static final String PREFIX = "Bearer ";

  private final byte[] expected;

  public BearerAuth(String token) {
    this.expected =
        token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
  }

  public boolean isEnabled() {
    return expected != null;
  }

  /**
   * Lets the request through, or refuses it.
   *
   * <p>The comparison is constant-time. A plain string equality leaks the token one character at a
   * time to anyone willing to measure, which is a real attack on a shared secret even though it
   * reads as paranoia.
   */
  public void check(Optional<String> authorizationHeader) {
    if (expected == null) {
      return;
    }
    String header = authorizationHeader.orElse("");
    if (!header.startsWith(PREFIX)) {
      throw HttpException.unauthorized("Bearer token required");
    }
    byte[] presented = header.substring(PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, presented)) {
      throw HttpException.unauthorized("Bearer token rejected");
    }
  }
}
