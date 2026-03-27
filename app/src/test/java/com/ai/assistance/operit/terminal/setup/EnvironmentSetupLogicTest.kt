package com.ai.assistance.operit.terminal.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentSetupLogicTest {

    @Test
    fun buildInstallCommands_usesAlpinePackagesAndUvBootstrap() {
        val commands = EnvironmentSetupLogic.buildInstallCommands(
            selectedPackageIds = listOf("python3", "uv", "nodejs", "tmux"),
            repositorySetupCommand = ""
        )

        assertEquals("apk update", commands[0])

        val apkAdd = commands.first { it.startsWith("apk add ") }
        assertTrue(apkAdd.contains("python3"))
        assertTrue(apkAdd.contains("py3-pip"))
        assertTrue(apkAdd.contains("py3-virtualenv"))
        assertTrue(apkAdd.contains("nodejs"))
        assertTrue(apkAdd.contains("npm"))
        assertTrue(apkAdd.contains("tmux"))

        assertTrue(commands.contains("ln -sf /usr/bin/python3 /usr/local/bin/python || true"))
        assertTrue(commands.contains("python3 -m pip install --upgrade pip"))
        assertTrue(commands.contains("python3 -m pip install uv"))
    }

    @Test
    fun buildInstallCommands_prependsRepositorySetupWhenProvided() {
        val commands = EnvironmentSetupLogic.buildInstallCommands(
            selectedPackageIds = listOf("curl"),
            repositorySetupCommand = "echo mirror-ready"
        )

        assertEquals("echo mirror-ready", commands.first())
        assertTrue(commands.any { it == "apk update" })
    }
}
