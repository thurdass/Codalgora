import org.gradle.internal.os.OperatingSystem
plugins { application }
val hostOs = OperatingSystem.current()
val javafxPlatform = if (hostOs.isWindows) "win" else if (hostOs.isMacOsX) "mac" else "linux"
val javafxVersion = "21.0.13"
dependencies {
    implementation(project(":interpreter"))
    implementation("org.fxmisc.richtext:richtextfx:0.11.7")
    implementation("com.google.code.gson:gson:2.11.0")
    for (module in listOf("base", "graphics", "controls")) {
        implementation("org.openjfx:javafx-$module:$javafxVersion:$javafxPlatform")
    }
}
application { mainClass.set("io.codalgora.desktop.CodalgoraLauncher") }
tasks.named<JavaExec>("run") { jvmArgs("-Dfile.encoding=UTF-8"); workingDir(rootProject.projectDir) }
val packageInput = tasks.register<Sync>("preparePackageInput") {
    dependsOn(tasks.jar)
    into(layout.buildDirectory.dir("jpackage/input"))
    from(tasks.jar)
    from(configurations.runtimeClasspath)
}
val jpackageExecutable = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}.map { it.metadata.installationPath.file("bin/jpackage${if (hostOs.isWindows) ".exe" else ""}").asFile.absolutePath }
val applicationIcon = layout.projectDirectory.file(
    "src/main/resources/io/codalgora/desktop/icons/codalgora.${if (hostOs.isWindows) "ico" else "png"}"
)
val appImageDirectory = layout.buildDirectory.dir("jpackage/image")
val appImage = tasks.register<Exec>("jpackageAppImage") {
    group = "distribution"
    description = "Create a self-contained Java 21 desktop application with jpackage"
    dependsOn(packageInput)
    doFirst {
        delete(appImageDirectory)
        commandLine(jpackageExecutable.get(), "--type", "app-image", "--dest", appImageDirectory.get().asFile.absolutePath,
            "--input", layout.buildDirectory.dir("jpackage/input").get().asFile.absolutePath,
            "--name", "Codalgora", "--app-version", project.version.toString(), "--vendor", "Codalgora",
            "--description", "IDE de Portugol com interpretador independente",
            "--main-jar", tasks.jar.get().archiveFileName.get(), "--main-class", application.mainClass.get(),
            "--icon", applicationIcon.asFile.absolutePath,
            "--java-options", "-Dfile.encoding=UTF-8",
            "--add-modules", "java.base,java.desktop,java.logging,java.prefs,java.xml,jdk.unsupported,java.net.http")
    }
}

val guiExportDirectory = layout.buildDirectory.dir("gui-export")
val appImageName = if (hostOs.isMacOsX) "Codalgora.app" else "Codalgora"
val stageGuiProject = tasks.register("stageGuiProject") {
    group = "distribution"
    description = "Prepare a portable visual form and Portugol source for the exported app"
    outputs.dir(guiExportDirectory)
    doLast {
        val formPath = project.findProperty("guiForm")?.toString()
            ?: throw GradleException("Informe -PguiForm=/caminho/interface.cgui.json")
        val algorithmPath = project.findProperty("guiAlgorithm")?.toString()
            ?: throw GradleException("Informe -PguiAlgorithm=/caminho/algoritmo.alg")
        val formFile = file(formPath)
        val algorithmFile = file(algorithmPath)
        if (!formFile.isFile || !algorithmFile.isFile)
            throw GradleException("Os arquivos do formulário e do algoritmo precisam existir.")
        val projectOutput = guiExportDirectory.get().file("Projeto").asFile
        projectOutput.deleteRecursively()
        projectOutput.mkdirs()
        @Suppress("UNCHECKED_CAST")
        val form = groovy.json.JsonSlurper().parse(formFile) as MutableMap<String, Any?>
        form["algorithmPath"] = "algoritmo.alg"
        projectOutput.resolve("interface.cgui.json").writeText(
            groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(form)), Charsets.UTF_8
        )
        algorithmFile.copyTo(projectOutput.resolve("algoritmo.alg"), overwrite = true)
        val shellLauncher = guiExportDirectory.get().file("Executar.sh").asFile
        shellLauncher.writeText(
            "#!/bin/sh\nBASE=\"${'$'}(CDPATH= cd -- \"${'$'}(dirname -- \"${'$'}0\")\" && pwd)\"\n"
                + "\"${'$'}BASE/CodalgoraApp/${if (hostOs.isMacOsX) "Contents/MacOS/Codalgora" else "bin/Codalgora"}\" --gui-form \"${'$'}BASE/Projeto/interface.cgui.json\"\n",
            Charsets.UTF_8
        )
        shellLauncher.setExecutable(true)
        guiExportDirectory.get().file("Executar.bat").asFile.writeText(
            "@echo off\r\n\"%~dp0CodalgoraApp\\Codalgora.exe\" --gui-form \"%~dp0Projeto\\interface.cgui.json\"\r\n",
            Charsets.UTF_8
        )
    }
}

