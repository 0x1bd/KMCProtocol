package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.descriptors.SerialDescriptor

inline fun <reified T : Annotation> SerialDescriptor.getElementAnnotationFromIndex(index: Int): T? {
    val elementAnnotations = getElementAnnotations(index)
    for (position in elementAnnotations.indices) {
        val annotation = elementAnnotations[position]
        if (annotation is T) return annotation
    }
    return null
}

inline fun <reified T : Annotation> SerialDescriptor.getAnnotation(): T? {
    for (position in annotations.indices) {
        val annotation = annotations[position]
        if (annotation is T) return annotation
    }
    return null
}
