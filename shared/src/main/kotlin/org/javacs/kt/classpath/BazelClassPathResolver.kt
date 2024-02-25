package org.javacs.kt.classpath

import com.google.common.io.Resources
import org.javacs.kt.LOG
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.execAndReadStdoutAndStderr
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.relativeTo

enum class CqueryMode {
    SOURCE_JARS, OUTPUT_JARS
}

internal class BazelClassPathResolver(private val workspaceRoot: Path, private val packagePath: Path): ClassPathResolver {
    override val resolverType: String = "Bazel"
    override val classpath: Set<ClassPathEntry> get() : Set<ClassPathEntry> {
        return getClassPathsFromBazel()
    }

    private fun getSourceJarForOutput(sourceJars: List<String>, outputJarPath: String): String {
        return sourceJars.first {
            it.replace("-src.jar", ".jar") == outputJarPath || it.replace("-sources.jar", ".jar") == outputJarPath
        }
    }
    private fun getClassPathsFromBazel() : Set<ClassPathEntry> {
        LOG.debug { "Retrieving source jars for $packagePath" }
        val sourceJars = runBazelCquery(packagePath, CqueryMode.SOURCE_JARS)
        LOG.debug { "Retrieving output jars for $packagePath" }
        val outputJars = runBazelCquery(packagePath, CqueryMode.OUTPUT_JARS)
        return outputJars.map {
            ClassPathEntry(
                compiledJar = Paths.get(it),
                sourceJar = Paths.get(getSourceJarForOutput(sourceJars, it))
            )
        }.toSet()

    }

    private fun runBazelCquery(packagePath: Path, cqueryMode: CqueryMode) : List<String> {
        val relPackagePath = packagePath.relativeTo(workspaceRoot).toString()
        val cqueryFile = when(cqueryMode) {
            CqueryMode.SOURCE_JARS -> Resources.getResource("sourcejars.cquery").path
            CqueryMode.OUTPUT_JARS -> Resources.getResource("outputjars.cquery").path
        }
        val cmd = listOf("bazel", "cquery", "//$relPackagePath/...", "--output=starlark", "--starlark:file=${cqueryFile}")
        val (output, errors) = execAndReadStdoutAndStderr(cmd, workspaceRoot)
        if (errors.isNotEmpty()) {
            throw KotlinLSException("Error running bazel cquery: $errors")
        }
        return output.split("\n").filter { it.isNotEmpty() }
    }

    companion object {
        /** Create a Bazel resolver if a file is a BUILD.bazel. */
        fun maybeCreate(file: Path): BazelClassPathResolver? =
            file.takeIf { file.endsWith("BUILD.bazel") || file.endsWith("BUILD") }
                ?.let { BazelClassPathResolver(getWorkspaceRoot(it.parent), it) }

        private fun getWorkspaceRoot(path: Path) : Path {
            val cmd = listOf("bazel", "info", "workspace")
            val (output, errors) = execAndReadStdoutAndStderr(cmd, path)
            if (errors.isNotEmpty()) {
                throw KotlinLSException("Could not determine Bazel workspace root: $errors")
            }
            return Paths.get(output)
        }
    }


}
