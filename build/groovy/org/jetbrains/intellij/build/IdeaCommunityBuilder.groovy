/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtil
import org.codehaus.gant.GantBinding
import org.jetbrains.jps.gant.LayoutInfo
/**
 * @author nik
 */
class IdeaCommunityBuilder {
  private final GantBinding binding
  private final BuildContext buildContext

  IdeaCommunityBuilder(String home, GantBinding binding, BuildOptions options = new BuildOptions()) {
    this.binding = binding
    buildContext = BuildContext.createContext(binding.ant, binding.projectBuilder, binding.project, binding.global, home, home,
                                              new IdeaCommunityProperties(home), ProprietaryBuildTools.DUMMY,
                                              options)
  }

  IdeaCommunityBuilder(GantBinding binding, BuildContext buildContext) {
    this.binding = binding
    this.buildContext = buildContext
  }

  void compileModules() {
    BuildTasks.create(buildContext).compileProjectAndTests(["jps-builders"])
  }

  void buildDistJars() {
    def tasks = BuildTasks.create(buildContext)
    compileModules()
    tasks.buildSearchableOptions("resources-en", ["community-main"], [])
    layoutAll()
  }

  void buildDistributions() {
    def tasks = BuildTasks.create(buildContext)
    compileModules()
    tasks.buildSearchableOptions("resources-en", ["community-main"], [])

    layoutAll(true)
    tasks.buildUpdaterJar()
  }

  LayoutInfo layoutAll(boolean buildJps = false) {
    def layouts = binding["includeFile"]("$buildContext.paths.communityHome/build/scripts/layouts.gant")
    LayoutInfo info = layouts.layoutFull(buildContext)
    FileUtil.delete(new File(buildContext.paths.distAll, "lib/libpty"))//todo[nik] this is temporary workaround until IDEA fully migrates to the new scheme
    buildContext.messages.block("Build intellij-core") {
      String coreArtifactDir = "$buildContext.paths.artifacts/core"
      buildContext.ant.mkdir(dir: coreArtifactDir)
      layouts.layout_core(buildContext.paths.communityHome, coreArtifactDir)
      buildContext.notifyArtifactBuilt(coreArtifactDir)

      def intellijCoreZip = "${buildContext.paths.artifacts}/intellij-core-${buildContext.buildNumber}.zip"
      buildContext.ant.zip(destfile: intellijCoreZip) {
        fileset(dir: coreArtifactDir)
      }
      buildContext.notifyArtifactBuilt(intellijCoreZip)
    }
    if (buildJps) {
      buildContext.messages.block("Build standalone JPS") {
        String jpsArtifactDir = "$buildContext.paths.artifacts/jps"
        layouts.layoutJps(buildContext.paths.communityHome, jpsArtifactDir, buildContext.fullBuildNumber, {})
        buildContext.notifyArtifactBuilt(jpsArtifactDir)
      }
    }

    BuildTasks.create(buildContext).buildDistributions()
    return info
  }
}