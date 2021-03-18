plugins {
    id("ru.mipt.npm.gradle.mpp")
    kotlin("jupyter.api")
}

kscience{
    publish()
}

val dataforgeVersion: String by rootProject.extra

kotlin {
    js{
        browser {
            webpackTask {
                this.outputFileName = "js/plotlykt.js"
            }
        }
        binaries.executable()
    }

    afterEvaluate {
        val jsBrowserDistribution by tasks.getting

        tasks.getByName<ProcessResources>("jvmProcessResources") {
            dependsOn(jsBrowserDistribution)
            afterEvaluate {
                from(jsBrowserDistribution)
            }
        }
    }

    sourceSets {
        commonMain{
            dependencies{
                api(project(":plotlykt-core"))
            }
        }
        jvmMain {
            dependencies {
                api("io.ktor:ktor-server-cio:${ru.mipt.npm.gradle.KScienceVersions.ktorVersion}")
                //api("io.ktor:ktor-server-netty:$ktorVersion")
                api("io.ktor:ktor-html-builder:${ru.mipt.npm.gradle.KScienceVersions.ktorVersion}")
                api("io.ktor:ktor-websockets:${ru.mipt.npm.gradle.KScienceVersions.ktorVersion}")
                api("space.kscience:dataforge-context:$dataforgeVersion") {
                    exclude(module = "kotlinx-io")
                }
            }
        }
    }
}