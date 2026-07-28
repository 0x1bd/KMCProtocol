package org.kvxd.kmcprotocol.build

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

internal fun protocolArrayAnnotationOf(
    protocolArray: ClassName,
    numberEncoding: ClassName,
    info: ArrayInfo,
): AnnotationSpec {
    val countTypes = CodeBlock.builder().add("[")
    info.levels.forEachIndexed { index, level ->
        if (index > 0) countTypes.add(",·")
        countTypes.add("%T.%L", numberEncoding, level.countType)
    }
    countTypes.add("]")

    return AnnotationSpec.builder(protocolArray)
        .addMember("countTypes·=·%L", countTypes.build())
        .addMember("fixedCounts·=·[%L]", info.levels.joinToString(",·") { it.fixedCount.toString() })
        .addMember("countFields·=·[%L]", info.levels.joinToString(",·") { "\"${it.countField}\"" })
        .addMember("elementEncoding·=·%T.%L", numberEncoding, info.elementEncoding)
        .build()
}

internal fun protocolBinaryAnnotationOf(
    protocolBinary: ClassName,
    binaryKind: ClassName,
    numberEncoding: ClassName,
    info: BinaryInfo,
): AnnotationSpec =
    AnnotationSpec.builder(protocolBinary)
        .addMember("kind·=·%T.%L", binaryKind, info.kind)
        .apply {
            if (info.kind == "Buffer") {
                addMember("countType·=·%T.%L", numberEncoding, info.countType)
                if (info.fixedCount >= 0) addMember("fixedCount·=·%L", info.fixedCount)
            }
        }
        .build()

internal fun protocolSwitchAnnotationOf(protocolSwitch: ClassName, info: SwitchInfo): AnnotationSpec =
    AnnotationSpec.builder(protocolSwitch)
        .addMember("compareTo·=·%S", info.compareTo)
        .addMember("presentKeys·=·[%L]", info.presentKeys.joinToString(",·") { "\"${it}\"" })
        .addMember("voidKeys·=·[%L]", info.voidKeys.joinToString(",·") { "\"${it}\"" })
        .addMember("defaultPresent·=·%L", info.defaultPresent)
        .build()
