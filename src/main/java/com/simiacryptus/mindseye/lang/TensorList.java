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

package com.simiacryptus.mindseye.lang;

import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * This abstract data container is used to pass data between NNLayer components. It potentially represents data stored
 * off-heap, such as on a particular GPU. Use of this abstract class allows optimizations where adjacent GPU components
 * can operate with minimal CPU-GPU data transfer.
 */
public interface TensorList extends ReferenceCounting {
  
  /**
   * Add tensor list.
   *
   * @param right the right
   * @return the tensor list
   */
  default TensorList add(final TensorList right) {
    if (right.length() == 0) return this;
    if (length() == 0) throw new IllegalArgumentException();
    assert length() == right.length();
    return TensorArray.create(IntStream.range(0, length()).mapToObj(i -> {
      return get(i).add(right.get(i));
    }).toArray(i -> new Tensor[i]));
  }
  
  /**
   * Minus tensor list.
   *
   * @param right the right
   * @return the tensor list
   */
  default TensorList minus(final TensorList right) {
    if (right.length() == 0) return this;
    if (length() == 0) throw new IllegalArgumentException();
    assert length() == right.length();
    return TensorArray.create(IntStream.range(0, length()).mapToObj(i -> {
      return get(i).minus(right.get(i));
    }).toArray(i -> new Tensor[i]));
  }
  
  /**
   * Copy tensor list.
   *
   * @return the tensor list
   */
  default TensorList copy() {
    return TensorArray.create(
      IntStream.range(0, length()).mapToObj(i -> get(i).copy()).toArray(i -> new Tensor[i])
                             );
  }
  
  /**
   * Get tensor.
   *
   * @param i the
   * @return the tensor
   */
  Tensor get(int i);
  
  /**
   * Get dimensions int [ ].
   *
   * @return the int [ ]
   */
  int[] getDimensions();
  
  /**
   * Length int.
   *
   * @return the int
   */
  int length();
  
  /**
   * Stream stream.
   *
   * @return the stream
   */
  Stream<Tensor> stream();
  
  /**
   * Pretty print string.
   *
   * @return the string
   */
  default String prettyPrint() {
    return stream().map(t -> t.prettyPrint()).reduce((a, b) -> a + "\n" + b).get();
  }
  
}
