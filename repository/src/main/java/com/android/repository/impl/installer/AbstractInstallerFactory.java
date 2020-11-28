/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.repository.impl.installer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.Uninstaller;

/**
 * Framework for an actually {@link InstallerFactory}, with support for adding the listeners
 * generated by the {@link InstallerFactory.StatusChangeListenerFactory} to the generated installer
 * or uninstaller.
 */
public abstract class AbstractInstallerFactory implements InstallerFactory {

    private StatusChangeListenerFactory mListenerFactory;
    protected InstallerFactory mFallbackFactory;

    @Override
    public void setFallbackFactory(@Nullable InstallerFactory fallback) {
        mFallbackFactory = fallback;
        if (mFallbackFactory != null && mListenerFactory != null) {
            mFallbackFactory.setListenerFactory(mListenerFactory);
        }
    }

    @Override
    public void setListenerFactory(
            @NonNull StatusChangeListenerFactory listenerFactory) {
        mListenerFactory = listenerFactory;
        if (mFallbackFactory != null) {
            mFallbackFactory.setListenerFactory(listenerFactory);
        }
    }

    @NonNull
    @Override
    public final Installer createInstaller(
            @NonNull RemotePackage remote,
            @NonNull RepoManager mgr,
            @NonNull Downloader downloader) {
        if (!canHandlePackage(remote, mgr) && mFallbackFactory != null) {
            return mFallbackFactory.createInstaller(remote, mgr, downloader);
        }
        Installer installer = doCreateInstaller(remote, mgr, downloader);
        if (mFallbackFactory != null) {
            installer.setFallbackOperation(
                    mFallbackFactory.createInstaller(remote, mgr, downloader));
        }
        registerListeners(installer);
        return installer;
    }

    private void registerListeners(@NonNull PackageOperation op) {
        if (mListenerFactory != null) {
            for (PackageOperation.StatusChangeListener listener :
                    mListenerFactory.createListeners(op.getPackage())) {
                op.registerStateChangeListener(listener);
            }
        }
    }

    /** Subclasses should override this to do the actual creation of an {@link Installer}. */
    @NonNull
    protected abstract Installer doCreateInstaller(
            @NonNull RemotePackage p, @NonNull RepoManager mgr, @NonNull Downloader downloader);

    @NonNull
    @Override
    public final Uninstaller createUninstaller(
            @NonNull LocalPackage local, @NonNull RepoManager mgr) {
        if (!canHandlePackage(local, mgr) && mFallbackFactory != null) {
            return mFallbackFactory.createUninstaller(local, mgr);
        }
        Uninstaller uninstaller = doCreateUninstaller(local, mgr);
        if (mFallbackFactory != null) {
            uninstaller.setFallbackOperation(mFallbackFactory.createUninstaller(local, mgr));
        }
        registerListeners(uninstaller);
        return uninstaller;
    }

    /** Subclasses should override this to do the actual creation of an {@link Uninstaller}. */
    @NonNull
    protected abstract Uninstaller doCreateUninstaller(
            @NonNull LocalPackage p, @NonNull RepoManager mgr);

    /**
     * Subclasses should override this to indicate whether they can generate installers/uninstallers
     * for the given package.
     */
    protected boolean canHandlePackage(@NonNull RepoPackage pack, @NonNull RepoManager manager) {
        return true;
    }
}
