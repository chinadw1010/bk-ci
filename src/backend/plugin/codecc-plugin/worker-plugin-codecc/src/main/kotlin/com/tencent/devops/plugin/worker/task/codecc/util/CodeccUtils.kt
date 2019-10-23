package com.tencent.devops.plugin.worker.task.codecc.util

import com.tencent.devops.common.api.enums.OSType
import com.tencent.devops.common.pipeline.pojo.element.agent.LinuxCodeCCScriptElement.ProjectLanguage
import com.tencent.devops.plugin.codecc.pojo.coverity.CoverityConfig
import com.tencent.devops.plugin.codecc.pojo.coverity.CoverityProjectType
import com.tencent.devops.plugin.worker.task.codecc.ANT_PATH
import com.tencent.devops.plugin.worker.task.codecc.GOMETALINTER_PATH
import com.tencent.devops.plugin.worker.task.codecc.GO_PATH
import com.tencent.devops.plugin.worker.task.codecc.GRADLE_PATH
import com.tencent.devops.plugin.worker.task.codecc.JDK_PATH
import com.tencent.devops.plugin.worker.task.codecc.LIBZIP_PATH
import com.tencent.devops.plugin.worker.task.codecc.MAVEN_PATH
import com.tencent.devops.plugin.worker.task.codecc.MONO_PATH
import com.tencent.devops.plugin.worker.task.codecc.MOUNT_PATH
import com.tencent.devops.plugin.worker.task.codecc.NODE_PATH
import com.tencent.devops.plugin.worker.task.codecc.PHP_PATH
import com.tencent.devops.plugin.worker.task.codecc.PYLINT2_PATH
import com.tencent.devops.plugin.worker.task.codecc.PYLINT3_PATH
import com.tencent.devops.plugin.worker.task.codecc.PYTHON2_PATH
import com.tencent.devops.plugin.worker.task.codecc.PYTHON3_PATH
import com.tencent.devops.plugin.worker.task.codecc.SCRIPT_PATH
import com.tencent.devops.plugin.worker.task.codecc.SOFTWARE_PATH
import com.tencent.devops.plugin.worker.task.codecc.TOOL_SCRIT_PATH
import com.tencent.devops.worker.common.CommonEnv
import com.tencent.devops.worker.common.api.utils.ThirdPartyAgentBuildInfoUtils
import com.tencent.devops.worker.common.env.AgentEnv
import com.tencent.devops.worker.common.env.BuildEnv
import com.tencent.devops.worker.common.env.BuildType
import com.tencent.devops.worker.common.env.DockerEnv
import com.tencent.devops.worker.common.logger.LoggerService
import com.tencent.devops.worker.common.utils.CommandLineUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object CodeccUtils {
    private val executor = Executors.newFixedThreadPool(2)
    private val covTools = listOf("COVERITY", "KLOCWORK")
    private const val SVN_USER = "SVN_USER"
    private const val SVN_PASSWORD = "SVN_PASSWORD"

    init {
        LoggerService.addNormalLine("AgentEnv.isProd(): ${AgentEnv.isProd()}")
        // 第三方构建机
        if (BuildEnv.isThirdParty()) {
            LoggerService.addNormalLine("检测到这是非公共构建机")
        }
    }

    fun executeCoverityCommand(buildId: String, workspace: File, coverityConfig: CoverityConfig): String {
        val result = StringBuilder()
        val runCoverity = (coverityConfig.filterTools.isEmpty() && covTools.minus(coverityConfig.tools).size != 2) ||
            (coverityConfig.filterTools.isNotEmpty() && covTools.minus(coverityConfig.filterTools).size != 2)
        val runTools = (coverityConfig.filterTools.isEmpty() && coverityConfig.tools.minus(covTools).isNotEmpty()) ||
            (coverityConfig.filterTools.isNotEmpty() && coverityConfig.filterTools.minus(covTools).isNotEmpty())

        var expectCount = 0
        if (runCoverity) expectCount++
        if (runTools) expectCount++
        val lock = CountDownLatch(expectCount)
        val successCount = AtomicInteger(0)
        val errorMsg = StringBuilder()

        // 其他类型扫描走新的逻辑
        if (runTools) {
            executor.execute {
                try {
                    result.append(doCodeccToolCommand(buildId, workspace, coverityConfig))
                    successCount.getAndIncrement()
                    LoggerService.addNormalLine("run codecc tools successful")
                } catch (e: Exception) {
                    errorMsg.append("run codecc tools fail: ${e.message}\n")
                } finally {
                    lock.countDown()
                }
            }
        }
        // 判断最后结果
        // 4个小时当做超时
        lock.await(coverityConfig.timeOut, TimeUnit.MINUTES)
        if (successCount.get() != expectCount) throw RuntimeException("运行codecc任务失败: $errorMsg")

        return result.toString()
    }

    private fun doCodeccToolCommand(
        buildId: String,
        workspace: File,
        coverityConfig: CoverityConfig
    ): String {
        val list = ArrayList<String>()
        list.add("export LANG=zh_CN.UTF-8\n")
        list.add("export PATH=$SOFTWARE_PATH/$PYTHON3_PATH:$SOFTWARE_PATH/$GRADLE_PATH:$SOFTWARE_PATH/$MAVEN_PATH:$SOFTWARE_PATH/$ANT_PATH:\$PATH\n")

        CommonEnv.getCommonEnv().forEach { (key, value) ->
            list.add("export $key=$value\n")
        }
        val scanTools = if (coverityConfig.filterTools.isNotEmpty()) {
            coverityConfig.filterTools
        } else {
            coverityConfig.tools
        }
        if (scanTools.isEmpty()) return "scan tools is empty"

        val finalScanTools = scanTools.minus(covTools)

        list.add("python -V\n")
        list.add("pwd\n")
        list.add("set\n")

        list.add("python")
        list.add(getLocalToolScript(workspace))
        list.add(coverityConfig.name)
        list.add("-DLANDUN_BUILDID=$buildId")
        list.add("-DSCAN_TOOLS=${finalScanTools.joinToString(",").toLowerCase()}")
        list.add("-DOFFLINE=true")
        list.add("-DDATA_ROOT_PATH=${workspace.canonicalPath}")
        list.add("-DSTREAM_CODE_PATH=${workspace.canonicalPath}")
        list.add("-DPY27_PATH=$SOFTWARE_PATH/$PYTHON2_PATH")
        list.add("-DPY35_PATH=$SOFTWARE_PATH/$PYTHON3_PATH")
        if (finalScanTools.contains("PYLINT")) {
            list.add("-DPY27_PYLINT_PATH=$SOFTWARE_PATH/$PYLINT2_PATH")
            list.add("-DPY35_PYLINT_PATH=$SOFTWARE_PATH/$PYLINT3_PATH")
        } else {
            // 两个参数是必填的
            // 把路径配置成其他可用路径就可以
            list.add("-DPY27_PYLINT_PATH=${workspace.canonicalPath}")
            list.add("-DPY35_PYLINT_PATH=${workspace.canonicalPath}")
        }

        val subPath = "$SOFTWARE_PATH/$JDK_PATH:$SOFTWARE_PATH/$NODE_PATH:$SOFTWARE_PATH/$GOMETALINTER_PATH:" +
            "$SOFTWARE_PATH/$GO_PATH:$SOFTWARE_PATH/$MONO_PATH:$SOFTWARE_PATH/$PHP_PATH:$SOFTWARE_PATH/$LIBZIP_PATH"
        list.add("-DSUB_PATH=$subPath")
        list.add("-DCERT_TYPE=${coverityConfig.certType}")
        list.add("-DSCM_TYPE=${coverityConfig.scmType}")
        list.add(
            "-DREPO_URL_MAP='${toMapString(
                coverityConfig.repos.map {
                    it.repoHashId to it.url
                }.toMap()
            )}'"
        )
        list.add(
            "-DREPO_RELPATH_MAP='${toMapString(
                coverityConfig.repos.map {
                    it.repoHashId to it.relPath
                }.toMap()
            )}'"
        )

        val svnHttpCredential = CommonEnv.getSvnHttpCredential()
        if (svnHttpCredential != null) {
            list.add("-D$SVN_USER=${svnHttpCredential.first}")
            list.add("-D$SVN_PASSWORD='${svnHttpCredential.second}'")
        }
        list.add("-DSUB_CODE_PATH_LIST=${coverityConfig.scanCodePath}")
        list.add(
            "-DREPO_SCM_RELPATH_MAP='${toMapString(
                coverityConfig.repos.map {
                    it.repoHashId to it.relativePath
                }.toMap()
            )}'"
        )

        // 构建机信息
        if (BuildEnv.getBuildType() == BuildType.AGENT) {
            LoggerService.addNormalLine("检测到这是第三方构建机")
            list.add("-DDEVOPS_PROJECT_ID=${AgentEnv.getProjectId()}")
            list.add("-DDEVOPS_BUILD_TYPE=${BuildType.AGENT.name}")
            list.add("-DDEVOPS_AGENT_ID=${AgentEnv.getAgentId()}")
            list.add("-DDEVOPS_AGENT_SECRET_KEY=${AgentEnv.getAgentSecretKey()}")
            list.add("-DDEVOPS_AGENT_VM_SID=${ThirdPartyAgentBuildInfoUtils.getBuildInfo()!!.vmSeqId}")
        } else if (BuildEnv.getBuildType() == BuildType.DOCKER) {
            LoggerService.addNormalLine("检测到这是docker公共构建机")
            list.add("-DDEVOPS_PROJECT_ID=${DockerEnv.getProjectId()}")
            list.add("-DDEVOPS_BUILD_TYPE=${BuildType.DOCKER.name}")
            list.add("-DDEVOPS_AGENT_ID=${DockerEnv.getAgentId()}")
            list.add("-DDEVOPS_AGENT_SECRET_KEY=${DockerEnv.getAgentSecretKey()}")
        }

        list.add("-DLD_ENV_TYPE=${getEnvType()}")
        list.add("-DMOUNT_PATH=$MOUNT_PATH")

        val toolsScript = File(workspace, "paas_codecc_tools_script.sh")
        if (toolsScript.exists()) {
            toolsScript.delete()
        }
        toolsScript.writeText(list.joinToString(" "))

        // 打印日志
        LoggerService.addNormalLine("tools command content: ")
        list.forEach {
            if (!it.startsWith("-DSSH_PRIVATE_KEY") &&
                    !it.startsWith("-DKEY_PASSWORD") &&
                    !it.startsWith("-D$SVN_PASSWORD")
            ) {
                LoggerService.addNormalLine("[tools] $it")
            }
        }

        return CommandLineUtils.execute(toolsScript, workspace, true, "[tools] ")
    }

    private fun getLocalToolScript(workspace: File): String {
        val toolScript = File("$SCRIPT_PATH/$TOOL_SCRIT_PATH")
        val localScriptFolder = File(workspace.parent, "codecc_coverity")
        if (!localScriptFolder.exists()) {
            localScriptFolder.mkdirs()
        }
        val localScriptFile = File(localScriptFolder, TOOL_SCRIT_PATH)
        toolScript.copyTo(localScriptFile, true)
        val perms = PosixFilePermissions.fromString("rwxr-xr-x")
        Files.setPosixFilePermissions(Paths.get(localScriptFile.canonicalPath), perms)
        return localScriptFile.canonicalPath
    }

    private fun toMapString(toMap: Map<String, String>): String {
        val sb = StringBuilder()
        sb.append("{")
        toMap.entries.forEach {
            sb.append("\"${it.key}\":\"${it.value}\"")
            sb.append(",")
        }
        sb.deleteCharAt(sb.length - 1)
        sb.append("}")
        return sb.toString()
    }

    private fun getEnvType(): String {
        // 第三方机器
        return if (BuildEnv.getBuildType() == BuildType.AGENT) {
            when (AgentEnv.getOS()) {
                OSType.MAC_OS -> "MAC_THIRD_PARTY"
                OSType.WINDOWS -> "WIN_THIRD_PARTY"
                else -> "LINUX_THIRD_PARTY"
            }
        } else {
            "PUBLIC"
        }
    }

    private val map = mapOf(
        ProjectLanguage.C.name to CoverityProjectType.COMPILE,
        ProjectLanguage.C_PLUS_PLUSH.name to CoverityProjectType.COMPILE,
        ProjectLanguage.C_CPP.name to CoverityProjectType.COMPILE,
        ProjectLanguage.OBJECTIVE_C.name to CoverityProjectType.COMPILE,
        ProjectLanguage.OC.name to CoverityProjectType.COMPILE,
        ProjectLanguage.C_SHARP.name to CoverityProjectType.COMPILE,
        ProjectLanguage.JAVA.name to CoverityProjectType.COMPILE,
        ProjectLanguage.PYTHON.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.JAVASCRIPT.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.JS.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.PHP.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.RUBY.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.LUA.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.GOLANG.name to CoverityProjectType.COMBINE,
        ProjectLanguage.SWIFT.name to CoverityProjectType.COMBINE,
        ProjectLanguage.TYPESCRIPT.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.KOTLIN.name to CoverityProjectType.COMPILE,
        ProjectLanguage.OTHERS.name to CoverityProjectType.UN_COMPILE
    )
// Check the coverity project type by the project language
    /**
     * C/C++                	编译型
     * Objective-C/C++			编译型
     * C#						编译型
     * Java 					编译型
     * Python					非编译型
     * JavaScript				非编译型
     * PHP						非编译型
     * Ruby					    非编译型
     */
    fun projectType(languages: List<String>): CoverityProjectType {
        if (languages.isEmpty()) {
            return CoverityProjectType.UN_COMPILE
        }

        var type = map[languages[0]]

        languages.forEach {
            val currentType = map[it]
            if (type != null) {
                if (currentType != null && type != currentType) {
                    return CoverityProjectType.COMBINE
                }
            } else {
                type = currentType
            }
        }

        return type ?: CoverityProjectType.UN_COMPILE
    }
}