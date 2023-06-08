/*
 * Copyright (c) 2022-2023. Inspector Axon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.axoniq.inspector

import org.axonframework.common.ReflectionUtils
import java.lang.reflect.Field
import kotlin.reflect.KClass

/**
 * Find fields matching its own type. If found, it will unwrap the deeper value.
 * Useful for when users wrap components as decorators, like Axon FireStarter does.
 */
fun <T : Any> T.unwrapPossiblyDecoratedClass(clazz: Class<out T>): T {
    return fieldOfMatchingType(clazz)
        ?.let { ReflectionUtils.getFieldValue(it, this) as T }
        ?.unwrapPossiblyDecoratedClass(clazz)
    // No field of provided type - reached end of decorator chain
        ?: this
}

private fun <T : Any> T.fieldOfMatchingType(clazz: Class<out T>): Field? {
    // When we reach our own AS-classes, stop unwrapping
    if (this::class.java.name.startsWith("org.axonframework") && this::class.java.simpleName.startsWith("AxonServer")) return null
    return ReflectionUtils.fieldsOf(this::class.java)
        .firstOrNull { f -> f.type.isAssignableFrom(clazz) }
}

fun <K, V> MutableMap<K, V>.computeIfAbsentWithRetry(key: K, retries: Int = 0, defaultValue: (K) -> V): V {
    try {
        return computeIfAbsent(key, defaultValue)
    } catch (e: ConcurrentModificationException) {
        if(retries < 3) {
            return computeIfAbsentWithRetry(key, retries + 1, defaultValue)
        }
        // We cannot get it from the map. Return the default value without putting it in, so the code can continue.
        return defaultValue(key)
    }
}
