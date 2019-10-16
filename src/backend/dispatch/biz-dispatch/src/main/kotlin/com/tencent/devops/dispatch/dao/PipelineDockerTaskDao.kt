package com.tencent.devops.dispatch.dao

import com.tencent.devops.common.api.pojo.Zone
import com.tencent.devops.dispatch.pojo.enums.PipelineTaskStatus
import com.tencent.devops.model.dispatch.tables.TDispatchPipelineDockerTask
import com.tencent.devops.model.dispatch.tables.records.TDispatchPipelineDockerTaskRecord
import org.jooq.DSLContext
import org.jooq.DatePart
import org.jooq.Field
import org.jooq.Result
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
class PipelineDockerTaskDao {

    fun insertTask(
        dslContext: DSLContext,
        projectId: String,
        agentId: String,
        pipelineId: String,
        buildId: String,
        vmSeqId: Int,
        status: PipelineTaskStatus,
        secretKey: String,
        imageName: String,
        hostTag: String,
        channelCode: String,
        zone: String?,
        registryUser: String?,
        registryPwd: String?,
        imageType: String?
    ): Int {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            val now = LocalDateTime.now()
            val preRecord = dslContext.selectFrom(this).where(BUILD_ID.eq(buildId)).and(VM_SEQ_ID.eq(vmSeqId)).fetchAny()
            if (preRecord != null) { // 支持更新，让用户进行步骤重试时继续能使用
                dslContext.update(this)
                        .set(AGENT_ID, agentId)
                        .set(STATUS, status.status)
                        .set(SECRET_KEY, secretKey)
                        .set(IMAGE_NAME, imageName)
                        .set(CHANNEL_CODE, channelCode)
                        .set(HOST_TAG, hostTag)
                        .set(CREATED_TIME, now)
                        .set(UPDATED_TIME, now)
                        .set(ZONE, zone)
                        .set(REGISTRY_USER, registryUser)
                        .set(REGISTRY_PWD, registryPwd)
                        .set(IMAGE_TYPE, imageType)
                        .where(ID.eq(preRecord.id)).execute()
                return preRecord.id
            }
            return dslContext.insertInto(this,
                    PROJECT_ID,
                    AGENT_ID,
                    PIPELINE_ID,
                    BUILD_ID,
                    VM_SEQ_ID,
                    STATUS,
                    SECRET_KEY,
                    IMAGE_NAME,
                    CHANNEL_CODE,
                    HOST_TAG,
                    CREATED_TIME,
                    UPDATED_TIME,
                    ZONE,
                    REGISTRY_USER,
                    REGISTRY_PWD,
                    IMAGE_TYPE)
                    .values(
                            projectId,
                            agentId,
                            pipelineId,
                            buildId,
                            vmSeqId,
                            status.status,
                            secretKey,
                            imageName,
                            channelCode,
                            hostTag,
                            now,
                            now,
                            zone,
                            registryUser,
                            registryPwd,
                            imageType
                    )
                    .returning(ID)
                    .fetchOne().id
        }
    }

    fun updateStatus(
        dslContext: DSLContext,
        buildId: String,
        vmSeqId: Int,
        status: PipelineTaskStatus
    ): Boolean {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.update(this)
                    .set(STATUS, status.status)
                    .set(UPDATED_TIME, LocalDateTime.now())
                    .where(BUILD_ID.eq(buildId))
                    .and(VM_SEQ_ID.eq(vmSeqId))
                    .execute() == 1
        }
    }

    fun updateContainerId(
        dslContext: DSLContext,
        buildId: String,
        vmSeqId: Int,
        containerId: String,
        hostTag: String?
    ): Boolean {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            val update = dslContext.update(this)
                .set(CONTAINER_ID, containerId)
                .set(UPDATED_TIME, LocalDateTime.now())

            if (!hostTag.isNullOrBlank()) {
                update.set(HOST_TAG, hostTag)
            }

            return update.where(BUILD_ID.eq(buildId))
                .and(VM_SEQ_ID.eq(vmSeqId))
                .execute() == 1
        }
    }

    fun updateStatusAndTag(
        dslContext: DSLContext,
        buildId: String,
        vmSeqId: Int,
        status: PipelineTaskStatus,
        hostTag: String
    ): Boolean {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.update(this)
                    .set(STATUS, status.status)
                    .set(HOST_TAG, hostTag)
                    .set(UPDATED_TIME, LocalDateTime.now())
                    .where(BUILD_ID.eq(buildId))
                    .and(VM_SEQ_ID.eq(vmSeqId))
                    .execute() == 1
        }
    }

    fun listTasks(
        dslContext: DSLContext,
        buildId: String
    ): Result<TDispatchPipelineDockerTaskRecord> {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.selectFrom(this)
                    .where(BUILD_ID.eq(buildId))
                    .fetch()
        }
    }

    fun getTask(
        dslContext: DSLContext,
        buildId: String,
        vmSeqId: Int
    ): TDispatchPipelineDockerTaskRecord? {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.selectFrom(this)
                    .where(BUILD_ID.eq(buildId))
                    .and(VM_SEQ_ID.eq(vmSeqId))
                    .fetchOne()
        }
    }

    fun getQueueTasksExcludeProj(
        dslContext: DSLContext,
        projectIds: Set<String>
    ): Result<TDispatchPipelineDockerTaskRecord> {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.selectFrom(this)
                    .where(STATUS.eq(PipelineTaskStatus.QUEUE.status))
                    .and(HOST_TAG.eq(""))
                    .and(ZONE.eq("")).and(PROJECT_ID.notIn(projectIds))
                    .orderBy(UPDATED_TIME.asc())
                    .fetch()
        }
    }

    fun getQueueTasksByProj(
        dslContext: DSLContext,
        projectIds: Set<String>
    ): Result<TDispatchPipelineDockerTaskRecord> {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.selectFrom(this)
                    .where(STATUS.eq(PipelineTaskStatus.QUEUE.status))
                    .and(HOST_TAG.eq(""))
                    .and(ZONE.eq("")).and(PROJECT_ID.`in`(projectIds))
                    .orderBy(UPDATED_TIME.asc())
                    .fetch()
        }
    }

    fun getQueueTasksExcludeProj(
        dslContext: DSLContext,
        projectIds: Set<String>,
        zone: Zone
    ): Result<TDispatchPipelineDockerTaskRecord> {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.selectFrom(this)
                    .where(STATUS.eq(PipelineTaskStatus.QUEUE.status))
                    .and(ZONE.eq(zone.name))
                    .and(HOST_TAG.eq("")).and(PROJECT_ID.notIn(projectIds))
                    .orderBy(UPDATED_TIME.asc())
                    .fetch()
        }
    }

    fun getQueueTasksByProj(
        dslContext: DSLContext,
        projectIds: Set<String>,
        zone: Zone
    ): Result<TDispatchPipelineDockerTaskRecord> {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.selectFrom(this)
                    .where(STATUS.eq(PipelineTaskStatus.QUEUE.status))
                    .and(ZONE.eq(zone.name))
                    .and(HOST_TAG.eq("")).and(PROJECT_ID.`in`(projectIds))
                    .orderBy(UPDATED_TIME.asc())
                    .fetch()
        }
    }

    fun getQueueTasksExcludeProj(
        dslContext: DSLContext,
        projectIds: Set<String>,
        hostTag: String
    ): Result<TDispatchPipelineDockerTaskRecord> {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.selectFrom(this)
                    .where(STATUS.eq(PipelineTaskStatus.QUEUE.status))
                    .and(HOST_TAG.eq(hostTag)).and(PROJECT_ID.notIn(projectIds))
                    .orderBy(UPDATED_TIME.asc())
                    .fetch()
        }
    }

    fun getQueueTasksByProj(
        dslContext: DSLContext,
        projectIds: Set<String>,
        hostTag: String
    ): Result<TDispatchPipelineDockerTaskRecord> {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.selectFrom(this)
                    .where(STATUS.eq(PipelineTaskStatus.QUEUE.status))
                    .and(HOST_TAG.eq(hostTag)).and(PROJECT_ID.`in`(projectIds))
                    .orderBy(UPDATED_TIME.asc())
                    .fetch()
        }
    }

    fun getDoneTasks(
        dslContext: DSLContext,
        hostTag: String
    ): Result<TDispatchPipelineDockerTaskRecord> {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            val statusCond = STATUS.eq(PipelineTaskStatus.DONE.status).or(STATUS.eq(PipelineTaskStatus.FAILURE.status))
            return dslContext.selectFrom(this)
                    .where(statusCond)
                    .and(HOST_TAG.eq(hostTag))
                    .orderBy(UPDATED_TIME.asc())
                    .fetch()
        }
    }

    fun deleteTask(dslContext: DSLContext, id: Int) {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            dslContext.deleteFrom(this)
                    .where(ID.eq(id))
                    .execute()
        }
    }

    fun getTimeOutTask(dslContext: DSLContext): Result<TDispatchPipelineDockerTaskRecord> {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.selectFrom(this)
                    .where(timestampDiff(org.jooq.DatePart.DAY, UPDATED_TIME.cast(java.sql.Timestamp::class.java)).greaterOrEqual(2))
                    .fetch()
        }
    }

    fun updateTimeOutTask(dslContext: DSLContext): Boolean {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.update(this)
                    .set(STATUS, PipelineTaskStatus.FAILURE.status)
                    .where(timestampDiff(org.jooq.DatePart.DAY, UPDATED_TIME.cast(java.sql.Timestamp::class.java)).greaterOrEqual(2))
                    .execute() == 1
        }
    }

    fun getUnclaimedHostTask(dslContext: DSLContext): Result<TDispatchPipelineDockerTaskRecord> {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.selectFrom(this)
                    .where(timestampDiff(org.jooq.DatePart.SECOND, UPDATED_TIME.cast(java.sql.Timestamp::class.java)).greaterOrEqual(20))
                    .and(STATUS.eq(PipelineTaskStatus.QUEUE.status))
                    .and(HOST_TAG.isNotNull).and(HOST_TAG.notEqual(""))
                    .fetch()
        }
    }

    fun clearHostTagForUnclaimedHostTask(dslContext: DSLContext): Boolean {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.update(this)
                    .set(HOST_TAG, "")
                    .where(timestampDiff(org.jooq.DatePart.SECOND, UPDATED_TIME.cast(java.sql.Timestamp::class.java)).greaterOrEqual(20))
                    .and(STATUS.eq(PipelineTaskStatus.QUEUE.status))
                    .and(HOST_TAG.isNotNull).and(HOST_TAG.notEqual(""))
                    .execute() == 1
        }
    }

    fun getUnclaimedZoneTask(dslContext: DSLContext): Result<TDispatchPipelineDockerTaskRecord> {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.selectFrom(this)
                    .where(timestampDiff(org.jooq.DatePart.SECOND, UPDATED_TIME.cast(java.sql.Timestamp::class.java)).greaterOrEqual(40))
                    .and(STATUS.eq(PipelineTaskStatus.QUEUE.status))
                    .and(ZONE.isNotNull).and(ZONE.notEqual(""))
                    .fetch()
        }
    }

    fun resetZoneForUnclaimedZoneTask(dslContext: DSLContext): Boolean {
        with(TDispatchPipelineDockerTask.T_DISPATCH_PIPELINE_DOCKER_TASK) {
            return dslContext.update(this)
                    .set(ZONE, Zone.SHENZHEN.name)
                    .where(timestampDiff(org.jooq.DatePart.SECOND, UPDATED_TIME.cast(java.sql.Timestamp::class.java)).greaterOrEqual(40))
                    .and(STATUS.eq(PipelineTaskStatus.QUEUE.status))
                    .and(ZONE.isNotNull).and(ZONE.notEqual("")).and(ZONE.notEqual(Zone.SHENZHEN.name))
                    .execute() == 1
        }
    }

    fun timestampDiff(part: DatePart, t1: Field<Timestamp>): Field<Int> {
        return DSL.field("timestampdiff({0}, {1}, NOW())",
                Int::class.java, DSL.keyword(part.toSQL()), t1)
    }
}

