package io.akka.memory;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import io.akka.memory.application.AttributeHydrationAgent;
import io.akka.memory.application.EntityExtractionAgent;
import io.akka.memory.application.EntityResolutionAgent;
import io.akka.memory.application.FactExtractionAgent;

/**
 * Shared setup for tests that need the service running.
 *
 * <p>Every agent gets a stub model. That is not only about cost — a real model makes the test
 * non-deterministic, so a suite that calls one is measuring the model's mood as much as the code.
 * Each subclass programmes the responses its own case needs.
 *
 * <p>The store is the real one when it is reachable. Stubbing it would mean the projection was never
 * exercised, and the projection is where most of the read side's behaviour lives.
 */
public abstract class TestBase extends TestKitSupport {

  protected final TestModelProvider entityExtraction = new TestModelProvider();
  protected final TestModelProvider factExtraction = new TestModelProvider();
  protected final TestModelProvider entityResolution = new TestModelProvider();
  protected final TestModelProvider attributeHydration = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(EntityExtractionAgent.class, entityExtraction)
        .withModelProvider(FactExtractionAgent.class, factExtraction)
        .withModelProvider(EntityResolutionAgent.class, entityResolution)
        .withModelProvider(AttributeHydrationAgent.class, attributeHydration);
  }
}
