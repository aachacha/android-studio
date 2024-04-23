/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.test.inspectors.database.room

import androidx.room.Database
import androidx.room.RoomDatabase as AndroidxRoomDatabase

/**
 * A simple Room database that we use to test invalidation code
 *
 * We don't do much with this database except register an invalidation listener
 */
@Database(entities = [RoomUserEntity::class], version = 1, exportSchema = false)
internal abstract class RoomDatabase : AndroidxRoomDatabase()
