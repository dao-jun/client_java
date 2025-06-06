package io.prometheus.metrics.core.exemplars;

import static io.prometheus.metrics.model.snapshots.Exemplar.TRACE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.prometheus.metrics.config.ExemplarsProperties;
import io.prometheus.metrics.model.snapshots.Exemplar;
import io.prometheus.metrics.model.snapshots.Exemplars;
import io.prometheus.metrics.tracer.common.SpanContext;
import io.prometheus.metrics.tracer.initializer.SpanContextSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpanContextSupplierTest {

  public SpanContext makeSpanContext(String traceId, String spanId) {

    return new SpanContext() {
      @Override
      public String getCurrentTraceId() {
        return traceId;
      }

      @Override
      public String getCurrentSpanId() {
        return spanId;
      }

      @Override
      public boolean isCurrentSpanSampled() {
        return true;
      }

      @Override
      public void markCurrentSpanAsExemplar() {}
    };
  }

  SpanContext spanContextA = makeSpanContext("A", "a");
  SpanContext spanContextB = makeSpanContext("B", "b");
  SpanContext origSpanContext;

  ExemplarSamplerConfig config =
      new ExemplarSamplerConfig(
          10, // min retention period in milliseconds
          20, // max retention period in milliseconds
          5, // sample interval in milliseconds
          1, // number of exemplars
          null // histogram upper bounds
          );

  @BeforeEach
  public void setUp() {
    origSpanContext = SpanContextSupplier.getSpanContext();
  }

  @AfterEach
  public void tearDown() {
    SpanContextSupplier.setSpanContext(origSpanContext);
  }

  /**
   * Test: When a {@link SpanContext} is provided as a constructor argument to the {@link
   * ExemplarSampler}, then that {@link SpanContext} is used, not the one from the {@link
   * SpanContextSupplier}.
   */
  @Test
  public void testConstructorInjection() {
    ExemplarsProperties properties = ExemplarsProperties.builder().build();
    ExemplarSamplerConfig config = new ExemplarSamplerConfig(properties, 1);
    ExemplarSampler exemplarSampler = new ExemplarSampler(config, spanContextA);

    SpanContextSupplier.setSpanContext(spanContextB);
    exemplarSampler.observe(1.0);
    Exemplars exemplars = exemplarSampler.collect();
    assertThat(exemplars.size()).isOne();
    Exemplar exemplar = exemplars.get(0);
    assertThat(exemplar.getLabels().get(TRACE_ID)).isEqualTo("A");
  }

  /**
   * When the global {@link SpanContext} is updated via {@link
   * SpanContextSupplier#setSpanContext(SpanContext)}, the {@link ExemplarSampler} recognizes the
   * update (unless a {@link ExemplarSampler} was provided as constructor argument to {@link
   * ExemplarSampler}).
   */
  @Test
  public void testUpdateSpanContext() throws InterruptedException {
    ExemplarSampler exemplarSampler = new ExemplarSampler(config);

    SpanContextSupplier.setSpanContext(spanContextB);
    exemplarSampler.observe(1.0);
    Exemplars exemplars = exemplarSampler.collect();
    assertThat(exemplars.size()).isOne();
    Exemplar exemplar = exemplars.get(0);
    assertThat(exemplar.getLabels().get(TRACE_ID)).isEqualTo("B");

    Thread.sleep(15); // more than the minimum retention period defined in config above.

    SpanContextSupplier.setSpanContext(spanContextA);
    exemplarSampler.observe(1.0);
    exemplars = exemplarSampler.collect();
    assertThat(exemplars.size()).isOne();
    exemplar = exemplars.get(0);
    assertThat(exemplar.getLabels().get(TRACE_ID)).isEqualTo("A");
  }
}
