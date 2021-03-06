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

package com.simiacryptus.mindseye.test.data;

import com.simiacryptus.lang.SupplierWeakCache;
import com.simiacryptus.notebook.NotebookOutput;
import com.simiacryptus.util.test.LabeledObject;

import javax.annotation.Nonnull;
import java.awt.image.BufferedImage;
import java.util.stream.Stream;

/**
 * The type Image classifier apply base.
 */
public class CaltechDatasetDemo extends ImageCategoryDatasetDemo {

  @Override
  public Stream<LabeledObject<SupplierWeakCache<BufferedImage>>> getTrainingStream(@Nonnull NotebookOutput log) {
    return log.eval(() -> {
      Stream<LabeledObject<SupplierWeakCache<BufferedImage>>> trainingDataStream = Caltech101.trainingDataStream();
      return trainingDataStream;
    });
  }

  @Nonnull
  @Override
  protected Class<?> getTargetClass() {
    return Caltech101.class;
  }
}
