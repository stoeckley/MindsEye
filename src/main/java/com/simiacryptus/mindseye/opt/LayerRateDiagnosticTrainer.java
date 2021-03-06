/*
 * Copyright (c) 2018 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.mindseye.opt;

import com.simiacryptus.mindseye.eval.Trainable;
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.network.DAGNetwork;
import com.simiacryptus.mindseye.opt.line.LineSearchStrategy;
import com.simiacryptus.mindseye.opt.line.QuadraticSearch;
import com.simiacryptus.mindseye.opt.line.SimpleLineSearchCursor;
import com.simiacryptus.mindseye.opt.orient.GradientDescent;
import com.simiacryptus.mindseye.opt.orient.OrientationStrategy;
import com.simiacryptus.util.Util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This trains a subject apply a diagnostic goal: trainCjGD each key individually, and measureStyle the ideal rate for each
 * phase. This can indicate how balanced a network is, and how to trainCjGD it.
 */
public class LayerRateDiagnosticTrainer {


  private final Map<Layer, LayerStats> layerRates = new HashMap<>();
  private final Trainable subject;
  private AtomicInteger currentIteration = new AtomicInteger(0);
  private int iterationsPerSample = 1;
  private int maxIterations = Integer.MAX_VALUE;
  private TrainingMonitor monitor = new TrainingMonitor();
  private OrientationStrategy<?> orientation;
  private boolean strict = false;
  private double terminateThreshold;
  private Duration timeout;

  /**
   * Instantiates a new LayerBase rate diagnostic trainer.
   *
   * @param subject the subject
   */
  public LayerRateDiagnosticTrainer(final Trainable subject) {
    this.subject = subject;
    timeout = Duration.of(5, ChronoUnit.MINUTES);
    terminateThreshold = Double.NEGATIVE_INFINITY;
    setOrientation(new GradientDescent());
  }

  public Layer toLayer(UUID id) {
    return ((DAGNetwork)subject.getLayer()).getLayersById().get(id);
  }

  @Nonnull
  private DeltaSet<UUID> filterDirection(@Nonnull final DeltaSet<UUID> direction, @Nonnull final Layer layer) {
    @Nonnull final DeltaSet<UUID> maskedDelta = new DeltaSet<UUID>();
    direction.getMap().forEach((layer2, delta) -> maskedDelta.get(layer2, delta.target));
    maskedDelta.get(layer.getId(), layer.state().get(0)).addInPlace(direction.get(layer.getId(), (double[]) null).getDelta());
    return maskedDelta;
  }

  /**
   * Gets current iteration.
   *
   * @return the current iteration
   */
  public AtomicInteger getCurrentIteration() {
    return currentIteration;
  }

  /**
   * Sets current iteration.
   *
   * @param currentIteration the current iteration
   * @return the current iteration
   */
  @Nonnull
  public LayerRateDiagnosticTrainer setCurrentIteration(final AtomicInteger currentIteration) {
    this.currentIteration = currentIteration;
    return this;
  }

  /**
   * Gets iterations per sample.
   *
   * @return the iterations per sample
   */
  public int getIterationsPerSample() {
    return iterationsPerSample;
  }

  /**
   * Sets iterations per sample.
   *
   * @param iterationsPerSample the iterations per sample
   * @return the iterations per sample
   */
  @Nonnull
  public LayerRateDiagnosticTrainer setIterationsPerSample(final int iterationsPerSample) {
    this.iterationsPerSample = iterationsPerSample;
    return this;
  }

  /**
   * Gets key rates.
   *
   * @return the key rates
   */
  @Nonnull
  public Map<Layer, LayerStats> getLayerRates() {
    return layerRates;
  }

  /**
   * Gets line search strategy.
   *
   * @return the line search strategy
   */
  @Nonnull
  protected LineSearchStrategy getLineSearchStrategy() {
    return new QuadraticSearch();
  }

  /**
   * Gets max iterations.
   *
   * @return the max iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Sets max iterations.
   *
   * @param maxIterations the max iterations
   * @return the max iterations
   */
  @Nonnull
  public LayerRateDiagnosticTrainer setMaxIterations(final int maxIterations) {
    this.maxIterations = maxIterations;
    return this;
  }

