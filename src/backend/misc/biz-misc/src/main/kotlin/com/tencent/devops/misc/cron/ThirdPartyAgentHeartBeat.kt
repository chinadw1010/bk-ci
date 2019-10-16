package com.tencent.devops.misc.cron

import com.tencent.devops.common.api.enum.AgentAction
import com.tencent.devops.common.api.enum.AgentStatus
import com.tencent.devops.common.api.util.HashUtil
import com.tencent.devops.common.misc.ThirdPartyAgentHeartbeatUtils
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.environment.THIRD_PARTY_AGENT_HEARTBEAT_INTERVAL
import com.tencent.devops.environment.pojo.enums.NodeStatus
import com.tencent.devops.misc.dao.NodeDao
import com.tencent.devops.misc.dao.ThirdPartyAgentDao
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ThirdPartyAgentHeartBeat @Autowired constructor(
    private val dslContext: DSLContext,
    private val thirdPartyAgentDao: ThirdPartyAgentDao,
    private val nodeDao: NodeDao,
    private val thirdPartyAgentHeartbeatUtils: ThirdPartyAgentHeartbeatUtils,
    private val redisOperation: RedisOperation
) {

    @Scheduled(initialDelay = 5000, fixedDelay = 3000)
    fun heartbeat() {
        val lockValue = redisOperation.get(LOCK_KEY)
        if (lockValue != null) {
            logger.info("get lock failed, skip")
            return
        } else {
            redisOperation.set(
                LOCK_KEY,
                LOCK_VALUE, 60)
        }
        try {
            checkOKAgent()

            checkExceptionAgent()

            checkUnimportAgent()
        } catch (t: Throwable) {
            logger.warn("Fail to check the third party agent heartbeat", t)
        } finally {
            redisOperation.delete(LOCK_KEY)
        }
    }

    private fun checkOKAgent() {
        val nodeRecords = thirdPartyAgentDao.listByStatus(dslContext,
            setOf(AgentStatus.IMPORT_OK))
        if (nodeRecords.isEmpty()) {
            return
        }
        nodeRecords.forEach { record ->
            val heartbeatTime = thirdPartyAgentHeartbeatUtils.getHeartbeatTime(record.id, record.projectId) ?: return@forEach

            val escape = System.currentTimeMillis() - heartbeatTime
            if (escape > 10 * THIRD_PARTY_AGENT_HEARTBEAT_INTERVAL * 1000) {
                logger.warn("The agent(${HashUtil.encodeLongId(record.id)}) has not receive the heart for $escape ms, mark it as exception")
                dslContext.transaction { configuration ->
                    val context = DSL.using(configuration)
                    thirdPartyAgentDao.updateStatus(context, record.id, null, record.projectId, AgentStatus.IMPORT_EXCEPTION)
                    thirdPartyAgentDao.addAgentAction(context, record.projectId, record.id, AgentAction.OFFLINE.name)
                    if (record.nodeId == null) {
                        logger.info("[${record.projectId}|${record.id}|${record.ip}] The node id is null")
                        return@transaction
                    }
                    val nodeRecord = nodeDao.get(context, record.projectId, record.nodeId)
                    if (nodeRecord == null || nodeRecord.nodeStatus == NodeStatus.DELETED.name) {
                        deleteAgent(context, record.projectId, record.id)
                    }
                    nodeDao.updateNodeStatus(context, record.nodeId, NodeStatus.ABNORMAL)
                }
            }
        }
    }

    private fun checkUnimportAgent() {
        val nodeRecords = thirdPartyAgentDao.listByStatus(dslContext,
            setOf(AgentStatus.UN_IMPORT_OK))
        if (nodeRecords.isEmpty()) {
            return
        }
        nodeRecords.forEach { record ->
            val heartbeatTime = thirdPartyAgentHeartbeatUtils.getHeartbeatTime(record.id, record.projectId) ?: return@forEach
            val escape = System.currentTimeMillis() - heartbeatTime
            if (escape > 2 * THIRD_PARTY_AGENT_HEARTBEAT_INTERVAL * 1000) {
                logger.warn("The un-import agent(${HashUtil.encodeLongId(record.id)}) has not receive the heart for $escape ms, mark it as exception")
                dslContext.transaction { configuration ->
                    val context = DSL.using(configuration)
                    thirdPartyAgentDao.updateStatus(context, record.id, null, record.projectId, AgentStatus.UN_IMPORT, AgentStatus.UN_IMPORT_OK)
                }
            }
        }
    }

    private fun checkExceptionAgent() {
        // Trying to delete the third party agents
        val exceptionRecord = thirdPartyAgentDao.listByStatus(dslContext,
            setOf(AgentStatus.IMPORT_EXCEPTION))
        if (exceptionRecord.isEmpty()) {
            return
        }

        exceptionRecord.forEach { record ->
            if (record.nodeId == null) {
                return@forEach
            }
            val nodeRecord = nodeDao.get(dslContext, record.projectId, record.nodeId)
            if (nodeRecord == null || nodeRecord.nodeStatus == NodeStatus.DELETED.name) {
                deleteAgent(dslContext, record.projectId, record.id)
            }
        }
    }

    private fun deleteAgent(dslContext: DSLContext, projectId: String, agentId: Long) {
        logger.info("Trying to delete the agent($agentId) of project($projectId)")
        thirdPartyAgentDao.delete(dslContext, agentId, projectId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ThirdPartyAgentHeartBeat::class.java)
        private const val LOCK_KEY = "env_cron_agent_heartbeat_check"
        private const val LOCK_VALUE = "env_cron_agent_heartbeat_check"
    }
}