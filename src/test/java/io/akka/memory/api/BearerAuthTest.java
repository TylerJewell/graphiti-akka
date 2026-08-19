package io.akka.memory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Authentication is off by default, and that default is the part that matters most.
 *
 * <p>A build that quietly required a credential would break every caller written against the source
 * system while looking like a security improvement. The first test here is the one that would catch
 * that.
 */
class BearerAuthTest {

  @Test
  @DisplayName("with no token configured, every request is allowed")
  void offByDefault() {
    var auth = new BearerAuth("");
    assertThat(auth.isEnabled()).isFalse();
    assertThatCode(() -> auth.check(Optional.empty())).doesNotThrowAnyException();
    assertThatCode(() -> auth.check(Optional.of("Bearer nonsense"))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("an unset token is the same as an empty one")
  void nullIsOffToo() {
    assertThat(new BearerAuth(null).isEnabled()).isFalse();
    assertThat(new BearerAuth("   ").isEnabled()).isFalse();
  }

  @Test
  @DisplayName("with a token configured, the right one is accepted")
  void theRightTokenPasses() {
    var auth = new BearerAuth("s3cret");
    assertThat(auth.isEnabled()).isTrue();
    assertThatCode(() -> auth.check(Optional.of("Bearer s3cret"))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a missing, malformed or wrong credential is refused")
  void everythingElseIsRefused() {
    var auth = new BearerAuth("s3cret");
    assertThatThrownBy(() -> auth.check(Optional.empty())).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> auth.check(Optional.of("s3cret")))
        .as("the scheme is part of the credential")
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> auth.check(Optional.of("Basic s3cret")))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> auth.check(Optional.of("Bearer s3cre")))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> auth.check(Optional.of("Bearer s3crett")))
        .as("a prefix of the token is not the token")
        .isInstanceOf(RuntimeException.class);
  }
}