  /**
   * Gets monitor.
   *
   * @return the monitor
   */
  public TrainingMonitor getMonitor() {
    return monitor;
  }

  /**
   * Sets monitor.
   *
   * @param monitor the monitor
   * @return the monitor
   */
  @Nonnull
  public LayerRateDiagnosticTrainer setMonitor(final TrainingMonitor monitor) {
    this.monitor = monitor;
    return this;
  }

  /**
   * Gets orientation.
   *
   * @return the orientation
   */
  public OrientationStrategy<?> getOrientation() {
    return orientation;
  }

  /**
   * Sets orientation.
   *
   * @param orientation the orientation
   * @return the orientation
   */
  @Nonnull
  public LayerRateDiagnosticTrainer setOrientation(final OrientationStrategy<?> orientation) {
    this.orientation = orientation;
    return this;
  }

  /**
   * Gets terminate threshold.
   *
   * @return the terminate threshold
   */
  public double getTerminateThreshold() {
    return terminateThreshold;
  }

  /**
   * Sets terminate threshold.
   *
   * @param terminateThreshold the terminate threshold
   * @return the terminate threshold
   */
  @Nonnull
  public LayerRateDiagnosticTrainer setTerminateThreshold(final double terminateThreshold) {
    this.terminateThreshold = terminateThreshold;
    return this;
  }

  /**
   * Gets timeout.
   *
   * @return the timeout
   */
  public Duration getTimeout() {
    return timeout;
  }

  /**
   * Sets timeout.
   *
   * @param timeout the timeout
   * @return the timeout
   */
  @Nonnull
  public LayerRateDiagnosticTrainer setTimeout(final Duration timeout) {
    this.timeout = timeout;
    return this;
  }

  /**
   * Is strict boolean.
   *
   * @return the boolean
   */
  public boolean isStrict() {
    return strict;
  }

  /**
   * Sets strict.
   *
   * @param strict the strict
   * @return the strict
   */
  @Nonnull
  public LayerRateDiagnosticTrainer setStrict(final boolean strict) {
    this.strict = strict;
    return this;
  }

  /**
   * Measure point sample.
   *
   * @return the point sample
   */
  public PointSample measure() {
    PointSample currentPoint;
    int retries = 0;
    do {
      if (!subject.reseed(System.nanoTime()) && retries > 0) throw new IterativeStopException();
      if (10 < retries++) throw new IterativeStopException();
      currentPoint = subject.measure(monitor);
    } while (!Double.isFinite(currentPoint.sum));
    assert Double.isFinite(currentPoint.sum);
    return currentPoint;
  }

