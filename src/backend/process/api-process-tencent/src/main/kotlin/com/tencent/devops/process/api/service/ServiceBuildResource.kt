package com.tencent.devops.process.api.service

import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.devops.common.api.pojo.BuildHistoryPage
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.pojo.SimpleResult
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.process.pojo.BuildBasicInfo
import com.tencent.devops.process.pojo.BuildHistory
import com.tencent.devops.process.pojo.BuildHistoryVariables
import com.tencent.devops.process.pojo.BuildId
import com.tencent.devops.process.pojo.BuildManualStartupInfo
import com.tencent.devops.process.pojo.ReviewParam
import com.tencent.devops.process.pojo.VmInfo
import com.tencent.devops.process.pojo.pipeline.ModelDetail
import com.tencent.devops.process.pojo.pipeline.PipelineLatestBuild
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Api(tags = ["SERVICE_BUILD"], description = "服务-构建资源")
@Path("/service/builds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ServiceBuildResource {

    @ApiOperation("Notify process that the vm startup for the build")
    @PUT
    @Path("/projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/vmStatus")
    fun setVMStatus(
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("构建ID", required = true)
        @PathParam("buildId")
        buildId: String,
        @ApiParam("VM SEQ ID", required = true)
        @QueryParam("vmSeqId")
        vmSeqId: String,
        @ApiParam("status", required = true)
        @QueryParam("status")
        status: BuildStatus
    ): Result<Boolean>

    @ApiOperation("Notify process that the vm startup for the build")
    @PUT
    @Path("/projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/vmStarted")
    fun vmStarted(
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("构建ID", required = true)
        @PathParam("buildId")
        buildId: String,
        @ApiParam("VM SEQ ID", required = true)
        @QueryParam("vmSeqId")
        vmSeqId: String,
        @ApiParam("VM NAME", required = true)
        @QueryParam("vmName")
        vmName: String
    ): Result<Boolean>

    @ApiOperation("根据构建ID获取项目ID以及流水线ID")
    @GET
    @Path("/builds/{buildId}/basic")
    fun serviceBasic(
        @ApiParam("构建ID", required = true)
        @PathParam("buildId")
        buildId: String
    ): Result<BuildBasicInfo>

    @ApiOperation("根据构建ID获取项目ID以及流水线ID")
    @POST
    @Path("/batchBasic")
    fun batchServiceBasic(
        @ApiParam("构建ID", required = true)
        buildIds: Set<String>
    ): Result<Map<String, BuildBasicInfo>>

    @ApiOperation("获取流水线手动启动参数")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/manualStartupInfo")
    fun manualStartupInfo(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("渠道号，默认为DS", required = false)
        @QueryParam("channelCode")
        channelCode: ChannelCode
    ): Result<BuildManualStartupInfo>

    @ApiOperation("手动启动流水线")
    @POST
    @Path("/projects/{projectId}/pipelines/{pipelineId}/manualStartup")
    fun manualStartup(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("启动参数", required = true)
        values: Map<String, String>,
        @ApiParam("渠道号，默认为DS", required = false)
        @QueryParam("channelCode")
        channelCode: ChannelCode
    ): Result<BuildId>

    @ApiOperation("手动停止流水线")
    @DELETE
    @Path("/projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/stop")
    fun manualShutdown(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("构建ID", required = true)
        @PathParam("buildId")
        buildId: String,
        @ApiParam("渠道号，默认为DS", required = false)
        @QueryParam("channelCode")
        channelCode: ChannelCode
    ): Result<Boolean>

    @ApiOperation("系统异常导致停止流水线")
    @DELETE
    @Path("/projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/shutdown")
    fun serviceShutdown(
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("构建ID", required = true)
        @PathParam("buildId")
        buildId: String,
        @ApiParam("渠道号，默认为DS", required = false)
        @QueryParam("channelCode")
        channelCode: ChannelCode
    ): Result<Boolean>

    @ApiOperation("人工审核")
    @POST
    @Path("/projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/elements/{elementId}/review")
    fun manualReview(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("构建ID", required = true)
        @PathParam("buildId")
        buildId: String,
        @ApiParam("步骤Id", required = true)
        @PathParam("elementId")
        elementId: String,
        @ApiParam("审核信息", required = true)
        params: ReviewParam,
        @ApiParam("渠道号，默认为DS", required = false)
        @QueryParam("channelCode")
        channelCode: ChannelCode
    ): Result<Boolean>

    @ApiOperation("获取构建详情")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/detail")
    fun getBuildDetail(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("构建ID", required = true)
        @PathParam("buildId")
        buildId: String,
        @ApiParam("渠道号，默认为DS", required = false)
        @QueryParam("channelCode")
        channelCode: ChannelCode
    ): Result<ModelDetail>

    @ApiOperation("获取流水线构建历史")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/history")
    fun getHistoryBuild(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("第几页", required = false, defaultValue = "1")
        @QueryParam("page")
        page: Int?,
        @ApiParam("每页多少条", required = false, defaultValue = "20")
        @QueryParam("pageSize")
        pageSize: Int?,
        @ApiParam("渠道号，默认为DS", required = false)
        @QueryParam("channelCode")
        channelCode: ChannelCode
    ): Result<BuildHistoryPage<BuildHistory>>

    @ApiOperation("获取构建详情")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/status")
    fun getBuildStatus(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("构建ID", required = true)
        @PathParam("buildId")
        buildId: String,
        @ApiParam("渠道号，默认为DS", required = true)
        @QueryParam("channelCode")
        channelCode: ChannelCode
    ): Result<BuildHistoryWithVars>

    @ApiOperation("获取构建全部变量")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/vars")
    fun getBuildVars(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("构建ID", required = true)
        @PathParam("buildId")
        buildId: String,
        @ApiParam("渠道号，默认为DS", required = false)
        @QueryParam("channelCode")
        channelCode: ChannelCode = ChannelCode.BS
    ): Result<BuildHistoryVariables>

    @ApiOperation("批量获取构建详情")
    @POST
    @Path("/projects/{projectId}/batchStatus")
    fun getBatchBuildStatus(
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("构建ID", required = true)
        buildId: Set<String>,
        @ApiParam("渠道号，默认为DS", required = true)
        @QueryParam("channelCode")
        channelCode: ChannelCode = ChannelCode.BS
    ): Result<List<BuildHistory>>

    @ApiOperation("根据流水线id获取最新执行信息")
    @POST
    @Path("/projects/{projectCode}/getPipelineLatestBuild")
    fun getPipelineLatestBuildByIds(
        @ApiParam("项目id", required = true)
        @PathParam("projectCode")
        projectCode: String,
        @ApiParam("流水线id列表", required = true)
        pipelineIds: List<String>
    ): Result<Map<String, PipelineLatestBuild>>

    @ApiOperation("第三方构建机Agent构建结束")
    @POST
    @Path("/projects/{projectCode}/pipelines/{pipelineId}/builds/{buildId}/seqs/{vmSeqId}/workerBuildFinish")
    fun workerBuildFinish(
        @ApiParam("项目id", required = true)
        @PathParam("projectCode")
        projectCode: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("构建ID", required = true)
        @PathParam("buildId")
        buildId: String,
        @ApiParam("Container序列号", required = true)
        @PathParam("vmSeqId")
        vmSeqId: String,
        @ApiParam("结果状态", required = true)
        simpleResult: SimpleResult
    ): Result<Boolean>

    @ApiOperation("获取构建详情")
    @POST
    @Path("/projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/seqs/{vmSeqId}/saveBuildVmInfo")
    fun saveBuildVmInfo(
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("构建ID", required = true)
        @PathParam("buildId")
        buildId: String,
        @ApiParam("构建 VM ID", required = true)
        @PathParam("vmSeqId")
        vmSeqId: String,
        @ApiParam("参数", required = true)
        vmInfo: VmInfo
    ): Result<Boolean>
}