/**

DROP TABLE IF EXISTS `T_DISPATCH_PIPELINE_DOCKER_TASK`;
CREATE TABLE `T_DISPATCH_PIPELINE_DOCKER_TASK` (
`ID` int(11) NOT NULL AUTO_INCREMENT,
`PROJECT_ID` varchar(64) NOT NULL,
`AGENT_ID` varchar(32) NOT NULL,
`PIPELINE_ID` varchar(32) NOT NULL DEFAULT '',
`BUILD_ID` varchar(32) NOT NULL,
`VM_SEQ_ID` int(20) NOT NULL,
`STATUS` int(11) NOT NULL,
`SECRET_KEY` varchar(128) NOT NULL,
`IMAGE_NAME` varchar(1024) NOT NULL,
`CHANNEL_CODE` varchar(128) NULL
`HOST_TAG` varchar(128) NULL,
`CONTAINER_ID` varchar(128) NULL,
`CREATED_TIME` datetime NOT NULL,
`UPDATED_TIME` datetime NOT NULL,
`CHANNEL_CODE` varchar(128) NULL,
PRIMARY KEY (`ID`),
UNIQUE KEY `BUILD_ID` (`BUILD_ID`,`VM_SEQ_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=93 DEFAULT CHARSET=utf8;

ALTER TABLE T_DISPATCH_PIPELINE_DOCKER_TASK ADD COLUMN `ZONE` varchar(128) NULL;
ALTER TABLE T_DISPATCH_PIPELINE_DOCKER_BUILD ADD COLUMN `ZONE` varchar(128) NULL;
ALTER TABLE T_DISPATCH_PIPELINE_DOCKER_DEBUG ADD COLUMN `ZONE` varchar(128) NULL;

ALTER TABLE T_DISPATCH_PIPELINE_DOCKER_TASK ADD COLUMN `REGISTRY_USER` varchar(128) NULL;
ALTER TABLE T_DISPATCH_PIPELINE_DOCKER_TASK ADD COLUMN `REGISTRY_PWD` varchar(128) NULL;
ALTER TABLE T_DISPATCH_PIPELINE_DOCKER_TASK ADD COLUMN `IMAGE_TYPE` varchar(128) NULL;

* */