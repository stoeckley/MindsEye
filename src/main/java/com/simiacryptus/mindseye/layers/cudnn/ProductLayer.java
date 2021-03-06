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
import com.simiacryptus.mindseye.lang.cudnn.*;
import com.simiacryptus.mindseye.layers.java.ProductInputsLayer;
import jcuda.jcudnn.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * This key multiplies together the inputs, element-by-element. It can be used to implement integer-power activation
 * layers, such as the square needed in MeanSqLossLayer.
 */
@SuppressWarnings("serial")
public class ProductLayer extends LayerBase implements MultiPrecision<ProductLayer> {

  private Precision precision = Precision.Double;

  /**
   * Instantiates a new Product inputs key.
   */
  public ProductLayer() {
  }

  /**
   * Instantiates a new Product inputs key.
   *
   * @param id the id
   */
  protected ProductLayer(@Nonnull final JsonObject id) {
    super(id);
    this.precision = Precision.valueOf(id.getAsJsonPrimitive("precision").getAsString());
  }

  /**
   * From json product inputs key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the product inputs key
   */
  public static ProductLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new ProductLayer(json);
  }

  /**
   * Gets compatibility key.
   *
   * @return the compatibility key
   */
  @Nonnull
  public Layer getCompatibilityLayer() {
    return this.as(ProductInputsLayer.class);
  }


  @Nullable
  @Override
  public Result evalAndFree(@Nonnull final Result... inObj) {
    if (!CudaSystem.isEnabled()) return getCompatibilityLayer().evalAndFree(inObj);
    if (inObj.length != 2) {
      throw new IllegalArgumentException("inObj.length=" + inObj.length);
    }
    Result left = inObj[0];
    Result right = inObj[1];
    final TensorList leftData = left.getData();
    final TensorList rightData = right.getData();
    @Nonnull final int[] leftDimensions = leftData.getDimensions();
    @Nonnull final int[] rightDimensions = rightData.getDimensions();
    final int length = leftData.length();
    if (3 != leftDimensions.length) {
      throw new IllegalArgumentException("dimensions=" + Arrays.toString(leftDimensions));
    }
    return new Result(CudaSystem.run(gpu -> {
      @Nonnull final CudaResource<cudnnOpTensorDescriptor> opDescriptor = gpu.newOpDescriptor(cudnnOpTensorOp.CUDNN_OP_TENSOR_MUL, precision);
      @Nonnull final CudaDevice.CudaTensorDescriptor outputDescriptor = gpu.newTensorDescriptor(precision, length,
          leftDimensions[2], leftDimensions[1], leftDimensions[0],
          leftDimensions[2] * leftDimensions[1] * leftDimensions[0],
          leftDimensions[1] * leftDimensions[0],
          leftDimensions[0],
          1);
      @Nullable final CudaTensor lPtr = gpu.getTensor(leftData, precision, MemoryType.Device, false);
      @Nullable final CudaTensor rPtr = gpu.getTensor(rightData, precision, MemoryType.Device, false);
      //assert lPtr.size == rPtr.size;
      @Nonnull final CudaMemory outputPtr = gpu.allocate((long) precision.size * outputDescriptor.nStride * length, MemoryType.Device, true);
      CudaMemory lPtrMemory = lPtr.getMemory(gpu);
      CudaMemory rPtrMemory = rPtr.getMemory(gpu);
      CudaSystem.handle(gpu.cudnnOpTensor(opDescriptor.getPtr(),
          precision.getPointer(1.0), lPtr.descriptor.getPtr(), lPtrMemory.getPtr(),
          precision.getPointer(1.0), rPtr.descriptor.getPtr(), rPtrMemory.getPtr(),
          precision.getPointer(0.0), outputDescriptor.getPtr(), outputPtr.getPtr()));
      assert CudaDevice.isThreadDeviceId(gpu.getDeviceId());
      lPtrMemory.dirty();
      rPtrMemory.dirty();
      outputPtr.dirty();
      lPtrMemory.freeRef();
      rPtrMemory.freeRef();
      rPtr.freeRef();
      lPtr.freeRef();
      opDescriptor.freeRef();
      CudaTensor cudaTensor = CudaTensor.wrap(outputPtr, outputDescriptor, precision);
      return CudaTensorList.wrap(cudaTensor, length, leftDimensions, precision);
    }, leftData), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList delta) -> {
      if (left.isAlive()) {
        @Nonnull TensorList data = CudaSystem.run(gpu -> {
          @Nonnull final CudaResource<cudnnOpTensorDescriptor> opDescriptor = gpu.newOpDescriptor(cudnnOpTensorOp.CUDNN_OP_TENSOR_MUL, precision);
          @Nonnull final CudaDevice.CudaTensorDescriptor outputDescriptor = gpu.newTensorDescriptor(precision, length,
              leftDimensions[2], leftDimensions[1], leftDimensions[0],
              leftDimensions[2] * leftDimensions[1] * leftDimensions[0],
              leftDimensions[1] * leftDimensions[0],
              leftDimensions[0],
              1);
          @Nullable final CudaTensor deltaTensor = gpu.getTensor(delta, precision, MemoryType.Device, false);
          @Nullable final CudaTensor rightTensor = gpu.getTensor(right.getData(), precision, MemoryType.Device, false);
          //assert deltaTensor.size == rightTensor.size;
          @Nonnull final CudaMemory outputPtr = gpu.allocate((long) precision.size * outputDescriptor.nStride * length, MemoryType.Device, true);
          CudaMemory deltaTensorMemory = deltaTensor.getMemory(gpu);
          CudaMemory rightTensorMemory = rightTensor.getMemory(gpu);
          CudaSystem.handle(gpu.cudnnOpTensor(opDescriptor.getPtr(),
              precision.getPointer(1.0), deltaTensor.descriptor.getPtr(), deltaTensorMemory.getPtr(),
              precision.getPointer(1.0), rightTensor.descriptor.getPtr(), rightTensorMemory.getPtr(),
              precision.getPointer(0.0), outputDescriptor.getPtr(), outputPtr.getPtr()));
          deltaTensorMemory.dirty();
          rightTensorMemory.dirty();
          outputPtr.dirty();
          deltaTensorMemory.freeRef();
          rightTensorMemory.freeRef();
          CudaTensor cudaTensor = new CudaTensor(outputPtr, outputDescriptor, precision);
          Arrays.stream(new ReferenceCounting[]{deltaTensor, rightTensor, opDescriptor, outputDescriptor}).forEach(ReferenceCounting::freeRef);
          outputPtr.freeRef();
          return CudaTensorList.wrap(cudaTensor, length, leftDimensions, precision);
        }, delta);
        left.accumulate(buffer, data);
      }
      if (right.isAlive()) {
        @Nonnull TensorList data = CudaSystem.run(gpu -> {
          @Nonnull final CudaResource<cudnnOpTensorDescriptor> opDescriptor = gpu.newOpDescriptor(cudnnOpTensorOp.CUDNN_OP_TENSOR_MUL, precision);
          @Nonnull final CudaDevice.CudaTensorDescriptor expandedDescriptor = gpu.newTensorDescriptor(precision, length,
              leftDimensions[2], leftDimensions[1], leftDimensions[0],
              leftDimensions[2] * leftDimensions[1] * leftDimensions[0],
              leftDimensions[1] * leftDimensions[0],
              leftDimensions[0],
              1);
          @Nullable final CudaTensor deltaTensor = gpu.getTensor(delta, precision, MemoryType.Device, false);
          delta.freeRef();
          @Nullable final CudaTensor leftTensor = gpu.getTensor(left.getData(), precision, MemoryType.Device, false);
          //assert deltaTensor.size == rightTensor.size;
          @Nonnull final CudaMemory outputPtr = gpu.allocate((long) precision.size * expandedDescriptor.nStride * length, MemoryType.Device, true);
          CudaMemory deltaTensorMemory = deltaTensor.getMemory(gpu);
          CudaMemory leftTensorMemory = leftTensor.getMemory(gpu);
          CudaSystem.handle(gpu.cudnnOpTensor(opDescriptor.getPtr(),
              precision.getPointer(1.0), deltaTensor.descriptor.getPtr(), deltaTensorMemory.getPtr(),
              precision.getPointer(1.0), leftTensor.descriptor.getPtr(), leftTensorMemory.getPtr(),
              precision.getPointer(0.0), expandedDescriptor.getPtr(), outputPtr.getPtr()));
          deltaTensorMemory.dirty();
          leftTensorMemory.dirty();
          outputPtr.dirty();
          if (Arrays.equals(rightDimensions, leftDimensions) && length == rightData.length()) {
            deltaTensorMemory.freeRef();
            leftTensorMemory.freeRef();
            assert CudaDevice.isThreadDeviceId(gpu.getDeviceId());
            outputPtr.dirty();
            CudaTensor cudaTensor = new CudaTensor(outputPtr, expandedDescriptor, precision);
            Stream.of(deltaTensor, leftTensor, opDescriptor, expandedDescriptor, outputPtr).forEach(ReferenceCounting::freeRef);
            CudaTensorList tensorList = CudaTensorList.wrap(cudaTensor, length, rightDimensions, precision);
            return tensorList;
          } else {
            @Nonnull final CudaDevice.CudaTensorDescriptor reducedOutputDescriptor = gpu.newTensorDescriptor(precision, rightData.length(),
                rightDimensions[2], rightDimensions[1], rightDimensions[0],
                rightDimensions[2] * rightDimensions[1] * rightDimensions[0],
                rightDimensions[1] * rightDimensions[0],
                rightDimensions[0],
                1);
            long size = (long) precision.size * reducedOutputDescriptor.nStride * rightData.length();
            @Nonnull final CudaMemory reducedOutputPtr = gpu.allocate(size, MemoryType.Managed, true);
            CudaResource<cudnnReduceTensorDescriptor> reduceTensorDescriptor = gpu.cudnnCreateReduceTensorDescriptor(
                cudnnReduceTensorOp.CUDNN_REDUCE_TENSOR_ADD, precision.code, cudnnNanPropagation.CUDNN_NOT_PROPAGATE_NAN,
                cudnnReduceTensorIndices.CUDNN_REDUCE_TENSOR_NO_INDICES, cudnnIndicesType.CUDNN_32BIT_INDICES);

            @Nonnull final CudaMemory workspacePtr = gpu.allocate(outputPtr.size, MemoryType.Device, true);
            @Nonnull final CudaMemory indexPtr = gpu.allocate(3, MemoryType.Device, false);

            //outputPtr.synchronize();
            gpu.cudnnReduceTensor(reduceTensorDescriptor.getPtr(),
                indexPtr.getPtr(), indexPtr.size, workspacePtr.getPtr(), workspacePtr.size,
                precision.getPointer(1.0), expandedDescriptor.getPtr(), outputPtr.getPtr(),
                precision.getPointer(0.0), reducedOutputDescriptor.getPtr(), reducedOutputPtr.getPtr());
            reducedOutputPtr.dirty();
            workspacePtr.dirty();
            outputPtr.dirty();

            deltaTensorMemory.freeRef();
            leftTensorMemory.freeRef();
            CudaTensor cudaTensor = new CudaTensor(reducedOutputPtr, reducedOutputDescriptor, precision);
            Stream.of(deltaTensor, leftTensor, opDescriptor, expandedDescriptor, outputPtr, reducedOutputPtr, reducedOutputDescriptor, reduceTensorDescriptor, workspacePtr, indexPtr)
                .forEach(ReferenceCounting::freeRef);
            CudaTensorList tensorList = CudaTensorList.wrap(cudaTensor, rightData.length(), rightDimensions, precision);
            return tensorList;
          }
        }, delta);
        right.accumulate(buffer, data);
      } else {
        delta.freeRef();
      }
    }) {

      @Override
      public void accumulate(final DeltaSet<UUID> buffer, final TensorList delta) {
        getAccumulator().accept(buffer, delta);
      }


      @Override
      protected void _free() {
        leftData.freeRef();
        rightData.freeRef();
        left.freeRef();
        right.freeRef();
      }


      @Override
      public boolean isAlive() {
        for (@Nonnull final Result element : inObj)
          if (element.isAlive()) {
            return true;
          }
        return false;
      }

    };
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, DataSerializer dataSerializer) {
    @Nonnull JsonObject json = super.getJsonStub();
    json.addProperty("precision", precision.name());
    return json;
  }

  @Override
  public Precision getPrecision() {
    return precision;
  }

  @Nonnull
  @Override
  public ProductLayer setPrecision(final Precision precision) {
    this.precision = precision;
    return this;
  }

  @Nonnull
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
}
