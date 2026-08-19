package io.akka.memory;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.typesafe.config.Config;
import io.akka.memory.application.Embedder;
import io.akka.memory.application.FlureeStore;
import io.akka.memory.application.RetrievalService;

/**
 * Service startup: builds the objects the runtime cannot build for itself.
 *
 * <p>The store and the embedder each hold a long-lived HTTP client, so they are created once here
 * and handed to whichever components ask for them, rather than each component opening its own
 * connection pool.
 *
 * <p>Their locations come from configuration and not from constants, because the addresses differ
 * between a developer's machine and a deployment — and a hard-coded address is the kind of thing
 * that works everywhere it is tested and nowhere it is run.
 */
@Setup
public class Bootstrap implements ServiceSetup {

  private final FlureeStore store;
  private final Embedder embedder;
  private final RetrievalService retrieval;

  public Bootstrap(Config config) {
    this.store =
        new FlureeStore(
            config.getString("memory.store.base-url"), config.getString("memory.store.ledger"));
    this.embedder = Embedder.fromEnvironment();
    this.retrieval = new RetrievalService(store, embedder);
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return new DependencyProvider() {
      @Override
      @SuppressWarnings("unchecked")
      public <T> T getDependency(Class<T> clazz) {
        if (clazz == FlureeStore.class) {
          return (T) store;
        }
        if (clazz == Embedder.class) {
          return (T) embedder;
        }
        if (clazz == RetrievalService.class) {
          return (T) retrieval;
        }
        throw new IllegalArgumentException("no such dependency: " + clazz.getName());
      }
    };
  }
}
