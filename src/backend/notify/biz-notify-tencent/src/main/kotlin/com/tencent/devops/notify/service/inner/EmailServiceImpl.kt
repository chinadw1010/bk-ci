package com.tencent.devops.notify.service.inner

import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.common.notify.enums.EnumEmailFormat
import com.tencent.devops.common.notify.enums.EnumEmailType
import com.tencent.devops.common.notify.enums.EnumNotifyPriority
import com.tencent.devops.common.notify.enums.EnumNotifySource
import com.tencent.devops.common.notify.pojo.EmailNotifyPost
import com.tencent.devops.common.notify.utils.CommonUtils
import com.tencent.devops.model.notify.tables.records.TNotifyEmailRecord
import com.tencent.devops.notify.EXCHANGE_NOTIFY
import com.tencent.devops.notify.ROUTE_EMAIL
import com.tencent.devops.notify.dao.EmailNotifyDao
import com.tencent.devops.notify.model.EmailNotifyMessageWithOperation
import com.tencent.devops.notify.pojo.EmailNotifyMessage
import com.tencent.devops.notify.pojo.NotificationResponse
import com.tencent.devops.notify.pojo.NotificationResponseWithPage
import com.tencent.devops.notify.service.EmailService
import com.tencent.devops.notify.utils.TOFConfiguration
import com.tencent.devops.notify.utils.TOFService
import com.tencent.devops.notify.utils.TOFService.Companion.EMAIL_URL
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.stream.Collectors