tasks.register<Zip>("exportGuiApplication") {
    group = "distribution"
    description = "Create a self-contained visual application archive for the host platform"
    dependsOn(appImage, stageGuiProject)
    archiveFileName.set("codalgora-visual-app.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(appImageDirectory.map { it.dir(appImageName) }) {
        into("CodalgoraApp")
        filePermissions { unix("755") }
    }
    from(guiExportDirectory) {
        include("Projeto/**")
        filePermissions { unix("644") }
    }
    if (hostOs.isWindows) {
        from(guiExportDirectory.map { it.file("Executar.bat") }) {
            filePermissions { unix("644") }
        }
    } else {
        from(guiExportDirectory.map { it.file("Executar.sh") }) {
            filePermissions { unix("755") }
        }
    }
}

tasks.register<Exec>("jpackageInstaller") {
    group = "distribution"
    description = "Create the native installer on the current host (deb on Linux, exe on Windows)"
    dependsOn(appImage)
    doFirst {
        val output = layout.buildDirectory.dir("jpackage/installer").get().asFile
        delete(output)
        val args = mutableListOf(jpackageExecutable.get(), "--type", if(hostOs.isWindows) "exe" else if(hostOs.isMacOsX) "dmg" else "deb",
            "--dest", output.absolutePath, "--name", "Codalgora", "--app-version", project.version.toString(),
            "--vendor", "Codalgora", "--icon", applicationIcon.asFile.absolutePath, "--app-image", appImageDirectory.get().dir("Codalgora").asFile.absolutePath)
        if(hostOs.isWindows) args += listOf("--win-menu", "--win-shortcut", "--win-dir-chooser", "--win-per-user-install")
        if(hostOs.isLinux) args += listOf("--resource-dir", layout.projectDirectory.dir("packaging/linux").asFile.absolutePath, "--linux-menu-group", "Development", "--linux-shortcut", "--linux-package-name", "codalgora", "--linux-deb-maintainer", "thurdass@users.noreply.github.com")
        commandLine(args)
    }
}

tasks.register<Exec>("jpackageSmokeTest") {
    group = "verification"
    description = "Launch and exercise the self-contained native application image"
    dependsOn(appImage)
    doFirst {
        val image = appImageDirectory.get().dir("Codalgora").asFile
        val executable = if(hostOs.isWindows) image.resolve("Codalgora.exe") else if(hostOs.isMacOsX) image.resolve("Contents/MacOS/Codalgora") else image.resolve("bin/Codalgora")
        commandLine(executable.absolutePath, "--smoke-test")
    }
}

val windowsMsiInstaller = tasks.register<Exec>("jpackageWindowsMsi") {
    group = "distribution"
    description = "Create the direct Windows Installer package"
    dependsOn(appImage)
    onlyIf { hostOs.isWindows }
    doFirst {
        val output = layout.buildDirectory.dir("jpackage/installer").get().asFile
        output.mkdirs()
        commandLine(jpackageExecutable.get(), "--type", "msi",
            "--dest", output.absolutePath, "--name", "Codalgora", "--app-version", project.version.toString(),
            "--vendor", "Codalgora", "--icon", applicationIcon.asFile.absolutePath,
            "--app-image", appImageDirectory.get().dir("Codalgora").asFile.absolutePath,
            "--win-menu", "--win-shortcut", "--win-dir-chooser", "--win-per-user-install")
    }
}
if (hostOs.isWindows) {
    tasks.named("jpackageInstaller") { finalizedBy(windowsMsiInstaller) }
}
