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

import com.android.SdkConstants.ANDROID_NS_NAME
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_AUTO_VERIFY
import com.android.SdkConstants.ATTR_ENABLED
import com.android.SdkConstants.ATTR_EXPORTED
import com.android.SdkConstants.ATTR_HOST
import com.android.SdkConstants.ATTR_ICON
import com.android.SdkConstants.ATTR_LABEL
import com.android.SdkConstants.ATTR_MIME_TYPE
import com.android.SdkConstants.ATTR_ORDER
import com.android.SdkConstants.ATTR_PATH
import com.android.SdkConstants.ATTR_PATH_ADVANCED_PATTERN
import com.android.SdkConstants.ATTR_PATH_PATTERN
import com.android.SdkConstants.ATTR_PATH_PREFIX
import com.android.SdkConstants.ATTR_PATH_SUFFIX
import com.android.SdkConstants.ATTR_PORT
import com.android.SdkConstants.ATTR_PRIORITY
import com.android.SdkConstants.ATTR_SCHEME
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.PREFIX_THEME_REF
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_DATA
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VALUE_0
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_ADVANCED_GLOB
import com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_LITERAL
import com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_PREFIX
import com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_SIMPLE_GLOB
import com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_SUFFIX
import com.android.tools.lint.checks.AppLinksValidDetector.UriInfo.Companion.calculateUriIntersection
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.isDataBindingExpression
import com.android.tools.lint.detector.api.isManifestPlaceHolderExpression
import com.android.tools.lint.detector.api.resolvePlaceHolders
import com.android.utils.CharSequences
import com.android.utils.XmlUtils
import com.android.utils.iterator
import com.android.xml.AndroidManifest
import com.android.xml.AndroidManifest.ATTRIBUTE_NAME
import com.android.xml.AndroidManifest.NODE_ACTION
import com.android.xml.AndroidManifest.NODE_DATA
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import java.net.MalformedURLException
import java.net.URL
import java.util.function.Consumer
import java.util.regex.Pattern
import org.jetbrains.annotations.VisibleForTesting
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val MAXIMUM_DATA_TAG_THRESHOLD = 200

