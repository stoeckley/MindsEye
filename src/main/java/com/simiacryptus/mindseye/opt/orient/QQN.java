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

package com.simiacryptus.mindseye.opt.orient;

import com.simiacryptus.mindseye.eval.Trainable;
import com.simiacryptus.mindseye.lang.DeltaSet;
import com.simiacryptus.mindseye.lang.PointSample;
import com.simiacryptus.mindseye.opt.TrainingMonitor;
import com.simiacryptus.mindseye.opt.line.LineSearchCursor;
import com.simiacryptus.mindseye.opt.line.LineSearchPoint;
import com.simiacryptus.mindseye.opt.line.SimpleLineSearchCursor;

/**
 * Quadratic Quasi-Newton optimization
 * <p>
 * This method hybridizes pure gradient descent with higher-order quasinewton
 * implementations such as L-BFGS. During each iteration, a quadratic curve is
 * interpolated which aligns with the gradient's direction prediction and
 * intersects with the quasinewton's optimal point prediction. A simple
 * parameteric quadratic function blends both inner cursors into a simple
 * nonlinear path which should combine the stability of both methods.
 */
public class QQN implements OrientationStrategy<LineSearchCursor> {
  
  private final LBFGS inner = new LBFGS();
  
  @Override
  public LineSearchCursor orient(Trainable subject, PointSample origin, TrainingMonitor monitor) {
    inner.addToHistory(origin, monitor);
    SimpleLineSearchCursor lbfgsCursor = inner.orient(subject, origin, monitor);
    final DeltaSet lbfgs = lbfgsCursor.direction;
    DeltaSet gd = origin.delta.scale(-1.0);
    double lbfgsMag = lbfgs.getMagnitude();
    double gdMag = gd.getMagnitude();
    if ((Math.abs(lbfgsMag - gdMag) / (lbfgsMag + gdMag)) > 1e-2) {
      DeltaSet scaledGradient = gd.scale(lbfgsMag / gdMag);
      monitor.log(String.format("Returning Quadratic Cursor %s GD, %s QN", gdMag, lbfgsMag));
      return new LineSearchCursor() {
        
        @Override
        public String getDirectionType() {
          return "QQN";
        }
        
        @Override
        public LineSearchPoint step(double t, TrainingMonitor monitor) {
          if (!Double.isFinite(t)) throw new IllegalArgumentException();
          reset();
          position(t).accumulate(1);
          PointSample sample = subject.measure(true, monitor).setRate(t);
          //monitor.log(String.format("delta buffers %d %d %d %d %d", sample.delta.run.size(), origin.delta.run.size(), lbfgs.run.size(), gd.run.size(), scaledGradient.run.size()));
          inner.addToHistory(sample, monitor);
          DeltaSet tangent = scaledGradient.scale(1 - 2 * t).add(lbfgs.scale(2 * t));
          return new LineSearchPoint(sample, tangent.dot(sample.delta));
        }
        
        @Override
        public DeltaSet position(double t) {
          if (!Double.isFinite(t)) throw new IllegalArgumentException();
          return scaledGradient.scale(t - t * t).add(lbfgs.scale(t * t));
        }
        
        @Override
        public void reset() {
          lbfgsCursor.reset();
        }
      };
    }
    else {
      return lbfgsCursor;
    }
  }
  
  @Override
  public void reset() {
    inner.reset();
  }
  
  
  /**
   * Gets min history.
   *
   * @return the min history
   */
  public int getMinHistory() {
    return inner.getMinHistory();
  }
  
  /**
   * Sets min history.
   *
   * @param minHistory the min history
   * @return the min history
   */
  public QQN setMinHistory(int minHistory) {
    inner.setMinHistory(minHistory);
    return this;
  }
  
  /**
   * Gets max history.
   *
   * @return the max history
   */
  public int getMaxHistory() {
    return inner.getMaxHistory();
  }
  
  /**
   * Sets max history.
   *
   * @param maxHistory the max history
   * @return the max history
   */
  public QQN setMaxHistory(int maxHistory) {
    inner.setMaxHistory(maxHistory);
    return this;
  }
  
}
