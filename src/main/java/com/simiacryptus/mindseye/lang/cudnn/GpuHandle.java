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

package com.simiacryptus.mindseye.lang.cudnn;

import com.simiacryptus.util.lang.StaticResourcePool;
import jcuda.jcudnn.JCudnn;
import jcuda.jcudnn.cudnnHandle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The type Gpu handle.
 */
public class GpuHandle extends GpuDevice {
  private static final ThreadLocal<GpuHandle> threadContext = new ThreadLocal<>();
  private static final boolean DISABLE = Boolean.parseBoolean(System.getProperty("DISABLE_CUDNN", Boolean.toString(false)));
  private static final boolean FORCE_SINGLE_GPU = Boolean.parseBoolean(System.getProperty("FORCE_SINGLE_GPU", Boolean.toString(false)));
  private static final int THREADS_PER_GPU = Integer.parseInt(System.getProperty("THREADS_PER_GPU", Integer.toString(3)));
  
  /**
   * The constant gpuContexts.
   */
  public static final StaticResourcePool<GpuHandle> POOL = new StaticResourcePool<>(loadGpuContexts());
  private final jcuda.jcudnn.cudnnHandle handle;
  
  /**
   * Instantiates a new Cu dnn.
   *
   * @param deviceNumber the device number
   */
  private GpuHandle(final int deviceNumber) {
    super(deviceNumber);
    if (0 <= this.deviceNumber) {
      initThread();
      handle = new cudnnHandle();
      JCudnn.cudnnCreate(getHandle());
    }
    else {
      handle = null;
    }
    //cudaSetDevice();
  }
  
  /**
   * Run.
   *
   * @param fn the fn
   */
  public static void apply(final Consumer<GpuHandle> fn) {apply(fn, true);}
  
  /**
   * Run.
   *
   * @param fn          the fn
   * @param synchronize the synchronize
   */
  public static void apply(final Consumer<GpuHandle> fn, boolean synchronize) {
    GpuHandle threadlocal = threadContext.get();
    try {
      if (threadlocal != null) {
        try {
          threadlocal.initThread();
          fn.accept(threadlocal);
        } catch (final Exception e) {
          throw new RuntimeException(e);
        }
      }
      else {
        POOL.apply(exe -> {
          try {
            threadContext.set(exe);
            exe.initThread();
            fn.accept(exe);
          } catch (final Exception e) {
            throw new RuntimeException(e);
          } finally {
            threadContext.remove();
          }
        });
      }
    } finally {
      if (synchronize) CuDNN.cudaDeviceSynchronize();
    }
  }
  
  /**
   * Call t.
   *
   * @param <T> the type parameter
   * @param fn  the fn
   * @return the t
   */
  public static <T> T run(final Function<GpuHandle, T> fn) {return run(fn, true);}
  
  /**
   * Call t.
   *
   * @param <T>         the type parameter
   * @param fn          the fn
   * @param synchronize the synchronize
   * @return the t
   */
  public static <T> T run(final Function<GpuHandle, T> fn, boolean synchronize) {
    if (POOL.getAll().isEmpty()) {
      return fn.apply(new GpuHandle(-1));
    }
    else {
      try {
        GpuHandle threadlocal = threadContext.get();
        if (threadlocal != null) {
          try {
            threadlocal.initThread();
            T result = fn.apply(threadlocal);
            return result;
          } catch (final Exception e) {
            throw new RuntimeException(e);
          }
        }
        else {
          return POOL.run(exe -> {
            try {
              threadContext.set(exe);
              exe.initThread();
              T result = fn.apply(exe);
              return result;
            } catch (final Exception e) {
              throw new RuntimeException(e);
            } finally {
              threadContext.remove();
            }
          });
        }
      } finally {
        if (synchronize) CuDNN.cudaDeviceSynchronize();
      }
    }
  }
  
  /**
   * For each.
   *
   * @param fn the fn
   */
  public static void forEach(final Consumer<? super GpuDevice> fn) {
    POOL.getAll().forEach(x -> {
      x.initThread();
      fn.accept(x);
    });
  }
  
  /**
   * Load gpu contexts list. If the property disableCuDnn is set to true, no GPUs will be recognized. This is useful for
   * testing CPU-only compatibility.
   *
   * @return the list
   */
  private static List<GpuHandle> loadGpuContexts() {
    if (DISABLE) {
      logger.warn("Disabled CuDNN");
      return Arrays.asList();
    }
    final int deviceCount;
    if (FORCE_SINGLE_GPU) {
      logger.warn("Forcing Single-GPU Mode");
      deviceCount = 1;
    }
    else {
      deviceCount = CuDNN.deviceCount();
    }
    logger.info(String.format("Found %s devices", deviceCount));
    final List<Integer> devices = new ArrayList<>();
    for (int d = 0; d < deviceCount; d++) {
      int deviceNumber = d;
      //if(device>0) System.err.println(String.format("IGNORING Device %s - %s", device, getDeviceName(device)));
      CuDNN.withDevice(deviceNumber, () -> {
        logger.info(String.format("Device %s - %s", deviceNumber, CuDNN.getDeviceName(deviceNumber)));
        devices.add(deviceNumber);
        try {
          //CuDNN.handle(CuDNN.cudaSetDeviceFlags(JCuda.cudaDeviceScheduleBlockingSync));
        } catch (Throwable e) {
          logger.warn("Error initializing GPU", e);
          throw new RuntimeException(e);
        }
        for (DeviceLimits limit : DeviceLimits.values()) {
          logger.info(String.format("Default Limit %s = %s", limit, limit.get()));
        }
        DeviceLimits.HeapSize.set(16 * 1024 * 1024 * 1024);
        DeviceLimits.FifoSize.set(8 * 1024 * 1024);
        for (DeviceLimits limit : DeviceLimits.values()) {
          logger.info(String.format("Configured Limit %s = %s", limit, limit.get()));
        }
      });
    }
    if (System.getProperties().containsKey("gpus")) {
      List<Integer> devices2 = Arrays.stream(System.getProperty("gpus").split(","))
                                     .map(Integer::parseInt).collect(Collectors.toList());
      devices.clear();
      devices.addAll(devices2);
    }
    logger.info(String.format("Found %s devices; using devices %s", deviceCount, devices));
    return devices.stream()
                  .flatMap(i -> {
                    try {
                      return IntStream.range(0, THREADS_PER_GPU).mapToObj(j -> new GpuHandle(i));
                    } catch (Throwable e) {
                      logger.warn(String.format("Error initializing device %d", i), e);
                      return Stream.empty();
                    }
                  }).collect(Collectors.toList());
  }
  
  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + deviceNumber + "; " + deviceName + "}@" + Long.toHexString(System.identityHashCode(this));
  }
  
  @Override
  public void finalize() throws Throwable {
    final int result = JCudnn.cudnnDestroy(getHandle());
    CuDNN.log("cudnnDestroy", result, getHandle());
    CuDNN.handle(result);
  }
  
  /**
   * The Cudnn handle.
   *
   * @return the handle
   */
  public cudnnHandle getHandle() {
    return handle;
  }
}
