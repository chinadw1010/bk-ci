package com.tencent.devops.notify.model

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("消息通知")
data class NotifyMessageCommonTemplate(
    @ApiModelProperty("ID", required = true)
    val id: String,
    @ApiModelProperty("模板代码", required = true)
    val templateCode: String,
    @ApiModelProperty("模板名称", required = true)
    val templateName: String,
    @ApiModelProperty("优先级别", required = true)
    val priority: String,
    @ApiModelProperty("通知来源", required = true)
    val source: Int
)