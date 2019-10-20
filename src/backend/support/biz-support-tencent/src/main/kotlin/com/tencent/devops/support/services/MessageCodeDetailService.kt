package com.tencent.devops.support.services

import com.tencent.devops.common.api.constant.BCI_CODE_PREFIX
import com.tencent.devops.common.api.constant.CommonMessageCode
import com.tencent.devops.common.api.pojo.MessageCodeDetail
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.common.service.utils.MessageCodeUtil
import com.tencent.devops.support.dao.MessageCodeDetailDao
import com.tencent.devops.support.model.code.AddMessageCodeRequest
import com.tencent.devops.support.model.code.MessageCodeResp
import com.tencent.devops.support.model.code.UpdateMessageCodeRequest
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class MessageCodeDetailService @Autowired constructor(
    private val dslContext: DSLContext,
    private val redisOperation: RedisOperation,
    private val messageCodeDetailDao: MessageCodeDetailDao
) {
    private val logger = LoggerFactory.getLogger(MessageCodeDetailService::class.java)

    @PostConstruct
    fun initMessageCodeDetail() {
        logger.info("begin init messageCodeDetail")
        val messageCodeDetailList = messageCodeDetailDao.getMessageCodeDetails(dslContext, null, null, null)?.map { messageCodeDetailDao.convert(it) }
        messageCodeDetailList?.forEach {
            redisOperation.set(key = BCI_CODE_PREFIX + it.messageCode, value = JsonUtil.getObjectMapper().writeValueAsString(it), expired = false)
        }
    }

    /**
     * 获取code信息列表
     */
    fun getMessageCodeDetails(messageCode: String?, page: Int?, pageSize: Int?): Result<MessageCodeResp> {
        logger.info("messageCode is: $messageCode,page is: $page,pageSize is: $pageSize")
        val messageCodeDetailList = messageCodeDetailDao.getMessageCodeDetails(dslContext, messageCode, page, pageSize)?.map { messageCodeDetailDao.convert(it) }
        // 处理分页逻辑
        val totalSize = messageCodeDetailDao.getMessageCodeDetailCount(dslContext, messageCode)
        val totalPage = PageUtil.calTotalPage(pageSize, totalSize)
        return Result(MessageCodeResp(totalSize, page, pageSize, totalPage, messageCodeDetailList))
    }

    /**
     * 获取code信息详细信息
     */
    fun getMessageCodeDetail(messageCode: String): Result<MessageCodeDetail?> {
        logger.info("messageCode is: $messageCode")
        val messageCodeDetailRecord = messageCodeDetailDao.getMessageCodeDetail(dslContext, messageCode)
        return if (null == messageCodeDetailRecord) {
            Result(data = null)
        } else {
            Result(messageCodeDetailDao.convert(messageCodeDetailRecord))
        }
    }

    /**
     * 刷新code信息在redis的缓存
     */
    fun refreshMessageCodeCache(messageCode: String): Result<Boolean> {
        logger.info("messageCode is: $messageCode")
        val messageCodeDetailResult = getMessageCodeDetail(messageCode)
        val messageCodeDetail = messageCodeDetailResult.data
        return if (null != messageCodeDetail) {
            redisOperation.set(key = BCI_CODE_PREFIX + messageCode, value = JsonUtil.getObjectMapper().writeValueAsString(messageCodeDetailResult.data), expired = false)
            Result(data = true)
        } else {
            MessageCodeUtil.generateResponseDataObject(CommonMessageCode.PARAMETER_IS_INVALID, arrayOf(messageCode))
        }
    }

    /**
     * 添加code信息信息
     */
    fun addMessageCodeDetail(addMessageCodeRequest: AddMessageCodeRequest): Result<Boolean> {
        logger.info("addMessageCodeRequest is: $addMessageCodeRequest")
        val messageCodeDetailResult = getMessageCodeDetail(addMessageCodeRequest.messageCode)
        // 判断code信息是否存在，存在才添加
        val messageCode = addMessageCodeRequest.messageCode
        if (null != messageCodeDetailResult.data) return MessageCodeUtil.generateResponseDataObject(CommonMessageCode.PARAMETER_IS_EXIST, arrayOf(messageCode), false)
        val id = UUIDUtil.generate()
        messageCodeDetailDao.addMessageCodeDetail(dslContext, id, addMessageCodeRequest)
        val messageCodeDetail = MessageCodeDetail(
            id = id,
            messageCode = messageCode,
            moduleCode = addMessageCodeRequest.moduleCode.code,
            messageDetailZhCn = addMessageCodeRequest.messageDetailZhCn,
            messageDetailZhTw = addMessageCodeRequest.messageDetailZhTw,
            messageDetailEn = addMessageCodeRequest.messageDetailEn
        )
        redisOperation.set(key = BCI_CODE_PREFIX + messageCode, value = JsonUtil.getObjectMapper().writeValueAsString(messageCodeDetail), expired = false)
        return Result(data = true)
    }

    /**
     * 更新code信息信息
     */
    fun updateMessageCodeDetail(messageCode: String, updateMessageCodeRequest: UpdateMessageCodeRequest): Result<Boolean> {
        logger.info("messageCode is: $messageCode,updateMessageCodeRequest is: $updateMessageCodeRequest")
        val messageCodeDetailResult = getMessageCodeDetail(messageCode)
        // 判断code信息是否存在，存在才更新
        val messageCodeDetail = messageCodeDetailResult.data ?: return MessageCodeUtil.generateResponseDataObject(CommonMessageCode.PARAMETER_IS_INVALID, arrayOf(messageCode), false)
        messageCodeDetailDao.updateMessageCodeDetail(dslContext, messageCode, updateMessageCodeRequest.messageDetailZhCn, updateMessageCodeRequest.messageDetailZhTw, updateMessageCodeRequest.messageDetailEn)
        messageCodeDetail.messageDetailZhCn = updateMessageCodeRequest.messageDetailZhCn
        messageCodeDetail.messageDetailZhTw = updateMessageCodeRequest.messageDetailZhTw
        messageCodeDetail.messageDetailEn = updateMessageCodeRequest.messageDetailEn
        redisOperation.set(key = BCI_CODE_PREFIX + messageCode, value = JsonUtil.getObjectMapper().writeValueAsString(messageCodeDetail), expired = false)
        return Result(data = true)
    }
}