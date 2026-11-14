plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation("junit:junit:4.13.2") }
kotlin.compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
tasks.withType<JavaCompile>().configureEach { sourceCompatibility = "17"; targetCompatibility = "17" }
