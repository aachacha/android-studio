/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.ide.common.repository.NetworkCache
import com.android.tools.lint.detector.api.LintFix
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GooglePlaySdkIndexTest {
  private lateinit var proto: Index
  private lateinit var index: GooglePlaySdkIndex

  @Before
  fun prepareIndex() {
    proto =
      Index.newBuilder()
        .addSdks(
          Sdk.newBuilder()
            .setIndexUrl("http://index.example.url/")
            .setIndexAvailability(Sdk.IndexAvailability.AVAILABLE_IN_PUBLIC_SDK_INDEX)
            .addLibraries(
              Library.newBuilder()
                .setLibraryId(
                  LibraryIdentifier.newBuilder()
                    .setMavenId(
                      LibraryIdentifier.MavenIdentifier.newBuilder()
                        .setGroupId("log4j")
                        .setArtifactId("log4j")
                        .build()
                    )
                )
                // Ok, latest, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("1.2.18").setIsLatestVersion(true)
                )
                // Ok, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("1.2.17").setIsLatestVersion(false)
                )
                // Critical
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.2.16")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setCriticalIssueInfo(
                          LibraryVersionLabels.CriticalIssueInfo.newBuilder()
                            .setDescription("This is a custom message from sdk developer.")
                        )
                    )
                )
                // Outdated
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.2.15")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setOutdatedIssueInfo(LibraryVersionLabels.OutdatedIssueInfo.newBuilder())
                    )
                )
                // Policy (deprecated label)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.2.14")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(LibraryVersionLabels.PolicyIssuesInfo.newBuilder())
                    )
                )
            )
        )
        .addSdks(
          Sdk.newBuilder()
            .setIndexUrl("http://another.example.url/")
            .setIndexAvailability(Sdk.IndexAvailability.AVAILABLE_IN_PUBLIC_SDK_INDEX)
            .addLibraries(
              Library.newBuilder()
                .setLibraryId(
                  LibraryIdentifier.newBuilder()
                    .setMavenId(
                      LibraryIdentifier.MavenIdentifier.newBuilder()
                        .setGroupId("com.example.ads.third.party")
                        .setArtifactId("example")
                        .build()
                    )
                )
                // Ok, latest
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("8.0.0").setIsLatestVersion(true)
                )
                // Ok
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("7.2.2").setIsLatestVersion(false)
                )
                // Ok
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("7.2.1").setIsLatestVersion(false)
                )
                // Outdated & non compliant (user data) & Critical
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.2.0")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setCriticalIssueInfo(LibraryVersionLabels.CriticalIssueInfo.newBuilder())
                        .setOutdatedIssueInfo(LibraryVersionLabels.OutdatedIssueInfo.newBuilder())
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                            )
                        )
                    )
                )
                // Non-compliant (Ads, non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.0")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_ADS
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Device and Network Abuse, blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.1")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                .SDK_POLICY_DEVICE_AND_NETWORK_ABUSE
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Deceptive Behavior, no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.2")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                .SDK_POLICY_DECEPTIVE_BEHAVIOR
                            )
                        )
                    )
                )
                // Non-compliant (User Data, non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.3")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Permissions, blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.4")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Mobile Unwanted Software, no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.5")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                .SDK_POLICY_MOBILE_UNWANTED_SOFTWARE
                            )
                        )
                    )
                )
                // Non-compliant (Malware, non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.6")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Multiple violations, non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.7")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                            )
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                            )
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Multiple violations, blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.8")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                            )
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Multiple violations, no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.9")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                            )
                        )
                    )
                )
                // Non-compliant (Unknown violations, no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.10")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            // Use very large numbers that will probably never be used by real
                            // violation types
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                            .addViolatedSdkPoliciesValue(1234567)
                            .addViolatedSdkPoliciesValue(2345678)
                        )
                    )
                )
            )
        )
        // No URL set (causes blank result for indexUrl)
        .addSdks(
          Sdk.newBuilder()
            .setIndexAvailability(Sdk.IndexAvailability.NOT_AVAILABLE)
            .addLibraries(
              Library.newBuilder()
                .setLibraryId(
                  LibraryIdentifier.newBuilder()
                    .setMavenId(
                      LibraryIdentifier.MavenIdentifier.newBuilder()
                        .setGroupId("no.url")
                        .setArtifactId("no.url")
                        .build()
                    )
                )
                // Ok, latest, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("2.0.0").setIsLatestVersion(true)
                )
                // Policy issues
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.0.3")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                        )
                    )
                )
                // Ok, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("1.0.2").setIsLatestVersion(false)
                )
                // Ok, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("1.0.1").setIsLatestVersion(false)
                )
                // Outdated
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.0.0")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setOutdatedIssueInfo(
                          LibraryVersionLabels.OutdatedIssueInfo.newBuilder()
                            // A closed range
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder()
                                .setLowerBound("1.0.1")
                                .setUpperBound("1.0.2")
                            )
                            // An open range
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder().setLowerBound("2.0.0")
                            )
                        )
                    )
                )
            )
        )
        // URL set not in SDK Index
        .addSdks(
          Sdk.newBuilder()
            .setIndexUrl("http://not.in.sdk.index.url/")
            .setIndexAvailability(Sdk.IndexAvailability.NOT_AVAILABLE)
            .addLibraries(
              Library.newBuilder()
                .setLibraryId(
                  LibraryIdentifier.newBuilder()
                    .setMavenId(
                      LibraryIdentifier.MavenIdentifier.newBuilder()
                        .setGroupId("not.in.sdk.index.url")
                        .setArtifactId("not.in.sdk")
                        .build()
                    )
                )
                // Ok, latest, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("3.0.4").setIsLatestVersion(true)
                )
                // Policy issues with version range
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("3.0.3")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                            // A closed range
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder()
                                .setLowerBound("2.0.0")
                                .setUpperBound("3.0.1")
                            )
                            // An open range
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder().setLowerBound("3.0.4")
                            )
                        )
                    )
                )
            )
        )
        .build()
    index =
      object : GooglePlaySdkIndex() {
        override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
          ReadUrlDataResult(null, true).also { fail("Trying to read proto from the network!") }

        override fun readDefaultData(relative: String): InputStream? {
          fail("Trying to read default proto!")
          return null
        }

        override fun error(throwable: Throwable, message: String?) {}
      }
    index.initialize(ByteArrayInputStream(proto.toByteArray()))
    assertThat(index.getLastReadSource()).isEqualTo(NetworkCache.DataSourceType.TEST_DATA)
  }

  @Test
  fun `outdated issues shown`() {
    assertThat(countOutdatedIssues()).isEqualTo(3)
  }

  @Test
  fun `critical issues shown`() {
    assertThat(countCriticalIssues()).isEqualTo(2)
  }

  @Test
  fun `policy issues shown`() {
    assertThat(countPolicyIssues()).isEqualTo(15)
  }

  @Test
  fun `errors and warnings shown correctly`() {
    assertThat(countHasErrorOrWarning()).isEqualTo(17)
  }

  @Test
  fun `links are present when indexUrl is not blank and is available in SDK Index`() {
    for (sdk in proto.sdksList) {
      val isFromIndex = sdk.indexAvailability != Sdk.IndexAvailability.NOT_AVAILABLE
      val expectedUrl = if (isFromIndex) sdk.indexUrl else null
      for (library in sdk.librariesList) {
        val group = library.libraryId.mavenId.groupId
        val artifact = library.libraryId.mavenId.artifactId
        val lintLink =
          index.generateSdkLinkLintFix(
            group,
            artifact,
            versionString = "noVersion",
            buildFile = null,
          )
        if (expectedUrl.isNullOrBlank()) {
          assertThat(lintLink).isNull()
        } else {
          assertThat(lintLink).isNotNull()
          assertThat(lintLink).isInstanceOf(LintFix.ShowUrl::class.java)
          assertThat((lintLink as LintFix.ShowUrl).url).isEqualTo(expectedUrl)
        }
      }
    }
  }

  @Test
  fun `offline snapshot can be used correctly`() {
    val offlineIndex =
      object : GooglePlaySdkIndex() {
        override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
          ReadUrlDataResult(null, true)

        override fun error(throwable: Throwable, message: String?) {}
      }
    offlineIndex.initialize()
    assertThat(offlineIndex.isReady()).isTrue()
    assertThat(offlineIndex.getLastReadSource()).isEqualTo(NetworkCache.DataSourceType.DEFAULT_DATA)
  }

  @Test
  fun `No violation type policy issue message`() {
    assertThat(index.generatePolicyMessages("logj4", "logj4", "1.2.14"))
      .isEqualTo(
        listOf(
          "logj4:logj4 version 1.2.14 has policy issues that will block publishing of your app to Play Console in the future"
        )
      )
  }

  @Test
  fun `policy with other issues message`() {
    verifyPolicyMessages("7.2.0", listOf("User Data policy"))
  }

  @Test
  fun `Ads policy issue message`() {
    verifyPolicyMessages("7.1.0", listOf("Ads policy"))
  }

  @Test
  fun `Device and Network Abuse policy issue message`() {
    verifyPolicyMessages("7.1.1", listOf("Device and Network Abuse policy"))
  }

  @Test
  fun `Deceptive Behavior policy issue message`() {
    verifyPolicyMessages("7.1.2", listOf("Deceptive Behavior policy"))
  }

  @Test
  fun `User Data policy issue message`() {
    verifyPolicyMessages("7.1.3", listOf("User Data policy"))
  }

  @Test
  fun `Permissions policy issue message`() {
    verifyPolicyMessages("7.1.4", listOf("Permissions policy"))
  }

  @Test
  fun `Mobile Unwanted Software policy issue message`() {
    verifyPolicyMessages("7.1.5", listOf("Mobile Unwanted Software policy"))
  }

  @Test
  fun `Malware policy issue message`() {
    verifyPolicyMessages("7.1.6", listOf("Malware policy"))
  }

  @Test
  fun `multiple policy types issue message`() {
    verifyPolicyMessages(
      "7.1.7",
      listOf("User Data policy", "Malware policy", "Permissions policy"),
    )
  }

  @Test
  fun `unknown policy type issue message`() {
    verifyPolicyMessages("7.1.10", listOf("Permissions policy", "policy"))
  }

  @Test
  fun `no quickfix for null indexUrl`() {
    val lintLink =
      index.generateSdkLinkLintFix(
        "not.existing.group",
        "not.existing.artifact",
        versionString = "noVersion",
        buildFile = null,
      )
    assertThat(lintLink).isNull()
  }

  private fun countOutdatedIssues(): Int {
    var result = 0
    for (sdk in proto.sdksList) {
      for (library in sdk.librariesList) {
        val group = library.libraryId.mavenId.groupId
        val artifact = library.libraryId.mavenId.artifactId
        for (version in library.versionsList) {
          if (index.isLibraryOutdated(group, artifact, version.versionString, null)) {
            result++
          }
        }
      }
    }
    return result
  }

  private fun countPolicyIssues(): Int {
    var result = 0
    for (sdk in proto.sdksList) {
      for (library in sdk.librariesList) {
        val group = library.libraryId.mavenId.groupId
        val artifact = library.libraryId.mavenId.artifactId
        for (version in library.versionsList) {
          if (index.isLibraryNonCompliant(group, artifact, version.versionString, null)) {
            result++
          }
        }
      }
    }
    return result
  }

  private fun countCriticalIssues(): Int {
    var result = 0
    for (sdk in proto.sdksList) {
      for (library in sdk.librariesList) {
        val group = library.libraryId.mavenId.groupId
        val artifact = library.libraryId.mavenId.artifactId
        for (version in library.versionsList) {
          if (index.hasLibraryCriticalIssues(group, artifact, version.versionString, null)) {
            result++
          }
        }
      }
    }
    return result
  }

  private fun countHasErrorOrWarning(): Int {
    var result = 0
    for (sdk in proto.sdksList) {
      for (library in sdk.librariesList) {
        val group = library.libraryId.mavenId.groupId
        val artifact = library.libraryId.mavenId.artifactId
        for (version in library.versionsList) {
          if (index.hasLibraryErrorOrWarning(group, artifact, version.versionString)) {
            result++
          }
        }
      }
    }
    return result
  }

  private fun verifyPolicyMessages(version: String, policyTypes: List<String>) {
    val expectedBlockingMessages =
      policyTypes.map { policyType ->
        "**[Prevents app release in Google Play Console]** com.example.ads.third.party:example version $version has $policyType issues that will block publishing of your app to Play Console"
      }
    assertThat(
        index.generateBlockingPolicyMessages("com.example.ads.third.party", "example", version)
      )
      .isEqualTo(expectedBlockingMessages)
    val expectedNonBlockingMessages =
      policyTypes.map { policyType ->
        "com.example.ads.third.party:example version $version has $policyType issues that will block publishing of your app to Play Console in the future"
      }
    assertThat(index.generatePolicyMessages("com.example.ads.third.party", "example", version))
      .isEqualTo(expectedNonBlockingMessages)
  }
}
