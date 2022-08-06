/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class SitesGenerator {

    private static final String LOC_JAVA = "loc_java";
    private static final String LOC_C = "loc_c";
    private static final String LOC_H = "loc_h";

    private static final List<Function> functions = new ArrayList<>();

    static {
        functions.add(
                new Function(
                        "AppData",
                        "\"/data/data/\" + pkg + \"/\"",
                        "String pkg",
                        "const std::string pkg"));
        functions.add(
                new Function(
                        "AppCodeCache",
                        "AppData(pkg) + \"code_cache/\"",
                        "String pkg",
                        "const std::string pkg"));
        functions.add(
                new Function(
                        "AppStudio",
                        "AppCodeCache(pkg) + \".studio/\"",
                        "String pkg",
                        "const std::string pkg"));
        functions.add(
                new Function(
                        "AppLog",
                        "AppData(pkg) + \".agent-logs/\"",
                        "String pkg",
                        "const std::string pkg"));
        functions.add(
                new Function(
                        "AppStartupAgent",
                        "AppCodeCache(pkg) + \"startup_agents/\"",
                        "String pkg",
                        "const std::string pkg"));
        // TODO: Change name to AppOverlay (no 's' at the end).
        functions.add(
                new Function(
                        "AppOverlays",
                        "AppCodeCache(pkg) + \".overlay/\"",
                        "String pkg",
                        "const std::string pkg"));
        functions.add(
                new Function(
                        "AppLiveLiteral",
                        "AppCodeCache(pkg) + \".ll/\"",
                        "String pkg",
                        "const std::string pkg"));

        functions.add(new Function("DeviceStudioFolder", qString("/data/local/tmp/.studio/")));
        functions.add(new Function("InstallerExecutableFolder", "DeviceStudioFolder() + \"bin/\""));
        functions.add(new Function("InstallerTmpFolder", "DeviceStudioFolder() + \"tmp/\""));
        functions.add(new Function("InstallerBinary", qString("installer")));
        functions.add(
                new Function("InstallerPath", "InstallerExecutableFolder() + InstallerBinary()"));
    }

    private static String qString(String string) {
        return '"' + string + '"';
    }

    private static void print(String path, String code) throws FileNotFoundException {
        try (PrintWriter writer = new PrintWriter(path)) {
            writer.print(code);
        }
    }

    // Transform function name to java style (lower-case first letter).
    private static String jFunc(String func) {
        return func.substring(0, 1).toLowerCase() + func.substring(1);
    }

    private static void issueWarning(StringBuilder code) {
        code.append("// This file is automatically generated. Changes will be\n");
        code.append("// overwritten.\n");
    }

    private static String createJavaCode() {
        StringBuilder code = new StringBuilder();
        issueWarning(code);
        code.append("package com.android.tools.deployer;\n");
        code.append("public class Sites {\n");

        for (Function func : functions) {
            code.append("public static String " + func.name + "(" + func.javaSignature + ") {\n");
            code.append(" return ");
            code.append(func.code);
            code.append(";}\n");
        }

        code.append("}\n");

        String src = code.toString();
        // Fix function names and callsites
        for (Function func : functions) {
            String funcName = func.name;
            src = src.replace(funcName + "(", jFunc(funcName) + "(");
        }
        return src;
    }

    private static String createCppHeaderCode() {
        StringBuilder code = new StringBuilder();
        issueWarning(code);
        code.append("#ifndef SITE_H\n");
        code.append("#define SITE_H\n");
        code.append("#include <string>\n");

        code.append("namespace deploy {\n");
        code.append("namespace Sites {\n");

        for (Function func : functions) {
            code.append("std::string " + func.name + "(" + func.cppSignature + ");\n");
        }

        code.append("} // namespace Sites\n");
        code.append("} // namespace deploy\n");
        code.append("#endif\n");
        return code.toString();
    }

    private static String createCppBodyCode() {
        StringBuilder code = new StringBuilder();
        issueWarning(code);
        code.append("#include <string>\n");
        code.append("namespace deploy {\n");
        code.append("namespace Sites {\n");

        for (Function func : functions) {
            code.append("std::string " + func.name + "(" + func.cppSignature + "){\n");
            code.append(" return ");
            code.append(func.code);
            code.append(";}\n");
        }

        code.append("} // namespace Sites\n");
        code.append("} // namespace deploy\n");
        return code.toString();
    }

    private static HashMap parseArgs(String[] args) {
        HashMap<String, String> parameters = new HashMap();
        for (String arg : args) {
            String[] tokens = arg.split("=");
            if (tokens.length != 2) {
                String msg = "Option '" + arg + "' is malformed (key=value) expected";
                throw new IllegalArgumentException(msg);
            }
            String key = tokens[0];
            String value = tokens[1];
            parameters.put(key, value);
        }
        return parameters;
    }

    private static void checkPaths(Set<String> set) {
        String[] mandatories = {LOC_JAVA, LOC_C, LOC_H};
        for (String mandatory : mandatories) {
            if (!set.contains(mandatory)) {
                String msg = "Missing mandatory parameter '" + mandatory + "'";
                throw new IllegalArgumentException(msg);
            }
        }
    }

    public static void main(String[] args) throws FileNotFoundException {
        HashMap<String, String> paths = parseArgs(args);
        checkPaths(paths.keySet());
        print(paths.get(LOC_JAVA), createJavaCode());
        print(paths.get(LOC_H), createCppHeaderCode());
        print(paths.get(LOC_C), createCppBodyCode());
    }
}