@Service
class EmailServiceImpl @Autowired constructor(
    private val tofService: TOFService,
    private val emailNotifyDao: EmailNotifyDao,
    private val rabbitTemplate: RabbitTemplate,
    private val configuration: TOFConfiguration
) : EmailService {

    private val logger = LoggerFactory.getLogger(EmailServiceImpl::class.java)

    override fun sendMqMsg(message: EmailNotifyMessage) {
        rabbitTemplate.convertAndSend(EXCHANGE_NOTIFY, ROUTE_EMAIL, message)
    }

    override fun sendMessage(emailNotifyMessageWithOperation: EmailNotifyMessageWithOperation) {
        val emailNotifyPost = generateEmailNotifyPost(emailNotifyMessageWithOperation)
        if (emailNotifyPost == null) {
            logger.warn("EmailNotifyPost is empty after being processed, EmailNotifyMessageWithOperation: $emailNotifyMessageWithOperation")
            return
        }

        val retryCount = emailNotifyMessageWithOperation.retryCount
        val id = emailNotifyMessageWithOperation.id ?: UUIDUtil.generate()
        val tofConfs = configuration.getConfigurations(emailNotifyMessageWithOperation.tofSysId)
        val result = tofService.post(
            EMAIL_URL, emailNotifyPost, tofConfs!!)
        if (result.Ret == 0) {
            // 成功
            emailNotifyDao.insertOrUpdateEmailNotifyRecord(
                success = true,
                source = emailNotifyMessageWithOperation.source,
                id = id,
                retryCount = retryCount,
                lastErrorMessage = null,
                to = emailNotifyPost.to,
                cc = emailNotifyPost.cc,
                bcc = emailNotifyPost.bcc,
                sender = emailNotifyPost.from,
                title = emailNotifyPost.title,
                body = emailNotifyPost.content,
                type = emailNotifyPost.emailType,
                format = emailNotifyPost.bodyFormat,
                priority = emailNotifyPost.priority.toInt(),
                contentMd5 = emailNotifyPost.contentMd5,
                frequencyLimit = emailNotifyPost.frequencyLimit,
                tofSysId = tofConfs["sys-id"],
                fromSysId = emailNotifyPost.fromSysId
            )
        } else {
            // 写入失败记录
            emailNotifyDao.insertOrUpdateEmailNotifyRecord(
                success = false,
                source = emailNotifyMessageWithOperation.source,
                id = id,
                retryCount = retryCount,
                lastErrorMessage = result.ErrMsg,
                to = emailNotifyPost.to,
                cc = emailNotifyPost.cc,
                bcc = emailNotifyPost.bcc,
                sender = emailNotifyPost.from,
                title = emailNotifyPost.title,
                body = emailNotifyPost.content,
                type = emailNotifyPost.emailType,
                format = emailNotifyPost.bodyFormat,
                priority = emailNotifyPost.priority.toInt(),
                contentMd5 = emailNotifyPost.contentMd5,
                frequencyLimit = emailNotifyPost.frequencyLimit,
                tofSysId = tofConfs["sys-id"],
                fromSysId = emailNotifyPost.fromSysId
            )
            if (retryCount < 3) {
                // 开始重试
                reSendMessage(
                    post = emailNotifyPost,
                    source = emailNotifyMessageWithOperation.source,
                    retryCount = retryCount + 1,
                    id = id
                )
            }
        }
    }

    private fun reSendMessage(
        post: EmailNotifyPost,
        source: EnumNotifySource,
        retryCount: Int,
        id: String
    ) {
        val emailNotifyMessageWithOperation = EmailNotifyMessageWithOperation()
        emailNotifyMessageWithOperation.apply {
            this.id = id
            this.retryCount = retryCount
            this.source = source
            title = post.title
            body = post.content
            sender = post.from
            addAllReceivers(Sets.newHashSet(post.to.split(",")))
            addAllCcs(Sets.newHashSet(post.cc.split(",")))
            addAllBccs(Sets.newHashSet(post.bcc.split(",")))
            sender = post.from
            type = EnumEmailType.parse(post.emailType)
            format = EnumEmailFormat.parse(post.bodyFormat)
            priority = EnumNotifyPriority.parse(post.priority)
            frequencyLimit = post.frequencyLimit
            tofSysId = post.tofSysId
            fromSysId = post.fromSysId
        }
        rabbitTemplate.convertAndSend(
            EXCHANGE_NOTIFY,
            ROUTE_EMAIL,
            emailNotifyMessageWithOperation
        ) { message ->
            var delayTime = 0
            when (retryCount) {
                1 -> delayTime = 30000
                2 -> delayTime = 120000
                3 -> delayTime = 300000
            }
            if (delayTime > 0) {
                message.messageProperties.setHeader("x-delay", delayTime)
            }
            message
        }
    }

    private fun generateEmailNotifyPost(emailNotifyMessage: EmailNotifyMessage): EmailNotifyPost? {

        // 由于 soda 中 cc 与 bcc 基本没人使用，暂且不对 cc 和 bcc 作频率限制
        val contentMd5 = CommonUtils.getMessageContentMD5("", emailNotifyMessage.body)
        val tos = Lists.newArrayList(
            filterReceivers(
                receivers = emailNotifyMessage.getReceivers(),
                contentMd5 = contentMd5,
                frequencyLimit = emailNotifyMessage.frequencyLimit
            )
        )
        val ccs = emailNotifyMessage.getCc()
        val bccs = emailNotifyMessage.getBcc()
        if (tos.isEmpty() && ccs.isEmpty() && bccs.isEmpty()) {
            return null
        }

        val post = EmailNotifyPost()
        post.apply {
            title = emailNotifyMessage.title
            from = emailNotifyMessage.sender
            if (tos.isNotEmpty()) {
                to = tos.joinToString(",")
            }
            if (ccs.isNotEmpty()) {
                cc = ccs.joinToString(",")
            }
            if (bccs.isNotEmpty()) {
                bcc = bccs.joinToString(",")
            }
            priority = emailNotifyMessage.priority.getValue()
            bodyFormat = emailNotifyMessage.format.getValue()
            emailType = emailNotifyMessage.type.getValue()
            content = emailNotifyMessage.body
            this.contentMd5 = contentMd5
            frequencyLimit = emailNotifyMessage.frequencyLimit
            tofSysId = emailNotifyMessage.tofSysId
            fromSysId = emailNotifyMessage.fromSysId
        }

        return post
    }

    private fun filterReceivers(
        receivers: Set<String>,
        contentMd5: String,
        frequencyLimit: Int
    ): Set<String> {
        val filteredReceivers = HashSet(receivers)
        val filteredOutReceivers = HashSet<String>()
        if (frequencyLimit > 0) {
            val recordedReceivers = emailNotifyDao.getTosByContentMd5AndTime(
                contentMd5, (frequencyLimit * 60).toLong()
            )
            receivers.forEach { rec ->
                for (recordedRec in recordedReceivers) {
                    if (",$recordedRec,".contains(rec)) {
                        filteredReceivers.remove(rec)
                        filteredOutReceivers.add(rec)
                        break
                    }
                }
            }
            logger.warn("Filtered out receivers:$filteredOutReceivers")
        }
        return filteredReceivers
    }

    override fun listByCreatedTime(
        page: Int,
        pageSize: Int,
        success: Boolean?,
        fromSysId: String?,
        createdTimeSortOrder: String?
    ): NotificationResponseWithPage<EmailNotifyMessageWithOperation> {
        val count = emailNotifyDao.count(success, fromSysId)
        val result: List<NotificationResponse<EmailNotifyMessageWithOperation>> = if (count == 0) {
            listOf()
        } else {
            val emailRecords = emailNotifyDao.list(
                page = page,
                pageSize = pageSize,
                success = success,
                fromSysId = fromSysId,
                createdTimeSortOrder = createdTimeSortOrder
            )
            emailRecords.stream().map(this::parseFromTNotifyEmailToResponse)?.collect(Collectors.toList()) ?: listOf()
        }
        return NotificationResponseWithPage(
            count = count,
            page = page,
            pageSize = pageSize,
            data = result
        )
    }

    private fun parseFromTNotifyEmailToResponse(record: TNotifyEmailRecord): NotificationResponse<EmailNotifyMessageWithOperation> {
        val receivers: MutableSet<String> = mutableSetOf()
        if (!record.to.isNullOrEmpty())
            receivers.addAll(record.to.split(";"))
        val cc: MutableSet<String> = mutableSetOf()
        if (!record.cc.isNullOrEmpty())
            cc.addAll(record.cc.split(";"))
        val bcc: MutableSet<String> = mutableSetOf()
        if (!record.bcc.isNullOrEmpty())
            bcc.addAll(record.bcc.split(";"))

        val message = EmailNotifyMessageWithOperation()
        message.apply {
            frequencyLimit = record.frequencyLimit
            fromSysId = record.fromSysId
            tofSysId = record.tofSysId
            format = EnumEmailFormat.parse(record.format)
            type = EnumEmailType.parse(record.type)
            body = record.body
            sender = record.sender
            title = record.title
            priority = EnumNotifyPriority.parse(record.priority.toString())
            source = EnumNotifySource.parseName(record.source)
            retryCount = record.retryCount
            lastError = record.lastError
            addAllReceivers(receivers)
            addAllCcs(cc)
            addAllBccs(bcc)
        }

        return NotificationResponse(
            id = record.id,
            success = record.success,
            createdTime = if (record.createdTime == null) null else DateTimeUtil.convertLocalDateTimeToTimestamp(record.createdTime),
            updatedTime = if (record.updatedTime == null) null else DateTimeUtil.convertLocalDateTimeToTimestamp(record.updatedTime),
            contentMD5 = record.contentMd5,
            notificationMessage = message
        )
    }
}