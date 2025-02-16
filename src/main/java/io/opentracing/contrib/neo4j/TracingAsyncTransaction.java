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

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.neo4j.driver.Query;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Value;
import org.neo4j.driver.async.AsyncTransaction;
import org.neo4j.driver.async.ResultCursor;

import static io.opentracing.contrib.neo4j.TracingHelper.decorate;
import static io.opentracing.contrib.neo4j.TracingHelper.mapToString;
import static io.opentracing.contrib.neo4j.TracingHelper.onError;

public class TracingAsyncTransaction implements AsyncTransaction {

  private final AsyncTransaction transaction;
  private final Span parent;
  private final Tracer tracer;
  private final boolean finishSpan;

  public TracingAsyncTransaction(
      AsyncTransaction transaction, Span parent,
      Tracer tracer) {
    this(transaction, parent, tracer, false);
  }

  public TracingAsyncTransaction(
      AsyncTransaction transaction, Span parent, Tracer tracer,
      boolean finishSpan) {
    this.transaction = transaction;
    this.tracer = tracer;
    this.parent = parent;
    this.finishSpan = finishSpan;
  }

  @Override
  public CompletionStage<Void> commitAsync() {
    return transaction.commitAsync().whenComplete((aVoid, throwable) -> {
      if (throwable != null) {
        onError(throwable, parent);
      }
      parent.finish();
    });
  }

  @Override
  public CompletionStage<Void> rollbackAsync() {
    return transaction.rollbackAsync().whenComplete((aVoid, throwable) -> {
      if (throwable != null) {
        onError(throwable, parent);
      }
      parent.finish();
    });
  }

  @Override
  public CompletionStage<ResultCursor> runAsync(String query, Value parameters) {
    Span span = TracingHelper.build("runAsync", parent, tracer);
    span.setTag(Tags.DB_STATEMENT.getKey(), query);
    span.setTag("parameters", parameters.toString());
    return decorate(transaction.runAsync(query, parameters), span);
  }

  @Override
  public CompletionStage<ResultCursor> runAsync(
      String query, Map<String, Object> parameters) {
    Span span = TracingHelper.build("runAsync", parent, tracer);
    span.setTag(Tags.DB_STATEMENT.getKey(), query);
    if (parameters != null) {
      span.setTag("parameters", mapToString(parameters));
    }
    return decorate(transaction.runAsync(query, parameters), span);
  }

  @Override
  public CompletionStage<ResultCursor> runAsync(String query, Record parameters) {
    Span span = TracingHelper.build("runAsync", parent, tracer);
    span.setTag(Tags.DB_STATEMENT.getKey(), query);
    if (parameters != null) {
      span.setTag("parameters", TracingHelper.mapToString(parameters.asMap()));
    }

    return decorate(transaction.runAsync(query, parameters), span);
  }

  @Override
  public CompletionStage<ResultCursor> runAsync(String query) {
    Span span = TracingHelper.build("runAsync", parent, tracer);
    span.setTag(Tags.DB_STATEMENT.getKey(), query);
    return decorate(transaction.runAsync(query), span);
  }

  @Override
  public CompletionStage<ResultCursor> runAsync(Query query) {
    Span span = TracingHelper.build("runAsync", parent, tracer);
    span.setTag(Tags.DB_STATEMENT.getKey(), query.toString());
    return decorate(transaction.runAsync(query), span);
  }
}
