/*
 * Copyright 2020 JetBrains s.r.o. and Kotlin Deep Learning project contributors. All Rights Reserved.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package api.core.metric

/**
 * Represents result of evaluation on test dataset.
 *
 * @property lossValue Value of loss function on test dataset.
 * @property metrics Values of calculated metrics.
 */
public data class EvaluationResult(val lossValue: Double, val metrics: Map<Metrics, Double>)

