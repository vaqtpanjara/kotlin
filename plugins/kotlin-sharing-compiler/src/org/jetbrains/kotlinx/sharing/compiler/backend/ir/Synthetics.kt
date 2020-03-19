/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.sharing.compiler.backend.ir

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.FieldDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType


/**
 * Simple property descriptor with backing field without getters/setters for ir generation purposes
 */
class SyntheticPropertyDescriptor(
    owner: ClassDescriptor,
    name: Name,
    type: KotlinType,
    isVar: Boolean = false,
    visibility: Visibility = Visibilities.PRIVATE
) : PropertyDescriptorImpl(
    owner,
    null,
    Annotations.EMPTY,
    Modality.FINAL,
    visibility,
    isVar,
    name,
    CallableMemberDescriptor.Kind.SYNTHESIZED,
    owner.source,
    false, false, false, false, false, false
) {

    private val _backingField = FieldDescriptorImpl(Annotations.EMPTY, this)

    init {
        super.setType(type, emptyList(), owner.thisAsReceiverParameter, null)
        super.initialize(null, null, _backingField, null)
    }
}