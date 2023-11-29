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
package com.android.flags

import java.util.Locale
import java.util.function.Supplier

/**
 * A flag is a setting with an unique ID and some value. Flags are often used to gate features (e.g.
 * start with the feature disabled or enabled) or initialize a feature with some default value (e.g.
 * how much memory to initialize a system with, what mode a system should use by default).
 */
sealed class Flag<T>(
  /** Returns the [FlagGroup] that this flag is part of. */
  val group: FlagGroup,
  name: String,
  /** Returns a user-friendly display name for this flag. */
  val displayName: String,
  /** Returns a user-friendly description for what feature this flag gates. */
  val description: String,
  defaultValue: T,
  private val valueConverter: ValueConverter<T>
) {

  /**
   * Returns a unique ID for this flag. It will be composed of the group's name prefixed to this
   * flag's name.
   */
  val id: String = group.name + "." + name
  private val stringDefaultValue: String = valueConverter.serialize(defaultValue)
  private val originalDefaultValue: T = defaultValue

  init {
    group.flags.register(this)
  }

  /** Verifies that this flag has valid information */
  fun validate() {
    group.validate()
    verifyDefaultValue(originalDefaultValue, stringDefaultValue, valueConverter)
    verifyFlagIdFormat(id)
    verifyDisplayTextFormat(displayName)
    verifyDisplayTextFormat(description)
  }

  /** Returns the value of this flag. */
  fun get(): T {
    val strValue = group.flags.getOverriddenValue(this) ?: stringDefaultValue

    try {
      return valueConverter.deserialize(strValue)
    } catch (e: Exception) {
      return valueConverter.deserialize(stringDefaultValue)
    }
  }

  /**
   * Override the value of this flag at runtime.
   *
   * This method does not modify this flag definition directly, but instead adds an entry into its
   * parent [Flags.getOverrides] collection.
   */
  fun override(overrideValue: T) {
    group.flags.overrides.put(this, valueConverter.serialize(overrideValue))
  }

  /** Clear any override previously set by [.override]. */
  fun clearOverride() {
    group.flags.overrides.remove(this)
  }

  val isOverridden: Boolean
    get() = group.flags.overrides[this] != null

  /**
   * Simple interface for converting a value to and from a String. This is useful as all flags are
   * really strings underneath, although it's convenient to expose, say, boolean flags to users
   * instead.
   */
  protected interface ValueConverter<T> {
    fun serialize(value: T): String

    fun deserialize(strValue: String): T
  }

  companion object {
    /**
     * Verify that a flag's ID is correctly formatted, i.e. consisting of only lower-case letters,
     * numbers, and periods. Furthermore, the first character of an ID must be a letter and cannot
     * end with one.
     */
    @JvmStatic
    fun verifyFlagIdFormat(id: String) {
      require(id.matches("[a-z][a-z0-9]*(\\.[a-z0-9]+)*".toRegex())) { "Invalid id: $id" }
    }

    /** Verify that display text is correctly formatted. */
    @JvmStatic
    fun verifyDisplayTextFormat(name: String) {
      require(name.isNotEmpty() && name[0] != ' ' && name[name.length - 1] != ' ') {
        "Invalid name: $name"
      }
    }

    private fun <T> verifyDefaultValue(
      defaultValue: T,
      stringDefaultValue: String,
      converter: ValueConverter<T>
    ) {
      val deserialized =
        try {
          converter.deserialize(stringDefaultValue)
        } catch (e: Exception) {
          throw IllegalArgumentException("Default value cannot be deserialized.")
        }

      require(deserialized == defaultValue) { "Deserialized value does not match default value." }
    }

    @JvmStatic
    @Deprecated(
      "Replaced by BooleanFlag constructor",
      ReplaceWith(
        "BooleanFlag(group, name, displayName, description, defaultValue)",
        "com.android.flags.BooleanFlag"
      )
    )
    fun create(
      group: FlagGroup,
      name: String,
      displayName: String,
      description: String,
      defaultValue: Boolean
    ) = BooleanFlag(group, name, displayName, description, defaultValue)

    @JvmStatic
    @Deprecated(
      "Replaced by IntFlag constructor",
      ReplaceWith(
        "IntFlag(group, name, displayName, description, defaultValue)",
        "com.android.flags.IntFlag"
      )
    )
    fun create(
      group: FlagGroup,
      name: String,
      displayName: String,
      description: String,
      defaultValue: Int
    ) = IntFlag(group, name, displayName, description, defaultValue)

    @JvmStatic
    @Deprecated(
      "Replaced by LongFlag constructor",
      ReplaceWith(
        "LongFlag(group, name, displayName, description, defaultValue)",
        "com.android.flags.LongFlag"
      )
    )
    fun create(
      group: FlagGroup,
      name: String,
      displayName: String,
      description: String,
      defaultValue: Long
    ) = LongFlag(group, name, displayName, description, defaultValue)

    @JvmStatic
    @Deprecated(
      "Replaced by StringFlag constructor",
      ReplaceWith(
        "StringFlag(group, name, displayName, description, defaultValue)",
        "com.android.flags.StringFlag"
      )
    )
    fun create(
      group: FlagGroup,
      name: String,
      displayName: String,
      description: String,
      defaultValue: String
    ) = StringFlag(group, name, displayName, description, defaultValue)

    @JvmStatic
    @Deprecated(
      "Replaced by EnumFlag constructor",
      ReplaceWith(
        "EnumFlag(group, name, displayName, description, defaultValue)",
        "com.android.flags.EnumFlag"
      )
    )
    fun <T : Enum<T>> create(
      group: FlagGroup,
      name: String,
      displayName: String,
      description: String,
      defaultValue: T
    ) = EnumFlag(group, name, displayName, description, defaultValue)
  }
}

