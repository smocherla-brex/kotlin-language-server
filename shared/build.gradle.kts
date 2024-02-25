import com.google.protobuf.gradle.id

plugins {
    id("maven-publish")
    kotlin("jvm")
    id("kotlin-language-server.publishing-conventions")
    id("kotlin-language-server.kotlin-conventions")
    id("com.google.protobuf") version "0.9.4"
}

repositories {
    mavenCentral()
}

dependencies {
    // dependencies are constrained to versions defined
    // in /platform/build.gradle.kts
    implementation(platform(project(":platform")))

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.exposed:exposed-core")
    implementation("com.google.protobuf:protobuf-kotlin:3.21.2")
    implementation("com.google.protobuf:protobuf-java-util:3.21.4")
    implementation("org.jetbrains.exposed:exposed-dao")
    testImplementation("org.hamcrest:hamcrest-all")
    testImplementation("junit:junit")
    protobuf(files("src/main/protobuf/"))
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.19.4"
    }

    // Enable Kotlin generation
    generateProtoTasks {
        all().forEach {
            it.builtins {
                id("kotlin")
            }
            it.sourceDirs

        }
    }
}
