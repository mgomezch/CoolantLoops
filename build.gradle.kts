import java.awt.Color
import java.awt.image.BufferedImage
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

tasks.register("composeInstrumentTextures") {
    doLast {
        val gtIconDir = file("../GT5-Unofficial/src/main/resources/assets/gregtech/textures/blocks/iconsets")
        val steelBaseFile = File(gtIconDir, "BLOCK_STEELPREIN.png")
        val screenOverlayFile = File(gtIconDir, "OVERLAY_SCREEN.png")
        val screenMetaFile = File(gtIconDir, "OVERLAY_SCREEN.png.mcmeta")
        val redstoneOverlayFile = File(gtIconDir, "OVERLAY_REDSTONE_TRANSMITTER.png")

        val outDir = file("src/main/resources/assets/coolantloops/textures/blocks")
        outDir.mkdirs()

        // 1. instrument_side.png
        Files.copy(steelBaseFile.toPath(), File(outDir, "instrument_side.png").toPath(), StandardCopyOption.REPLACE_EXISTING)

        // 2. instrument_top.png
        val baseImg = ImageIO.read(steelBaseFile)
        val screenImg = ImageIO.read(screenOverlayFile)
        val topImg = BufferedImage(screenImg.width, screenImg.height, BufferedImage.TYPE_INT_ARGB)
        val gTop = topImg.createGraphics()
        val frameCount = screenImg.height / baseImg.height
        for (i in 0 until frameCount) {
            gTop.drawImage(baseImg, 0, i * baseImg.height, null)
        }
        gTop.drawImage(screenImg, 0, 0, null)
        gTop.dispose()
        ImageIO.write(topImg, "PNG", File(outDir, "instrument_top.png"))
        if (screenMetaFile.exists()) {
            Files.copy(screenMetaFile.toPath(), File(outDir, "instrument_top.png.mcmeta").toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        // 3. instrument_front.png
        val frontImg = BufferedImage(baseImg.width, baseImg.height, BufferedImage.TYPE_INT_ARGB)
        val gFront = frontImg.createGraphics()
        gFront.drawImage(baseImg, 0, 0, null)
        val redstoneImg = ImageIO.read(redstoneOverlayFile)
        gFront.drawImage(redstoneImg, 0, 0, null)
        gFront.color = Color(160, 0, 0)
        gFront.fillOval(5, 5, 6, 6)
        gFront.color = Color(255, 30, 30)
        gFront.fillOval(6, 6, 4, 4)
        gFront.color = Color(255, 180, 180)
        gFront.fillRect(7, 7, 1, 1)
        gFront.dispose()
        ImageIO.write(frontImg, "PNG", File(outDir, "instrument_front.png"))

        // 4. manifold.png (GregTech / IC2 mining pipe tip fullblock texture)
        val ic2Jar = configurations.named("compileClasspath").get().files.find { it.name.startsWith("ic2") || it.name.contains("industrialcraft") }
        if (ic2Jar != null) {
            ZipFile(ic2Jar).use { zip ->
                val entry = zip.getEntry("assets/ic2/textures/blocks/machine/blockMiningTip.png")
                if (entry != null) {
                    zip.getInputStream(entry).use { input ->
                        File(outDir, "manifold.png").outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }

        // 5. creative_heat_reservoir.png (Heat Proof Casing + Heat Exchanger overlay)
        val heatCasingFile = File(gtIconDir, "MACHINE_HEATPROOFCASING.png")
        val heatExchangerOverlay = File(gtIconDir, "OVERLAY_FRONT_HEAT_EXCHANGER.png")
        if (heatCasingFile.exists()) {
            val baseHeatImg = ImageIO.read(heatCasingFile)
            val resImg = BufferedImage(baseHeatImg.width, baseHeatImg.height, BufferedImage.TYPE_INT_ARGB)
            val gRes = resImg.createGraphics()
            gRes.drawImage(baseHeatImg, 0, 0, null)
            if (heatExchangerOverlay.exists()) {
                val overlayImg = ImageIO.read(heatExchangerOverlay)
                gRes.drawImage(overlayImg, 0, 0, null)
            }
            gRes.dispose()
            ImageIO.write(resImg, "PNG", File(outDir, "creative_heat_reservoir.png"))
        }

        // 6. pipe_insulation.png: 16x16 seamless subtle thermal insulation cladding overlay
        val insImg = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                // Seamless periodic noise with subtle thermal weave pattern
                val weaveX = if (x % 4 == 0) 10 else 0
                val weaveY = if (y % 4 == 0) 10 else 0
                val noise = ((Math.sin(x * Math.PI / 8.0) * Math.cos(y * Math.PI / 8.0) * 8.0) +
                             (Math.sin(x * Math.PI / 4.0) * Math.sin(y * Math.PI / 4.0) * 5.0)).toInt()
                val base = 212 + weaveX + weaveY + noise
                val r = Math.max(0, Math.min(255, base - 6))
                val g = Math.max(0, Math.min(255, base))
                val b = Math.max(0, Math.min(255, base + 8))
                val a = 190 // Semi-transparent overlay letting base pipe metal texture subtly show through
                val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                insImg.setRGB(x, y, argb)
            }
        }
        ImageIO.write(insImg, "PNG", File(outDir, "pipe_insulation.png"))
    }
}

tasks.named("processResources") {
    dependsOn("composeInstrumentTextures")
}