/** Checks for invalid app links URLs */
class AppLinksValidDetector : Detector(), XmlScanner {
  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_ACTIVITY, TAG_ACTIVITY_ALIAS, TAG_INTENT_FILTER)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    when (val tag = element.tagName) {
      TAG_INTENT_FILTER -> checkIntentFilter(context, element)
      TAG_ACTIVITY,
      TAG_ACTIVITY_ALIAS -> checkActivity(context, element)
      else -> error("Unhandled tag $tag")
    }
  }

  /**
   * Reports incidents for intent filter [intentFilter]. Note that intent filters inside an
   * <activity> (as opposed to those inside a <service>, <receiver>, etc.) have additional
   * restrictions/requirements, which are not checked here.
   */
  private fun checkIntentFilter(context: XmlContext, intentFilter: Element) {
    // --- Check "splitting" (Recommend splitting a data tag into multiple tags with individual
    // attributes ---
    var dataTag: Node? = XmlUtils.getFirstSubTagByName(intentFilter, NODE_DATA) ?: return
    if (XmlUtils.getNextTagByName(dataTag, NODE_DATA) == null)
      return // This check only applies if there's > 1 data tag.

    val incidents = mutableListOf<Incident>()
    var dataTagCount = 0
    do {
      dataTagCount++

      val length = dataTag!!.attributes.length
      val attributes = mutableListOf<Node>()
      for (attributeIndex in 0 until length) {
        val attribute = dataTag.attributes.item(attributeIndex)
        if (attribute.namespaceURI == TOOLS_URI) continue
        attributes += attribute
      }

      val hasNonPath =
        attributes.any {
          // Allow tags with only path attributes, since it's obvious that those
          // are independent, and combining them may make sense.
          it.namespaceURI != ANDROID_URI || !it.localName.startsWith(ATTR_PATH)
        }

      val hasOnlyHostAndPort =
        hasNonPath &&
          attributes.all {
            it.namespaceURI == ANDROID_URI &&
              (it.localName == ATTR_HOST || it.localName == ATTR_PORT)
          }

      if (!hasNonPath || hasOnlyHostAndPort) {
        // Tags with only paths or with only host+port don't contribute to the threshold
        dataTagCount--
        continue
      }

      // If there's only 1 attribute, it's already correctly formatted. But this is checked
      // here so that the above logic to ignore path-only tags is accounted for, even
      // if that means a tag with only a path attribute.
      if (attributes.size <= 1) continue

      attributes.sortBy {
        INTENT_FILTER_DATA_SORT_REFERENCE.indexOf(it.localName).takeIf { it >= 0 } ?: Int.MAX_VALUE
      }

      val namespace = intentFilter.lookupPrefix(ANDROID_URI) ?: ANDROID_NS_NAME
      val hostIndex = attributes.indexOfFirst { it.localName == ATTR_HOST }
      val portIndex = attributes.indexOfFirst { it.localName == ATTR_PORT }

      val firstLine =
        if (hostIndex >= 0 && portIndex >= 0) {
          val host: Node
          val port: Node
          // Need to remove higher index first to avoid shifting
          if (hostIndex > portIndex) {
            host = attributes.removeAt(hostIndex)
            port = attributes.removeAt(portIndex)
          } else {
            port = attributes.removeAt(portIndex)
            host = attributes.removeAt(hostIndex)
          }
          "<$TAG_DATA $namespace:${host.localName}=\"${host.nodeValue}\" " +
            "$namespace:${port.localName}=\"${port.nodeValue}\"/>\n"
        } else null

      val location = context.getLocation(dataTag)
      val indent = (0 until (location.start?.column ?: 4)).joinToString(separator = "") { " " }
      val newText =
        firstLine.orEmpty() +
          attributes
            .mapIndexed { index, it ->
              // Don't indent the first line, as the default fix location is already indented
              val lineIndent = indent.takeIf { index > 0 || firstLine != null }.orEmpty()
              "$lineIndent<$TAG_DATA $namespace:${it.localName}=\"${it.nodeValue}\"/>"
            }
            .joinToString(separator = "\n")

      incidents +=
        Incident(
          INTENT_FILTER_UNIQUE_DATA_ATTRIBUTES,
          "Consider splitting $TAG_DATA tag into multiple tags with individual" +
            " attributes to avoid confusion",
          location,
          dataTag,
          LintFix.create().replace().with(newText).autoFix().build(),
        )
    } while (
      run {
        dataTag = XmlUtils.getNextTagByName(dataTag, NODE_DATA)
        dataTag
      } != null
    )

    // Only report if there's more than 1 offending data tag, since if there is only 1,
    // it's clear what URI should be caught.
    if (dataTagCount > 1) {
      incidents.forEach(context::report)
    }
  }

  private fun checkActivity(context: XmlContext, element: Element) {
    val infos = checkActivityIntentFiltersAndGetUriInfos(element, context)
    var current = XmlUtils.getFirstSubTagByName(element, TAG_VALIDATION)
    while (current != null) {
      if (TOOLS_URI == current.namespaceURI) {
        val testUrlAttr = current.getAttributeNode("testUrl")
        if (testUrlAttr == null) {
          val message = "Expected `testUrl` attribute"
          reportUrlError(context, current, context.getLocation(current), message)
        } else {
          val testUrlString = testUrlAttr.value
          try {
            val testUrl = URL(testUrlString)
            val reason = checkTestUrlMatchesAtLeastOneInfo(testUrl, infos)
            if (reason != null) {
              reportTestUrlFailure(
                context,
                testUrlAttr,
                context.getValueLocation(testUrlAttr),
                reason,
              )
            }
          } catch (e: MalformedURLException) {
            val message = "Invalid test URL: " + e.localizedMessage
            reportTestUrlFailure(
              context,
              testUrlAttr,
              context.getValueLocation(testUrlAttr),
              message,
            )
          }
        }
      } else {
        reportTestUrlFailure(
          context,
          current,
          context.getNameLocation(current),
          "Validation nodes should be in the `tools:` namespace to " +
            "ensure they are removed from the manifest at build time",
        )
      }
      current = XmlUtils.getNextTagByName(current, TAG_VALIDATION)
    }
  }

  private fun reportUrlError(
    context: XmlContext,
    node: Node,
    location: Location,
    message: String,
    quickfixData: LintFix? = null,
  ) {
    // Validation errors were reported here before
    if (context.driver.isSuppressed(context, _OLD_ISSUE_URL, node)) {
      return
    }
    context.report(VALIDATION, node, location, message, quickfixData)
  }

  private fun reportTestUrlFailure(
    context: XmlContext,
    node: Node,
    location: Location,
    message: String,
  ) {
    context.report(TEST_URL, node, location, message)
  }

  /**
   * Given an activity (or activity alias) element, looks up the intent filters that contain URIs,
   * reports incidents for them, and creates a list of [UriInfo] objects to describe them.
   *
   * @param activity the activity
   * @param context a Lint context to validate the activity attributes and report link-related
   *   problems (such as unknown scheme, wrong port number, etc.)
   * @return a list of URI infos, if any
   */
  @VisibleForTesting
  fun checkActivityIntentFiltersAndGetUriInfos(
    activity: Element,
    context: XmlContext,
  ): List<UriInfo> {
    var intent = XmlUtils.getFirstSubTagByName(activity, TAG_INTENT_FILTER)
    val infos: MutableList<UriInfo> = Lists.newArrayList()
    while (intent != null) {
      handleIntentFilterInActivity(context, intent, activity)?.let { infos.add(it) }
      intent = XmlUtils.getNextTagByName(intent, TAG_INTENT_FILTER)
    }
    return infos
  }

  /**
   * Given a test URL and a list of URI infos (previously returned from
   * [checkActivityIntentFiltersAndGetUriInfos]) this method checks whether the URL matches, and if
   * so returns null, otherwise returning the reason for the mismatch.
   *
   * @param testUrl the URL to test
   * @param infos the URL information
   * @return null for a match, otherwise the failure reason
   */
  @VisibleForTesting
  fun checkTestUrlMatchesAtLeastOneInfo(testUrl: URL, infos: List<UriInfo>): String? {
    val reasons = mutableListOf<String>()
    for (info in infos) {
      val reason = info.match(testUrl) ?: return null // found a match
      if (!reasons.contains(reason)) {
        reasons.add(reason)
      }
    }
    return if (reasons.isNotEmpty()) {
      "Test URL " + Joiner.on(" or ").join(reasons)
    } else {
      null
    }
  }

  /**
   * Processes an intent filter element ([intentFilter]), inside the activity element [activity], in
   * the [XmlContext] [context].
   *
   * Note that intent filters inside an <activity> (as opposed to those inside a <service>,
   * <receiver>, etc.) have additional restrictions/requirements. As a result, this function is only
   * called for intent filters inside an activity, whereas [checkIntentFilter] is called for all
   * intent filters (including those inside a <service>, <receiver>, etc.).
   *
   * @return The [UriInfo] containing the schemes, hosts, ports, and paths within this intent
   *   filter.
   */
  private fun handleIntentFilterInActivity(
    context: XmlContext,
    intentFilter: Element,
    activity: Element,
  ): UriInfo? {
    // --- Check "non-exported activity" (Activity has one intent filter supporting ACTION_VIEW, but
    // is not exported) ---
    val actionView = hasActionView(intentFilter)
    if (actionView) {
      ensureExported(context, activity, intentFilter)
    }

    // --- Check "missing data node" (Intent filter has ACTION_VIEW & CATEGORY_BROWSABLE, but no
    // data node) ---
    val firstData = XmlUtils.getFirstSubTagByName(intentFilter, TAG_DATA)
    val browsable = isBrowsable(intentFilter)
    if (firstData == null) {
      if (actionView && browsable) {
        // If this intent has ACTION_VIEW and CATEGORY_BROWSABLE, but doesn't have a data node, it's
        // a likely mistake
        reportUrlError(
          context,
          intentFilter,
          context.getLocation(intentFilter),
          "Missing data element",
        )
      }
      return null
    }

    // Gather data about scheme, host, port, path, mimeType tags so that we can check them
    val schemes = mutableSetOf<String>()
    val hostPortPairs = mutableSetOf<Pair<String?, String?>>()
    val paths = mutableSetOf<AndroidPatternMatcher>()
    var hasMimeType = false
    var data = firstData
    while (data != null) {
      val mimeType = data.getAttributeNodeNS(ANDROID_URI, ATTR_MIME_TYPE)
      if (mimeType != null) {
        hasMimeType = true
        val mimeTypeValue = mimeType.value
        val resolved = resolvePlaceHolders(context.project, mimeTypeValue, null, "")
        if (CharSequences.containsUpperCase(resolved)) {
          var message =
            ("Mime-type matching is case sensitive and should only " + "use lower-case characters")
          if (
            !CharSequences.containsUpperCase(resolvePlaceHolders(null, mimeTypeValue, null, ""))
          ) {
            // The upper case character is only present in the substituted
            // manifest placeholders, so include them in the message
            message += " (without placeholders, value is `$resolved`)"
          }
          // --- Check 4 "mimeType" (mimeType matching has uppercase characters) ---
          reportUrlError(context, mimeType, context.getValueLocation(mimeType), message)
        }
      }

      // Multiple checks for "valid attributes" (Schemes, hosts, ports, paths all pass validation
      // (see validateAttribute, and requireNonEmpty below))
      addAttribute(context, ATTR_SCHEME, schemes, data)
      val host = checkAndGetAttributeValue(context, ATTR_HOST, data)
      val port = checkAndGetAttributeValue(context, ATTR_PORT, data)
      // Don't add ports on their own to hostPortPairs to make it simpler to check whether there are
      // any hosts (hostPortPairs.isEmpty()).
      if (host != null) {
        hostPortPairs.add(Pair(host, port))
      }
      addMatcher(context, ATTR_PATH, PATTERN_LITERAL, paths, data)
      addMatcher(context, ATTR_PATH_PREFIX, PATTERN_PREFIX, paths, data)
      addMatcher(context, ATTR_PATH_PATTERN, PATTERN_SIMPLE_GLOB, paths, data)
      addMatcher(context, ATTR_PATH_ADVANCED_PATTERN, PATTERN_ADVANCED_GLOB, paths, data)
      addMatcher(context, ATTR_PATH_SUFFIX, PATTERN_SUFFIX, paths, data)
      data = XmlUtils.getNextTagByName(data, TAG_DATA)
    }
    var isHttp = false
    var implicitSchemes = false
    var hasSubstitutedScheme = false
    if (schemes.isEmpty()) {
      if (hasMimeType) {
        // Per documentation
        //   https://developer.android.com/guide/topics/manifest/data-element.html
        // "If the filter has a data type set (the mimeType attribute) but no scheme, the
        //  content: and file: schemes are assumed."
        schemes.add("content")
        schemes.add("file")
        implicitSchemes = true
      }
    } else {
      for (scheme in schemes) {
        when {
          "http" == scheme || "https" == scheme -> isHttp = true
          isSubstituted(scheme) -> hasSubstitutedScheme = true
        }
      }
    }

    val hasExplicitScheme = schemes.isNotEmpty() && !implicitSchemes

    // Validation
    // autoVerify means this is an Android App Link:
    // https://developer.android.com/training/app-links#android-app-links
    if (shouldDisplayLaunchAppLinksAssistantQuickFix(ElementWrapper(intentFilter, context))) {
      // If we are in Studio then add quick-fix data so that Studio adds the
      // "Launch App Links Assistant" quick-fix.
      val fix =
        if (LintClient.isStudio) {
          fix().data(KEY_SHOW_APP_LINKS_ASSISTANT, true)
        } else {
          null
        }

      // --- Check "valid app link" (has autoVerify (i.e. is Android App Link) but is missing a
      // required element / attribute) ---
      reportUrlError(
        context,
        intentFilter,
        context.getLocation(intentFilter),
        "Missing required elements/attributes for Android App Links",
        fix,
      )
    }

    val showMissingSchemeCheck =
      !hasExplicitScheme && (paths.isNotEmpty() || hostPortPairs.isNotEmpty())

    // --- Check "missing scheme" ---
    // If there are hosts, paths, or ports, then there should be a scheme.
    // We insist on this because hosts, paths, and ports will be ignored if there is no explicit
    // scheme, which makes the intent filter very misleading.
    if (showMissingSchemeCheck) {
      val fix =
        if (hostPortPairs.isEmpty()) {
          // If there are no hosts, ask the user to specify the scheme.
          fix().set().todo(ANDROID_URI, ATTR_SCHEME)
        } else {
          // If there's at least one host, it's likely they want http(s), so we can prompt them with
          // http.
          fix().set().todo(ANDROID_URI, ATTR_SCHEME, "http")
        }
      reportUrlError(
        context,
        firstData,
        context.getLocation(firstData),
        "At least one `scheme` must be specified",
        fix.build(),
      )
    }

    // --- Check "missing URI" (This intent filter has a view action but no URI) ---
    // We only show this check if the "missing scheme check" isn't already showing, because they're
    // very similar.
    val showMissingUriCheck = schemes.isEmpty() && actionView
    if (!showMissingSchemeCheck && showMissingUriCheck) {
      reportUrlError(
        context,
        firstData,
        context.getLocation(firstData),
        "VIEW actions require a URI",
        fix()
          .alternatives(
            fix().set().todo(ANDROID_URI, ATTR_SCHEME).build(),
            fix().set().todo(ANDROID_URI, ATTR_MIME_TYPE).build(),
          ),
      )
    }

    // --- Check "missing host" (Hosts are required when a path is used. ) ---
    // We insist on this because paths will be ignored if there is no host, which makes the intent
    // filter very misleading.
    if (paths.isNotEmpty() && hostPortPairs.all { (host, _) -> host.isNullOrBlank() }) {
      val fix = LintFix.create().set().todo(ANDROID_URI, ATTR_HOST).build()
      reportUrlError(
        context,
        firstData,
        context.getLocation(firstData),
        "At least one `host` must be specified",
        fix,
      )
    }

    // --- Check "view + browsable" (If this intent filter has an ACTION_VIEW action, and it has a
    // http URL but doesn't have BROWSABLE, it may be a mistake and we will report a warning.) ---
    if (actionView && isHttp && !browsable) {
      reportUrlError(
        context,
        intentFilter,
        context.getLocation(intentFilter),
        "Activity supporting ACTION_VIEW is not set as BROWSABLE",
      )
    }
    return UriInfo(schemes, hostPortPairs, paths)
  }

  private fun ensureExported(context: XmlContext, activity: Element, intentFilter: Element) {
    val exported = activity.getAttributeNodeNS(ANDROID_URI, ATTR_EXPORTED) ?: return
    if (VALUE_TRUE == exported.value) return

    // Make sure there isn't some *earlier* intent filter for this activity
    // that also reported this; we don't want duplicate warnings
    var prevIntent = XmlUtils.getPreviousTagByName(intentFilter, TAG_INTENT_FILTER)
    while (prevIntent != null) {
      if (hasActionView(prevIntent)) return
      prevIntent = XmlUtils.getNextTagByName(prevIntent, TAG_INTENT_FILTER)
    }

    // Report error if the activity supporting action view is not exported.
    reportUrlError(
      context,
      activity,
      context.getLocation(activity),
      "Activity supporting ACTION_VIEW is not exported",
    )
  }

  /**
   * Check if the intent filter supports action view.
   *
   * @param intentFilter the intent filter
   * @return true if it does
   */
  private fun hasActionView(intentFilter: Element): Boolean {
    for (action in XmlUtils.getSubTagsByName(intentFilter, NODE_ACTION)) {
      if (action.hasAttributeNS(ANDROID_URI, ATTRIBUTE_NAME)) {
        val attr = action.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_NAME)
        if (attr.value == "android.intent.action.VIEW") {
          return true
        }
      }
    }
    return false
  }

  /**
   * Check if the intent filter is browsable.
   *
   * @param intentFilter the intent filter
   * @return true if it does
   */
  private fun isBrowsable(intentFilter: Element): Boolean {
    for (e in XmlUtils.getSubTagsByName(intentFilter, AndroidManifest.NODE_CATEGORY)) {
      if (e.hasAttributeNS(ANDROID_URI, ATTRIBUTE_NAME)) {
        val attr = e.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_NAME)
        if (attr.nodeValue == "android.intent.category.BROWSABLE") {
          return true
        }
      }
    }
    return false
  }

  private fun isAutoVerify(intentFilter: Element) =
    intentFilter.getAttributeNS(ANDROID_URI, ATTR_AUTO_VERIFY) == VALUE_TRUE

  private fun hasCategoryDefault(intentFilter: Element) =
    intentFilter.iterator().asSequence().any {
      it.tagName == TAG_CATEGORY &&
        it.getAttributeNS(ANDROID_URI, ATTRIBUTE_NAME) == "android.intent.category.DEFAULT"
    }

  /**
   * Reports incidents related to attribute with name [attributeName] inside [data] <data> element,
   * within XmlContext [context].
   *
   * @return The value that [attributeName] defines.
   */
  private fun checkAndGetAttributeValue(
    context: XmlContext?,
    attributeName: String,
    data: Element,
  ): String? {
    val attribute = data.getAttributeNodeNS(ANDROID_URI, attributeName)
    if (attribute != null) {
      var value = attribute.value
      if (requireNonEmpty(context, attribute, value)) {
        return null
      }
      if (value.startsWith(PREFIX_RESOURCE_REF) || value.startsWith(PREFIX_THEME_REF)) {
        value = replaceUrlWithValue(context, value)
      }
      if (
        isSubstituted(value) ||
          value.startsWith(PREFIX_RESOURCE_REF) ||
          value.startsWith(PREFIX_THEME_REF)
      ) { // already checked but can be nested
        return value
      }

      // Validation
      if (context != null) {
        validateAttribute(context, attributeName, data, attribute, value)
      }
      return value
    }
    return null
  }

  private fun addAttribute(
    context: XmlContext?,
    attributeName: String,
    existing: MutableSet<String>,
    data: Element,
  ) {
    checkAndGetAttributeValue(context, attributeName, data)?.let { existing.add(it) }
  }

  private fun validateAttribute(
    context: XmlContext,
    attributeName: String,
    data: Element,
    attribute: Attr,
    value: String,
  ) {
    // See https://developer.android.com/guide/topics/manifest/data-element.html
    when (attributeName) {
      ATTR_SCHEME -> {
        if (value.endsWith(":")) {
          reportUrlError(
            context,
            attribute,
            context.getValueLocation(attribute),
            "Don't include trailing colon in the `scheme` declaration",
          )
        } else if (CharSequences.containsUpperCase(value)) {
          reportUrlError(
            context,
            attribute,
            context.getValueLocation(attribute),
            "Scheme matching is case sensitive and should only " + "use lower-case characters",
          )
        }
      }
      ATTR_HOST -> {
        if (value.lastIndexOf('*') > 0) {
          reportUrlError(
            context,
            attribute,
            context.getValueLocation(attribute),
            "The host wildcard (`*`) can only be the first character",
          )
        } else if (CharSequences.containsUpperCase(value)) {
          reportUrlError(
            context,
            attribute,
            context.getValueLocation(attribute),
            "Host matching is case sensitive and should only " + "use lower-case characters",
          )
        }
      }
      ATTR_PORT -> {
        try {
          val port = value.toInt() // might also throw number exc
          if (port < 1 || port > 65535) {
            throw NumberFormatException()
          }
        } catch (e: NumberFormatException) {
          reportUrlError(
            context,
            attribute,
            context.getValueLocation(attribute),
            "not a valid port number",
          )
        }

        // The port *only* takes effect if it's specified on the *same* XML
        // element as the host (this isn't true for the other attributes,
        // which can be spread out across separate <data> elements)
        if (!data.hasAttributeNS(ANDROID_URI, ATTR_HOST)) {
          reportUrlError(
            context,
            attribute,
            context.getValueLocation(attribute),
            "The port must be specified in the same `<data>` " + "element as the `host`",
          )
        }
      }
    }
  }

  private fun addMatcher(
    context: XmlContext?,
    attributeName: String,
    type: Int,
    matcher: MutableSet<AndroidPatternMatcher>,
    data: Element,
  ) {
    val attribute = data.getAttributeNodeNS(ANDROID_URI, attributeName)
    if (attribute != null) {
      var value = attribute.value
      if (requireNonEmpty(context, attribute, value)) return
      if (value.startsWith(PREFIX_RESOURCE_REF) || value.startsWith(PREFIX_THEME_REF)) {
        value = replaceUrlWithValue(context, value)
      }
      val currentMatcher = AndroidPatternMatcher(value, type)
      matcher.add(currentMatcher)
      if (context != null && !isSubstituted(value) && !value.startsWith(PREFIX_RESOURCE_REF)) {
        if (!value.startsWith("/") && attributeName in setOf(ATTR_PATH, ATTR_PATH_PREFIX)) {
          val fix = LintFix.create().replace().text(attribute.value).with("/$value").build()
          reportUrlError(
            context,
            attribute,
            context.getValueLocation(attribute),
            "`${attribute.name}` attribute should start with `/`, but it is `$value`",
            fix,
          )
        }
        if (
          !(value.startsWith("/") || value.startsWith(".*")) && attributeName == ATTR_PATH_PATTERN
        ) {
          reportUrlError(
            context,
            attribute,
            context.getValueLocation(attribute),
            "`${attribute.name}` attribute should start with `/` or `.*`, but it is `$value`",
          )
        }
      }
    }
  }

  private fun requireNonEmpty(context: XmlContext?, attribute: Attr, value: String?): Boolean {
    if (context != null && value.isNullOrEmpty()) {
      reportUrlError(
        context,
        attribute,
        context.getLocation(attribute),
        "`${attribute.name}` cannot be empty",
      )
      return true
    }
    return false
  }

  /** URL information from an intent filter */
  class UriInfo(
    val schemes: Set<String>,
    val hostPortPairs: Set<Pair<String?, String?>>,
    val paths: Set<AndroidPatternMatcher>,
  ) {
    val isEmpty = schemes.isEmpty() && hostPortPairs.isEmpty() && paths.isEmpty()

    /**
     * Matches a URL against this info, and returns null if successful or the failure reason if not
     * a match
     *
     * @param testUrl the URL to match
     * @return null for a successful match or the failure reason
     */
    fun match(testUrl: URL): String? {
      val schemeOk =
        schemes.any { scheme: String -> scheme == testUrl.protocol || isSubstituted(scheme) }
      if (!schemeOk) {
        return "did not match scheme ${Joiner.on(", ").join(schemes)}"
      }
      if (hostPortPairs.isNotEmpty()) {
        val hostOk =
          hostPortPairs.any { (host, port) ->
            (host?.let { matchesHost(testUrl.host, it) || isSubstituted(it) } ?: true) &&
              (port?.let { testUrl.port.toString() == port || isSubstituted(port) } ?: true)
          }
        if (!hostOk) {
          return "did not match${if (hostPortPairs.size > 1) " any of" else ""} ${
            hostPortPairs.joinToString(", ") { (host, port) -> if (host != null && port != null) "host+port $host:$port" else if (host != null) "host $host" else "" }
          }"
        }
      }
      if (paths.isNotEmpty()) {
        val testPath = testUrl.path
        val pathOk = paths.any { isSubstituted(it.path) || it.match(testPath) }
        if (!pathOk) {
          val sb = StringBuilder()
          paths.forEach(
            Consumer { matcher: AndroidPatternMatcher ->
              sb.append("path ").append(matcher.toString()).append(", ")
            }
          )
          if (CharSequences.endsWith(sb, ", ", true)) {
            sb.setLength(sb.length - 2)
          }
          var message = "did not match $sb"
          if (containsUpperCase(paths) || CharSequences.containsUpperCase(testPath)) {
            message += " Note that matching is case sensitive."
          }
          return message
        }
      }
      return null // OK
    }

    private fun containsUpperCase(matchers: Set<AndroidPatternMatcher>): Boolean {
      return matchers.any { CharSequences.containsUpperCase(it.path) }
    }

    /**
     * Check whether a given host matches the hostRegex. The hostRegex could be a regular host name,
     * or it could contain only one '*', such as *.example.com, where '*' matches any string whose
     * length is at least 1.
     *
     * @param actualHost The actual host we want to check.
     * @param hostPattern The criteria host, which could contain a '*'.
     * @return Whether the actualHost matches the hostRegex
     */
    private fun matchesHost(actualHost: String, hostPattern: String): Boolean {
      // Per https://developer.android.com/guide/topics/manifest/data-element.html
      // the asterisk must be the first character
      return if (!hostPattern.startsWith("*")) {
        actualHost == hostPattern
      } else
        try {
          val pattern = Regex(".*${Pattern.quote(hostPattern.substring(1))}")
          actualHost.matches(pattern)
        } catch (ignore: Throwable) {
          // Make sure we don't fail to compile the regex, though with the quote call
          // above this really shouldn't happen
          false
        }
    }

    override fun toString(): String {
      return "URIs(schemes=${asSortedMarkdownCodes(schemes)}" +
        (if (hostPortPairs.isEmpty()) {
          ""
        } else {
          ", hosts=${asSortedMarkdownCodes(hostPortPairs.map { it.first + if (it.second.isNullOrBlank()) "" else ":${it.second}" })}"
        }) +
        (if (paths.isEmpty()) {
          ""
        } else {
          ", paths=${asSortedMarkdownCodes(paths.map { it.path })}"
        }) +
        ")"
    }

    companion object {
      /**
       * Helper function for pretty-printing URIs in the INTERSECTING_DEEP_LINKS messages. We use
       * backticks to prevent asterisks in paths from creating italics. We sort for consistency in
       * tests.
       */
      private fun asSortedMarkdownCodes(strings: Collection<String>): List<String> {
        return strings.map { "`${it}`" }.sorted()
      }

      private fun asString(pathMatcher: AndroidPatternMatcher): String {
        val path = pathMatcher.path
        return when (pathMatcher.type) {
          PATTERN_LITERAL ->
            if (path.isNullOrEmpty()) "/.*" else if (path.startsWith("/")) path else "/$path"
          PATTERN_PREFIX ->
            if (path.isNullOrEmpty()) "/.*" else if (path.startsWith("/")) "$path.*" else "/$path.*"
          PATTERN_SIMPLE_GLOB,
          PATTERN_ADVANCED_GLOB ->
            when (path) {
              ".*" -> "/.*"
              "." -> "/"
              else ->
                if (path.isNullOrEmpty()) "/.*" else if (path.startsWith("/")) path else "/$path"
            }
          PATTERN_SUFFIX -> if (path.isNullOrEmpty()) "/.*" else "/.*$path"
          else -> "/.*"
        }
      }

      fun calculateUriIntersection(uriInfo1: UriInfo, uriInfo2: UriInfo): UriInfo? {
        if (uriInfo1.schemes.isEmpty() || uriInfo2.schemes.isEmpty()) return null
        val schemeIntersection = uriInfo1.schemes.intersect(uriInfo2.schemes)
        if (schemeIntersection.isEmpty()) return null
        if (uriInfo1.hostPortPairs.isEmpty() != uriInfo2.hostPortPairs.isEmpty()) return null
        val hostPortsIntersection = uriInfo1.hostPortPairs.intersect(uriInfo2.hostPortPairs)
        if (uriInfo1.hostPortPairs.isNotEmpty() && hostPortsIntersection.isEmpty()) return null
        if (uriInfo1.paths.isEmpty() != uriInfo2.paths.isEmpty()) return null
        val uriInfo1PathStrings = uriInfo1.paths.mapTo(mutableSetOf()) { asString(it) }
        val uriInfo2PathStrings = uriInfo2.paths.mapTo(mutableSetOf()) { asString(it) }
        val pathsIntersection =
          uriInfo1PathStrings.intersect(uriInfo2PathStrings).mapTo(mutableSetOf()) {
            AndroidPatternMatcher(it, PATTERN_ADVANCED_GLOB)
          }
        if (uriInfo1.paths.isNotEmpty() && pathsIntersection.isEmpty()) return null
        return UriInfo(schemeIntersection, hostPortsIntersection, pathsIntersection)
      }
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (!context.isEnabled(INTERSECTING_DEEP_LINKS)) return
    if (context.project.isLibrary) return
    if (context.project.applicationId == null) return

    val uriInfos =
      mutableListOf<Pair<String, List<Pair<DeepLinkDataForIntersectionCheck, Location>>>>()
    val mergedManifestDocumentElement = context.project.mergedManifest?.documentElement ?: return
    for (node in mergedManifestDocumentElement) {
      if (node.tagName == TAG_APPLICATION) {
        for (applicationChild in node) {
          if (applicationChild.tagName !in setOf(TAG_ACTIVITY, TAG_ACTIVITY_ALIAS)) continue
          // Activities are enabled by default, so we only omit them if enabled is set to false.
          if (applicationChild.getAttributeNS(ANDROID_URI, ATTR_ENABLED) == VALUE_FALSE) continue
          // Ignore activities which have ignored the deeplink intersection check.
          val activityLocation = context.getLocation(applicationChild)
          val source = activityLocation.source
          if (source is Node && context.driver.isSuppressed(null, INTERSECTING_DEEP_LINKS, source))
            continue
          val activityIcon = applicationChild.getAttributeNS(ANDROID_URI, ATTR_ICON)
          val activityLabel = applicationChild.getAttributeNS(ANDROID_URI, ATTR_LABEL)
          val fqActivityName = applicationChild.getAttributeNS(ANDROID_URI, ATTRIBUTE_NAME)
          val urisForActivity = mutableListOf<Pair<DeepLinkDataForIntersectionCheck, Location>>()
          for (activityChild in applicationChild) {
            if (activityChild.tagName != TAG_INTENT_FILTER) continue
            val intentFilterIcon = activityChild.getAttributeNS(ANDROID_URI, ATTR_ICON)
            val intentFilterLabel = activityChild.getAttributeNS(ANDROID_URI, ATTR_LABEL)
            // If there was an intent filter with a huge number of data tags, then just abort the
            // whole thing
            val data = getIntentFilterData(ElementWrapper(activityChild, context)) ?: return
            if (data.uriInfo.isEmpty) continue
            if (
              data.autoVerify ||
                (data.actions.contains(ACTION_VIEW) &&
                  data.categories.contains(CATEGORY_BROWSABLE) &&
                  data.categories.contains(CATEGORY_DEFAULT))
            ) {
              urisForActivity.add(
                Pair(
                  DeepLinkDataForIntersectionCheck(
                    data.uriInfo,
                    data.order,
                    data.priority,
                    if (!intentFilterIcon.isNullOrBlank()) intentFilterIcon else activityIcon,
                    if (!intentFilterLabel.isNullOrBlank()) intentFilterLabel else activityLabel,
                    fqActivityName,
                  ),
                  activityLocation,
                )
              )
            }
          }
          uriInfos.add(Pair(fqActivityName, urisForActivity))
        }
      }
    }

    // Now report intersections
    for (i in 0 until uriInfos.size) {
      for ((data1, location1) in uriInfos[i].second) {
        for (j in i + 1 until uriInfos.size) {
          for ((data2, location2) in uriInfos[j].second) {
            val uriIntersection = getReportInfo(data1, data2) ?: continue
            context.report(
              INTERSECTING_DEEP_LINKS,
              // visible = false means the location won't be rendered in IDE. We do this because we
              // have
              // special treatment for IDE (see below).
              location1.withSecondary(
                location2.apply { visible = false },
                constructOtherLocationMessage(uriIntersection, data1.parentActivityFqName),
              ),
              constructOtherLocationMessage(uriIntersection, data2.parentActivityFqName),
            )
            // When we're in Studio, we create a second report with the primary and secondary
            // locations
            // swapped. This allows us to create a quick-fix which jumps from the location of the
            // reported incident to its secondary.
            // (See JumpToIntersectingDeepLinkFix.)
            // (Also note that this second report is needed because secondary locations don't have
            // references back to their primary, so the quickfix can only work on the "primary"
            // location.)
            if (LintClient.isStudio) {
              // Make copies of the location's file, start, and end fields to prevent cycles
              val loc1 = location1.copy()
              val loc2 = location2.copy()
              context.report(
                INTERSECTING_DEEP_LINKS,
                loc2.withSecondary(
                  loc1.apply { visible = false },
                  constructOtherLocationMessage(uriIntersection, data2.parentActivityFqName),
                ),
                constructOtherLocationMessage(uriIntersection, data1.parentActivityFqName),
              )
            }
          }
        }
      }
    }
  }

  companion object {
    internal const val ACTION_VIEW = "android.intent.action.VIEW"
    internal const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"
    internal const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
    internal val PATH_ATTRIBUTES =
      listOf(
        ATTR_PATH,
        ATTR_PATH_PREFIX,
        ATTR_PATH_PATTERN,
        ATTR_PATH_ADVANCED_PATTERN,
        ATTR_PATH_SUFFIX,
      )
    private const val HTTP = "http"
    private const val HTTPS = "https"

    private fun replaceUrlWithValue(context: Context?, str: String): String {
      if (context == null) {
        return str
      }
      val client = context.client
      val url = ResourceUrl.parse(str)
      if (url == null || url.isFramework) {
        return str
      }
      val project = context.project
      val resources = client.getResources(project, ResourceRepositoryScope.ALL_DEPENDENCIES)
      val items = resources.getResources(ResourceNamespace.TODO(), ResourceType.STRING, url.name)
      if (items.isEmpty()) {
        return str
      }
      val resourceValue = items[0].resourceValue ?: return str
      return if (resourceValue.value == null) str else resourceValue.value!!
    }

    internal fun attrToAndroidPatternMatcher(attr: String): Int {
      return when (attr) {
        ATTR_PATH -> PATTERN_LITERAL
        ATTR_PATH_PREFIX -> PATTERN_PREFIX
        ATTR_PATH_PATTERN -> PATTERN_SIMPLE_GLOB
        ATTR_PATH_ADVANCED_PATTERN -> PATTERN_ADVANCED_GLOB
        ATTR_PATH_SUFFIX -> PATTERN_SUFFIX
        else ->
          throw AssertionError(
            "Input was required to be one of the <data> path attributes, but was $attr"
          )
      }
    }

    fun androidPatternMatcherToAttr(androidPatternMatcherConstant: Int): String {
      return when (androidPatternMatcherConstant) {
        PATTERN_LITERAL -> ATTR_PATH
        PATTERN_PREFIX -> ATTR_PATH_PREFIX
        PATTERN_SIMPLE_GLOB -> ATTR_PATH_PATTERN
        PATTERN_ADVANCED_GLOB -> ATTR_PATH_ADVANCED_PATTERN
        PATTERN_SUFFIX -> ATTR_PATH_SUFFIX
        else ->
          throw AssertionError(
            "Input was required to be an AndroidPatternMatcher constant but was $androidPatternMatcherConstant"
          )
      }
    }

    fun isWebScheme(scheme: String): Boolean {
      return scheme == HTTP || scheme == HTTPS
    }

    interface TagWrapper {
      val name: String
      val subTags: Sequence<TagWrapper>

      /** Get the attribute value after applying string substitutions. */
      fun getAttributeValueWithSubstitution(attrName: String): String?
    }

    class ElementWrapper(private val element: Element, private val context: Context) : TagWrapper {
      override val name: String = element.tagName

      override fun getAttributeValueWithSubstitution(attrName: String): String? {
        val value = element.getAttributeNodeNS(ANDROID_URI, attrName)?.value ?: return null
        if (value.startsWith(PREFIX_RESOURCE_REF) || value.startsWith(PREFIX_THEME_REF)) {
          return replaceUrlWithValue(context, value)
        }
        return value
      }

      // Use asSequence for performance improvement
      override val subTags =
        XmlUtils.getSubTags(element).asSequence().map { ElementWrapper(it, context) }
    }

    data class IntentFilterData(
      val autoVerify: Boolean,
      val order: Int,
      val priority: Int,
      val actions: Set<String>, // These strings may be blank.
      val categories: Set<String>, // These strings may be blank.
      val schemes: Set<String>, // These strings may be blank.
      val hostPortPairs: Set<Pair<String, String?>>, // These strings may be blank.
      val paths: Set<AndroidPatternMatcher>,
      val mimeTypes: Set<String>,
    ) {
      val uriInfo = UriInfo(schemes, hostPortPairs, paths)
    }

    /**
     * @return [IntentFilterData] representing the intent filter, or null if there were more than
     *   [MAXIMUM_DATA_TAG_THRESHOLD] data tags.
     */
    fun getIntentFilterData(intentFilter: TagWrapper): IntentFilterData? {
      val autoVerify =
        intentFilter.getAttributeValueWithSubstitution(ATTR_AUTO_VERIFY) == VALUE_TRUE
      val order =
        intentFilter.getAttributeValueWithSubstitution(ATTR_ORDER)?.ifEmpty { VALUE_0 }?.toInt()
          ?: 0
      val priority =
        intentFilter.getAttributeValueWithSubstitution(ATTR_PRIORITY)?.ifEmpty { VALUE_0 }?.toInt()
          ?: 0
      val actions = mutableSetOf<String>()
      val categories = mutableSetOf<String>()
      val schemes = mutableSetOf<String>()
      val hostPortPairs = mutableSetOf<Pair<String, String?>>()
      val paths = mutableSetOf<AndroidPatternMatcher>()
      val mimeTypes = mutableSetOf<String>()
      var dataTagCount = 0
      for (subTag in intentFilter.subTags) {
        when (subTag.name) {
          TAG_ACTION ->
            subTag.getAttributeValueWithSubstitution(ATTRIBUTE_NAME)?.let { actions.add(it) }
          TAG_CATEGORY ->
            subTag.getAttributeValueWithSubstitution(ATTRIBUTE_NAME)?.let { categories.add(it) }
          TAG_DATA -> {
            dataTagCount++
            if (dataTagCount > MAXIMUM_DATA_TAG_THRESHOLD) return null
            subTag.getAttributeValueWithSubstitution(ATTR_SCHEME)?.let { schemes.add(it) }
            subTag.getAttributeValueWithSubstitution(ATTR_HOST)?.let {
              hostPortPairs.add(Pair(it, subTag.getAttributeValueWithSubstitution(ATTR_PORT)))
            }
            for (pathAttribute in PATH_ATTRIBUTES) {
              subTag.getAttributeValueWithSubstitution(pathAttribute)?.let {
                paths.add(AndroidPatternMatcher(it, attrToAndroidPatternMatcher(pathAttribute)))
              }
            }
            subTag.getAttributeValueWithSubstitution(ATTR_MIME_TYPE)?.let { mimeTypes.add(it) }
          }
        }
      }
      return IntentFilterData(
        autoVerify,
        order,
        priority,
        actions,
        categories,
        schemes,
        hostPortPairs,
        paths,
        mimeTypes,
      )
    }

    fun shouldDisplayLaunchAppLinksAssistantQuickFix(intentFilter: TagWrapper): Boolean {
      // If the intent filter is overwhelmingly large, return false
      val data = getIntentFilterData(intentFilter) ?: return false
      return (data.autoVerify &&
        (!data.actions.contains(ACTION_VIEW) ||
          !data.categories.contains(CATEGORY_DEFAULT) ||
          !data.categories.contains(CATEGORY_BROWSABLE) ||
          // All schemes in the intent filter must be web schemes for domain verification to be
          // requested.
          // This is a bug in Android, but they have no intent to fix it.
          (data.schemes.isNotEmpty() &&
            !data.schemes.all { isWebScheme(it) || isSubstituted(it) }) ||
          data.hostPortPairs.isEmpty()))
    }

    // Helpers for INTERSECTING_DEEP_LINKS check
    data class DeepLinkDataForIntersectionCheck(
      val uriInfo: UriInfo,
      val order: Int,
      val priority: Int,
      val icon: String,
      val label: String,
      val parentActivityFqName: String,
    )

    private fun getReportInfo(
      data1: DeepLinkDataForIntersectionCheck,
      data2: DeepLinkDataForIntersectionCheck,
    ): UriInfo? {
      val uriIntersection = calculateUriIntersection(data1.uriInfo, data2.uriInfo) ?: return null
      if (uriIntersection.schemes.isEmpty()) return null
      // Figure out if these intent filters are still intersecting when priority, icons, and labels
      // are considered. Note that if we ever want to check for intersecting intent filters across
      // different application IDs, then we will also want to consider order for custom-scheme
      // deeplinks.
      val hasWebScheme = uriIntersection.schemes.any { it == "http" || it == "https" }
      val prioritiesSame = data1.priority == data2.priority
      val iconsAndLabelsSame = (data1.icon == data2.icon) && (data1.label == data2.label)
      // App links don't respect order and priority and don't show disambiguations (which means
      // icons and labels don't help), so we always need to proceed to the next section to report
      // errors for app links.
      // If it's not an app link, we skip intersections where order, priority, icon, or label
      // differs.
      if (!hasWebScheme && (!prioritiesSame || !iconsAndLabelsSame)) return null
      return uriIntersection
    }

    private fun constructOtherLocationMessage(uriInfo: UriInfo, otherActivityName: String): String {
      return "Deep link URI intersection $uriInfo with activity $otherActivityName"
    }

    private fun Location.copy(): Location =
      start?.let { Location.create(file, it, end) } ?: Location.create(file)

    private val IMPLEMENTATION =
      Implementation(AppLinksValidDetector::class.java, Scope.MANIFEST_SCOPE)

    @JvmField
    val TEST_URL =
      Issue.create(
        id = "TestAppLink",
        briefDescription = "Unmatched URLs",
        explanation =
          """
                Using one or more `tools:validation testUrl="some url"/>` elements in your manifest allows \
                the link attributes in your intent filter to be checked for matches.
                """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val VALIDATION =
      Issue.create(
          id = "AppLinkUrlError",
          briefDescription = "URI invalid",
          explanation =
            """
                Ensure your intent filter has the documented elements for deep links, web links, or Android \
                App Links.""",
          category = Category.CORRECTNESS,
          priority = 5,
          moreInfo = "https://developer.android.com/training/app-links",
          severity = Severity.ERROR,
          implementation = IMPLEMENTATION,
        )
        .addMoreInfo("https://g.co/AppIndexing/AndroidStudio")

    /**
     * Only used for compatibility issue lookup (the driver suppression check takes an issue, not an
     * id)
     */
    private val _OLD_ISSUE_URL =
      Issue.create(
        id = "GoogleAppIndexingUrlError",
        briefDescription = "?",
        explanation = "?",
        category = Category.USABILITY,
        priority = 5,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    /** Multiple attributes in an intent filter data declaration */
    @JvmField
    @Suppress("LintImplUnexpectedDomain")
    val INTENT_FILTER_UNIQUE_DATA_ATTRIBUTES =
      Issue.create(
        id = "IntentFilterUniqueDataAttributes",
        briefDescription = "Data tags should only declare unique attributes",
        explanation =
          """
                `<intent-filter>` `<data>` tags should only declare a single unique attribute \
                (i.e. scheme OR host, but not both). This better matches the runtime behavior of \
                intent filters, as they combine all of the declared data attributes into a single \
                matcher which is allowed to handle any combination across attribute types.

                For example, the following two `<intent-filter>` declarations are the same:
                ```xml
                <intent-filter>
                    <data android:scheme="http" android:host="example.com" />
                    <data android:scheme="https" android:host="example.org" />
                </intent-filter>
                ```

                ```xml
                <intent-filter>
                    <data android:scheme="http"/>
                    <data android:scheme="https"/>
                    <data android:host="example.com" />
                    <data android:host="example.org" />
                </intent-filter>
                ```

                They both handle all of the following:
                * http://example.com
                * https://example.com
                * http://example.org
                * https://example.org

                The second one better communicates the combining behavior and is clearer to an \
                external reader that one should not rely on the scheme/host being self contained. \
                It is not obvious in the first that http://example.org is also matched, which can \
                lead to confusion (or incorrect behavior) with a more complex set of schemes/hosts.

                Note that this does not apply to host + port, as those must be declared in the same \
                `<data>` tag and are only associated with each other.
                """,
        category = Category.CORRECTNESS,
        severity = Severity.WARNING,
        moreInfo = "https://developer.android.com/guide/components/intents-filters",
        implementation = IMPLEMENTATION,
      )

    /** Two deep link intent filters are intersecting. */
    @JvmField
    val INTERSECTING_DEEP_LINKS =
      Issue.create(
        id = "IntersectingDeepLinks",
        briefDescription = "Two activities' deep links will match the same URIs",
        explanation =
          """
          If multiple `<intent-filter>`s match the same URIs, the app may not behave as expected. App \
          links will choose the last-declared app link on Android 12+ and use the order attribute on \
          Android 12-. Custom-scheme links will create an activity disambiguation. You may use order and \
          priority to set the evaluation order of your intent filters, or use the icon and label \
          attributes to ensure that users can better understand the options in a disambiguation menu, but \
          it is recommended to configure your deep links to be non-intersecting.
          """,
        category = Category.CORRECTNESS,
        severity = Severity.WARNING,
        moreInfo = "https://developer.android.com/guide/components/intents-filters",
        implementation = IMPLEMENTATION,
      )

    private const val TAG_VALIDATION = "validation"

    const val KEY_SHOW_APP_LINKS_ASSISTANT = "SHOW_APP_LINKS_ASSISTANT"
    const val KEY_INTERSECTING_DEEP_LINK_LOCATION = "INTERSECTING_DEEP_LINK_LOCATION"

    // Path matchers use the order used in
    // https://developer.android.com/guide/topics/manifest/data-element
    // <scheme>://<host>:<port>[<path>|<pathPrefix>|<pathPattern>|<pathSuffix>|<pathAdvancedPattern>]
    private val INTENT_FILTER_DATA_SORT_REFERENCE =
      listOf(
        ATTR_SCHEME,
        ATTR_HOST,
        ATTR_PORT,
        ATTR_PATH,
        ATTR_PATH_PREFIX,
        ATTR_PATH_PATTERN,
        ATTR_PATH_SUFFIX,
        ATTR_PATH_ADVANCED_PATTERN,
        ATTR_MIME_TYPE,
      )

    private fun isSubstituted(expression: String): Boolean {
      return isDataBindingExpression(expression) || isManifestPlaceHolderExpression(expression)
    }
  }

  override fun sameMessage(issue: Issue, new: String, old: String): Boolean {
    if (issue == VALIDATION && old == "Missing URL" && new == "VIEW actions require a URI")
      return true // See commit 406811b
    return super.sameMessage(issue, new, old)
  }
}
