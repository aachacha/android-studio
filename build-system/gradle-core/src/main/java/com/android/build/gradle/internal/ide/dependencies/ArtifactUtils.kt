/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("ArtifactUtils")

package com.android.build.gradle.internal.ide.dependencies

import com.android.build.api.attributes.AgpVersionAttr
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.ide.DependencyFailureHandler
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputs.RuntimeType
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Sets
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.capabilities.Capability
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import java.io.File

/**
 * This holder class exists to allow lint to depend on the artifact collections.
 */
interface ArtifactCollectionsInputs {
    enum class RuntimeType { FULL, PARTIAL }

    @get:Input
    val projectPath: String
    @get:Internal
    val projectBuildTreePath: Provider<String>
    @get:Input
    val variantName: String
    @get:Nested
    val compileClasspath: ArtifactCollections
    @get:Nested
    @get:Optional
    val runtimeClasspath: ArtifactCollections?

    @get:Internal
    val runtimeLintJars: ArtifactCollection
    @get:Internal
    val compileLintJars: ArtifactCollection

    @get:Nested
    val level1RuntimeArtifactCollections: Level1RuntimeArtifactCollections

    fun getAllArtifacts(
        consumedConfigType: AndroidArtifacts.ConsumedConfigType,
        dependencyFailureHandler: DependencyFailureHandler? = null
    ): Set<ResolvedArtifact>
}

/**
 * This holder class exists to allow lint to depend on the artifact collections.
 *
 * It is used as a [org.gradle.api.tasks.Nested] input to the lint model generation task.
 */
class ArtifactCollectionsInputsImpl constructor(
    variantDependencies: VariantDependencies,
    override val projectPath: String,
    override val variantName: String,
    @get:Input val runtimeType: RuntimeType,
): ArtifactCollectionsInputs {

    constructor(
        componentImpl: ComponentCreationConfig,
        runtimeType: RuntimeType,
    ) : this(
        componentImpl.variantDependencies,
        componentImpl.services.projectInfo.path,
        componentImpl.name,
        runtimeType,
    )

    override val projectBuildTreePath: Provider<String> = getBuildTreePath(variantDependencies)

    override val compileClasspath: ArtifactCollections = ArtifactCollections(
        variantDependencies,
        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
    )

    /** The full runtime graphs for more complex dependency models */
    override val runtimeClasspath: ArtifactCollections? = if (runtimeType == RuntimeType.FULL) {
        ArtifactCollections(
            variantDependencies,
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
        )
    } else null

    /** The partial graphs for level 1 dependencies */
    override val level1RuntimeArtifactCollections: Level1RuntimeArtifactCollections = Level1RuntimeArtifactCollections(variantDependencies)

    // This contains the list of all the lint jar provided by the runtime dependencies.
    // We'll match this to the component identifier of each artifact to find the lint.jar
    // that is coming via AARs.
    override val runtimeLintJars: ArtifactCollection =
        variantDependencies.getArtifactCollectionForToolingModel(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL,
            AndroidArtifacts.ArtifactType.LINT
        )

    @get:Classpath
    val runtimeLintJarsFileCollection: FileCollection
        get() = runtimeLintJars.artifactFiles

    // Similar to runtimeLintJars, but for compile dependencies; there will be overlap between the
    // two in most cases, but we need compileLintJars to support compileOnly dependencies.
    override val compileLintJars: ArtifactCollection =
        variantDependencies.getArtifactCollectionForToolingModel(
            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL,
            AndroidArtifacts.ArtifactType.LINT
        )

    @get:Classpath
    val compileLintJarsFileCollection: FileCollection
        get() = compileLintJars.artifactFiles

    override fun getAllArtifacts(
        consumedConfigType: AndroidArtifacts.ConsumedConfigType,
        dependencyFailureHandler: DependencyFailureHandler?
    ): Set<ResolvedArtifact> {
        val collections = if (consumedConfigType == AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH) {
            compileClasspath
        } else {
            runtimeClasspath!!
        }
        return getAllArtifacts(
            collections,
            dependencyFailureHandler,
            projectPath,
            variantName
        )
    }
}

