/*
 * Copyright (c) 2017 by Andrew Charneski.
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

package com.simiacryptus.mindseye.layers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.IntStream;

/**
 * The type Delta buffer.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DeltaBuffer {
  
  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(DeltaBuffer.class);
  
  /**
   * The Delta.
   */
  public final double[] delta;
  /**
   * The Layer.
   */
  public final NNLayer layer;
  /**
   * The Target.
   */
  public final double[] target;
  
  /**
   * Instantiates a new Delta buffer.
   *
   * @param values the values
   * @param array  the array
   * @param layer  the layer
   */
  public DeltaBuffer(final double[] values, final double[] array, final NNLayer layer) {
    if(null == values) throw new IllegalArgumentException();
    if(null == array) throw new IllegalArgumentException();
    this.target = values;
    this.layer = layer;
    this.delta = array;
  }
  
  /**
   * Instantiates a new Delta buffer.
   *
   * @param values the values
   * @param layer  the layer
   */
  public DeltaBuffer(final double[] values, final NNLayer layer) {
    if(null == values) throw new IllegalArgumentException();
    this.target = values;
    this.layer = layer;
    this.delta = new double[values.length];
    Arrays.setAll(this.delta, i -> 0);
  }
  
  /**
   * Accumulate delta buffer.
   *
   * @param data the data
   * @return the delta buffer
   */
  public DeltaBuffer accumulate(final double[] data) {
    assert Arrays.stream(data).allMatch(Double::isFinite);
    final int dim = length();
    for (int i = 0; i < dim; i++) {
      this.delta[i] = this.delta[i] + data[i];
    }
    return this;
  }
  
  /**
   * Copy delta double [ ].
   *
   * @return the double [ ]
   */
  public double[] copyDelta() {
    return null == delta ? null : Arrays.copyOf(delta, delta.length);
  }
  
  /**
   * Copy target double [ ].
   *
   * @return the double [ ]
   */
  public double[] copyTarget() {
    return null == target ? null : Arrays.copyOf(target, target.length);
  }
  
  /**
   * Gets id.
   *
   * @return the id
   */
  public UUID getId() {
    return this.layer.getId();
  }
  
  /**
   * Gets vector.
   *
   * @param fraction the fraction
   * @return the vector
   */
  public DeltaBuffer getVector(final double fraction) {
    return this;
  }
  
  /**
   * Is frozen boolean.
   *
   * @return the boolean
   */
  public boolean isFrozen() {
    return false;
  }
  
  /**
   * Length int.
   *
   * @return the int
   */
  public int length() {
    return this.target.length;
  }
  
  /**
   * Map delta buffer.
   *
   * @param mapper the mapper
   * @return the delta buffer
   */
  public DeltaBuffer map(final DoubleUnaryOperator mapper) {
    return new DeltaBuffer(this.target, Arrays.stream(this.delta).map(x -> mapper.applyAsDouble(x)).toArray(), this.layer);
  }
  
  /**
   * Scale delta buffer.
   *
   * @param f the f
   * @return the delta buffer
   */
  public DeltaBuffer scale(final double f) {
    return map(x -> x * f);
  }
  
  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(getClass().getSimpleName());
    builder.append("/");
    builder.append(this.layer.getClass().getSimpleName());
    builder.append("/");
    builder.append(this.layer.getId());
    return builder.toString();
  }
  
  /**
   * Write.
   *
   * @param factor the factor
   */
  public synchronized final void write(final double factor) {
    double[] calcVector = this.delta;
    if (null == calcVector)
      return;
    calcVector = Arrays.copyOf(calcVector, calcVector.length);
    for (int i = 0; i < this.delta.length; i++) {
      calcVector[i] = calcVector[i] * factor;
    }
    final int dim = length();
    for (int i = 0; i < dim; i++) {
      this.target[i] = this.target[i] + calcVector[i];
    }
  }
  
  /**
   * Overwrite.
   */
  public synchronized final void overwrite() {
    final int dim = length();
    for (int i = 0; i < dim; i++) {
      this.target[i] = this.delta[i];
    }
  }
  
  /**
   * Dot double.
   *
   * @param right the right
   * @return the double
   */
  public double dot(DeltaBuffer right) {
    assert (this.target == right.target);
    assert (this.delta.length == right.delta.length);
    return IntStream.range(0, this.delta.length).mapToDouble(i -> delta[i] * right.delta[i]).sum();
  }
  
  /**
   * Sum double.
   *
   * @return the double
   */
  public double sum() {
    return Arrays.stream(this.delta).sum();
  }
  
  /**
   * Sum sq double.
   *
   * @return the double
   */
  public double sumSq() {
    return Arrays.stream(this.delta).map(x -> x * x).sum();
  }
  
  /**
   * Copy delta buffer.
   *
   * @return the delta buffer
   */
  public DeltaBuffer copy() {
    return new DeltaBuffer(target, copyDelta(), layer);
  }
}
