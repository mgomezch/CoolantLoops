import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import javax.imageio.ImageIO

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

val generateProceduralTextures by tasks.registering(Exec::class) {
    group = "build"
    description = "Procedurally generates all custom block textures from pure code (zero third-party asset extraction)"
    commandLine(
        "java",
        file("tools/textures/GenerateTextures.java").absolutePath,
        file("src/main/resources/assets/coolantloops/textures/blocks").absolutePath
    )
    inputs.file(file("tools/textures/GenerateTextures.java"))
    outputs.dir(file("src/main/resources/assets/coolantloops/textures/blocks"))
}

tasks.named("processResources") {
    dependsOn(generateProceduralTextures)
}

tasks.named("sourcesJar") {
    dependsOn(generateProceduralTextures)
}