// This is the partial set of file collections used by the Level1 model builder
class Level1RuntimeArtifactCollections(variantDependencies: VariantDependencies) {
    @get:Internal
    val runtimeArtifacts: ArtifactCollection =
        variantDependencies.getArtifactCollectionForToolingModel(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL, AndroidArtifacts.ArtifactType.AAR_OR_JAR
        )

    @get:Classpath
    val runtimeArtifactsFileCollection: FileCollection
        get() = runtimeArtifacts.artifactFiles

    /** See [ArtifactCollections.projectJars]. */
    @get:Internal
    val runtimeProjectJars = variantDependencies.getArtifactCollectionForToolingModel(
        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
        AndroidArtifacts.ArtifactScope.PROJECT, AndroidArtifacts.ArtifactType.JAR
    )

    @get:Internal
    val runtimeExternalJars = variantDependencies.getArtifactCollectionForToolingModel(
        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
        AndroidArtifacts.ArtifactScope.EXTERNAL, AndroidArtifacts.ArtifactType.PROCESSED_JAR
    )

    @get:Classpath
    val runtimeExternalJarsFileCollection: FileCollection
        get() = runtimeExternalJars.artifactFiles
}

class ArtifactCollections(
    variantDependencies: VariantDependencies,
    @get:Internal
    val consumedConfigType: AndroidArtifacts.ConsumedConfigType
) {
    constructor(
        componentImpl: ComponentCreationConfig,
        consumedConfigType: AndroidArtifacts.ConsumedConfigType
    ) : this(
        componentImpl.variantDependencies, consumedConfigType
    )

    /**
     * A collection containing 'all' artifacts, i.e. jar and AARs from subprojects, repositories
     * and files.
     *
     * This will give the following mapping:
     * * Java library project → Untransformed jar output
     * * Android library project → *jar* output, aar is not published between projects.
     *   This could be a separate type in the future if it was desired not to publish the jar from
     *   android-library projects.
     * * Remote jar → Untransformed jar
     * * Remote aar → Untransformed aar
     * * Local jar → untransformed jar
     * * Local aar → untransformed aar
     * * Jar wrapped as a project → untransformed aar
     * * aar wrapped as a project → untransformed aar
     *
     * Using an artifact view as that contains local dependencies, unlike
     * `configuration.incoming.resolutionResult` which only contains project and \[repository\]
     * module dependencies.
     *
     * This captures dependencies without transforming them using `AttributeCompatibilityRule`s.
     **/
    @get:Internal
    val all: ArtifactCollection = variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.AAR_OR_JAR
    )

    @get:Classpath
    val allFileCollection: FileCollection
        get() = all.artifactFiles

    @get:Internal
    val lintJar: ArtifactCollection = variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.LINT
    )

    // We still need to understand wrapped jars and aars. The former is difficult (TBD), but
    // the latter can be done by querying for EXPLODED_AAR. If a sub-project is in this list,
    // then we need to override the type to be external, rather than sub-project.
    // This is why we query for Scope.ALL
    // But we also simply need the exploded AARs for external Android dependencies so that
    // Studio can access the content.
    @get:Internal
    val explodedAars: ArtifactCollection = variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.EXPLODED_AAR
    )

    @get:Internal
    var asarJarsCollection: ArtifactCollection = variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_INTERFACE_DESCRIPTOR
    )

    @get:Classpath
    val explodedAarFileCollection: FileCollection
        get() = explodedAars.artifactFiles

    /**
     * For project jars, query for JAR instead of PROCESSED_JAR for two reasons:
     *  - Performance: Project jars are currently considered already processed (unlike external
     *    jars).
     *  - Workaround for a Gradle issue: Gradle may throw FileNotFoundException if a project jar has
     *    not been built yet; this issue does not affect external jars (see bug 110054209).
     */
    @get:Internal
    val projectJars: ArtifactCollection = variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.PROJECT,
        AndroidArtifacts.ArtifactType.JAR
    )

    @get:Classpath
    val projectJarsFileCollection: FileCollection
        get() = projectJars.artifactFiles
}

