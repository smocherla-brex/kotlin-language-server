package org.javacs.kt.classpath

import org.javacs.kt.LOG
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.execAndReadStdoutAndStderr
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

enum class CqueryMode {
    SOURCE_JARS, OUTPUT_JARS
}

internal class BazelClassPathResolver(private val workspaceRoots: Collection<Path>) : ClassPathResolver {
    override val resolverType: String = "Bazel"
    private val bazelWorkspaceRoot = getBazelWorkspaceRoot()
    private val bazelPackages = getBazelPackages()

    override val classpath: Set<ClassPathEntry> get(): Set<ClassPathEntry> {
        val paths = getClassPathsFromBazel()
        LOG.info { "classpaths: $paths" }
        return paths
    }

    private fun getBazelWorkspaceRoot(): Path {
        return workspaceRoots.first { Paths.get(it.toString(), "WORKSPACE").exists() }
    }

    private fun getBazelPackages(): Sequence<Path> {
        return bazelWorkspaceRoot.toFile().walk().filter { it.isFile and (it.name.endsWith("BUILD") or it.name.endsWith("BUILD.bazel")) and !it.name.contains("bazel-creditcard") or !it.name.contains("bazel-out") }.map { it.toPath().parent }
    }

    private fun getSourceJarForOutput(sourceJars: List<String>, outputJarPath: String): String {
        return sourceJars.first {
            it.replace("-src.jar", ".jar") == outputJarPath || it.replace("-sources.jar", ".jar") == outputJarPath
        }
    }
    private fun getClassPathsFromBazel(): Set<ClassPathEntry> {
        val bazelPackages = getKotlinBazelPackages()
        val sourceJars = runBazelCquery(bazelPackages, CqueryMode.SOURCE_JARS)
        LOG.info { "Retrieved ${sourceJars.size} source jars" }
        val outputJars = runBazelCquery(bazelPackages, CqueryMode.OUTPUT_JARS)
        LOG.info { "Retrieved ${outputJars.size} output jars" }
        return outputJars.map {
            ClassPathEntry(
                compiledJar = Paths.get(bazelWorkspaceRoot.toString(), it),
                // TODO: map source jar here correctly from sourceJars
                sourceJar = null,
            )
        }.toSet()
    }

    private fun runBazelCquery(packages: List<String>, cqueryMode: CqueryMode): List<String> {
        val packageScopes = packages.take(5).map {
            "//$it:all"
        }.joinToString(" ")
        val cqueryFile = when (cqueryMode) {
            CqueryMode.SOURCE_JARS -> Paths.get(this.javaClass.getResource("sourcejars.cquery").path)
            CqueryMode.OUTPUT_JARS -> Paths.get(this.javaClass.getResource("outputjars.cquery").path)
        }
        val cmd = listOf("bazel", "cquery", "set($packageScopes)", "--output=starlark", "--starlark:file=$cqueryFile")
        LOG.info("Running bazel command $cmd")
        val (output, errors, exitCode) = execAndReadStdoutAndStderr(cmd, bazelWorkspaceRoot)
        if (exitCode != 0) {
            throw KotlinLSException("Error running bazel cquery: $errors")
        }
        LOG.info(errors)
        return output.split("\n").filter { it.isNotEmpty() }
    }

    private fun getKotlinBazelPackages(): List<String> {
        val cmd = listOf("bazel", "query", "--output=package", "'kind(kt_jvm_library, //...)'")
        val (output, errors, exitCode) = execAndReadStdoutAndStderr(cmd, bazelWorkspaceRoot)
        if (exitCode != 0) {
            throw KotlinLSException("bazel query failed: $errors")
        }
        return output.split("\n").filter { it.isNotEmpty() }
    }

    companion object {
        private fun isBazelProject(workspaceRoots: Collection<Path>): Boolean {
            return workspaceRoots.any { Paths.get(it.toString(), "WORKSPACE").exists() }
        }

        fun global(workspaceRoots: Collection<Path>): ClassPathResolver =
            if (isBazelProject(workspaceRoots)) {
                BazelClassPathResolver(workspaceRoots)
            } else {
                ClassPathResolver.empty
            }
    }
}
