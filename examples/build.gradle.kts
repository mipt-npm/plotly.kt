plugins {
    kotlin("jvm")
}

repositories {
    mavenLocal()
    jcenter()
}

dependencies {
    implementation(project(":plotlykt-server"))
    implementation(kotlin("script-runtime"))
    implementation(project(":plotlykt-script"))
    implementation("de.mpicbg.scicomp:krangl:0.13")
    implementation("org.apache.commons:commons-csv:1.8")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

// A workaround for https://youtrack.jetbrains.com/issue/KT-44101

val copyPlotlyResources by tasks.creating(Copy::class){
    dependsOn(":plotlykt-core:jvmProcessResources")
    from(project(":plotlykt-core").buildDir.resolve("processedResources/jvm"))
    dependsOn(":plotlykt-server:jvmProcessResources")
    from(project(":plotlykt-server").buildDir.resolve("processedResources/jvm"))
    into(buildDir.resolve("resources"))
}

tasks.getByName("classes").dependsOn(copyPlotlyResources)

//val runDynamicServer by tasks.creating(JavaExec::class){
//    group = "run"
//    classpath = sourceSets["main"].runtimeClasspath
//    main = "DynamicServerKt"
//}
//
//val runCustomPage by tasks.creating(JavaExec::class){
//    group = "run"
//    classpath = sourceSets["main"].runtimeClasspath
//    main = "CustomPageKt"
//}