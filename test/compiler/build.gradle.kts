
apply { plugin("kotlin") }

dependencies {
    testCompile(project(":kotlin-test:kotlin-test-jvm"))
    testCompile(project(":compiler.tests-common"))
    testCompileOnly(project(":compiler:ir.ir2cfg"))
    testCompileOnly(project(":compiler:ir.tree")) // used for deepCopyWithSymbols call that is removed by proguard from the compiler TODO: make it more straightforward
    testCompile(ideaSdkDeps("openapi", "idea", "util", "asm-all", "commons-httpclient-3.1-patched"))
    // deps below are test runtime deps, but made test compile to split compilation and running to reduce mem req
    testCompile(project(":kotlin-stdlib"))
    testCompile(project(":kotlin-script-runtime"))
    testCompile(project(":kotlin-runtime"))
    testCompile(project(":kotlin-reflect"))
    testCompile(project(":plugins:android-extensions-compiler"))
    testCompile(project(":ant"))
    (rootProject.extra["compilerModules"] as Array<String>).forEach {
        testCompile(project(it))
    }
    testRuntime(ideaSdkCoreDeps("*.jar"))
    testRuntime(ideaSdkDeps("*.jar"))
    testCompile(project(":prepare:compiler", configuration = "default"))
}

configureKotlinProjectSources()
configureKotlinProjectTests("compiler/tests", sourcesBaseDir = rootDir)

tasks.withType<Test> {
    dependsOnTaskIfExistsRec("dist", project = rootProject)
    dependsOn(":prepare:mock-runtime-for-test:dist")
    dependsOn(":prepare:compiler:prepare")
    workingDir = rootDir
    systemProperty("idea.is.unit.test", "true")
    systemProperty("NO_FS_ROOTS_ACCESS_CHECK", "true")
    systemProperty("kotlin.test.script.classpath", the<JavaPluginConvention>().sourceSets.getByName("test").output.classesDirs.joinToString(File.pathSeparator))
    jvmArgs("-ea", "-XX:+HeapDumpOnOutOfMemoryError", "-Xmx1200m", "-XX:+UseCodeCacheFlushing", "-XX:ReservedCodeCacheSize=128m", "-Djna.nosys=true")
    maxHeapSize = "1200m"
    ignoreFailures = true
}

fixKotlinTaskDependencies()
