package com.tencent.devops.quality.dao

import com.tencent.devops.model.quality.tables.TGroup
import com.tencent.devops.model.quality.tables.records.TGroupRecord
import org.jooq.DSLContext
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import javax.ws.rs.NotFoundException

@Repository
class GroupDao {

    fun list(dslContext: DSLContext, projectId: String, offset: Int, limit: Int): Result<TGroupRecord> {
        with(TGroup.T_GROUP) {
            return dslContext.selectFrom(this)
                    .where(PROJECT_ID.eq(projectId))
                    .orderBy(CREATE_TIME.desc())
                    .offset(offset)
                    .limit(limit)
                    .fetch()
        }
    }

    fun list(dslContext: DSLContext, groupIds: Set<Long>): Result<TGroupRecord> {
        with(TGroup.T_GROUP) {
            return dslContext.selectFrom(this)
                    .where(ID.`in`(groupIds))
                    .fetch()
        }
    }

    fun count(dslContext: DSLContext, projectId: String): Long {
        with(TGroup.T_GROUP) {
            return dslContext.selectCount()
                    .from(this)
                    .where(PROJECT_ID.eq(projectId))
                    .fetchOne(0, Long::class.java)
        }
    }

    fun has(dslContext: DSLContext, projectId: String, name: String): Boolean {
        with(TGroup.T_GROUP) {
            return dslContext.selectCount()
                    .from(this)
                    .where(PROJECT_ID.eq(projectId))
                    .and(NAME.eq(name))
                    .fetchOne(0, Long::class.java) != 0L
        }
    }

    fun has(dslContext: DSLContext, projectId: String, name: String, excludeId: Long): Boolean {
        with(TGroup.T_GROUP) {
            return dslContext.selectCount()
                    .from(this)
                    .where(PROJECT_ID.eq(projectId))
                    .and(NAME.eq(name))
                    .and(ID.notEqual(excludeId))
                    .fetchOne(0, Long::class.java) != 0L
        }
    }

    fun getOrNull(dslContext: DSLContext, groupId: Long): TGroupRecord? {
        with(TGroup.T_GROUP) {
            return dslContext.selectFrom(this)
                    .where(ID.eq(groupId))
                    .fetchOne()
        }
    }

    fun get(dslContext: DSLContext, groupId: Long): TGroupRecord {
        with(TGroup.T_GROUP) {
            return dslContext.selectFrom(this)
                    .where(ID.eq(groupId))
                    .fetchOne() ?: throw NotFoundException("GroupId: $groupId not found")
        }
    }

    fun create(
        dslContext: DSLContext,
        projectId: String,
        name: String,
        innerUsers: String,
        innerUsersCount: Int,
        outerUsers: String,
        outerUsersCount: Int,
        remark: String?,
        creator: String,
        updator: String
    ): Long {
        val now = LocalDateTime.now()
        with(TGroup.T_GROUP) {
            val record = dslContext.insertInto(this,
                    PROJECT_ID,
                    NAME,
                    INNER_USERS,
                    INNER_USERS_COUNT,
                    OUTER_USERS,
                    OUTER_USERS_COUNT,
                    REMARK,
                    CREATOR,
                    UPDATOR,
                    CREATE_TIME,
                    UPDATE_TIME
            ).values(
                    projectId,
                    name,
                    innerUsers,
                    innerUsersCount,
                    outerUsers,
                    outerUsersCount,
                    remark,
                    creator,
                    updator,
                    now,
                    now)
                    .returning(ID)
                    .fetchOne()
            return record.id
        }
    }

    fun update(
        dslContext: DSLContext,
        id: Long,
        name: String,
        innerUsers: String,
        innerUsersCount: Int,
        outerUsers: String,
        outerUsersCount: Int,
        remark: String?,
        updator: String
    ) {
        val now = LocalDateTime.now()
        with(TGroup.T_GROUP) {
            dslContext.update(this)
                    .set(NAME, name)
                    .set(INNER_USERS, innerUsers)
                    .set(INNER_USERS_COUNT, innerUsersCount)
                    .set(OUTER_USERS_COUNT, outerUsersCount)
                    .set(OUTER_USERS, outerUsers)
                    .set(REMARK, remark)
                    .set(UPDATOR, updator)
                    .set(UPDATE_TIME, now)
                    .where(ID.eq(id))
                    .execute()
        }
    }

    fun delete(
        dslContext: DSLContext,
        id: Long
    ) {
        with(TGroup.T_GROUP) {
            dslContext.deleteFrom(this)
                    .where(ID.eq(id))
                    .execute()
        }
    }
}