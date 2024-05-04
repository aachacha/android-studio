/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.sdklib.repository.targets;


import com.android.annotations.NonNull;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import junit.framework.TestCase;

/**
 * Tests for {@link SystemImageManager}
 */
public class SystemImageManagerTest extends TestCase {
    // TODO: break up tests into separate cases

    public void testSystemImageManager() {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
        recordPlatform13(sdkRoot);
        recordGoogleTvAddon13(sdkRoot);
        recordGoogleApisSysImg23(sdkRoot);
        recordSysImg23(sdkRoot);
        recordGoogleApis13(sdkRoot);

        AndroidSdkHandler handler = new AndroidSdkHandler(sdkRoot, null);
        FakeProgressIndicator progress = new FakeProgressIndicator();

        SystemImageManager mgr =
                new SystemImageManager(
                        handler.getSdkManager(progress),
                        AndroidSdkHandler.getSysImgModule().createLatestFactory());
        Set<SystemImage> images = Sets.newTreeSet(mgr.getImages());
        progress.assertNoErrorsOrWarnings();
        assertEquals(5, images.size());
        Iterator<SystemImage> resultIter = images.iterator();

        ISystemImage platform13 = resultIter.next();
        verifyPlatform13(platform13, sdkRoot);
        assertEquals(2, platform13.getSkins().size());

        verifySysImg23(resultIter.next(), sdkRoot);

        ISystemImage google13 = resultIter.next();
        verifyGoogleAddon13(google13);
        assertEquals(2, google13.getSkins().size());

        ISystemImage google23 = resultIter.next();
        verifyGoogleApisSysImg23(google23, sdkRoot);

        ISystemImage addon13 = resultIter.next();
        verifyTvAddon13(addon13, sdkRoot);
        assertEquals("google_tv_addon", addon13.getTag().getId());
    }

    private void verifyGoogleAddon13(ISystemImage img) {
        // Nothing, just here for consistency. Note the new implementation will pick up skins from
        // the platform.
    }

    private void verifyPlatform13(@NonNull ISystemImage img, @NonNull Path sdkRoot) {
        assertEquals("armeabi", img.getPrimaryAbiType());
        assertNull(img.getAddonVendor());
        assertEquals(sdkRoot.resolve("platforms/android-13/images/"), img.getLocation());
        assertEquals("default", img.getTag().getId());
    }

    private void verifyTvAddon13(@NonNull ISystemImage img, @NonNull Path sdkRoot) {
        assertEquals("x86", img.getPrimaryAbiType());
        assertEquals("google", img.getAddonVendor().getId());
        assertEquals(
                sdkRoot.resolve("add-ons/addon-google_tv_addon-google-13/images/x86/"),
                img.getLocation());
    }

    private void verifyGoogleApisSysImg23(@NonNull ISystemImage img, @NonNull Path sdkRoot) {
        assertEquals("x86_64", img.getPrimaryAbiType());
        assertEquals("google", img.getAddonVendor().getId());
        assertEquals(
                sdkRoot.resolve("system-images/android-23/google_apis/x86_64/"), img.getLocation());
        assertEquals("google_apis", img.getTag().getId());
    }

    private void verifySysImg23(@NonNull ISystemImage img, @NonNull Path sdkRoot) {
        assertEquals("x86", img.getPrimaryAbiType());
        assertNull(img.getAddonVendor());
        assertEquals(sdkRoot.resolve("system-images/android-23/default/x86/"), img.getLocation());
        assertEquals(
                ImmutableList.of(
                        sdkRoot.resolve("system-images/android-23/default/x86/skins/res1/"),
                        sdkRoot.resolve("system-images/android-23/default/x86/skins/res2/")),
                img.getSkins());
        assertEquals("default", img.getTag().getId());
    }

    private static void recordPlatform13(Path sdkRoot) {
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("platforms/android-13/images/system.img"));

        InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("platforms/android-13/android.jar"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("platforms/android-13/framework.aidl"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("platforms/android-13/skins/HVGA/layout"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("platforms/android-13/skins/sample.txt"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("platforms/android-13/skins/WVGA800/layout"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("platforms/android-13/sdk.properties"),
                "sdk.ant.templates.revision=1\n" + "sdk.skin.default=WXGA\n");
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("platforms/android-13/package.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns2:sdk-repository "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-2A86BE32\" type=\"text\">License Text\n</license>"
                        + "<localPackage path=\"platforms;android-13\" obsolete=\"false\">"
                        + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns2:platformDetailsType\"><api-level>13</api-level>"
                        + "<layoutlib api=\"4\"/></type-details><revision><major>1</major>"
                        + "</revision><display-name>API 13: Android 3.2 (Honeycomb)</display-name>"
                        + "<uses-license ref=\"license-2A86BE32\"/><dependencies>"
                        + "<dependency path=\"tools\"><min-revision><major>12</major></min-revision>"
                        + "</dependency></dependencies></localPackage></ns2:sdk-repository>");
    }

    private static void recordGoogleTvAddon13(Path sdkRoot) {
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("add-ons/addon-google_tv_addon-google-13/package.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns5:sdk-addon xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-A06C75BE\" type=\"text\">Terms and Conditions\n"
                        + "</license><localPackage "
                        + "path=\"add-ons;addon-google_tv_addon-google-13\" obsolete=\"false\">"
                        + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns5:addonDetailsType\"><api-level>13</api-level>"
                        + "<vendor><id>google</id><display>Google Inc.</display></vendor>"
                        + "<tag><id>google_tv_addon</id><display>Google TV Addon</display></tag>"
                        + "<default-skin>720p</default-skin>"
                        + "</type-details><revision><major>1</major><minor>0</minor>"
                        + "<micro>0</micro></revision>"
                        + "<display-name>Google TV Addon, Android 13</display-name>"
                        + "<uses-license ref=\"license-A06C75BE\"/></localPackage>"
                        + "</ns5:sdk-addon>\n");
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("add-ons/addon-google_tv_addon-google-13/skins/1080p/layout"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("add-ons/addon-google_tv_addon-google-13/skins/sample.txt"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve(
                        "add-ons/addon-google_tv_addon-google-13/skins/720p-overscan/layout"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("add-ons/addon-google_tv_addon-google-13/images/x86/system.img"));
    }

    private static void recordSysImg23(Path sdkRoot) {
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("system-images/android-23/default/x86/system.img"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("system-images/android-23/default/x86/skins/res1/layout"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("system-images/android-23/default/x86/skins/sample"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("system-images/android-23/default/x86/skins/res2/layout"));

        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("system-images/android-23/default/x86/package.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns3:sdk-sys-img "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-A78C4257\" type=\"text\">Terms and Conditions\n"
                        + "</license><localPackage path=\"system-images;android-23;default;x86\" "
                        + "obsolete=\"false\">"
                        + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>23</api-level>"
                        + "<tag><id>default</id><display>Default</display></tag><abi>x86</abi>"
                        + "</type-details><revision><major>5</major></revision>"
                        + "<display-name>Intel x86 Atom System Image</display-name>"
                        + "<uses-license ref=\"license-A78C4257\"/></localPackage>"
                        + "</ns3:sdk-sys-img>\n");
    }

    private static void recordGoogleApisSysImg23(Path sdkRoot) {
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("system-images/android-23/google_apis/x86_64/system.img"));

        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("system-images/android-23/google_apis/x86_64/package.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns3:sdk-sys-img "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-9A5C00D5\" type=\"text\">Terms and Conditions\n"
                        + "</license><localPackage "
                        + "path=\"system-images;android-23;google_apis;x86_64\" "
                        + "obsolete=\"false\"><type-details "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>23</api-level>"
                        + "<tag><id>google_apis</id><display>Google APIs</display></tag>"
                        + "<vendor><id>google</id><display>Google Inc.</display></vendor>"
                        + "<abi>x86_64</abi></type-details><revision><major>9</major></revision>"
                        + "<display-name>Google APIs Intel x86 Atom_64 System Image</display-name>"
                        + "<uses-license ref=\"license-9A5C00D5\"/></localPackage>"
                        + "</ns3:sdk-sys-img>\n");
    }

    private static void recordGoogleApis13(Path sdkRoot) {
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("add-ons/addon-google_apis-google-13/images/system.img"));
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("add-ons/addon-google_apis-google-13/package.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                        + "<ns5:sdk-addon "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">\n"
                        + "<license id=\"license-DB79309F\" type=\"text\">\n"
                        + "Terms and Conditions\n"
                        + "</license>\n"
                        + "<localPackage path=\"add-ons;addon-google_apis-google-13\" "
                        + "obsolete=\"false\">\n"
                        + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns5:addonDetailsType\">\n"
                        + "<api-level>13</api-level>\n"
                        + "<vendor>\n"
                        + "<id>google</id>\n"
                        + "<display>Google Inc.</display>\n"
                        + "</vendor>\n"
                        + "<tag>\n"
                        + "<id>google_apis</id>\n"
                        + "<display>\n"
                        + "Google APIs</display>\n"
                        + "</tag>\n"
                        + "</type-details>\n"
                        + "<revision>\n"
                        + "<major>1</major>\n"
                        + "<minor>0</minor>\n"
                        + "<micro>0</micro>\n"
                        + "</revision>\n"
                        + "<display-name>Google APIs, Android 13</display-name>\n"
                        + "<uses-license ref=\"license-DB79309F\"/>\n"
                        + "</localPackage>\n"
                        + "</ns5:sdk-addon>\n");
    }

}
