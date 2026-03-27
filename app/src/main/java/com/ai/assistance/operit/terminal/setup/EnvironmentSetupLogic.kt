package com.ai.assistance.operit.terminal.setup

import com.ai.assistance.operit.terminal.utils.SourceManager

object EnvironmentSetupLogic {
    data class PackageDefinition(
        val id: String,
        val command: String,
        val categoryId: String
    )

    val packageDefinitions: List<PackageDefinition> = listOf(
        PackageDefinition("bash", "bash --version", "base"),
        PackageDefinition("curl", "curl --version", "base"),
        PackageDefinition("git", "git --version", "base"),
        PackageDefinition("nodejs", "node --version", "base"),
        PackageDefinition("npm", "npm --version", "base"),
        PackageDefinition("python3", "python3 --version", "base"),
        PackageDefinition("pip3", "pip3 --version", "base"),
        PackageDefinition("ripgrep", "rg --version", "base"),
        PackageDefinition("tmux", "tmux -V", "base"),
        PackageDefinition("uv", "uv --version", "base"),
        PackageDefinition("xz", "xz --version", "base")
    )

    fun buildInstallCommands(
        selectedPackageIds: List<String>,
        sourceManager: SourceManager
    ): List<String> {
        return buildInstallCommands(
            selectedPackageIds = selectedPackageIds,
            repositorySetupCommand = sourceManager.buildRepositorySetupCommand()
        )
    }

    internal fun buildInstallCommands(
        selectedPackageIds: List<String>,
        repositorySetupCommand: String
    ): List<String> {
        val requested = selectedPackageIds.toSet()
        if (requested.isEmpty()) {
            return emptyList()
        }
        val repoSetup = repositorySetupCommand.trim()
        val packageMap = linkedMapOf(
            "bash" to "bash",
            "curl" to "curl",
            "git" to "git",
            "nodejs" to "nodejs npm",
            "npm" to "npm",
            "python3" to "python3 py3-pip py3-virtualenv",
            "pip3" to "py3-pip",
            "ripgrep" to "ripgrep",
            "tmux" to "tmux",
            "xz" to "xz",
            "uv" to ""
        )

        val apkPackages = requested
            .flatMap { (packageMap[it] ?: "").split(' ') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val commands = mutableListOf<String>()
        if (repoSetup.isNotBlank()) {
            commands += repoSetup
        }
        if (apkPackages.isNotEmpty()) {
            commands += "apk update"
            commands += "apk add ${apkPackages.joinToString(" ")}"
        }

        if ("python3" in requested || "pip3" in requested || "uv" in requested) {
            commands += "ln -sf /usr/bin/python3 /usr/local/bin/python || true"
        }
        if ("uv" in requested) {
            commands += "python3 -m pip install --upgrade pip"
            commands += "python3 -m pip install uv"
        }

        return commands
    }

    fun buildCheckCommand(pkgId: String, command: String): String {
        val actual = when (pkgId) {
            "bash" -> "command -v bash"
            "curl" -> "command -v curl"
            "git" -> "command -v git"
            "nodejs" -> "command -v node"
            "npm" -> "command -v npm"
            "python3" -> "command -v python3"
            "pip3" -> "command -v pip3"
            "ripgrep" -> "command -v rg"
            "tmux" -> "command -v tmux"
            "uv" -> "command -v uv"
            "xz" -> "command -v xz"
            else -> command
        }
        return "$actual >/dev/null 2>&1 && echo INSTALLED || echo MISSING"
    }

    fun isPackageInstalled(pkgId: String, output: String): Boolean {
        val normalized = output.trim()
        return normalized.contains("INSTALLED") || normalized.contains(pkgId, ignoreCase = true)
    }
}
