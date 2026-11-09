// Package the attended demo using Gradle's existing Jar/Sync tasks, without a new plugin or loader.
val api = project(":apps:shipping-api")
val demoLibraries = tasks.register<Sync>("demoLibraries") {
    from(api.configurations.named("runtimeClasspath"))
    into(api.layout.buildDirectory.dir("demo/lib"))
}
tasks.register<Jar>("bootJar") {
    dependsOn(demoLibraries)
    from(api.extensions.getByType<SourceSetContainer>().named("main").map { it.output })
    archiveFileName = "shipping-demo.jar"
    destinationDirectory = api.layout.buildDirectory.dir("demo")
    val runtime = api.configurations.named("runtimeClasspath")
    inputs.files(runtime).withPropertyName("demoRuntime")
    manifest {
        attributes("Main-Class" to "io.github.stigjohnny.shipping.ShippingApplication",
            "Class-Path" to runtime.get().files.sortedBy { it.name }.joinToString(" ") { "lib/${it.name}" })
    }
}
