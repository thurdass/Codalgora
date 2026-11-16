plugins { application }
dependencies { implementation(project(":interpreter")) }
application { mainClass.set("io.codalgora.cli.Main") }
tasks.named<JavaExec>("run") { standardInput = System.`in`; workingDir(rootProject.projectDir) }
