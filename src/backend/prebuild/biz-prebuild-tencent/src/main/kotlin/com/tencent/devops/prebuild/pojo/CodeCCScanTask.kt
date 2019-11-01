package com.tencent.devops.prebuild.pojo

import com.tencent.devops.common.api.exception.OperationException
import com.tencent.devops.common.pipeline.enums.BuildScriptType
import com.tencent.devops.common.pipeline.pojo.element.agent.LinuxScriptElement
import com.tencent.devops.prebuild.service.PreBuildConfig
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("CodeCC代码检查任务(客户端)")
open class CodeCCScanTask(
    @ApiModelProperty("id", required = false)
    override var displayName: String,
    @ApiModelProperty("入参", required = true)
    override val input: CodeCCScanInput
) : AbstractTask(displayName, input) {
    companion object {
        const val classType = "codeCCScan"
    }

    override fun getClassType() = classType

    override fun covertToElement(config: PreBuildConfig): LinuxScriptElement {
        return LinuxScriptElement(
                displayName,
                null,
                null,
                BuildScriptType.SHELL,
                createScanScript(config),
                false
        )
    }

    private val scanTools = listOf("ccn", "dupc", "sensitive", "checkstyle", "cpplint", "detekt", "eslint", "goml", "occheck", "phpcs", "pylint", "styecop")

    private fun createScanScript(config: PreBuildConfig): String {
        val tools = input.tools.split(",").map { it.trim() }.filter { scanTools.contains(it) }
        if (tools.isEmpty()) {
            throw OperationException("工具不合法")
        }
        val toolsStr = tools.joinToString(",")
        val ruleSetCmd = if (input.rules.isNullOrBlank()) { " " } else { " -DRULE_SET_IDS=${input.rules!!.trim()} " }
        val skipPath = if (input.skipPath.isNullOrBlank()) { " " } else { " -DSKIP_PATHS=${input.skipPath!!.trim()} " }

        return if (input.scanType == 0) { // 全量
            val path = if (input.path == null) { "\${WORKSPACE} " } else { "\${WORKSPACE}/${input.path}" }
            "cd \${WORKSPACE} \r\n" +
            "export PATH=/data/codecc_software/python3.5/bin/:\$PATH \r\n" +
            "echo $path > /tmp/scan_file_list.txt \r\n" +
            "python ${config.codeCCSofwarePath} \${pipeline.name} -DSCAN_TOOLS=$toolsStr -DSCAN_LIST_FILE=/tmp/scan_file_list.txt $ruleSetCmd $skipPath -DWORKSPACE_PATH=\${WORKSPACE} \r\n"
        } else {
            "cd \${WORKSPACE} \r\n" +
            "export PATH=/data/codecc_software/python3.5/bin/:\$PATH \r\n" +
            "if [ -f \"scan_file_list.txt\" ];then mv scan_file_list.txt /tmp/ ; else echo '/data/prebuild/' > /tmp/scan_file_list.txt ; fi \r\n" +
            "python ${config.codeCCSofwarePath} \${pipeline.name} -DSCAN_TOOLS=$toolsStr -DSCAN_LIST_FILE=/tmp/scan_file_list.txt $ruleSetCmd $skipPath -DWORKSPACE_PATH=\${WORKSPACE} \r\n"
        }
    }
}

@ApiModel("CodeCC代码检查任务(客户端)")
open class CodeCCScanInput(
    @ApiModelProperty("扫描类型（0：全量, 1：增量）", required = false)
    open var scanType: Int? = 0,
    @ApiModelProperty("工具包,多个之间逗号分隔：ccn,dupc,sensitive,checkstyle,cpplint,detekt,eslint,goml,occheck,phpcs,pylint,styecop", required = true)
    var tools: String,
    @ApiModelProperty("要扫描的代码路径，默认为整个workspace", required = false)
    var path: String?,
    @ApiModelProperty("规则集,分隔", required = false)
    var rules: String?,
    @ApiModelProperty("排除的目录,分隔", required = false)
    var skipPath: String?
) : AbstractInput()