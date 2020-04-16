package examples.keras

import org.tensorflow.Graph
import org.tensorflow.op.Ops
import tensorflow.training.util.ImageDataset
import tf_api.keras.Metric
import tf_api.keras.Sequential
import tf_api.keras.activations.Activations
import tf_api.keras.initializers.Initializers
import tf_api.keras.layers.Dense
import tf_api.keras.layers.Source
import tf_api.keras.loss.LossFunctions
import tf_api.keras.optimizers.Optimizers

private const val VALIDATION_SIZE = 0

private val model = Sequential.of<Float>(
    Source(784),
    Dense(784, 128, Activations.Sigmoid, Initializers.TRUNCATED_NORMAL, Initializers.ZEROS),
    Dense(128, 10, Activations.Softmax, Initializers.TRUNCATED_NORMAL, Initializers.ZEROS)
)

fun main() {
    val dataset = ImageDataset.create(VALIDATION_SIZE)
    val (train, test) = dataset.split(0.75)

    Graph().use { graph ->
        val tf = Ops.create(graph)

        model.compile(tf, optimizer = Optimizers.SGD, loss = LossFunctions.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS)

        model.fit(graph, tf, trainDataset = train, epochs = 1, batchSize = 1000)

        val accuracy = model.evaluate(testDataset = test, metric = Metric.ACCURACY)

        println("Accuracy: $accuracy")
    }
}
