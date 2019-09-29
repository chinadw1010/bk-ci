package com.tencent.devops.experience.api.service

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_USER_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_USER_ID_DEFAULT_VALUE
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.experience.pojo.Experience
import com.tencent.devops.experience.pojo.ExperienceServiceCreate
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Api(tags = ["SERVICE_EXPERIENCE"], description = "版本体验-发布体验")
@Path("/service/experiences")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ServiceExperienceResource {
    @ApiOperation("创建体验")
    @Path("/{projectId}/")
    @POST
    fun create(
        @ApiParam("用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("发布详情", required = true)
        experience: ExperienceServiceCreate
    ): Result<Boolean>

    @ApiOperation("获取体验列表数量")
    @Path("/list/count")
    @POST
    fun count(
        @ApiParam("项目ID集合", required = false)
        projectIds: Set<String>? = setOf(),
        @ApiParam("", required = false)
        @QueryParam("expired")
        expired: Boolean? = false
    ): Result<Map<String, Int>>

    @ApiOperation("获取体验详情")
    @Path("/projects/{projectId}/experienceIds/{experienceHashId}")
    @GET
    fun get(
        @ApiParam("用户ID", required = true, defaultValue = AUTH_HEADER_DEVOPS_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_DEVOPS_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("体验HashId", required = false)
        @PathParam("experienceHashId")
        experienceHashId: String
    ): Result<Experience>
}