class BooleanFlag(
  group: FlagGroup,
  name: String,
  displayName: String,
  description: String,
  defaultValue: Boolean
) : Flag<Boolean>(group, name, displayName, description, defaultValue, Converter) {

  constructor(
    group: FlagGroup,
    name: String,
    displayName: String,
    description: String,
    defaultValueProvider: Supplier<Boolean>
  ) : this(group, name, displayName, description, defaultValueProvider.get())

  object Converter : ValueConverter<Boolean> {
    override fun serialize(value: Boolean) = value.toString()

    override fun deserialize(strValue: String) = strValue.toBoolean()
  }
}

class IntFlag(
  group: FlagGroup,
  name: String,
  displayName: String,
  description: String,
  defaultValue: Int
) : Flag<Int>(group, name, displayName, description, defaultValue, Converter) {

  constructor(
    group: FlagGroup,
    name: String,
    displayName: String,
    description: String,
    defaultValueProvider: Supplier<Int>
  ) : this(group, name, displayName, description, defaultValueProvider.get())

  object Converter : ValueConverter<Int> {
    override fun serialize(value: Int) = value.toString()

    override fun deserialize(strValue: String) = strValue.toInt()
  }
}

class LongFlag(
  group: FlagGroup,
  name: String,
  displayName: String,
  description: String,
  defaultValue: Long
) : Flag<Long>(group, name, displayName, description, defaultValue, Converter) {

  constructor(
    group: FlagGroup,
    name: String,
    displayName: String,
    description: String,
    defaultValueProvider: Supplier<Long>
  ) : this(group, name, displayName, description, defaultValueProvider.get())

  object Converter : ValueConverter<Long> {
    override fun serialize(value: Long) = value.toString()

    override fun deserialize(strValue: String) = strValue.toLong()
  }
}

class StringFlag(
  group: FlagGroup,
  name: String,
  displayName: String,
  description: String,
  defaultValue: String
) : Flag<String>(group, name, displayName, description, defaultValue, Converter) {

  constructor(
    group: FlagGroup,
    name: String,
    displayName: String,
    description: String,
    defaultValueProvider: Supplier<String>
  ) : this(group, name, displayName, description, defaultValueProvider.get())

  object Converter : ValueConverter<String> {
    override fun serialize(value: String) = value

    override fun deserialize(strValue: String) = strValue
  }
}

class EnumFlag<T : Enum<T>>(
  group: FlagGroup,
  name: String,
  displayName: String,
  description: String,
  defaultValue: T
) :
  Flag<T>(
    group,
    name,
    displayName,
    description,
    defaultValue,
    EnumConverter(defaultValue.javaClass)
  ) {

  constructor(
    group: FlagGroup,
    name: String,
    displayName: String,
    description: String,
    defaultValueProvider: Supplier<T>
  ) : this(group, name, displayName, description, defaultValueProvider.get())

  /**
   * Creates a [ValueConverter] for the given enum class. Values are stored using their names, to
   * make it easier to override them using JVM properties (lower-case names are also recognized).
   *
   * @see Enum#name()
   */
  private class EnumConverter<T : Enum<T>>(private val enumClass: Class<T>) : ValueConverter<T> {
    override fun serialize(value: T) = value.name

    override fun deserialize(strValue: String): T =
      java.lang.Enum.valueOf(enumClass, strValue.uppercase(Locale.US))
  }
}