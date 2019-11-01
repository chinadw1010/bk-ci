package com.tencent.devops.plugin.api

import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.plugin.pojo.ParametersInfo
import com.tencent.devops.plugin.pojo.tcm.TcmApp
import com.tencent.devops.plugin.pojo.tcm.TcmTemplate
import com.tencent.devops.plugin.pojo.tcm.TcmTemplateParam
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Api(tags = ["USER_TCM"], description = "用户-TCM原子相关接口")
@Path("/user/tcm")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface UserTcmResource {

    @ApiOperation("查询业务信息")
    @GET
    @Path("/apps")
    fun getApps(
        @ApiParam("用户ID", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String
    ): Result<List<TcmApp>>

    @ApiOperation("查询业务新手模板")
    @GET
    @Path("/templates")
    fun getTemplates(
        @ApiParam("用户ID", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam(value = "CC业务ID", required = true)
        @QueryParam("ccid")
        ccid: String,
        @ApiParam(value = "TCM业务ID", required = true)
        @QueryParam("tcmAppId")
        tcmAppId: String
    ): Result<List<TcmTemplate>>

    @ApiOperation("查询新手模板参数内容")
    @GET
    @Path("/templateInfo")
    fun getTemplateInfo(
        @ApiParam("用户ID", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam(value = "CC业务ID", required = true)
        @QueryParam("ccid")
        ccid: String,
        @ApiParam(value = "TCM业务ID", required = true)
        @QueryParam("tcmAppId")
        tcmAppId: String,
        @ApiParam(value = "模板ID", required = true)
        @QueryParam("templateId")
        templateId: String
    ): Result<List<TcmTemplateParam>>

    @ApiOperation("查询新手模板参数内容（新）")
    @GET
    @Path("/params")
    fun getParamsList(
        @ApiParam("用户ID", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam(value = "CC业务ID", required = true)
        @QueryParam("appId")
        appId: String,
        @ApiParam(value = "TCM业务ID", required = true)
        @QueryParam("tcmAppId")
        tcmAppId: String,
        @ApiParam(value = "模板ID", required = true)
        @QueryParam("templateId")
        templateId: String
    ): Result<List<ParametersInfo>>
}