package de.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.descriptors.SerialDescriptor

inline fun <reified T : Annotation> SerialDescriptor.getElementAnnotationFromIndex(index: Int): T? =
    getElementAnnotations(index)
        .filterIsInstance<T>()
        .singleOrNull()

inline fun <reified T : Annotation> SerialDescriptor.getAnnotation(): T? =
    annotations
        .filterIsInstance<T>()
        .singleOrNull()