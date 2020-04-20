/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.model.LmDependency
import java.util.ArrayDeque

/**
 * This class finds blacklisted dependencies in a project by looking
 * transitively
 */
class BlacklistedDeps(val project: Project) {

    private var map: MutableMap<String, List<LmDependency>>? = null

    init {
        // TODO: Should skip provided
        project.buildVariant?.mainArtifact?.dependencies?.compileDependencies?.let {
            visitLibraries(ArrayDeque(), it.roots)
        }
    }

    /**
     * Returns the path from this dependency to one of the blacklisted dependencies,
     * or null if this dependency is not blacklisted. If [remove] is true, the
     * dependency is removed from the map after this.
     */
    fun checkDependency(groupId: String, artifactId: String, remove: Boolean): List<LmDependency>? {
        val map = this.map ?: return null
        val coordinate = "$groupId:$artifactId"
        val path = map[coordinate] ?: return null
        if (remove) {
            map.remove(coordinate)
        }
        return path
    }

    /**
     * Returns all the dependencies found in this project that lead to a
     * blacklisted dependency. Each list is a list from the root dependency
     * to the blacklisted dependency.
     */
    fun getBlacklistedDependencies(): List<List<LmDependency>> {
        val map = this.map ?: return emptyList()
        return map.values.toMutableList().sortedBy { it[0].artifactName }
    }

    private fun visitLibraries(
        stack: ArrayDeque<LmDependency>,
        libraries: List<LmDependency>
    ) {
        for (library in libraries) {
            visitLibrary(stack, library)
        }
    }

    private fun visitLibrary(stack: ArrayDeque<LmDependency>, library: LmDependency) {
        stack.addLast(library)
        checkLibrary(stack, library)
        visitLibraries(stack, library.dependencies)
        stack.removeLast()
    }

    private fun checkLibrary(stack: ArrayDeque<LmDependency>, library: LmDependency) {
        if (isBlacklistedDependency(library.artifactName)) {
            if (map == null) {
                map = HashMap()
            }
            val root = stack.first.artifactName
            map?.put(root, ArrayList(stack))
        }
    }

    private fun isBlacklistedDependency(mavenName: String): Boolean {
        when (mavenName) {
            // org.apache.http.*
            "org.apache.httpcomponents:httpclient",

            // org.xmlpull.v1.*
            "xpp3:xpp3",

            // org.apache.commons.logging
            "commons-logging:commons-logging",

            // org.xml.sax.*, org.w3c.dom.*
            "xerces:xmlParserAPIs",

            // org.json.*
            "org.json:json",

            // javax.microedition.khronos.*
            "org.khronos:opengl-api",

            // all of the above
            "com.google.android:android" -> return true
            else -> return false
        }
    }
}
