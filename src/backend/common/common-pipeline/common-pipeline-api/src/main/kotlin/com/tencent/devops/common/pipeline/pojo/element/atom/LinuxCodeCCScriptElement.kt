package com.tencent.devops.common.pipeline.pojo.element.atom

import com.tencent.devops.common.pipeline.enums.BuildScriptType
import com.tencent.devops.common.pipeline.pojo.coverity.ProjectLanguage
import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.pojo.element.agent.LinuxScriptElement
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("CodeCC代码检查任务(service端)", description = LinuxScriptElement.classType)
open class LinuxCodeCCScriptElement(
    @ApiModelProperty("任务名称", required = true)
    override val name: String = "执行Linux脚本",
    @ApiModelProperty("id", required = false)
    override var id: String? = null,
    @ApiModelProperty("状态", required = false)
    override var status: String? = null,
    @ApiModelProperty("脚本类型", required = true)
    open val scriptType: BuildScriptType,
    @ApiModelProperty("脚本内容", required = true)
    open val script: String = "",
    @ApiModelProperty("CodeCC Task Name", required = false, hidden = true)
    open var codeCCTaskName: String? = null,
    @ApiModelProperty("CodeCC Task CN Name", required = false, hidden = true)
    open var codeCCTaskCnName: String? = null,
    @ApiModelProperty("工程语言", required = true)
    open val languages: List<ProjectLanguage>,
    @ApiModelProperty("是否异步", required = false)
    open val asynchronous: Boolean? = false,
    @ApiModelProperty("扫描类型（0：全量, 1：增量）", required = false)
    open var scanType: String? = "",
    @ApiModelProperty("代码存放路径", required = false)
    open val path: String? = null,
    @ApiModelProperty("codecc原子执行环境，例如WINDOWS，LINUX，MACOS等", required = true)
    var compilePlat: String? = null,
    @ApiModelProperty("JSONArray格式的字符串\n" +
            "eg：\"[\"COVERITY\",\"CPPLINT\",\"PYLINT\",\"TSCLUA\",\"CCN\",\"DUPC\",\"ESLINT\",\"GOML\",\"KLOCWORK\"]\"，其中\n" +
            "COVERITY：Coverity工具\n" +
            "CPPLINT：cpplint工具\n" +
            "PYLINT：pylint工具\n" +
            "TSCLUA：TSCLUA工具\n" +
            "CCN：圈复杂度工具\n" +
            "DUPC：重复率工具\n" +
            "GOML：go语言检查工具\n" +
            "KLOCWORK：KLOCWORK工具\n" +
            "CHECKSTYLE: CHECKSTYLE工具" +
            "STYLECOP: STYLECOP工具", required = true)
    var tools: List<String>? = null,
    @ApiModelProperty("非必填，当tools列表中有PYLINT时必填；值类型有且仅有两种：“py2”、“py3”，\n" +
            "其中“py2”表示使用python2版本，“py3”表示使用python3版本", required = false)
    var pyVersion: String? = null,
    @ApiModelProperty("eslint项目框架, React, Vue, Other", required = false)
    var eslintRc: String? = null,
    @ApiModelProperty("PHP标准", required = false)
    var phpcsStandard: String? = null,
    @ApiModelProperty("go语言WORKSPACE下相对路径", required = false)
    var goPath: String? = null,
    @ApiModelProperty("圈复杂度阈值", required = false)
    var ccnThreshold: Int? = null,
    @ApiModelProperty("是否隐藏代码内容，字符串的false和true", required = false)
    var needCodeContent: String? = null,
    val coverityToolSetId: String? = null,
    val klocworkToolSetId: String? = null,
    val cpplintToolSetId: String? = null,
    val eslintToolSetId: String? = null,
    val pylintToolSetId: String? = null,
    val gometalinterToolSetId: String? = null,
    val checkStyleToolSetId: String? = null,
    val styleCopToolSetId: String? = null,
    val detektToolSetId: String? = null,
    val phpcsToolSetId: String? = null,
    val sensitiveToolSetId: String? = null,
    val occheckToolSetId: String? = null,
    val gociLintToolSetId: String? = null
) : Element(name, id, status) {

    companion object {
        const val classType = "linuxCodeCCScript"
    }

    override fun getClassType() = classType
}