/**
 * Returns a set of ResolvedArtifact where the [ResolvedArtifact.dependencyType] and
 * [ResolvedArtifact.isWrappedModule] fields have been setup properly.
 *
 * @param componentImpl the variant to get the artifacts from
 * @param consumedConfigType the type of the dependency to resolve (compile vs runtime)
 * @param dependencyFailureHandler handler for dependency resolution errors
 */
fun getAllArtifacts(
    componentImpl: ComponentCreationConfig,
    consumedConfigType: AndroidArtifacts.ConsumedConfigType,
    dependencyFailureHandler: DependencyFailureHandler?,
): Set<ResolvedArtifact> {
    val collections = ArtifactCollections(componentImpl, consumedConfigType)
    return getAllArtifacts(
        collections,
        dependencyFailureHandler,
        componentImpl.services.projectInfo.path,
        componentImpl.name,
    )
}

private fun getAllArtifacts(
    collections: ArtifactCollections,
    dependencyFailureHandler: DependencyFailureHandler?,
    projectPath: String,
    variantName: String,
): Set<ResolvedArtifact> {

    // we need to figure out the following:
    // - Is it an external dependency or a sub-project?
    // - Is it an android or a java dependency

    // All artifacts: see comment on collections.all
    val incomingArtifacts = collections.all

    val explodedAars = collections.explodedAars.asMap { it.file }

    val lintJars = collections.lintJar.asMap { it.file }

    val asarJars = collections.asarJarsCollection.asMap { it.file }

    /** See [ArtifactCollections.projectJars]. */
    val projectJarsMap: ImmutableMultimap<VariantKey, ResolvedArtifactResult> by lazy(LazyThreadSafetyMode.NONE) {
        collections.all.artifacts.filter {
            it.variant.owner is ProjectComponentIdentifier && it.isJar()
        }.asMultiMap()
    }

    // collect dependency resolution failures
    if (dependencyFailureHandler != null) {
        val failures = incomingArtifacts.failures
        // compute the name of the configuration
        dependencyFailureHandler.addErrors(
            projectPath
                    + "@"
                    + variantName
                    + "/"
                    + collections.consumedConfigType.getName(),
            failures
        )
    }

    // build a list of wrapped AAR, and a map of all the exploded-aar artifacts
    val aarWrappedAsProjects =
        explodedAars.keys.filter { it.owner is ProjectComponentIdentifier }

    // build the final list, using the main list augmented with data from the previous lists.
    val resolvedArtifactResults = incomingArtifacts.artifacts

    // use a linked hash set to keep the artifact order.
    val artifacts =
        Sets.newLinkedHashSetWithExpectedSize<ResolvedArtifact>(resolvedArtifactResults.size)

    for (resolvedComponentResult in resolvedArtifactResults) {
        val variantKey = resolvedComponentResult.variant.toKey()

        fun addArtifact(
                dependencyType: ResolvedArtifact.DependencyType,
                mainArtifact: ResolvedArtifactResult,
                publishedLintJar: File? = null,
                artifactFile: File? = mainArtifact.file) {
            artifacts.add(
                ResolvedArtifact(
                        mainArtifact,
                        artifactFile,
                        explodedAars[variantKey] ?: asarJars[variantKey],
                        publishedLintJar,
                        dependencyType,
                        // check if this is a wrapped module
                        aarWrappedAsProjects.contains(variantKey),
                )
            )
        }

        val artifactType =
            resolvedComponentResult.variant.attributes.getAttribute(AndroidArtifacts.ARTIFACT_TYPE)
        when (artifactType) {
            AndroidArtifacts.ArtifactType.AAR.type -> {
                // This only happens for external dependencies - local android libraries do not
                // publish the AAR between projects.
                // In this case, we want to use the exploded AAR as the artifact we depend on rather
                // than just the JAR
                addArtifact(
                    dependencyType = ResolvedArtifact.DependencyType.ANDROID,
                    mainArtifact = resolvedComponentResult,
                    publishedLintJar = lintJars[variantKey]
                )

            }
            AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE.type -> {
                // When the dependency is ASAR, the resolved artifact needs to be the jar inside it
                // extractedAar will be pointing to that location of that jar
                addArtifact(
                    dependencyType = ResolvedArtifact.DependencyType.ANDROID_SANDBOX_SDK,
                    mainArtifact = resolvedComponentResult
                )
            }
            AndroidArtifacts.ArtifactType.JAR.type ->
                if (resolvedComponentResult.isAndroidProjectLibrary()) {
                    // When a project dependency is an Android one, the artifact file doesn't matter
                    // We can safely set it to null. The variant attributes will be used to compute
                    // the key to the library model objects, so they are passed further down.
                    addArtifact(
                        dependencyType = ResolvedArtifact.DependencyType.ANDROID,
                        mainArtifact = resolvedComponentResult,
                        publishedLintJar = lintJars[variantKey],
                        artifactFile = null
                    )
                } else {
                    // When a local android project publishes a simple jar, we want to explicitly
                    // not handle it, because:
                    // IDE doesn't handle explicit JARs coming from local Android projects
                    // AND the project dependency will be handled when handling the original JAR
                    //
                    // The check here iterates over all the jars this particular project publishes
                    // and skips this dependency completely if any of them is android.
                    if (projectJarsMap[variantKey].any {it.isAndroidProjectLibrary()}) {
                        continue
                    }
                    val projectJars = projectJarsMap[variantKey]
                    // If there are no project jars default to the resolved artifact
                    projectJars.ifEmpty {
                        // Note use this component directly to handle classified artifacts
                        // This is tested by AppWithClassifierDepTest.
                        listOf<ResolvedArtifactResult>(resolvedComponentResult)
                    }.forEach {
                        addArtifact(
                                dependencyType = ResolvedArtifact.DependencyType.JAVA,
                                mainArtifact = it
                        )
                    }
                }
            else -> throw IllegalStateException("Internal error: Artifact type $artifactType not expected, only jar or aar are handled.")
        }
    }

    return artifacts
}

