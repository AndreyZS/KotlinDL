/*
 * Copyright 2020 JetBrains s.r.o. and Kotlin Deep Learning project contributors. All Rights Reserved.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

import api.core.Sequential;
import api.core.activation.Activations;
import api.core.callback.Callback;
import api.core.initializer.Constant;
import api.core.initializer.GlorotNormal;
import api.core.initializer.Zeros;
import api.core.layer.Dense;
import api.core.layer.Flatten;
import api.core.layer.Input;
import api.core.layer.twodim.Conv2D;
import api.core.layer.twodim.ConvPadding;
import api.core.layer.twodim.MaxPool2D;
import api.core.loss.SoftmaxCrossEntropyWithLogits;
import api.core.metric.Metrics;
import api.core.optimizer.Adam;
import api.core.optimizer.NoClipGradient;
import datasets.Dataset;
import datasets.handlers.MnistUtilKt;
import kotlin.Pair;

public class LeNetClassic {
    public static final Integer EPOCHS = 3;
    public static final Integer TRAINING_BATCH_SIZE = 1000;
    public static final Long NUM_CHANNELS = 1L;
    public static final Long IMAGE_SIZE = 28L;
    public static final Long SEED = 12L;
    public static final Integer TEST_BATCH_SIZE = 1000;

    public static void main(String[] args) {
        Pair<Dataset, Dataset> result = Dataset.Companion.createTrainAndTestDatasets(
                MnistUtilKt.TRAIN_IMAGES_ARCHIVE,
                MnistUtilKt.TRAIN_LABELS_ARCHIVE,
                MnistUtilKt.TEST_IMAGES_ARCHIVE,
                MnistUtilKt.TEST_LABELS_ARCHIVE,
                MnistUtilKt.NUMBER_OF_CLASSES,
                MnistUtilKt::extractImages,
                MnistUtilKt::extractLabels
        );

        Dataset train = result.component1();
        Dataset test = result.component2();

        try (Sequential lenet5Classic = Sequential.Companion.of(
                new Input(new long[]{IMAGE_SIZE, IMAGE_SIZE, NUM_CHANNELS}, "x"),
                new Conv2D(6, new long[]{5, 5}, new long[]{1, 1, 1, 1}, new long[]{1, 1, 1, 1}, Activations.Tanh, new GlorotNormal(SEED), new Zeros(), ConvPadding.SAME, "conv2d_1"),
                new MaxPool2D(new int[]{1, 2, 2, 1}, new int[]{1, 2, 2, 1}, ConvPadding.VALID, "maxPool_1"),
                new Conv2D(16, new long[]{5, 5}, new long[]{1, 1, 1, 1}, new long[]{1, 1, 1, 1}, Activations.Tanh, new GlorotNormal(SEED), new Zeros(), ConvPadding.SAME, "conv2d_2"),
                new MaxPool2D(new int[]{1, 2, 2, 1}, new int[]{1, 2, 2, 1}, ConvPadding.VALID, "maxPool_2"),
                new Flatten(), // 3136
                new Dense(120, Activations.Tanh, new GlorotNormal(SEED), new Constant(0.1f), "dense_1"),
                new Dense(84, Activations.Tanh, new GlorotNormal(SEED), new Constant(0.1f), "dense_2"),
                new Dense(datasets.handlers.MnistUtilKt.NUMBER_OF_CLASSES, Activations.Linear, new GlorotNormal(SEED), new Constant(0.1f), "dense_3")
        )) {

            Adam adam = new Adam(0.001f, 0.9f, 0.999f, 1e-07f, new NoClipGradient());
            lenet5Classic.compile(adam, new SoftmaxCrossEntropyWithLogits(), Metrics.ACCURACY, new Callback());
            lenet5Classic.summary(30, 26);
            lenet5Classic.fit(train, EPOCHS, TRAINING_BATCH_SIZE, true);

            Double accuracy = lenet5Classic.evaluate(test, TEST_BATCH_SIZE).getMetrics().get(Metrics.ACCURACY);
            System.out.println("Accuracy: " + accuracy);
        }
    }
}


