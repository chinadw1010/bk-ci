package com.tencent.devops.support.api.op

import com.tencent.devops.common.api.pojo.MessageCodeDetail
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.support.model.code.AddMessageCodeRequest
import com.tencent.devops.support.model.code.MessageCodeResp
import com.tencent.devops.support.model.code.UpdateMessageCodeRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Api(tags = ["OP_MESSAGE_CODE"], description = "OP-返回码")
@Path("/op/message/codes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface OpMessageCodeResource {

    @ApiOperation("获取返回码信息")
    @GET
    @Path("/")
    fun getMessageCodeDetails(
        @ApiParam(value = "返回码", required = false)
        @QueryParam("messageCode")
        messageCode: String?,
        @ApiParam("页码", required = false)
        @QueryParam("page")
        page: Int?,
        @ApiParam("每页数量", required = false)
        @QueryParam("pageSize")
        pageSize: Int?
    ): Result<MessageCodeResp>

    @ApiOperation("获取返回码信息")
    @GET
    @Path("/{messageCode}")
    fun getMessageCodeDetail(
        @ApiParam(value = "返回码", required = true)
        @PathParam("messageCode")
        messageCode: String
    ): Result<MessageCodeDetail?>

    @ApiOperation("刷新返回码在redis的缓存")
    @GET
    @Path("/{messageCode}/refresh")
    fun refreshMessageCodeCache(
        @ApiParam(value = "返回码", required = true)
        @PathParam("messageCode")
        messageCode: String
    ): Result<Boolean>

    @ApiOperation("新增返回码信息")
    @POST
    @Path("/")
    fun addMessageCodeDetail(
        @ApiParam(value = "返回码新增请求报文体", required = true)
        addMessageCodeRequest: AddMessageCodeRequest
    ): Result<Boolean>

    @ApiOperation("更新返回码信息")
    @PUT
    @Path("/{messageCode}")
    fun updateMessageCodeDetail(
        @ApiParam(value = "返回码", required = true)
        @PathParam("messageCode")
        messageCode: String,
        @ApiParam(value = "返回码更新请求报文体", required = true)
        updateMessageCodeRequest: UpdateMessageCodeRequest
    ): Result<Boolean>
}