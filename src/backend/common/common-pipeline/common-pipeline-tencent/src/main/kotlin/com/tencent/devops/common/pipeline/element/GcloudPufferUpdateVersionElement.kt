package com.tencent.devops.common.pipeline.element

import com.tencent.devops.common.pipeline.pojo.element.Element
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("GCloud-puffer修改版本设置(IEG专用)", description = GcloudPufferUpdateVersionElement.classType)
data class GcloudPufferUpdateVersionElement(
    @ApiModelProperty("任务名称", required = true)
    override val name: String = "GCloud-资源版本更新(IEG专用)",
    @ApiModelProperty("id", required = false)
    override var id: String? = null,
    @ApiModelProperty("状态", required = false)
    override var status: String? = null,
    @ApiModelProperty("环境配置id", required = true)
    var configId: String = "",
    @ApiModelProperty("渠道 ID", required = true)
    var productId: String = "",
    @ApiModelProperty("游戏 ID", required = true)
    var gameId: String = "",
    @ApiModelProperty("版本号，格式同 IPv4 点分十进制格式，如 3.3.3.3", required = true)
    var versionStr: String = "",
    @ApiModelProperty("凭证id", required = true)
    var ticketId: String = "",
    @ApiModelProperty("版本标签(0: 不可用，1：正式版本, 2：审核版本）", required = false)
    var versionType: String?,
    @ApiModelProperty("版本描述", required = false)
    var versionDes: String?,
    @ApiModelProperty("自定义字符串", required = false)
    var customStr: String?
) : Element(name, id, status) {
    companion object {
        const val classType = "gcloudPufferUpdateVersion"
    }

    override fun getTaskAtom() = "gcloudPufferUpdateVersionTaskAtom"

    override fun getClassType() = classType
}