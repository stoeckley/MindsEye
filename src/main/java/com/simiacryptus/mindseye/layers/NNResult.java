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

import com.simiacryptus.mindseye.layers.NNLayer.ConstNNResult;
import com.simiacryptus.mindseye.layers.cudnn.CudaPtr;
import com.simiacryptus.util.ml.Tensor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * The type Nn result.
 */
public abstract class NNResult {
  
  protected final TensorList data;
  
  /**
   * Instantiates a new Nn result.
   *
   * @param data the data
   */
  public NNResult(final Tensor... data) {
    this(new TensorArray(data));
  }
  
  /**
   * Instantiates a new Nn result.
   *
   * @param data the data
   */
  public NNResult(final TensorList data) {
    super();
    this.data = data;
  }
  
  /**
   * Single result array nn result [ ].
   *
   * @param input - An array of inputs, each one of which is a batch for a  given input
   * @return nn result [ ]
   */
  public static NNResult[] singleResultArray(Tensor[][] input) {
    return Arrays.stream(input).map((Tensor[] x) -> new ConstNNResult(x)).toArray(i -> new NNResult[i]);
  }
  
  /**
   * Single result array nn result [ ].
   *
   * @param input the input
   * @return the nn result [ ]
   */
  public static NNResult[] singleResultArray(Tensor[] input) {
    return Arrays.stream(input).map((Tensor x) -> new ConstNNResult(x)).toArray(i -> new NNResult[i]);
  }
  
  /**
   * Batch result array nn result [ ].
   *
   * @param batchData - a list examples, ie each sub-array is a single example
   * @return - Returns a result array for NNLayer evaluation
   */
  public static NNResult[] batchResultArray(Tensor[][] batchData) {
    return IntStream.range(0, batchData[0].length).mapToObj(inputIndex -> {
      Tensor[] inputBatch = IntStream.range(0, batchData.length)
                  .mapToObj(trainingExampleId ->batchData[trainingExampleId][inputIndex]).toArray(i -> new Tensor[i]);
      return new ConstNNResult(inputBatch);
    }).toArray(x -> new NNResult[x]);
  }
  
  /**
   * Accumulate.
   *
   * @param buffer the buffer
   */
  public final void accumulate(DeltaSet buffer) {
    accumulate(buffer, 1.0);
  }
  
  /**
   * Accumulate.
   *
   * @param buffer the buffer
   * @param value  the value
   */
  public final void accumulate(DeltaSet buffer, double value) {
    Tensor[] defaultVector = IntStream.range(0, this.getData().length()).mapToObj(i -> {
      assert (Arrays.equals(this.getData().get(i).getDimensions(), new int[]{1}));
      return new Tensor(this.getData().get(i).getDimensions()).fill(() -> value);
    }).toArray(i -> new Tensor[i]);
    accumulate(buffer, new TensorArray(defaultVector));
  }
  
  /**
   * Accumulate.
   *
   * @param buffer the buffer
   * @param data   the data
   */
  public abstract void accumulate(DeltaSet buffer, final TensorList data);
  
  /**
   * Is alive boolean.
   *
   * @return the boolean
   */
  public abstract boolean isAlive();
  
  /**
   * The Data.
   */
  public TensorList getData() {
    return data;
  }
  
  private final Map<Integer, CudaPtr> stateCache = new HashMap<>();
  public CudaPtr getGpuFloats(int device) {
    return stateCache.computeIfAbsent(device, i->CudaPtr.toDeviceAsFloat(device, data));
  }
}
