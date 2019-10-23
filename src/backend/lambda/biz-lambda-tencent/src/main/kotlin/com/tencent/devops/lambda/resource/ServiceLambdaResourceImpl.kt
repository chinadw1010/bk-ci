package com.tencent.devops.lambda.resource

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.lambda.api.ServiceLambdaResource
import com.tencent.devops.lambda.pojo.BG
import com.tencent.devops.lambda.pojo.BuildResultWithPage
import com.tencent.devops.lambda.storage.ESService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class ServiceLambdaResourceImpl @Autowired constructor(private val esService: ESService) : ServiceLambdaResource {
    override fun getBuildHistory(
        projectId: String?,
        pipelineId: String?,
        beginTime: Long,
        endTime: Long?,
        bg: BG,
        deptName: String?,
        centerName: String?,
        offset: Int,
        limit: Int,
        project: String
    ): Result<BuildResultWithPage> {
        return Result(esService.getBuildResult(
            projectId,
            pipelineId,
            beginTime,
            endTime,
            bg.fullName,
            deptName,
            centerName,
            offset,
            limit,
            project))
    }
}