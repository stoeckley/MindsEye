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

package com.simiacryptus.mindseye.network.graph;

import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.layers.NNResult;

import java.io.Serializable;
import java.util.UUID;

/**
 * The interface Dag node.
 */
public interface DAGNode extends Serializable {
  
  
  /**
   * Gets id.
   *
   * @return the id
   */
  UUID getId();
  
  /**
   * Gets layer.
   *
   * @return the layer
   */
  NNLayer getLayer();
  
  /**
   * Get nn result.
   *
   * @param nncontext   the nncontext
   * @param buildExeCtx the build exe ctx
   * @return the nn result
   */
  NNResult get(NNLayer.NNExecutionContext nncontext, EvaluationContext buildExeCtx);
  
  /**
   * Get inputs dag node [ ].
   *
   * @return the dag node [ ]
   */
  default DAGNode[] getInputs() {
    return new DAGNode[]{};
  }
  
  
}