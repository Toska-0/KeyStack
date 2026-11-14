import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.awt.Color
import java.awt.LinearGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.java.dev.jna:jna:5.14.0")
}

val generatedWindowsIcon = layout.buildDirectory.file("generated/keystack/keystack.ico")
val generateWindowsIcon by tasks.registering {
    val output = generatedWindowsIcon
    outputs.file(output)
    doLast {
        val size = 256
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = Color.WHITE
        g.fill(RoundRectangle2D.Float(0f, 0f, size.toFloat(), size.toFloat(), 64f, 64f))

        val p = Path2D.Float()
        // Larger mark: fills more of the tile while keeping a clean inset.
        p.moveTo(59.0, 58.0)
        p.lineTo(197.0, 58.0)
        p.lineTo(133.0, 128.0)
        p.lineTo(197.0, 198.0)
        p.lineTo(59.0, 198.0)
        p.closePath()
        val colors = arrayOf(
            Color(0xFF, 0xD3, 0x4E),
            Color(0xFF, 0x78, 0x3E),
            Color(0xEF, 0x16, 0x8B),
            Color(0x8D, 0x18, 0xB8),
            Color(0x26, 0x3C, 0x9B)
        )
        val fractions = floatArrayOf(0f, 0.28f, 0.55f, 0.78f, 1f)
        g.paint = LinearGradientPaint(75f, 74f, 181f, 182f, fractions, colors)
        g.fill(p)
        g.dispose()

        val png = ByteArrayOutputStream().use { stream ->
            ImageIO.write(image, "png", stream)
            stream.toByteArray()
        }
        val file = output.get().asFile
        file.parentFile.mkdirs()
        DataOutputStream(FileOutputStream(file)).use { out ->
            fun short(v: Int) { out.writeByte(v and 0xff); out.writeByte((v shr 8) and 0xff) }
            fun int(v: Int) { out.writeByte(v and 0xff); out.writeByte((v shr 8) and 0xff); out.writeByte((v shr 16) and 0xff); out.writeByte((v shr 24) and 0xff) }
            short(0); short(1); short(1)
            out.writeByte(0); out.writeByte(0); out.writeByte(0); out.writeByte(0)
            short(1); short(32); int(png.size); int(22)
            out.write(png)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.toska.keystack.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "KeyStack"
            packageVersion = "0.1.0"
            description = "KeyStack - chat with AI models"
            vendor = "Toska"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                iconFile = generatedWindowsIcon.get().asFile
                upgradeUuid = "8f7c2a5e-3b1d-4c6a-9e2f-7d5b1a0c4e93"
            }
        }
    }
}

// The icon must exist before both the distributable validation and MSI packaging.
tasks.matching {
    it.name.contains("createDistributable", ignoreCase = true) ||
        it.name.contains("packageMsi", ignoreCase = true)
}.configureEach {
    dependsOn(generateWindowsIcon)
}
