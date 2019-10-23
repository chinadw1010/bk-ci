package com.tencent.devops.common.api.pojo.agent

enum class NodeStatus(val statusName: String) {
    NORMAL("正常"),
    ABNORMAL("异常"),
    DELETED("已删除"),
    LOST("失联"),
    CREATING("正在创建中"),
    RUNNING("安装Agent"),
    STARTING("正在开机中"),
    STOPPING("正在关机中"),
    STOPPED("已关机"),
    RESTARTING("正在重启中"),
    DELETING("正在销毁中"),
    BUILDING_IMAGE("正在制作镜像中"),
    BUILD_IMAGE_SUCCESS("制作镜像成功"),
    BUILD_IMAGE_FAILED("制作镜像失败"),
    UNKNOWN("未知");

    companion object {
        fun getStatusName(status: String): String {
            return when (status) {
                NORMAL.name -> NORMAL.statusName
                ABNORMAL.name -> ABNORMAL.statusName
                DELETED.name -> DELETED.statusName
                LOST.name -> LOST.statusName
                STARTING.name -> STARTING.statusName
                CREATING.name -> CREATING.statusName
                RUNNING.name -> RUNNING.statusName
                STARTING.name -> STARTING.statusName
                STOPPING.name -> STOPPING.statusName
                STOPPED.name -> STOPPED.statusName
                RESTARTING.name -> RESTARTING.statusName
                DELETING.name -> DELETING.statusName
                BUILDING_IMAGE.name -> BUILDING_IMAGE.statusName
                BUILD_IMAGE_SUCCESS.name -> BUILD_IMAGE_SUCCESS.statusName
                BUILD_IMAGE_FAILED.name -> BUILD_IMAGE_FAILED.statusName
                else -> UNKNOWN.statusName
            }
        }

        fun parseByName(name: String): NodeStatus {
            return when (name) {
                NORMAL.name -> NORMAL
                ABNORMAL.name -> ABNORMAL
                DELETED.name -> DELETED
                LOST.name -> LOST
                STARTING.name -> STARTING
                CREATING.name -> CREATING
                RUNNING.name -> RUNNING
                STARTING.name -> STARTING
                STOPPING.name -> STOPPING
                STOPPED.name -> STOPPED
                RESTARTING.name -> RESTARTING
                DELETING.name -> DELETING
                BUILDING_IMAGE.name -> BUILDING_IMAGE
                BUILD_IMAGE_SUCCESS.name -> BUILD_IMAGE_SUCCESS
                BUILD_IMAGE_FAILED.name -> BUILD_IMAGE_FAILED
                else -> UNKNOWN
            }
        }

        fun parseByStatusName(statusName: String): NodeStatus {
            return when (statusName) {
                NORMAL.statusName -> NORMAL
                ABNORMAL.statusName -> ABNORMAL
                DELETED.statusName -> DELETED
                STARTING.statusName -> STARTING
                LOST.statusName -> LOST
                CREATING.statusName -> CREATING
                RUNNING.statusName -> RUNNING
                STARTING.statusName -> STARTING
                STOPPING.statusName -> STOPPING
                STOPPED.statusName -> STOPPED
                RESTARTING.statusName -> RESTARTING
                DELETING.statusName -> DELETING
                BUILDING_IMAGE.statusName -> BUILDING_IMAGE
                BUILD_IMAGE_SUCCESS.statusName -> BUILD_IMAGE_SUCCESS
                BUILD_IMAGE_FAILED.statusName -> BUILD_IMAGE_FAILED
                else -> UNKNOWN
            }
        }
    }
}