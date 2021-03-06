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

package com.simiacryptus.mindseye.network;

import com.simiacryptus.mindseye.lang.CoreSettings;
import com.simiacryptus.mindseye.lang.Layer;
import com.simiacryptus.mindseye.lang.ReferenceCounting;
import com.simiacryptus.mindseye.lang.Result;
import com.simiacryptus.util.Util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A calculation node, to be evaluated by a network once the inputs are available.
 */
@SuppressWarnings("serial")
public final class InnerNode extends LazyResult {
  /**
   * The Created by.
   */
  @SuppressWarnings("unused")
  public final CharSequence[] createdBy = Util.currentStack();
  private final DAGNetwork dagNetwork;
  @Nonnull
  private final DAGNode[] inputNodes;
  private volatile Layer layer;
  private boolean parallel = true;

  /**
   * Instantiates a new Inner node.
   *
   * @param dagNetwork the dag network
   * @param layer      the key
   * @param inputNodes the input nodes
   */
  @SafeVarargs
  InnerNode(final DAGNetwork dagNetwork, @Nonnull final Layer layer, final DAGNode... inputNodes) {
    this(dagNetwork, layer, UUID.randomUUID(), inputNodes);
  }

  /**
   * Instantiates a new Inner node.
   *
   * @param dagNetwork the dag network
   * @param layer      the key
   * @param key        the key
   * @param inputNodes the input nodes
   */
  @SafeVarargs
  InnerNode(final DAGNetwork dagNetwork, @Nonnull final Layer layer, final UUID key, @Nonnull final DAGNode... inputNodes) {
    super(key);
    this.dagNetwork = dagNetwork;
    assert null != inputNodes;
    setLayer(layer);
    this.inputNodes = Arrays.copyOf(inputNodes, inputNodes.length);
    assert Arrays.stream(inputNodes).parallel().allMatch(x -> x != null);
    for (@Nonnull DAGNode node : this.inputNodes) {
      node.addRef();
    }
  }

  /**
   * Add dag node.
   *
   * @param nextHead the next head
   * @return the dag node
   */
  public DAGNode add(@Nonnull final Layer nextHead) {
    return dagNetwork.add(nextHead, InnerNode.this);
  }

  @Nullable
  @Override
  protected Result eval(final GraphEvaluationContext ctx) {
    assertAlive();
    @Nonnull final Layer innerLayer = getLayer();
    assert Arrays.stream(inputNodes).allMatch(x -> x != null);
    @Nonnull Stream<DAGNode> stream = Arrays.stream(inputNodes);
    if (!CoreSettings.INSTANCE().isSingleThreaded() && parallel) stream = stream.parallel();
    final Result[] in = stream.map(x -> x == null ? null : x.get(ctx)).toArray(i -> new Result[i]);
    assert Arrays.stream(in).allMatch(x -> x != null);
    @Nullable Result result = innerLayer.evalAndFree(in);
    return result;
  }

  @Nonnull
  @Override
  public DAGNode[] getInputs() {
    return inputNodes;
  }

  @Nonnull
  @SuppressWarnings("unchecked")
  @Override
  public <T extends Layer> T getLayer() {
    return (T) layer;
  }

  @Override
  public synchronized void setLayer(@Nonnull final Layer newLayer) {
    assertAlive();
    dagNetwork.assertAlive();
    newLayer.assertAlive();
    Layer prevLayer = this.layer;
    if (newLayer != prevLayer) {
      if (null != prevLayer) prevLayer.freeRef();
      this.layer = newLayer;
      if (null != newLayer) newLayer.addRef();
      dagNetwork.assertConsistent();
    }
  }

  @Override
  public DAGNetwork getNetwork() {
    return dagNetwork;
  }

  @Override
  protected void _free() {
    super._free();
    Arrays.stream(this.inputNodes).forEach(ReferenceCounting::freeRef);
    this.layer.freeRef();
    this.layer = null;
  }

  /**
   * Is parallel boolean.
   *
   * @return the boolean
   */
  public boolean isParallel() {
    return parallel;
  }

  /**
   * Sets parallel.
   *
   * @param parallel the parallel
   * @return the parallel
   */
  public InnerNode setParallel(boolean parallel) {
    this.parallel = parallel;
    return this;
  }
}
