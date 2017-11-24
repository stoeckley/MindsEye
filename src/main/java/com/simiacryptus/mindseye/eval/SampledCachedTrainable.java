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

package com.simiacryptus.mindseye.eval;

public class SampledCachedTrainable<T extends SampledTrainable> extends CachedTrainable<T> implements SampledTrainable {
  
  private long seed;
  
  /**
   * Instantiates a new Cached trainable.
   *
   * @param inner the inner
   */
  public SampledCachedTrainable(T inner) {
    super(inner);
  }

  @Override
  public int getTrainingSize() {
    return getInner().getTrainingSize();
  }

  @Override
  public SampledTrainable setTrainingSize(int trainingSize) {
    if(trainingSize != getTrainingSize()) {
      getInner().setTrainingSize(trainingSize);
      reseed(seed);
    }
    return this;
  }
  
  @Override
  public boolean reseed(long seed) {
    this.seed = seed;
    return super.reseed(seed);
  }
  
  @Override
  public SampledCachedTrainable<? extends SampledTrainable> cached() {
    return new SampledCachedTrainable<>(this);
  }
  
}