  /**
   * Run buildMap.
   *
   * @return the buildMap
   */
  @Nonnull
  public Map<Layer, LayerStats> run() {
    final long timeoutMs = System.currentTimeMillis() + timeout.toMillis();
    PointSample measure = measure();
    @Nonnull final ArrayList<UUID> layers = new ArrayList<>(measure.weights.getMap().keySet());
    while (timeoutMs > System.currentTimeMillis() && measure.sum > terminateThreshold) {
      if (currentIteration.get() > maxIterations) {
        break;
      }
      final PointSample initialPhasePoint = measure();

      measure = initialPhasePoint;
      for (int subiteration = 0; subiteration < iterationsPerSample; subiteration++) {
        if (currentIteration.incrementAndGet() > maxIterations) {
          break;
        }

        {
          @Nonnull final SimpleLineSearchCursor orient = (SimpleLineSearchCursor) getOrientation().orient(subject, measure, monitor);
          final double stepSize = 1e-12 * orient.origin.sum;
          @Nonnull final DeltaSet<UUID> pointB = orient.step(stepSize, monitor).point.delta.copy();
          @Nonnull final DeltaSet<UUID> pointA = orient.step(0.0, monitor).point.delta.copy();
          @Nonnull final DeltaSet<UUID> d1 = pointA;
          @Nonnull final DeltaSet<UUID> d2 = d1.add(pointB.scale(-1)).scale(1.0 / stepSize);
          @Nonnull final Map<UUID, Double> steps = new HashMap<>();
          final double overallStepEstimate = d1.getMagnitude() / d2.getMagnitude();
          for (final UUID layer : layers) {
            final DoubleBuffer<UUID> a = d2.get(layer, (double[]) null);
            final DoubleBuffer<UUID> b = d1.get(layer, (double[]) null);
            final double bmag = Math.sqrt(b.deltaStatistics().sumSq());
            final double amag = Math.sqrt(a.deltaStatistics().sumSq());
            final double dot = a.dot(b) / (amag * bmag);
            final double idealSize = bmag / (amag * dot);
            steps.put(layer, idealSize);
            monitor.log(String.format("Layers stats: %s (%s, %s, %s) => %s", layer, amag, bmag, dot, idealSize));
          }
          monitor.log(String.format("Estimated ideal rates for layers: %s (%s overall; probed at %s)", steps, overallStepEstimate, stepSize));
        }


        @Nullable SimpleLineSearchCursor bestOrient = null;
        @Nullable PointSample bestPoint = null;
layerLoop:
        for (@Nonnull final UUID id : layers) {
          Layer layer = toLayer(id);
          @Nonnull SimpleLineSearchCursor orient = (SimpleLineSearchCursor) getOrientation().orient(subject, measure, monitor);
          @Nonnull final DeltaSet<UUID> direction = filterDirection(orient.direction, layer);
          if (direction.getMagnitude() == 0) {
            monitor.log(String.format("Zero derivative for key %s; skipping", layer));
            continue layerLoop;
          }
          orient = new SimpleLineSearchCursor(orient.subject, orient.origin, direction);
          final PointSample previous = measure;
          measure = getLineSearchStrategy().step(orient, monitor);
          if (isStrict()) {
            monitor.log(String.format("Iteration %s reverting. Error: %s", currentIteration.get(), measure.sum));
            monitor.log(String.format("Optimal rate for key %s: %s", layer.getName(), measure.getRate()));
            if (null == bestPoint || bestPoint.sum < measure.sum) {
              bestOrient = orient;
              bestPoint = measure;
            }
            getLayerRates().put(layer, new LayerStats(measure.getRate(), initialPhasePoint.sum - measure.sum));
            orient.step(0, monitor);
            measure = previous;
          } else if (previous.sum == measure.sum) {
            monitor.log(String.format("Iteration %s failed. Error: %s", currentIteration.get(), measure.sum));
          } else {
            monitor.log(String.format("Iteration %s complete. Error: %s", currentIteration.get(), measure.sum));
            monitor.log(String.format("Optimal rate for key %s: %s", layer.getName(), measure.getRate()));
            getLayerRates().put(layer, new LayerStats(measure.getRate(), initialPhasePoint.sum - measure.sum));
          }
        }
        monitor.log(String.format("Ideal rates: %s", getLayerRates()));
        if (null != bestPoint) {
          bestOrient.step(bestPoint.rate, monitor);
        }
        monitor.onStepComplete(new Step(measure, currentIteration.get()));
      }
    }
    return getLayerRates();
  }

  /**
   * Sets timeout.
   *
   * @param number the number
   * @param units  the units
   * @return the timeout
   */
  @Nonnull
  public LayerRateDiagnosticTrainer setTimeout(final int number, @Nonnull final TemporalUnit units) {
    timeout = Duration.of(number, units);
    return this;
  }

  /**
   * Sets timeout.
   *
   * @param number the number
   * @param units  the units
   * @return the timeout
   */
  @Nonnull
  public LayerRateDiagnosticTrainer setTimeout(final int number, @Nonnull final TimeUnit units) {
    return setTimeout(number, Util.cvt(units));
  }

  /**
   * The type LayerBase stats.
   */
  public static class LayerStats {
    /**
     * The Delta.
     */
    public final double delta;
    /**
     * The Rate.
     */
    public final double rate;

    /**
     * Instantiates a new LayerBase stats.
     *
     * @param rate  the rate
     * @param delta the evalInputDelta
     */
    public LayerStats(final double rate, final double delta) {
      this.rate = rate;
      this.delta = delta;
    }

    @Nonnull
    @Override
    public String toString() {
      @Nonnull final StringBuffer sb = new StringBuffer("{");
      sb.append("rate=").append(rate);
      sb.append(", evalInputDelta=").append(delta);
      sb.append('}');
      return sb.toString();
    }
  }
}
