package com.tencent.devops.notify.model

import com.tencent.devops.common.notify.enums.EnumNotifyPriority
import com.tencent.devops.common.notify.enums.EnumNotifySource
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("wechat微信消息类型")
open class WechatNotifyMessage : BaseMessage() {

    @ApiModelProperty("通知接收者")
    private val receivers: MutableSet<String> = mutableSetOf()
    @ApiModelProperty("通知内容")
    var body: String = ""
    @ApiModelProperty("通知发送者")
    var sender: String = ""
    @ApiModelProperty("优先级", allowableValues = "-1,0,1", dataType = "int")
    var priority: EnumNotifyPriority = EnumNotifyPriority.HIGH
    @ApiModelProperty("通知来源", allowableValues = "0,1", dataType = "int")
    var source: EnumNotifySource = EnumNotifySource.BUSINESS_LOGIC

    fun addReceiver(receiver: String) {
        receivers.add(receiver)
    }

    fun addAllReceivers(receiverSet: Set<String>) {
        receivers.addAll(receiverSet)
    }

    fun clearReceivers() {
        receivers.clear()
    }

    fun getReceivers(): Set<String> {
        return receivers.toSet()
    }

    @ApiModelProperty(hidden = true)
    fun isReceiversEmpty(): Boolean {
        if (receivers.size == 0) return true
        return false
    }

    override fun toString(): String {
        return String.format("sender(%s), receivers(%s), body(%s) ",
                sender, receivers, body)
    }
}