/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.tools.appinspection.database.testing

import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.CellValue
import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.CellValue.OneOfCase
import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.Command
import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.GetSchemaCommand
import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.GetSchemaResponse
import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.KeepDatabasesOpenCommand
import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.KeepDatabasesOpenResponse
import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.QueryCommand
import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.QueryParameterValue
import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.Response
import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.TrackDatabasesCommand
import com.android.tools.appinspection.database.proto.DatabaseInspectorProtocol.TrackDatabasesResponse

val CellValue.value: Any?
  get() = valueType.first
val CellValue.type: String
  get() = valueType.second
val CellValue.valueType: Pair<Any?, String>
  get() =
    when (oneOfCase) {
      OneOfCase.STRING_VALUE -> stringValue to "text"
      OneOfCase.LONG_VALUE -> longValue to "integer"
      OneOfCase.DOUBLE_VALUE -> doubleValue to "float"
      OneOfCase.BLOB_VALUE -> blobValue.toByteArray().toTypedArray() to "blob"
      OneOfCase.ONEOF_NOT_SET -> null to "null"
      else -> throw IllegalArgumentException()
    }

fun GetSchemaResponse.toTableList(): List<Table> =
  tablesList.map { t -> Table(t.name, t.columnsList.map { c -> Column(c.name, c.type) }) }

object MessageFactory {
  fun createTrackDatabasesCommand(): Command =
    Command.newBuilder().setTrackDatabases(TrackDatabasesCommand.getDefaultInstance()).build()

  fun createTrackDatabasesResponse(): Response =
    Response.newBuilder().setTrackDatabases(TrackDatabasesResponse.getDefaultInstance()).build()

  fun createKeepDatabasesOpenCommand(setEnabled: Boolean): Command =
    Command.newBuilder()
      .setKeepDatabasesOpen(KeepDatabasesOpenCommand.newBuilder().setSetEnabled(setEnabled))
      .build()

  fun createKeepDatabasesOpenResponse(): Response =
    Response.newBuilder()
      .setKeepDatabasesOpen(KeepDatabasesOpenResponse.getDefaultInstance())
      .build()

  fun createGetSchemaCommand(databaseId: Int): Command =
    Command.newBuilder()
      .setGetSchema(GetSchemaCommand.newBuilder().setDatabaseId(databaseId).build())
      .build()

  fun createQueryCommand(
    databaseId: Int,
    query: String,
    queryParams: List<String?>? = null,
    responseSizeLimitHint: Long? = null,
  ): Command =
    Command.newBuilder()
      .setQuery(
        QueryCommand.newBuilder()
          .setDatabaseId(databaseId)
          .setQuery(query)
          .also { queryCommandBuilder ->
            if (queryParams != null)
              queryCommandBuilder.addAllQueryParameterValues(
                queryParams.map { param ->
                  QueryParameterValue.newBuilder()
                    .also { builder -> if (param != null) builder.stringValue = param }
                    .build()
                }
              )
            if (responseSizeLimitHint != null) {
              queryCommandBuilder.responseSizeLimitHint = responseSizeLimitHint
            }
          }
          .build()
      )
      .build()
}
