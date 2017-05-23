/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.launcher.daemon.bootstrap;

import org.gradle.launcher.bootstrap.ProcessBootstrap;
import org.gradle.util.GUtil;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

public class GradleDaemon {
    public static void main(String[] args) {
        assertXmx();
        new ProcessBootstrap().run("org.gradle.launcher.daemon.bootstrap.DaemonMain", args);
    }

    private static void assertXmx() {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMxBean.getInputArguments();
        for (String arg :arguments) {
            if (arg.startsWith("-Xmx1024")) {
                throw new RuntimeException("-Xmx set to daemon default (-Xmx1024): "
                    + GradleDaemon.class.getSimpleName()
                    + "\n" + GUtil.toString(runtimeMxBean.getInputArguments()));
            }
            if (arg.startsWith("-Xmx")) {
                return;
            }
        }
        throw new RuntimeException("-Xmx no defined for: "
            + GradleDaemon.class.getSimpleName()
            + "\n" + runtimeMxBean.getInputArguments());
    }
}
