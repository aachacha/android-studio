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
package com.android.tools.lint.checks.plurals

import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.lint.checks.PluralsDatabase

// GENERATED DATA.
// This data is generated by the #testDatabaseAccurate method in PluralsDatasetTest
// which will generate the following if it can find an ICU plurals database file
// in the unit test data folder.

object CLDR28Database :
  PluralsDatabase(
    languageCodes =
      arrayOf(
        "af",
        "ak",
        "am",
        "ar",
        "as",
        "az",
        "be",
        "bg",
        "bh",
        "bm",
        "bn",
        "bo",
        "br",
        "bs",
        "ca",
        "ce",
        "cs",
        "cy",
        "da",
        "de",
        "dv",
        "dz",
        "ee",
        "el",
        "en",
        "eo",
        "es",
        "et",
        "eu",
        "fa",
        "ff",
        "fi",
        "fo",
        "fr",
        "fy",
        "ga",
        "gd",
        "gl",
        "gu",
        "gv",
        "ha",
        "he",
        "hi",
        "hr",
        "hu",
        "hy",
        "id",
        "ig",
        "ii",
        "in",
        "is",
        "it",
        "iu",
        "iw",
        "ja",
        "ji",
        "jv",
        "ka",
        "kk",
        "kl",
        "km",
        "kn",
        "ko",
        "ks",
        "ku",
        "kw",
        "ky",
        "lb",
        "lg",
        "ln",
        "lo",
        "lt",
        "lv",
        "mg",
        "mk",
        "ml",
        "mn",
        "mr",
        "ms",
        "mt",
        "my",
        "nb",
        "nd",
        "ne",
        "nl",
        "nn",
        "no",
        "nr",
        "ny",
        "om",
        "or",
        "os",
        "pa",
        "pl",
        "ps",
        "pt",
        "rm",
        "ro",
        "ru",
        "se",
        "sg",
        "si",
        "sk",
        "sl",
        "sn",
        "so",
        "sq",
        "sr",
        "ss",
        "st",
        "sv",
        "sw",
        "ta",
        "te",
        "th",
        "ti",
        "tk",
        "tl",
        "tn",
        "to",
        "tr",
        "ts",
        "ug",
        "uk",
        "ur",
        "uz",
        "ve",
        "vi",
        "vo",
        "wa",
        "wo",
        "xh",
        "yi",
        "yo",
        "zh",
        "zu",
      ),
    languageFlags =
      intArrayOf(
        0x0002,
        0x0042,
        0x0042,
        0x001f,
        0x0042,
        0x0002,
        0x005a,
        0x0002,
        0x0042,
        0x0000,
        0x0042,
        0x0000,
        0x00de,
        0x004a,
        0x0002,
        0x0002,
        0x001a,
        0x001f,
        0x0002,
        0x0002,
        0x0002,
        0x0000,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0042,
        0x0042,
        0x0002,
        0x0002,
        0x0042,
        0x0002,
        0x001e,
        0x00ce,
        0x0002,
        0x0042,
        0x00de,
        0x0002,
        0x0016,
        0x0042,
        0x004a,
        0x0002,
        0x0042,
        0x0000,
        0x0000,
        0x0000,
        0x0000,
        0x0042,
        0x0002,
        0x0006,
        0x0016,
        0x0000,
        0x0002,
        0x0000,
        0x0002,
        0x0002,
        0x0002,
        0x0000,
        0x0042,
        0x0000,
        0x0002,
        0x0002,
        0x0006,
        0x0002,
        0x0002,
        0x0002,
        0x0042,
        0x0000,
        0x005a,
        0x0063,
        0x0042,
        0x0042,
        0x0002,
        0x0002,
        0x0042,
        0x0000,
        0x001a,
        0x0000,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0042,
        0x001a,
        0x0002,
        0x0042,
        0x0002,
        0x000a,
        0x005a,
        0x0006,
        0x0000,
        0x0042,
        0x001a,
        0x00ce,
        0x0002,
        0x0002,
        0x0002,
        0x004a,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0002,
        0x0000,
        0x0042,
        0x0002,
        0x0042,
        0x0002,
        0x0000,
        0x0002,
        0x0002,
        0x0002,
        0x005a,
        0x0002,
        0x0002,
        0x0002,
        0x0000,
        0x0002,
        0x0042,
        0x0000,
        0x0002,
        0x0002,
        0x0000,
        0x0000,
        0x0042,
      ),
    apiLevel = VersionCodes.N,
  ) {

  override fun getExampleForQuantityZero(language: String): String? {
    return when (getLanguageIndex(language)) {
      // set14
      72 ->
        // lv
        "0, 10~20, 30, 40, 50, 60, 100, 1000, 10000, 100000, 1000000, \u2026"
      else -> null
    }
  }

  override fun getExampleForQuantityOne(language: String): String? {
    return when (getLanguageIndex(language)) {
      // set1
      2,
      4,
      10,
      29,
      38,
      42,
      61,
      77,
      135 ->
        // am, as, bn, fa, gu, hi, kn, mr, zu
        "0, 1"
      // set11
      50 ->
        // is
        "1, 21, 31, 41, 51, 61, 71, 81, 101, 1001, \u2026"
      // set12
      74 ->
        // mk
        "1, 11, 21, 31, 41, 51, 61, 71, 101, 1001, \u2026"
      // set13
      117 ->
        // tl
        "0~3, 5, 7, 8, 10~13, 15, 17, 18, 20, 21, 100, 1000, 10000, 100000, 1000000, \u2026"
      // set14
      72 ->
        // lv
        "1, 21, 31, 41, 51, 61, 71, 81, 101, 1001, \u2026"
      // set2
      30,
      33,
      45 ->
        // ff, fr, hy
        "0, 1"
      // set20
      13,
      43,
      107 ->
        // bs, hr, sr
        "1, 21, 31, 41, 51, 61, 71, 81, 101, 1001, \u2026"
      // set21
      36 ->
        // gd
        "1, 11"
      // set22
      103 ->
        // sl
        "1, 101, 201, 301, 401, 501, 601, 701, 1001, \u2026"
      // set27
      6 ->
        // be
        "1, 21, 31, 41, 51, 61, 71, 81, 101, 1001, \u2026"
      // set28
      71 ->
        // lt
        "1, 21, 31, 41, 51, 61, 71, 81, 101, 1001, \u2026"
      // set30
      98,
      123 ->
        // ru, uk
        "1, 21, 31, 41, 51, 61, 71, 81, 101, 1001, \u2026"
      // set31
      12 ->
        // br
        "1, 21, 31, 41, 51, 61, 81, 101, 1001, \u2026"
      // set33
      39 ->
        // gv
        "1, 11, 21, 31, 41, 51, 61, 71, 101, 1001, \u2026"
      // set4
      101 ->
        // si
        "0, 1"
      // set5
      1,
      8,
      69,
      73,
      92,
      115,
      129 ->
        // ak, bh, ln, mg, pa, ti, wa
        "0, 1"
      // set7
      95 ->
        // pt
        "0, 1"
      else -> null
    }
  }

  override fun getExampleForQuantityTwo(language: String): String? {
    return when (getLanguageIndex(language)) {
      // set21
      36 ->
        // gd
        "2, 12"
      // set22
      103 ->
        // sl
        "2, 102, 202, 302, 402, 502, 602, 702, 1002, \u2026"
      // set31
      12 ->
        // br
        "2, 22, 32, 42, 52, 62, 82, 102, 1002, \u2026"
      // set33
      39 ->
        // gv
        "2, 12, 22, 32, 42, 52, 62, 72, 102, 1002, \u2026"
      else -> null
    }
  }
}
