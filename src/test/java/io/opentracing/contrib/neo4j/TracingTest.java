/*
 * Copyright 2018-2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.neo4j;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.harness.junit.rule.Neo4jRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.neo4j.driver.Values.parameters;

public class TracingTest {

  private final MockTracer tracer = new MockTracer();

  @Rule
  public Neo4jRule neoServer = new Neo4jRule();

  private Driver driver;

  @Before
  public void before() {
    tracer.reset();
    driver = new TracingDriver(GraphDatabase.driver(neoServer.boltURI().toString()), tracer);
  }

  @After
  public void after() {
    driver.close();
  }

  @Test
  public void testWriteTransaction() {
    final String message = "Hello, world";

    try (Session session = driver.session()) {
      String greeting = session.writeTransaction(tx -> {
        Result result = tx.run(
            "CREATE (a:Greeting) " +
                "SET a.message = $message " +
                "RETURN a.message + ', from node ' + id(a)",
            parameters("message", message));
        tx.run("CREATE (n:Person) RETURN n");
        return result.single().get(0).asString();
      });
      System.out.println(greeting);
    }

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(3, spans.size());
    validateSpans(spans);

    assertNull(tracer.activeSpan());
  }

  @Test
  public void testRun() {
    try (Session session = driver.session()) {
      Result result = session.run(new Query("CREATE (n:Person) RETURN n"));
      System.out.println(result.single());
    }

    try (Session session = driver.session()) {
      Result result = session.run("CREATE (n:Person) RETURN n");
      System.out.println(result.single());
    }

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(2, spans.size());
    validateSpans(spans);

    assertNull(tracer.activeSpan());
  }

  @Test
  public void testTransaction() {
    Session session = driver.session();
    Transaction transaction = session.beginTransaction();
    transaction.run("UNWIND range(1, 10) AS x RETURN x");
    transaction.run("CREATE (n:Person) RETURN n");
    transaction.close();
    session.close();

    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(3, spans.size());
    validateSpans(spans);

    assertNull(tracer.activeSpan());

  }

  private void validateSpans(List<MockSpan> spans) {
    for (MockSpan span : spans) {
      assertEquals(span.tags().get(Tags.SPAN_KIND.getKey()), Tags.SPAN_KIND_CLIENT);
      assertEquals(TracingHelper.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
      assertEquals(TracingHelper.DB_TYPE, span.tags().get(Tags.DB_TYPE.getKey()));
      assertEquals(0, span.generatedErrors().size());
    }
  }
}
