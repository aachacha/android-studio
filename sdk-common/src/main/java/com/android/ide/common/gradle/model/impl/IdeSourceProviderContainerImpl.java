/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model.impl;

import com.android.annotations.NonNull;
import com.android.ide.common.gradle.model.IdeSourceProvider;
import com.android.ide.common.gradle.model.IdeSourceProviderContainer;
import java.io.Serializable;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of a `SourceProviderContainer`. */
public final class IdeSourceProviderContainerImpl
        implements IdeSourceProviderContainer, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myArtifactName;
    @NonNull private final IdeSourceProvider mySourceProvider;
    private final int myHashCode;

    // Used for serialization by the IDE.
    IdeSourceProviderContainerImpl() {
        myArtifactName = "";
        mySourceProvider = new IdeSourceProviderImpl();

        myHashCode = 0;
    }

    public IdeSourceProviderContainerImpl(
            @NotNull String artifactName, @NotNull IdeSourceProviderImpl sourceProvider) {
        myArtifactName = artifactName;
        mySourceProvider = sourceProvider;

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getArtifactName() {
        return myArtifactName;
    }

    @Override
    @NonNull
    public IdeSourceProvider getSourceProvider() {
        return mySourceProvider;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeSourceProviderContainerImpl)) {
            return false;
        }
        IdeSourceProviderContainerImpl container = (IdeSourceProviderContainerImpl) o;
        return Objects.equals(myArtifactName, container.myArtifactName)
                && Objects.equals(mySourceProvider, container.mySourceProvider);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myArtifactName, mySourceProvider);
    }

    @Override
    public String toString() {
        return "IdeSourceProviderContainer{"
                + "myArtifactName='"
                + myArtifactName
                + '\''
                + ", mySourceProvider="
                + mySourceProvider
                + '}';
    }
}
