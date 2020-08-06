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
package com.android.build.gradle.external.gnumake

import com.android.SdkConstants
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue
import com.android.build.gradle.truth.NativeBuildConfigValueSubject
import com.google.common.truth.Truth
import com.google.gson.Gson
import org.junit.Test
import java.io.File
import java.util.Arrays

class NativeBuildConfigValueBuilderTest {
    @Test
    fun doubleTarget() {
        assertThatNativeBuildConfigEquals(
            """
            g++ -c a.c -o x86_64/a.o
            g++ x86_64/a.o -o x86_64/a.so
            g++ -c a.c -o x86/a.o
            g++ x86/a.o -o x86/a.so
            """.trimIndent(),
            """
            {
              "buildFiles": [
                {
                  "path": "/projects/MyProject/jni/Android.mk"
                }
              ],
              "cleanCommandsComponents": [
                ["echo", "clean", "command"]
              ],  
              "buildTargetsCommandComponents": ["echo", "build", "command", "{LIST_OF_TARGETS_TO_BUILD}"],
              "libraries": {
                "a-debug-x86_64": {
                  "abi" : "x86_64",      
                  "artifactName" : "a",
                  "buildCommandComponents": ["echo", "build", "command", "x86_64/a.so"],
                  "toolchain": "toolchain-x86_64",
                  "files": [
                    {
                      "src": {
                        "path": "/projects/MyProject/jni/a.c"
                      },
                      "flags": ""
                    }
                  ],
                  "output": {
                    "path": "x86_64/a.so"
                  }
                },
                "a-debug-x86": {
                  "abi" : "x86",
                  "artifactName" : "a",
                  "buildCommandComponents": ["echo", "build", "command", "x86/a.so"],
                  "toolchain": "toolchain-x86",
                  "files": [
                    {
                      "src": {
                        "path": "/projects/MyProject/jni/a.c"
                      },
                      "flags": ""
                    }
                  ],
                  "output": {
                    "path": "x86/a.so"
                  }
                }
              },
              "toolchains": {
                "toolchain-x86": {
                  "cCompilerExecutable": {
                    "path": "g++"
                  }
                },
                "toolchain-x86_64": {
                  "cCompilerExecutable": {
                    "path": "g++"
                  }
                }
              },
              "cFileExtensions": [
                "c"
              ],
              "cppFileExtensions": []
            }""".trimIndent()
        )
    }

    @Test
    fun includeInSource() {
        assertThatNativeBuildConfigEquals(
            "g++ -c a.c -o x/aa.o -Isome-include-path\n",
            """{
              "buildFiles": [
                {
                  "path": "/projects/MyProject/jni/Android.mk"
                }
              ],
              "cleanCommandsComponents": [
                ["echo", "clean", "command"]
              ],
              "buildTargetsCommandComponents": ["echo", "build", "command", "{LIST_OF_TARGETS_TO_BUILD}"],
              "libraries": {
                "aa-debug-x": {
                  "buildCommandComponents": ["echo", "build", "command", "x/aa.o"],
                  "toolchain": "toolchain-x",
                  "abi": "x",
                  "artifactName" : "aa",      "files": [
                    {
                      "src": {
                        "path": "/projects/MyProject/jni/a.c"
                      },
                      "flags": "-Isome-include-path"
                    }
                  ],
                  "output": {
                    "path": "x/aa.o"
                  }
                }
              },
              "toolchains": {
                "toolchain-x": {
                  "cCompilerExecutable": {
                    "path": "g++"
                  }
                }
              },
              "cFileExtensions": [
                "c"
              ],
              "cppFileExtensions": []
            }"""
        )
    }

    @Test
    fun weirdExtension1() {
        assertThatNativeBuildConfigEquals(
            """
                g++ -c a.c -o x86_64/aa.o
                g++ -c a.S -o x86_64/aS.so
                g++ x86_64/aa.o x86_64/aS.so -o x86/a.so
                """.trimIndent(),
            """{
              "buildFiles": [
                {
                  "path": "/projects/MyProject/jni/Android.mk"
                }
              ],
              "cleanCommandsComponents": [
                ["echo", "clean", "command"]
              ],  
              "buildTargetsCommandComponents": ["echo", "build", "command", "{LIST_OF_TARGETS_TO_BUILD}"],
              "libraries": {
                "a-debug-x86": {
                  "abi" : "x86",
                  "artifactName" : "a",
                  "buildCommandComponents": ["echo", "build", "command", "x86/a.so"],
                  "toolchain": "toolchain-x86",
                  "files": [
                    {
                      "src": {
                        "path": "/projects/MyProject/jni/a.S"
                      },
                      "flags": ""
                    },
                    {
                      "src": {
                        "path": "/projects/MyProject/jni/a.c"
                      },
                      "flags": ""
                    }
                  ],
                  "output": {
                    "path": "x86/a.so"
                  }
                }
              },
              "toolchains": {
                "toolchain-x86": {
                  "cCompilerExecutable": {
                    "path": "g++"
                  }
                }
              },
              "cFileExtensions": [
                "S",
                "c"
              ],
              "cppFileExtensions": []
            }"""
        )
    }

    @Test
    fun weirdExtension2() {
        assertThatNativeBuildConfigEquals(
            """
                g++ -c a.S -o x86_64/aS.so
                g++ -c a.c -o x86_64/aa.o
                g++ x86_64/aa.o x86_64/aS.so -o x86/a.so
                """.trimIndent(),
            """{
              "buildFiles": [
                {
                  "path": "/projects/MyProject/jni/Android.mk"
                }
              ],
              "cleanCommandsComponents": [
                ["echo", "clean", "command"]
              ],
              "buildTargetsCommandComponents": ["echo", "build", "command", "{LIST_OF_TARGETS_TO_BUILD}"],
              "libraries": {
                "a-debug-x86": {
                  "abi" : "x86",
                  "artifactName" : "a",      
                  "buildCommandComponents": ["echo", "build", "command", "x86/a.so"],
                  "toolchain": "toolchain-x86",
                  "files": [
                    {
                      "src": {
                        "path": "/projects/MyProject/jni/a.S"
                      },
                      "flags": ""
                    },
                    {
                      "src": {
                        "path": "/projects/MyProject/jni/a.c"
                      },
                      "flags": ""
                    }
                  ],
                  "output": {
                    "path": "x86/a.so"
                  }
                }
              },
              "toolchains": {
                "toolchain-x86": {
                  "cCompilerExecutable": {
                    "path": "g++"
                  }
                }
              },
              "cFileExtensions": [
                "c",
                "S"
              ],
              "cppFileExtensions": []
            }"""
        )
    }

    companion object {
        private fun assertThatNativeBuildConfigEquals(
            commands: String, expected: String
        ) {
            var expected = expected
            val projectPath = File("/projects/MyProject/jni/Android.mk")
            val actualValue =
                NativeBuildConfigValueBuilder(projectPath, projectPath.parentFile)
                    .setCommands(
                        Arrays.asList("echo", "build", "command"),
                        Arrays.asList("echo", "clean", "command"),
                        "debug",
                        commands
                    )
                    .build()
            if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
                expected = expected.replace("/", "\\\\")
            }
            val expectedValue = Gson()
                .fromJson(expected, NativeBuildConfigValue::class.java)
            Truth.assertAbout(
                NativeBuildConfigValueSubject.nativebuildConfigValues()
            )
                .that(actualValue)
                .isEqualTo(expectedValue)
        }
    }
}