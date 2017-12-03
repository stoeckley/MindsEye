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

package com.simiacryptus.mindseye.lang;

import com.simiacryptus.util.data.DoubleStatistics;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.stream.DoubleStream;

/**
 * The type Double array stats facade.
 */
public class DoubleArrayStatsFacade {
  /**
   * The Data.
   */
  private final double[] data;

  /**
   * Instantiates a new Double array stats facade.
   *
   * @param data the data
   */
  public DoubleArrayStatsFacade(double[] data) {
    this.data = data;
  }


  /**
   * Sum double.
   *
   * @return the double
   */
  public double sum() {
    DoubleSummaryStatistics statistics = Arrays.stream(data).summaryStatistics();
    return statistics.getSum();
  }

  /**
   * Sum sq double.
   *
   * @return the double
   */
  public double sumSq() {
    double[] sorted = Arrays.stream(data).sorted().toArray();
    double[] rsorted = Arrays.stream(data).map(x->-x).sorted().map(x->-x).toArray();
    double sumOfSquare = Arrays.stream(data).map(x->x*x).sorted().sum();
    DoubleStream doubleStream = Arrays.stream(data).map((double x) -> x * x);
    DoubleSummaryStatistics statistics = doubleStream.summaryStatistics();
    return statistics.getSum();
  }

  /**
   * Rms double.
   *
   * @return the double
   */
  public double rms() {
    return Math.sqrt(sumSq() / length());
  }

  /**
   * Length int.
   *
   * @return the int
   */
  public int length() {
    return data.length;
  }
}