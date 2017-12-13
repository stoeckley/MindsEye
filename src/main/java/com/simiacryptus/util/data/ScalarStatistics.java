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

package com.simiacryptus.util.data;

import com.google.gson.JsonObject;
import com.simiacryptus.util.MonitoredItem;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The type Scalar statistics.
 */
public class ScalarStatistics implements MonitoredItem, Serializable {
  private static final double zeroTol = 1e-20;
  private volatile int zeros = 0;
  private volatile int negatives = 0;
  private volatile int positives = 0;
  private volatile double sum0 = 0;
  private volatile double sum1 = 0;
  private volatile double sum2 = 0;
  private volatile double sumLog = 0;
  private volatile double min = Double.POSITIVE_INFINITY;
  private volatile double max = -Double.POSITIVE_INFINITY;
  
  /**
   * Stats scalar statistics.
   *
   * @param data the data
   * @return the scalar statistics
   */
  public static ScalarStatistics stats(double[] data) {
    ScalarStatistics statistics = new PercentileStatistics();
    Arrays.stream(data).forEach(statistics::add);
    return statistics;
  }
  
  /**
   * Add.
   *
   * @param values the values
   * @return the scalar statistics
   */
  public ScalarStatistics add(double... values) {
    double v1 = 0;
    double v2 = 0;
    double vmax = max;
    double vmin = max;
    int z = 0;
    double vlog = 0;
    int n = 0;
    int p = 0;
    for (double v : values) {
      v1 += v;
      v2 += v * v;
      vmin = Math.min(min, v);
      vmax = Math.max(max, v);
      if (Math.abs(v) < zeroTol) {
        z++;
      }
      else {
        if (v < 0) {
          n++;
        }
        else {
          p++;
        }
        vlog += Math.log10(Math.abs(v));
      }
    }
    synchronized (this) {
      sum0 += values.length;
      sum1 += v1;
      sum2 += v2;
      min = Math.min(min, vmin);
      max = Math.max(max, vmax);
      negatives += n;
      positives += p;
      sumLog += vlog;
      zeros += z;
    }
    return this;
  }
  
  /**
   * Add scalar statistics.
   *
   * @param right the right
   * @return the scalar statistics
   */
  public final synchronized ScalarStatistics add(ScalarStatistics right) {
    ScalarStatistics sum = new ScalarStatistics();
    sum.sum0 += this.sum0;
    sum.sum0 += right.sum0;
    sum.sum1 += this.sum1;
    sum.sum1 += right.sum1;
    sum.sum2 += this.sum2;
    sum.sum2 += right.sum2;
    return sum;
  }
  
  
  /**
   * Subtract scalar statistics.
   *
   * @param right the right
   * @return the scalar statistics
   */
  public final synchronized ScalarStatistics subtract(ScalarStatistics right) {
    ScalarStatistics sum = new ScalarStatistics();
    sum.sum0 += this.sum0;
    sum.sum0 -= right.sum0;
    sum.sum1 += this.sum1;
    sum.sum1 -= right.sum1;
    sum.sum2 += this.sum2;
    sum.sum2 -= right.sum2;
    return sum;
  }
  
  /**
   * Add.
   *
   * @param v the v
   */
  public final synchronized void add(double v) {
    sum0 += 1;
    sum1 += v;
    sum2 += v * v;
    min = Math.min(min, v);
    max = Math.max(max, v);
    if (Math.abs(v) < zeroTol) {
      zeros++;
    }
    else {
      if (v < 0) {
        negatives++;
      }
      else {
        positives++;
      }
      sumLog += Math.log10(Math.abs(v));
    }
  }
  
  @Override
  public Map<String, Object> getMetrics() {
    HashMap<String, Object> map = new HashMap<>();
    map.put("count", sum0);
    map.put("negative", negatives);
    map.put("positive", positives);
    map.put("min", min);
    map.put("max", max);
    map.put("mean", getMean());
    map.put("stdDev", getStdDev());
    map.put("meanExponent", getMeanPower());
    map.put("zeros", zeros);
    return map;
  }
  
  /**
   * Gets mean power.
   *
   * @return the mean power
   */
  public double getMeanPower() {
    return sumLog / (sum0 - zeros);
  }
  
  /**
   * Gets std dev.
   *
   * @return the std dev
   */
  public double getStdDev() {
    return Math.sqrt(Math.abs(Math.pow(getMean(), 2) - sum2 / sum0));
  }
  
  /**
   * Gets mean.
   *
   * @return the mean
   */
  public double getMean() {
    return sum1 / sum0;
  }
  
  /**
   * Clear.
   */
  public void clear() {
    min = Double.POSITIVE_INFINITY;
    max = -Double.POSITIVE_INFINITY;
    negatives = 0;
    positives = 0;
    zeros = 0;
    sum0 = 0;
    sum1 = 0;
    sum2 = 0;
    sumLog = 0;
  }
  
  /**
   * Gets count.
   *
   * @return the count
   */
  public double getCount() {
    return sum0;
  }
  
  /**
   * Gets json.
   *
   * @return the json
   */
  public JsonObject getJson() {
    JsonObject json = new JsonObject();
    json.addProperty("min", min);
    json.addProperty("max", max);
    json.addProperty("negatives", negatives);
    json.addProperty("positives", positives);
    json.addProperty("zeros", zeros);
    json.addProperty("sum0", sum0);
    json.addProperty("sum1", sum1);
    json.addProperty("sum2", sum2);
    json.addProperty("sumLog", sumLog);
    return json;
  }
  
  /**
   * Read json.
   *
   * @param json the json
   */
  public void readJson(JsonObject json) {
    if (null == json) return;
    this.min = json.get("min").getAsDouble();
    this.max = json.get("max").getAsDouble();
    this.negatives = json.get("negatives").getAsInt();
    this.positives = json.get("positives").getAsInt();
    this.zeros = json.get("zeros").getAsInt();
    this.sum0 = json.get("sum0").getAsDouble();
    this.sum1 = json.get("sum1").getAsDouble();
    this.sum2 = json.get("sum2").getAsDouble();
    this.sumLog = json.get("sumLog").getAsDouble();
  }
  
  @Override
  public String toString() {
    return getMetrics().toString();
  }
}