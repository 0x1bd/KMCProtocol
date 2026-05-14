package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * Returns a single annotation of the requested type for a descriptor element.
 */
inline fun <reified T : Annotation> SerialDescriptor.getElementAnnotationFromIndex(index: Int): T? =
    getElementAnnotations(index)
        .filterIsInstance<T>()
        .singleOrNull()

/**
 * Returns a single annotation of the requested type for this descriptor.
 */
inline fun <reified T : Annotation> SerialDescriptor.getAnnotation(): T? =
    annotations
        .filterIsInstance<T>()
        .singleOrNull()
