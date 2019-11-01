package com.tencent.devops.plugin.dao

import com.tencent.devops.model.plugin.tables.TPluginWetestEamilGroup
import com.tencent.devops.model.plugin.tables.records.TPluginWetestEamilGroupRecord
import org.jooq.DSLContext
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class WetestEmailGroupDao {

    fun insert(
        dslContext: DSLContext,
        projectId: String,
        name: String,
        userInternal: String?,
        qqExternal: String?,
        description: String?,
        wetestGroupId: String?,
        wetestGroupName: String?
    ): Int {

        with(TPluginWetestEamilGroup.T_PLUGIN_WETEST_EAMIL_GROUP) {
            val data = dslContext.insertInto(this,
                    PROJECT_ID,
                    NAME,
                    USER_INTERNAL,
                    QQ_EXTERNAL,
                    DESCRIPTION,
                    CREATED_TIME,
                    UPDATED_TIME,
                    WETEST_GROUP_ID,
                    WETEST_GROUP_NAME
            )
                    .values(projectId,
                            name,
                            userInternal,
                            qqExternal,
                            description,
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                            wetestGroupId,
                            wetestGroupName)
                    .returning(ID)
                    .fetchOne()
            return data.id
        }
    }

    fun update(
        dslContext: DSLContext,
        projectId: String,
        id: Int,
        name: String,
        userInternal: String?,
        qqExternal: String?,
        description: String?,
        wetestGroupId: String?,
        wetestGroupName: String?
    ) {
        with(TPluginWetestEamilGroup.T_PLUGIN_WETEST_EAMIL_GROUP) {
            dslContext.update(this)
                    .set(NAME, name)
                    .set(USER_INTERNAL, userInternal)
                    .set(QQ_EXTERNAL, qqExternal)
                    .set(DESCRIPTION, description)
                    .set(UPDATED_TIME, LocalDateTime.now())
                    .set(WETEST_GROUP_ID, wetestGroupId)
                    .set(WETEST_GROUP_NAME, wetestGroupName)
                    .where(ID.eq(id)).and(PROJECT_ID.eq(projectId))
                    .execute()
        }
    }

    fun getList(
        dslContext: DSLContext,
        projectId: String,
        page: Int,
        pageSize: Int
    ): Result<TPluginWetestEamilGroupRecord>? {
        with(TPluginWetestEamilGroup.T_PLUGIN_WETEST_EAMIL_GROUP) {
            return dslContext.selectFrom(this).where(PROJECT_ID.eq(projectId))
                    .orderBy(UPDATED_TIME.desc())
                    .limit(pageSize).offset((page - 1) * pageSize)
                    .fetch()
        }
    }

    fun getCount(dslContext: DSLContext, projectId: String): Int {
        with(TPluginWetestEamilGroup.T_PLUGIN_WETEST_EAMIL_GROUP) {
            return dslContext.selectCount().from(this).where(PROJECT_ID.eq(projectId))
                    .fetchOne().get(0) as Int
        }
    }

    fun getRecord(
        dslContext: DSLContext,
        projectId: String,
        id: Int
    ): TPluginWetestEamilGroupRecord? {
        with(TPluginWetestEamilGroup.T_PLUGIN_WETEST_EAMIL_GROUP) {
            return dslContext.selectFrom(this)
                    .where(ID.eq(id)).and(PROJECT_ID.eq(projectId))
                    .fetchOne()
        }
    }

    fun getByName(
        dslContext: DSLContext,
        projectId: String,
        name: String
    ): TPluginWetestEamilGroupRecord? {
        with(TPluginWetestEamilGroup.T_PLUGIN_WETEST_EAMIL_GROUP) {
            return dslContext.selectFrom(this)
                    .where(PROJECT_ID.eq(projectId)).and(NAME.eq(name))
                    .fetchOne()
        }
    }

    fun delete(
        dslContext: DSLContext,
        projectId: String,
        id: Int
    ) {
        with(TPluginWetestEamilGroup.T_PLUGIN_WETEST_EAMIL_GROUP) {
            dslContext.deleteFrom(this)
                    .where(ID.eq(id)).and(PROJECT_ID.eq(projectId))
                    .execute()
        }
    }
}

/*

DROP TABLE IF EXISTS `devops_plugin`.`T_PLUGIN_WETEST_EAMIL_GROUP`;
CREATE TABLE `T_PLUGIN_WETEST_EAMIL_GROUP` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PROJECT_ID` varchar(128) NOT NULL,
  `NAME` varchar(128) NOT NULL,
  `USER_INTERNAL` longtext NULL,
  `QQ_EXTERNAL` longtext NULL,
  `DESCRIPTION` varchar(1024) NULL,
  `CREATED_TIME` datetime NOT NULL,
  `UPDATED_TIME` datetime NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=12522 DEFAULT CHARSET=utf8;

ALTER TABLE T_PLUGIN_WETEST_EAMIL_GROUP ADD COLUMN `WETEST_GROUP_ID` VARCHAR(128) NULL;
ALTER TABLE T_PLUGIN_WETEST_EAMIL_GROUP ADD COLUMN `WETEST_GROUP_NAME` VARCHAR(128) NULL;

*/