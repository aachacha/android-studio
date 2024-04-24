// Top-level build file where you can add configuration options common to all sub-projects/modules.
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.kotlin) apply false
  alias(libs.plugins.kotlin.kapt) apply false
  alias(libs.plugins.kotlinAndroid) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.protobuf) apply false
  alias(libs.plugins.sqldelight) apply false
}
true // Needed to make the Suppress annotation work for the plugins block