private fun ResolvedArtifactResult.isJar() =
        variant.attributes.getAttribute(AndroidArtifacts.ARTIFACT_TYPE) == AndroidArtifacts.ArtifactType.JAR.type

/**
 * Checks whether a local project library is and Android one.
 *
 * [AgpVersionAttr] is only present for android libraries.
 */
private fun ResolvedArtifactResult.isAndroidProjectLibrary() =
    variant.attributes.getAttribute(AgpVersionAttr.ATTRIBUTE) != null


/**
 * This is a multi map to handle when there are multiple jars with the same component id.
 *
 * e.g. see `AppWithClassifierDepTest`
 */
private fun Iterable<ResolvedArtifactResult>.asMultiMap(): ImmutableMultimap<VariantKey, ResolvedArtifactResult> {
    return ImmutableMultimap.builder<VariantKey, ResolvedArtifactResult>()
        .also { builder ->
            for (artifact in this) {
                builder.put(artifact.variant.toKey(), artifact)
            }
        }.build()
}

private fun <T> ArtifactCollection.asMap(action: (ResolvedArtifactResult) -> T): Map<VariantKey, T> =
    artifacts.associate { it.variant.toKey() to action(it) }

/**
 * A custom key for [ResolvedVariantResult].
 *
 * This is used when we want to compare artifacts to see if they are coming from the same
 * dependency as we cannot use [ComponentIdentifier] (it does not handle multi-variant cases
 * like test fixtures), and we cannot use [ResolvedVariantResult] directly because one of its
 * attributes is artifactType which is tied to the query that returned the artifact.
 *
 * This key only takes into account the [ComponentIdentifier], the list of [Capability] and
 * [ResolvedVariantResult.getExternalVariant]
 */
data class VariantKey(
    val owner: ComponentIdentifier,
    val capabilities: List<Capability>,
    val externalVariant: VariantKey?
)

@Suppress("UnstableApiUsage")
fun ResolvedVariantResult.toKey(): VariantKey = VariantKey(
    owner,
    capabilities,
    externalVariant.orElse(null)?.toKey()
)
