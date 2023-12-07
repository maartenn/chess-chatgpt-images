plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.microsoft.playwright:playwright:1.40.0")
    implementation(platform("com.google.inject:guice-bom:7.0.0"))
    implementation("org.testng:testng:7.8.0")
    implementation("commons-codec:commons-codec:1.16.0")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
