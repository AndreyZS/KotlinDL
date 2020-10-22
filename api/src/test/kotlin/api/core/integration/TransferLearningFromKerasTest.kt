/*
 * Copyright 2020 JetBrains s.r.o. and Kotlin Deep Learning project contributors. All Rights Reserved.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package api.core.integration

import api.core.Sequential
import api.core.activation.Activations
import api.core.initializer.*
import api.core.layer.Dense
import api.core.layer.Layer
import api.core.layer.twodim.Conv2D
import api.core.layer.twodim.ConvPadding
import api.core.loss.Losses
import api.core.metric.Metrics
import api.core.optimizer.Adam
import api.inference.keras.loadWeights
import api.inference.keras.loadWeightsForFrozenLayers
import datasets.Dataset
import datasets.handlers.*
import io.jhdf.HdfFile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

private const val pathToConfig = "inference/lenet/modelConfig.json"
private val realPathToConfig = TransferLearningTest::class.java.classLoader.getResource(pathToConfig).path.toString()

private const val pathToIncorrectConfig = "inference/lenet/unsupportedInitializers/modelConfig.json"
private val realPathToIncorrectConfig =
    TransferLearningTest::class.java.classLoader.getResource(pathToIncorrectConfig).path.toString()

private const val pathToWeights = "inference/lenet/mnist_weights_only.h5"
private val realPathToWeights = TransferLearningTest::class.java.classLoader.getResource(pathToWeights).path.toString()

class TransferLearningTest : IntegrationTest() {
    /** Loads configuration with default initializers for the last Dense layer from Keras. But Zeros initializer (default initializers for bias) is not supported yet. */
    @Test
    fun loadIncorrectSequentialJSONConfig() {
        val jsonConfigFile = File(realPathToIncorrectConfig)

        val exception =
            assertThrows(IllegalStateException::class.java) {
                Sequential.loadModelConfiguration(jsonConfigFile)
            }
        assertEquals(
            "Zeros is not supported yet!",
            exception.message
        )
    }

    @Test
    fun loadModelConfigFromKeras() {
        val jsonConfigFile = File(realPathToConfig)
        val testModel = Sequential.loadModelConfiguration(jsonConfigFile)

        val flattenLayerName = "flatten"
        val conv2dLayerName = "conv2d"
        val conv2d1LayerName = "conv2d_1"
        val denseLayerName = "dense"
        val dense1LayerName = "dense_1"

        assertEquals(testModel.layers.size, 8)
        assertTrue(testModel.getLayer(flattenLayerName).isTrainable)
        assertFalse(testModel.getLayer(flattenLayerName).hasActivation())
        assertTrue(testModel.getLayer(conv2dLayerName) is Conv2D)
        assertTrue((testModel.getLayer(conv2dLayerName) as Conv2D).kernelInitializer is GlorotNormal)
        assertTrue((testModel.getLayer(conv2dLayerName) as Conv2D).biasInitializer is GlorotUniform)
        assertTrue((testModel.getLayer(conv2dLayerName) as Conv2D).padding == ConvPadding.SAME)
        assertTrue((testModel.getLayer(conv2dLayerName) as Conv2D).activation == Activations.Relu)
        assertTrue(testModel.getLayer(conv2dLayerName).isTrainable)
        assertTrue(testModel.getLayer(conv2dLayerName).hasActivation())
        assertTrue((testModel.getLayer(conv2d1LayerName) as Conv2D).kernelInitializer is HeNormal)
        assertTrue((testModel.getLayer(conv2d1LayerName) as Conv2D).biasInitializer is HeUniform)
        assertTrue((testModel.getLayer(conv2d1LayerName) as Conv2D).padding == ConvPadding.SAME)
        assertTrue((testModel.getLayer(conv2d1LayerName) as Conv2D).activation == Activations.Relu)
        assertTrue((testModel.getLayer(denseLayerName) as Dense).kernelInitializer is LeCunNormal)
        assertTrue((testModel.getLayer(denseLayerName) as Dense).biasInitializer is LeCunUniform)
        assertTrue((testModel.getLayer(denseLayerName) as Dense).outputSize == 256)
        assertTrue((testModel.getLayer(denseLayerName) as Dense).activation == Activations.Relu)
        assertTrue((testModel.getLayer(dense1LayerName) as Dense).kernelInitializer is RandomNormal)
        assertTrue((testModel.getLayer(dense1LayerName) as Dense).biasInitializer is RandomUniform)
        assertTrue((testModel.getLayer(dense1LayerName) as Dense).outputSize == 84)
        assertTrue((testModel.getLayer(dense1LayerName) as Dense).activation == Activations.Relu)
        assertArrayEquals(testModel.inputLayer.packedDims, longArrayOf(IMAGE_SIZE, IMAGE_SIZE, NUM_CHANNELS))
    }

    /** Weights are not loaded, but initialized via default initializers. */
    @Test
    fun loadModelConfigFromKerasAndTrain() {
        val jsonConfigFile = File(realPathToConfig)

        val testModel = Sequential.loadModelConfiguration(jsonConfigFile)

        val (train, test) = Dataset.createTrainAndTestDatasets(
            FASHION_TRAIN_IMAGES_ARCHIVE,
            FASHION_TRAIN_LABELS_ARCHIVE,
            FASHION_TEST_IMAGES_ARCHIVE,
            FASHION_TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractFashionImages,
            ::extractFashionLabels
        )

        testModel.use {
            it.compile(
                optimizer = Adam(),
                loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS,
                metric = Metrics.ACCURACY
            )

            it.fit(
                dataset = train,
                validationRate = VALIDATION_RATE,
                epochs = EPOCHS,
                trainBatchSize = TRAINING_BATCH_SIZE,
                validationBatchSize = VALIDATION_BATCH_SIZE,
                verbose = true
            )

            val accuracy = it.evaluate(dataset = test, batchSize = VALIDATION_BATCH_SIZE).metrics[Metrics.ACCURACY]

            if (accuracy != null) {
                assertTrue(accuracy > 0.7)
            }
        }
    }

    /** Compilation is missed. */
    @Test
    fun loadModelConfigFromKerasAndMissCompilation() {
        val jsonConfigFile = File(realPathToConfig)

        val testModel = Sequential.loadModelConfiguration(jsonConfigFile)

        val (train, test) = Dataset.createTrainAndTestDatasets(
            FASHION_TRAIN_IMAGES_ARCHIVE,
            FASHION_TRAIN_LABELS_ARCHIVE,
            FASHION_TEST_IMAGES_ARCHIVE,
            FASHION_TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractFashionImages,
            ::extractFashionLabels
        )

        testModel.use {
            val exception =
                assertThrows(IllegalStateException::class.java) {
                    it.fit(
                        dataset = train,
                        validationRate = VALIDATION_RATE,
                        epochs = EPOCHS,
                        trainBatchSize = TRAINING_BATCH_SIZE,
                        validationBatchSize = VALIDATION_BATCH_SIZE,
                        verbose = true
                    )
                }
            assertEquals(
                "The model is not compiled yet. Compile the model to use this method.",
                exception.message
            )
        }
    }

    @Test
    fun loadWeights() {
        val file = File(realPathToWeights)
        val hdfFile = HdfFile(file)
        assertEquals(3400864L, hdfFile.size())

        val name = "conv2d"
        val kernelData = hdfFile.getDatasetByPath("/$name/$name/kernel:0").data as Array<Array<Array<FloatArray>>>
        val biasData = hdfFile.getDatasetByPath("/$name/$name/bias:0").data as FloatArray

        assertEquals(kernelData[0][0][0][0], 0.06445057f)
        assertEquals(biasData[15], -0.25060207f)
    }

    @Test
    fun loadModelConfigAndWeightsFromKeras() {
        val jsonConfigFile = File(realPathToConfig)
        val testModel = Sequential.loadModelConfiguration(jsonConfigFile)

        val file = File(realPathToWeights)
        val hdfFile = HdfFile(file)

        testModel.use {
            it.compile(
                optimizer = Adam(),
                loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS,
                metric = Metrics.ACCURACY
            )

            it.loadWeights(hdfFile)

            val conv2DKernelWeights = it.getLayer("conv2d").getWeights()[0] as Array<Array<Array<FloatArray>>>
            assertEquals(conv2DKernelWeights[0][0][0][0], 0.06445057f)

            val conv2DKernelWeights1 = it.getLayer("conv2d_1").getWeights()[0] as Array<Array<Array<FloatArray>>>
            assertEquals(conv2DKernelWeights1[0][0][0][0], 0.027743129f)
        }
    }

    @Test
    fun loadModelConfigAndWeightsTwiceFromKeras() {
        val jsonConfigFile = File(realPathToConfig)
        val testModel = Sequential.loadModelConfiguration(jsonConfigFile)

        val file = File(realPathToWeights)
        val hdfFile = HdfFile(file)

        testModel.use {
            it.compile(
                optimizer = Adam(),
                loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS,
                metric = Metrics.ACCURACY
            )

            it.loadWeights(hdfFile)

            val exception =
                assertThrows(IllegalStateException::class.java) {
                    it.loadWeights(hdfFile)
                }
            assertEquals(
                "Model is initialized already!",
                exception.message
            )
        }
    }

    /** Simple transfer learning with additional training and without layers freezing. */
    @Test
    fun loadModelConfigAndWeightsFromKerasAndTrain() {
        val jsonConfigFile = File(realPathToConfig)
        val testModel = Sequential.loadModelConfiguration(jsonConfigFile)

        val file = File(realPathToWeights)
        val hdfFile = HdfFile(file)

        val (train, test) = Dataset.createTrainAndTestDatasets(
            FASHION_TRAIN_IMAGES_ARCHIVE,
            FASHION_TRAIN_LABELS_ARCHIVE,
            FASHION_TEST_IMAGES_ARCHIVE,
            FASHION_TEST_LABELS_ARCHIVE,
            datasets.handlers.NUMBER_OF_CLASSES,
            ::extractFashionImages,
            ::extractFashionLabels
        )

        testModel.use {
            it.compile(
                optimizer = Adam(),
                loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS,
                metric = Metrics.ACCURACY
            )

            it.loadWeights(hdfFile)

            val accuracyBefore = it.evaluate(dataset = test, batchSize = 100).metrics[Metrics.ACCURACY]

            if (accuracyBefore != null) {
                assertTrue(accuracyBefore > 0.8)
            }

            it.fit(
                dataset = train,
                validationRate = 0.1,
                epochs = 3,
                trainBatchSize = 1000,
                validationBatchSize = 100,
                verbose = false
            )

            val accuracyAfterTraining = it.evaluate(dataset = test, batchSize = 100).metrics[Metrics.ACCURACY]

            if (accuracyAfterTraining != null && accuracyBefore != null) {
                assertTrue(accuracyAfterTraining > accuracyBefore)
            }
        }

    }

    /** Simple transfer learning with additional training and Conv2D layers weights freezing. */
    @Test
    fun loadModelConfigAndWeightsFromKerasAndTrainDenseLayersOnly() {
        val jsonConfigFile = File(realPathToConfig)
        val testModel = Sequential.loadModelConfiguration(jsonConfigFile)

        val file = File(realPathToWeights)
        val hdfFile = HdfFile(file)

        val (train, test) = Dataset.createTrainAndTestDatasets(
            FASHION_TRAIN_IMAGES_ARCHIVE,
            FASHION_TRAIN_LABELS_ARCHIVE,
            FASHION_TEST_IMAGES_ARCHIVE,
            FASHION_TEST_LABELS_ARCHIVE,
            datasets.handlers.NUMBER_OF_CLASSES,
            ::extractFashionImages,
            ::extractFashionLabels
        )

        testModel.use {
            for (layer in it.layers) {
                if (layer is Conv2D)
                    layer.isTrainable = false
            }

            it.compile(
                optimizer = Adam(),
                loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS,
                metric = Metrics.ACCURACY
            )

            it.loadWeights(hdfFile)

            val accuracyBefore = it.evaluate(dataset = test, batchSize = 100).metrics[Metrics.ACCURACY]

            if (accuracyBefore != null) {
                assertTrue(accuracyBefore > 0.8)
            }

            val conv2DKernelWeightsBeforeTraining =
                it.getLayer("conv2d").getWeights()[0] as Array<Array<Array<FloatArray>>>
            assertEquals(conv2DKernelWeightsBeforeTraining[0][0][0][0], 0.06445057f)

            val denseDKernelWeightsBeforeTraining = it.getLayer("dense").getWeights()[0] as Array<FloatArray>
            assertEquals(denseDKernelWeightsBeforeTraining[0][0], 0.012644082f)

            it.fit(
                dataset = train,
                validationRate = 0.1,
                epochs = 3,
                trainBatchSize = 1000,
                validationBatchSize = 100,
                verbose = false
            )

            val conv2DKernelWeightsAfterTraining =
                it.getLayer("conv2d").getWeights()[0] as Array<Array<Array<FloatArray>>>
            assertEquals(conv2DKernelWeightsAfterTraining[0][0][0][0], 0.06445057f)
            assertArrayEquals(conv2DKernelWeightsBeforeTraining, conv2DKernelWeightsAfterTraining)

            val denseDKernelWeightsAfterTraining = it.getLayer("dense").getWeights()[0]
            assertFalse(denseDKernelWeightsBeforeTraining.contentEquals(denseDKernelWeightsAfterTraining))

            val accuracyAfterTraining = it.evaluate(dataset = test, batchSize = 100).metrics[Metrics.ACCURACY]

            if (accuracyAfterTraining != null && accuracyBefore != null) {
                assertTrue(accuracyAfterTraining > accuracyBefore)
            }
        }
    }

    /**
     * Simple transfer learning with additional training and Conv2D layers weights freezing.
     *
     * NOTE: Dense weights are initialized via default initializers and trained from zero to hero.
     */
    @Test
    fun loadModelConfigAndWeightsPartiallyFromKerasAndTrainDenseLayersOnly() {
        val jsonConfigFile = File(realPathToConfig)
        val testModel = Sequential.loadModelConfiguration(jsonConfigFile)

        val file = File(realPathToWeights)
        val hdfFile = HdfFile(file)

        val (train, test) = Dataset.createTrainAndTestDatasets(
            FASHION_TRAIN_IMAGES_ARCHIVE,
            FASHION_TRAIN_LABELS_ARCHIVE,
            FASHION_TEST_IMAGES_ARCHIVE,
            FASHION_TEST_LABELS_ARCHIVE,
            datasets.handlers.NUMBER_OF_CLASSES,
            ::extractFashionImages,
            ::extractFashionLabels
        )

        testModel.use {
            val layerList = mutableListOf<Layer>()

            for (layer in it.layers) {
                if (layer is Conv2D) {
                    layer.isTrainable = false
                    layerList.add(layer)
                }
            }

            it.compile(
                optimizer = Adam(),
                loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS,
                metric = Metrics.ACCURACY
            )

            it.loadWeights(hdfFile, layerList)

            val accuracyBefore = it.evaluate(dataset = test, batchSize = 100).metrics[Metrics.ACCURACY]

            if (accuracyBefore != null) {
                assertTrue(accuracyBefore > 0.1) // Dense layers has no meaningful weights
            }

            val conv2DKernelWeighsBeforeTraining =
                it.getLayer("conv2d").getWeights()[0] as Array<Array<Array<FloatArray>>>
            assertEquals(conv2DKernelWeighsBeforeTraining[0][0][0][0], 0.06445057f)
            val denseDKernelWeightsBeforeTraining = it.getLayer("dense").getWeights()[0] as Array<FloatArray>
            assertEquals(denseDKernelWeightsBeforeTraining[0][0], 0.008463251f)

            it.fit(
                dataset = train,
                validationRate = 0.1,
                epochs = 4,
                trainBatchSize = 1000,
                validationBatchSize = 100,
                verbose = false
            )

            val conv2DKernelWeightsAfterTraining =
                it.getLayer("conv2d").getWeights()[0] as Array<Array<Array<FloatArray>>>
            assertEquals(conv2DKernelWeightsAfterTraining[0][0][0][0], 0.06445057f)
            assertArrayEquals(conv2DKernelWeighsBeforeTraining, conv2DKernelWeightsAfterTraining)

            val denseDKernelWeightsAfterTraining = it.getLayer("dense").getWeights()[0] as Array<FloatArray>
            assertFalse(denseDKernelWeightsBeforeTraining.contentEquals(denseDKernelWeightsAfterTraining))

            val accuracyAfterTraining = it.evaluate(dataset = test, batchSize = 100).metrics[Metrics.ACCURACY]

            if (accuracyAfterTraining != null && accuracyBefore != null) {
                assertTrue(accuracyAfterTraining > accuracyBefore)
            }
        }
    }

    /**
     * Simple transfer learning with additional training and Conv2D layers weights freezing.
     *
     * NOTE: Dense weights are initialized via default initializers and trained from zero to hero.
     */
    @Test
    fun loadModelConfigAndWeightsPartiallyByLayersListFromKerasAndTrainDenseLayersOnly() {
        val jsonConfigFile = File(realPathToConfig)
        val testModel = Sequential.loadModelConfiguration(jsonConfigFile)

        val file = File(realPathToWeights)
        val hdfFile = HdfFile(file)

        val (train, test) = Dataset.createTrainAndTestDatasets(
            FASHION_TRAIN_IMAGES_ARCHIVE,
            FASHION_TRAIN_LABELS_ARCHIVE,
            FASHION_TEST_IMAGES_ARCHIVE,
            FASHION_TEST_LABELS_ARCHIVE,
            datasets.handlers.NUMBER_OF_CLASSES,
            ::extractFashionImages,
            ::extractFashionLabels
        )

        testModel.use {
            val layerList = mutableListOf<Layer>()

            for (layer in it.layers) {
                if (layer is Conv2D) {
                    layer.isTrainable = false
                    layerList.add(layer)
                }
            }

            it.compile(
                optimizer = Adam(),
                loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS,
                metric = Metrics.ACCURACY
            )

            it.loadWeightsForFrozenLayers(hdfFile)

            val accuracyBefore = it.evaluate(dataset = test, batchSize = 100).metrics[Metrics.ACCURACY]

            if (accuracyBefore != null) {
                assertTrue(accuracyBefore > 0.1) // Dense layers has no meaningful weights
            }

            val conv2DKernelWeighsBeforeTraining =
                it.getLayer("conv2d").getWeights()[0] as Array<Array<Array<FloatArray>>>
            assertEquals(conv2DKernelWeighsBeforeTraining[0][0][0][0], 0.06445057f)
            val denseDKernelWeightsBeforeTraining = it.getLayer("dense").getWeights()[0] as Array<FloatArray>
            assertEquals(denseDKernelWeightsBeforeTraining[0][0], 0.008463251f)

            it.fit(
                dataset = train,
                validationRate = 0.1,
                epochs = 4,
                trainBatchSize = 1000,
                validationBatchSize = 100,
                verbose = false
            )

            val conv2DKernelWeightsAfterTraining =
                it.getLayer("conv2d").getWeights()[0] as Array<Array<Array<FloatArray>>>
            assertEquals(conv2DKernelWeightsAfterTraining[0][0][0][0], 0.06445057f)
            assertArrayEquals(conv2DKernelWeighsBeforeTraining, conv2DKernelWeightsAfterTraining)

            val denseDKernelWeightsAfterTraining = it.getLayer("dense").getWeights()[0] as Array<FloatArray>
            assertFalse(denseDKernelWeightsBeforeTraining.contentEquals(denseDKernelWeightsAfterTraining))

            val accuracyAfterTraining = it.evaluate(dataset = test, batchSize = 100).metrics[Metrics.ACCURACY]

            if (accuracyAfterTraining != null && accuracyBefore != null) {
                assertTrue(accuracyAfterTraining > accuracyBefore)
            }
        }
    }
}