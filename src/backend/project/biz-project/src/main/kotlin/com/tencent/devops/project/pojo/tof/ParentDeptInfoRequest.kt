package com.tencent.devops.project.pojo.tof

data class ParentDeptInfoRequest(
    val app_code: String,
    val app_secret: String,
    val dept_id: String,
    val level: Int
)