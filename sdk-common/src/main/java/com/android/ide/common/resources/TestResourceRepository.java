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
package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/** Simple repository implementation that just stores what the {@link ResourceMerger} emits. */
public final class TestResourceRepository extends AbstractResourceRepository
        implements SingleNamespaceResourceRepository {
    private final ResourceNamespace namespace;
    private final ResourceTable resourceTable = new ResourceTable();

    public TestResourceRepository(@NonNull ResourceNamespace namespace) {
        this.namespace = namespace;
    }

    @Override
    @NonNull
    public ResourceTable getFullTable() {
        return resourceTable;
    }

    @Override
    @Nullable
    protected ListMultimap<String, ResourceItem> getMap(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType type, boolean create) {
        ListMultimap<String, ResourceItem> multimap = resourceTable.get(namespace, type);
        if (multimap == null && create) {
            multimap = ArrayListMultimap.create();
            resourceTable.put(namespace, type, multimap);
        }
        return multimap;
    }

    @Override
    @NonNull
    public ResourceNamespace getNamespace() {
        return namespace;
    }

    @Override
    @Nullable
    public String getPackageName() {
        return namespace.getPackageName();
    }

    public void update(@NonNull ResourceMerger merger) {
        ResourceRepositories.updateTableFromMerger(merger, resourceTable);
    }
}
