
import java.io.File
import proguard.gradle.ProGuardTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        classpath("com.github.jengelman.gradle.plugins:shadow:1.2.3")
        classpath("net.sf.proguard:proguard-gradle:5.3.1")
    }
}

// Set to false to disable proguard run on kotlin-compiler.jar. Speeds up the build
val shrink = true
val bootstrapBuild = false

val compilerManifestClassPath =
    if (bootstrapBuild) "kotlin-runtime-internal-bootstrap.jar kotlin-reflect-internal-bootstrap.jar kotlin-script-runtime-internal-bootstrap.jar"
    else "kotlin-runtime.jar kotlin-reflect.jar kotlin-script-runtime.jar"

val ideaSdkCoreCfg = configurations.create("ideaSdk-core")
val otherDepsCfg = configurations.create("other-deps")
val proguardLibraryJarsCfg = configurations.create("library-jars")
val mainCfg = configurations.create("default")
val packedCfg = configurations.create("packed")
val embeddableCfg = configurations.create("embeddable")
//val withBootstrapRuntimeCfg = configurations.create("withBootstrapRuntime")

val outputBeforeSrinkJar = "$buildDir/libs/kotlin-compiler-before-shrink.jar"
val outputJar = rootProject.extra["compilerJar"].toString()
val outputEmbeddableJar = rootProject.extra["embeddableCompilerJar"].toString()
//val outputJarWithBootstrapRuntime = rootProject.extra["compilerJarWithBootstrapRuntime"].toString()

val kotlinEmbeddableRootPackage = "org.jetbrains.kotlin"

artifacts.add(mainCfg.name, file(outputJar))
artifacts.add(embeddableCfg.name, file(outputEmbeddableJar))

val javaHome = System.getProperty("java.home")

val compilerProject = project(":compiler")

dependencies {
    ideaSdkCoreCfg(ideaSdkCoreDeps(*(rootProject.extra["ideaCoreSdkJars"] as Array<String>)))
    ideaSdkCoreCfg(ideaSdkDeps("jna-platform", "oromatcher"))
    ideaSdkCoreCfg(ideaSdkDeps("jps-model.jar", subdir = "jps"))
    otherDepsCfg(commonDep("javax.inject"))
    otherDepsCfg(commonDep("jline"))
    otherDepsCfg(protobufFull())
    otherDepsCfg(commonDep("com.github.spullara.cli-parser", "cli-parser"))
    otherDepsCfg(commonDep("com.google.code.findbugs", "jsr305"))
    otherDepsCfg(commonDep("io.javaslang","javaslang"))
    otherDepsCfg(preloadedDeps("json-org"))
    buildVersion()
    proguardLibraryJarsCfg(files("$javaHome/lib/rt.jar".takeIf { File(it).exists() } ?: "$javaHome/../Classes/classes.jar",
                                 "$javaHome/lib/jsse.jar".takeIf { File(it).exists() } ?: "$javaHome/../Classes/jsse.jar"))
    proguardLibraryJarsCfg(kotlinDep("stdlib"))
    proguardLibraryJarsCfg(kotlinDep("script-runtime"))
    proguardLibraryJarsCfg(kotlinDep("reflect"))
    proguardLibraryJarsCfg(files("${System.getProperty("java.home")}/../lib/tools.jar"))
//    proguardLibraryJarsCfg(project(":prepare:runtime", configuration = "default").apply { isTransitive = false })
//    proguardLibraryJarsCfg(project(":prepare:reflect", configuration = "default").apply { isTransitive = false })
//    proguardLibraryJarsCfg(project(":core:script.runtime").apply { isTransitive = false })
//    embeddableCfg(project(":prepare:runtime", configuration = "default"))
//    embeddableCfg(project(":prepare:reflect", configuration = "default"))
//    embeddableCfg(projectDepIntransitive(":core:script.runtime"))
    embeddableCfg(projectDepIntransitive(":build-common"))
//    embeddableCfg(projectDepIntransitive(":kotlin-test:kotlin-test-jvm"))
//    embeddableCfg(projectDepIntransitive(":kotlin-stdlib"))
//    withBootstrapRuntimeCfg(kotlinDep("stdlib"))
//    withBootstrapRuntimeCfg(kotlinDep("script-runtime"))
//    withBootstrapRuntimeCfg(kotlinDep("reflect"))
}

val packCompilerTask = task<ShadowJar>("internal.pack-compiler") {
    configurations = listOf(mainCfg)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveName = if (shrink) outputBeforeSrinkJar else outputJar
    dependsOn(protobufFullTask)
    setupRuntimeJar("Kotlin Compiler")
    (rootProject.extra["compilerModules"] as Array<String>).forEach {
        dependsOn("$it:classes")
        from(project(it).getCompiledClasses())
    }
    from(ideaSdkCoreCfg.files)
    from(otherDepsCfg.files)
    from(project(":core:builtins").getResourceFiles()) { include("kotlin/**") }

    manifest.attributes.put("Class-Path", compilerManifestClassPath)
    manifest.attributes.put("Main-Class", "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
}

val proguardTask = task<ProGuardTask>("internal.proguard-compiler") {
    dependsOn(packCompilerTask)
    configuration("$rootDir/compiler/compiler.pro")

    inputs.files(outputBeforeSrinkJar)
    outputs.file(outputJar)

    doFirst {
        System.setProperty("kotlin-compiler-jar-before-shrink", outputBeforeSrinkJar.toString())
        System.setProperty("kotlin-compiler-jar", outputJar.toString())
    }

    proguardLibraryJarsCfg.files.forEach { jar ->
        libraryjars(jar)
    }
    printconfiguration("$buildDir/compiler.pro.dump")
}

val mainTask = task("prepare") {
    dependsOn(if (shrink) proguardTask else packCompilerTask)
}

val embeddableTask = task<ShadowJar>("prepare-embeddable-compiler") {
    archiveName = outputEmbeddableJar
    configurations = listOf(embeddableCfg)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(mainTask, ":build-common:assemble", ":core:script.runtime:assemble") //, ":kotlin-test:kotlin-test-jvm:assemble", ":kotlin-stdlib:assemble")
    from(files(outputJar))
    from(embeddableCfg.files)
    relocate("com.google.protobuf", "org.jetbrains.kotlin.protobuf" )
    relocate("com.intellij", "$kotlinEmbeddableRootPackage.com.intellij")
    relocate("com.google", "$kotlinEmbeddableRootPackage.com.google")
    relocate("com.sampullara", "$kotlinEmbeddableRootPackage.com.sampullara")
    relocate("org.apache", "$kotlinEmbeddableRootPackage.org.apache")
    relocate("org.jdom", "$kotlinEmbeddableRootPackage.org.jdom")
    relocate("org.fusesource", "$kotlinEmbeddableRootPackage.org.fusesource") {
        // TODO: remove "it." after #KT-12848 get addressed
        exclude("org.fusesource.jansi.internal.CLibrary")
    }
    relocate("org.picocontainer", "$kotlinEmbeddableRootPackage.org.picocontainer")
    relocate("jline", "$kotlinEmbeddableRootPackage.jline")
    relocate("gnu", "$kotlinEmbeddableRootPackage.gnu")
    relocate("javax.inject", "$kotlinEmbeddableRootPackage.javax.inject")
}

defaultTasks(mainTask.name, embeddableTask.name)

artifacts.add(mainCfg.name, File(outputJar))
artifacts.add(packedCfg.name, File(if (shrink) outputJar else outputBeforeSrinkJar))

