/*
 * Copyright 2020 JetBrains s.r.o. and Kotlin Deep Learning project contributors. All Rights Reserved.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package api.core.metric

import api.core.util.getDType
import org.tensorflow.Operand
import org.tensorflow.op.Ops

/**
 * Metrics.
 */
public enum class Metrics {
    /**
     * Computes the rate of true answers.
     *
     * `metric = sum(y_true == y_pred)`
     */
    ACCURACY,

    /**
     * Computes the mean of absolute difference between labels and predictions.
     *
     * `metric = abs(y_true - y_pred)`
     */
    MAE,

    /**
     * Computes the mean of squares of errors between labels and predictions.
     *
     * `metric = square(y_true - y_pred)`
     */
    MSE,

    /**
     * Computes the mean squared logarithmic error between `y_true` and `y_pred`.
     *
     * `loss = square(log(y_true + 1.) - log(y_pred + 1.))`
     */
    MLSE,

    /**
     * Computes the root of mean of squares of errors between labels and predictions.
     *
     * `metric = root(square(y_true - y_pred))`
     */
    RMSE;

    public companion object {
        /** Converts enum value to sub-class of [Metric]. */
        public fun convert(metricType: Metrics): Metric {
            return when (metricType) {
                ACCURACY -> Accuracy()
                MAE -> MAE()
                MSE -> MSE()
                RMSE -> RMSE()
                MLSE -> MLSE()
            }
        }

        /** Converts sub-class of [Metric] to enum value. */
        public fun convertBack(metric: Metric): Metrics {
            return when (metric::class) {
                Accuracy::class -> ACCURACY
                api.core.metric.MAE::class -> MAE
                api.core.metric.MSE::class -> MSE
                api.core.metric.RMSE::class -> RMSE
                else -> ACCURACY
            }
        }
    }
}

/**
 * @see [Metrics.ACCURACY]
 */
public class Accuracy : Metric {
    override fun apply(tf: Ops, yPred: Operand<Float>, yTrue: Operand<Float>): Operand<Float> {
        val predicted: Operand<Long> = tf.math.argMax(yPred, tf.constant(1))
        val expected: Operand<Long> = tf.math.argMax(yTrue, tf.constant(1))

        return tf.math.mean(tf.dtypes.cast(tf.math.equal(predicted, expected), getDType()), tf.constant(0))
    }
}

/**
 * @see [Metrics.MAE]
 */
public class MAE : Metric {
    override fun apply(tf: Ops, yPred: Operand<Float>, yTrue: Operand<Float>): Operand<Float> {
        val absoluteErrors = tf.math.abs(tf.math.sub(yPred, yTrue))
        return tf.reduceSum(tf.math.mean(absoluteErrors, tf.constant(-1)), tf.constant(0))
    }
}

/**
 * @see [Metrics.MSE]
 */
public class MSE : Metric {
    override fun apply(tf: Ops, yPred: Operand<Float>, yTrue: Operand<Float>): Operand<Float> {
        val squaredError = tf.math.squaredDifference(yPred, yTrue)
        return tf.reduceSum(tf.math.mean(squaredError, tf.constant(-1)), tf.constant(0))
    }
}

/**
 * @see [Metrics.RMSE]
 */
public class RMSE : Metric {
    override fun apply(tf: Ops, yPred: Operand<Float>, yTrue: Operand<Float>): Operand<Float> {
        val rootSquaredError = tf.math.sqrt(tf.math.squaredDifference(yPred, yTrue))
        return tf.reduceSum(tf.math.mean(rootSquaredError, tf.constant(-1)), tf.constant(0))
    }
}

/**
 * @see [Metrics.MLSE]
 */
public class MLSE : Metric {
    override fun apply(tf: Ops, yPred: Operand<Float>, yTrue: Operand<Float>): Operand<Float> {
        val epsilon = 1e-5f

        val firstLog = tf.math.log(tf.math.add(tf.math.maximum(yPred, tf.constant(epsilon)), tf.constant(1.0f)))
        val secondLog = tf.math.log(tf.math.add(tf.math.maximum(yTrue, tf.constant(epsilon)), tf.constant(1.0f)))

        return tf.reduceSum(
            tf.math.mean(tf.math.squaredDifference(firstLog, secondLog), tf.constant(-1)), tf.constant(0)
        )
    }
}

