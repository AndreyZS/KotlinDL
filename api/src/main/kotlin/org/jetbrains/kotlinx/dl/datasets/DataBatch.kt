/*
 * Copyright 2020 JetBrains s.r.o. and Kotlin Deep Learning project contributors. All Rights Reserved.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.kotlinx.dl.datasets

import java.nio.FloatBuffer

/**
 * This class represents the batch of data in [Dataset].
 * @param [x] Data observations.
 * @param [y] Labels.
 * @param [size] Number of rows in batch.
 */
public data class DataBatch internal constructor(val x: FloatBuffer, val y: FloatBuffer, val size: Int) {
    /** */
    public fun shape(elementSize: Int): List<Long> = listOf(size.toLong(), elementSize.toLong())
}
