/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators


import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSetting
import org.jetbrains.kotlin.tools.projectWizard.core.safeAs
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.KotlinBuildSystemPluginIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.maven.MavenPropertyIR
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.isGradle
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModulesToIrConversionData
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.templates.TemplatesPlugin
import org.jetbrains.kotlin.tools.projectWizard.settings.DisplayableSettingItem
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.ModuleKind
import org.jetbrains.kotlin.tools.projectWizard.settings.javaPackage
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplate
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplateDescriptor
import java.nio.file.Path

interface JvmModuleConfigurator : ModuleConfiguratorWithTests {
    companion object : ModuleConfiguratorSettings() {
        val targetJvmVersion by enumSetting<TargetJvmVersion>(
            KotlinNewProjectWizardBundle.message("module.configurator.jvm.setting.target.jvm.version"),
            GenerationPhase.PROJECT_GENERATION
        ) {
            defaultValue = value(TargetJvmVersion.JVM_1_8)
        }
    }

    override fun getConfiguratorSettings(): List<ModuleConfiguratorSetting<*, *>> = buildList {
        +super.getConfiguratorSettings()
        +targetJvmVersion
    }
}

enum class TargetJvmVersion(@NonNls val value: String) : DisplayableSettingItem {
    JVM_1_6("1.6"),
    JVM_1_8("1.8"),
    JVM_9("9"),
    JVM_10("10"),
    JVM_11("11"),
    JVM_12("12"),
    JVM_13("13");

    override val text: String
        get() = value
}


interface ModuleConfiguratorWithModuleType : ModuleConfigurator {
    val moduleType: ModuleType
}

val ModuleConfigurator.moduleType: ModuleType?
    get() = safeAs<ModuleConfiguratorWithModuleType>()?.moduleType

object MppModuleConfigurator : ModuleConfigurator {
    override val moduleKind = ModuleKind.multiplatform

    @NonNls
    override val suggestedModuleName = "shared"

    @NonNls
    override val id = "multiplatform"
    override val text = KotlinNewProjectWizardBundle.message("module.configurator.mpp")
    override val canContainSubModules = true

    override fun createKotlinPluginIR(configurationData: ModulesToIrConversionData, module: Module): KotlinBuildSystemPluginIR? =
        KotlinBuildSystemPluginIR(
            KotlinBuildSystemPluginIR.Type.multiplatform,
            version = configurationData.kotlinVersion
        )

    override fun Writer.runArbitraryTask(
        configurationData: ModulesToIrConversionData,
        module: Module,
        modulePath: Path
    ): TaskResult<Unit> = computeM {
        val javaPackage = module.javaPackage(configurationData.pomIr)
        val settings = mapOf("package" to javaPackage.asCodePackage())

        val androidKt = FileTemplateDescriptor(
            "mobileMpp/android.kt.vm",
            "src" / "androidMain" / "kotlin" / "android.kt"
        )

        val androidTestKt = FileTemplateDescriptor(
            "mobileMpp/androidTest.kt.vm",
            "src" / "androidTest" / "kotlin" / "androidTest.kt"
        )

        val iosKt = FileTemplateDescriptor(
            "mobileMpp/ios.kt.vm",
            "src" / "iosMain" / "kotlin" / "ios.kt"
        )

        val iosTestKt = FileTemplateDescriptor(
            "mobileMpp/iosTest.kt.vm",
            "src" / "iosTest" / "kotlin" / "iosTest.kt"
        )

        val commonKt = FileTemplateDescriptor(
            "mobileMpp/common.kt.vm",
            "src" / "commonMain" / "kotlin" / "common.kt"
        )

        TemplatesPlugin::addFileTemplates.execute(
            listOf(
                FileTemplate(androidKt, modulePath, settings),
                FileTemplate(androidTestKt, modulePath, settings),
                FileTemplate(commonKt, modulePath, settings),
                FileTemplate(iosKt, modulePath, settings),
                FileTemplate(iosTestKt, modulePath, settings)
            )
        )
    }
}


interface SinglePlatformModuleConfigurator : ModuleConfigurator {
    val needCreateBuildFile: Boolean get() = true
}

object JvmSinglePlatformModuleConfigurator : JvmModuleConfigurator,
    SinglePlatformModuleConfigurator,
    ModuleConfiguratorWithModuleType {
    override val moduleType get() = ModuleType.jvm
    override val moduleKind: ModuleKind get() = ModuleKind.singleplatformJvm
    @NonNls override val suggestedModuleName = "jvm"
    @NonNls override val id = "JVM Module"
    override val text = KotlinNewProjectWizardBundle.message("module.configurator.jvm")


    override fun defaultTestFramework(): KotlinTestFramework = KotlinTestFramework.JUNIT4

    override val canContainSubModules = true

    override fun createKotlinPluginIR(configurationData: ModulesToIrConversionData, module: Module): KotlinBuildSystemPluginIR? =
        KotlinBuildSystemPluginIR(
            KotlinBuildSystemPluginIR.Type.jvm,
            version = configurationData.kotlinVersion
        )


    override fun createBuildFileIRs(
        reader: Reader,
        configurationData: ModulesToIrConversionData,
        module: Module
    ): List<BuildSystemIR> =
        buildList {
            +GradleImportIR("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")

            val targetVersionValue = withSettingsOf(module) {
                reader {
                    JvmModuleConfigurator.targetJvmVersion.reference.settingValue.value
                }
            }
            when {
                configurationData.buildSystemType.isGradle -> {
                    +GradleConfigureTaskIR(
                        GradleByClassTasksAccessIR("KotlinCompile"),
                        irs = listOf(
                            GradleAssignmentIR("kotlinOptions.jvmTarget", GradleStringConstIR(targetVersionValue))
                        )
                    )
                }
                configurationData.buildSystemType == BuildSystemType.Maven -> {
                    +MavenPropertyIR("kotlin.compiler.jvmTarget", targetVersionValue)
                }
            }
        }
}


val ModuleType.defaultTarget
    get() = when (this) {
        ModuleType.jvm -> JvmTargetConfigurator
        ModuleType.js -> JsBrowserTargetConfigurator
        ModuleType.native -> NativeForCurrentSystemTarget
        ModuleType.common -> CommonTargetConfigurator
        ModuleType.android -> AndroidTargetConfigurator
    }