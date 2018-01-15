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

package com.simiacryptus.mindseye.layers.cudnn;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.layers.cudnn.lang.*;
import com.simiacryptus.mindseye.layers.java.ReLuActivationLayer;
import com.simiacryptus.mindseye.layers.java.SigmoidActivationLayer;
import jcuda.jcudnn.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The generic Activation layer, exposing the activation types provided by CuDNN. This layer is stateless and is
 * determined by a univariate function, e.g. ReLU or Sigmoid.
 */
@SuppressWarnings("serial")
public class ActivationLayer extends NNLayer implements LayerPrecision<ActivationLayer> {
  /**
   * The Mode.
   */
  final int mode;
  private Precision precision = Precision.Double;
  
  
  /**
   * Instantiates a new Activation layer.
   *
   * @param id the id
   */
  public ActivationLayer(final int id) {
    mode = id;
  }
  
  /**
   * Instantiates a new Activation layer.
   *
   * @param json the json
   */
  protected ActivationLayer(final JsonObject json) {
    super(json);
    mode = json.getAsJsonPrimitive("mode").getAsInt();
    precision = Precision.valueOf(json.get("precision").getAsString());
  }
  
  /**
   * Instantiates a new Activation layer.
   *
   * @param mode the mode
   */
  public ActivationLayer(final Mode mode) {
    this(mode.id);
  }
  
  /**
   * From json activation layer.
   *
   * @param json the json
   * @param rs   the rs
   * @return the activation layer
   */
  public static ActivationLayer fromJson(final JsonObject json, Map<String, byte[]> rs) {
    return new ActivationLayer(json);
  }
  
  /**
   * Gets compatibility layer.
   *
   * @return the compatibility layer
   */
  public NNLayer getCompatibilityLayer() {
    if (mode == Mode.SIGMOID.id) {
      return new SigmoidActivationLayer().setBalanced(false);
    }
    else if (mode == Mode.RELU.id) {
      return new ReLuActivationLayer();
    }
    else {
      throw new RuntimeException("Not Implemented");
    }
  }
  
  @Override
  public NNResult eval(final NNResult... inObj) {
    if (!CuDNN.isEnabled()) return getCompatibilityLayer().eval(inObj);
    //assert Arrays.stream(inObj).flatMapToDouble(input->input.data.stream().flatMapToDouble(x-> Arrays.stream(x.getData()))).allMatch(v->Double.isFinite(v));
    final NNResult input = inObj[0];
    final TensorList batch = input.getData();
    final int[] inputSize = batch.getDimensions();
    final int[] outputSize = inputSize;
    final int length = batch.length();
    final int inputDims = Tensor.dim(inputSize);
    try {
      return CuDNN.run(nncontext -> {
        final CudaResource<cudnnTensorDescriptor> inputDescriptor = CuDNN.newTensorDescriptor(
          precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, length, inputSize[2], inputSize[1], inputSize[0]);
        final CudaResource<cudnnTensorDescriptor> outputDescriptor = CuDNN.newTensorDescriptor(
          precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, length, inputSize[2], inputSize[1], inputSize[0]);
        final CudaPtr inputData = CudaPtr.write(nncontext.getDeviceNumber(), precision, batch);
        final CudaPtr outputData = CuDNN.alloc(nncontext.getDeviceNumber(), precision.size * 1l * inputDims * length, true);
        final CudaResource<cudnnActivationDescriptor> activationDesc = CuDNN.newActivationDescriptor(mode, cudnnNanPropagation.CUDNN_NOT_PROPAGATE_NAN, 0);
        try {
          CuDNN.handle(CuDNN.cudnnActivationForward(nncontext.cudnnHandle, activationDesc.getPtr(),
                                                    precision.getPointer(1.0),
                                                    inputDescriptor.getPtr(), inputData.getPtr(),
                                                    precision.getPointer(0.0),
                                                    outputDescriptor.getPtr(), outputData.getPtr()));
        } catch (final Throwable e) {
          throw new ComponentException("Error with " + Arrays.toString(inputSize), e);
        }
        final TensorList output = new GpuTensorList(outputData, length, outputSize, precision);
        //assert output.stream().flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(v->Double.isFinite(v));
        return new NNResult(output) {
        
          @Override
          public void free() {
            Arrays.stream(inObj).forEach(NNResult::free);
          }
        
          @Override
          public void accumulate(final DeltaSet<NNLayer> buffer, final TensorList error) {
            if (input.isAlive()) {
              final GpuTensorList data = CuDNN.run(nncontext -> {
                //assert (error.length() == batch.length());
                //assert error.stream().flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(v->Double.isFinite(v));
                final CudaPtr errorPtr = CudaPtr.write(nncontext.getDeviceNumber(), precision, error);
                final CudaPtr passbackBuffer = CuDNN.alloc(nncontext.getDeviceNumber(), inputDims * 1l * precision.size * length, true);
                try {
                  final CudaResource<cudnnActivationDescriptor> activationDesc = CuDNN.newActivationDescriptor(mode, cudnnNanPropagation.CUDNN_NOT_PROPAGATE_NAN, 0);
                  CuDNN.handle(CuDNN.cudnnActivationBackward(nncontext.cudnnHandle, activationDesc.getPtr(),
                                                             precision.getPointer(1.0),
                                                             inputDescriptor.getPtr(), outputData.getPtr(),
                                                             inputDescriptor.getPtr(), errorPtr.getPtr(),
                                                             inputDescriptor.getPtr(), inputData.getPtr(),
                                                             precision.getPointer(0.0),
                                                             inputDescriptor.getPtr(), passbackBuffer.getPtr()));
                } catch (final Throwable e) {
                  throw new ComponentException("Error with " + Arrays.toString(inputSize), e);
                }
                return new GpuTensorList(passbackBuffer, length, inputSize, precision);
              });
              input.accumulate(buffer, data);
              data.recycle();
            }
          }
        
          @Override
          public boolean isAlive() {
            return input.isAlive() || !isFrozen();
          }
        };
      });
    } catch (final Throwable e) {
      throw new ComponentException("Error with image res " + Arrays.toString(inputSize), e);
    }
  }
  
  @Override
  public JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
    final JsonObject json = super.getJsonStub();
    json.addProperty("mode", mode);
    json.addProperty("precision", precision.name());
    return json;
  }
  
  @Override
  public Precision getPrecision() {
    return precision;
  }
  
  @Override
  public ActivationLayer setPrecision(final Precision precision) {
    this.precision = precision;
    return this;
  }
  
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
  
  
  /**
   * The enum Mode.
   */
  public enum Mode {
    /**
     * Relu mode.
     */
    RELU(cudnnActivationMode.CUDNN_ACTIVATION_RELU),
    /**
     * Sigmoid mode.
     */
    SIGMOID(cudnnActivationMode.CUDNN_ACTIVATION_SIGMOID);
    /**
     * The Id.
     */
    public final int id;
  
    Mode(final int id) {
      this.id = id;
    }
  }
  
}
