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

package com.simiacryptus.mindseye.opt.region;

import com.simiacryptus.mindseye.lang.RecycleBin;
import com.simiacryptus.util.ArrayUtil;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This constraint ensures that the L2 magnitude of the weight evalInputDelta cannot exceed a simple threshold. A simpler version
 * of AdaptiveTrustSphere, it places a limit on the step size for a given layer.
 */
public class OrthonormalConstraint implements TrustRegion {
  
  private final int[][] indexMap;
  
  public OrthonormalConstraint(int[]... indexMap) {
    assert Arrays.stream(indexMap).mapToInt(x -> x.length).distinct().count() == 1;
    assert Arrays.stream(indexMap).flatMapToInt(x -> Arrays.stream(x)).distinct().count() == Arrays.stream(indexMap).flatMapToInt(x -> Arrays.stream(x)).count();
    assert Arrays.stream(indexMap).flatMapToInt(x -> Arrays.stream(x)).max().getAsInt() == Arrays.stream(indexMap).flatMapToInt(x -> Arrays.stream(x)).count() - 1;
    assert Arrays.stream(indexMap).flatMapToInt(x -> Arrays.stream(x)).min().getAsInt() == 0;
    this.indexMap = indexMap;
  }
  
  /**
   * Length double.
   *
   * @param weights the weights
   * @return the double
   */
  public double length(@Nonnull final double[] weights) {
    return ArrayUtil.magnitude(weights);
  }
  
  @Nonnull
  @Override
  public double[] project(@Nonnull final double[] weights, @Nonnull final double[] point) {
    return recompose(unitVectors(decompose(point)));
  }
  
  public List<double[]> unitVectors(final List<double[]> vectors) {
    double[] magnitudes = vectors.stream().mapToDouble(x -> Math.sqrt(Arrays.stream(x).map(a -> a * a).sum())).toArray();
    return IntStream.range(0, magnitudes.length).mapToObj(n -> Arrays.stream(vectors.get(n)).map(x -> x / magnitudes[n]).toArray()).collect(Collectors.toList());
  }
  
  public double[] recompose(final List<double[]> unitVectors) {
    double[] doubles = RecycleBin.DOUBLES.create(Arrays.stream(indexMap).mapToInt(x -> x.length).sum());
    IntStream.range(0, indexMap.length).forEach(n -> {
      double[] array = unitVectors.get(n);
      IntStream.range(0, array.length).forEach(m -> {
        doubles[indexMap[n][m]] = unitVectors.get(n)[m];
      });
    });
    return doubles;
  }
  
  public List<double[]> decompose(@Nonnull final double[] point) {
    return Arrays.stream(indexMap).map(x -> Arrays.stream(x).mapToDouble(i -> point[i]).toArray()).collect(Collectors.toList());
  }
  
}