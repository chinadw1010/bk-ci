package com.tencent.devops.plugin.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.JsonParser
import com.tencent.devops.artifactory.api.service.ServiceArtifactoryResource
import com.tencent.devops.artifactory.pojo.FileDetail
import com.tencent.devops.artifactory.pojo.enums.ArtifactoryType
import com.tencent.devops.common.api.exception.PermissionForbiddenException
import com.tencent.devops.common.api.exception.RemoteServiceException
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_APP_VERSION
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_VS_LEAK_COUNT
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_VS_LEAK_HIGH_COUNT
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_VS_LEAK_LIGHT_COUNT
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_VS_LEAK_MIDDLE_COUNT
import com.tencent.devops.common.auth.api.pojo.BkAuthGroup
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.service.utils.HomeHostUtil
import com.tencent.devops.common.api.util.OkhttpUtils
import com.tencent.devops.common.auth.api.*
import com.tencent.devops.common.auth.code.PipelineAuthServiceCode
import com.tencent.devops.common.auth.code.VSAuthServiceCode
import com.tencent.devops.log.utils.LogUtils
import com.tencent.devops.model.plugin.tables.TPluginJingang
import com.tencent.devops.model.plugin.tables.TPluginJingangResult
import com.tencent.devops.plugin.dao.JinGangAppDao
import com.tencent.devops.plugin.dao.JinGangAppMetaDao
import com.tencent.devops.plugin.pojo.JinGangApp
import com.tencent.devops.plugin.pojo.JinGangAppCallback
import com.tencent.devops.plugin.pojo.JinGangAppResultReponse
import com.tencent.devops.plugin.pojo.JinGangBugCount
import com.tencent.devops.process.api.service.ServiceJfrogResource
import com.tencent.devops.process.api.service.ServiceMetadataResource
import com.tencent.devops.process.api.service.ServicePipelineResource
import com.tencent.devops.process.pojo.Property
import com.tencent.devops.project.api.service.ServiceProjectResource
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.jooq.DSLContext
import org.json.XML
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDate

@Service
class JinGangService @Autowired constructor(
    private val rabbitTemplate: RabbitTemplate,
    private val jinGangAppDao: JinGangAppDao,
    private val jinGangAppMetaDao: JinGangAppMetaDao,
    private val authPermissionApi: BSAuthPermissionApi,
    private val bkAuthProjectApi: BSAuthProjectApi,
    private val client: Client,
    private val objectMapper: ObjectMapper,
    private val dslContext: DSLContext,
    private val authProjectApi: AuthProjectApi,
    private val pipelineServiceCode: PipelineAuthServiceCode,
    private val vsServiceCode: VSAuthServiceCode
) {

    companion object {
        private val logger = LoggerFactory.getLogger(JinGangService::class.java)
    }

    @Value("\${gateway.url}")
    private lateinit var gatewayUrl: String

    @Value("\${jinGang.url}")
    private lateinit var jinGangUrl: String

    @Value("\${star.url}")
    private lateinit var starUrl: String

    fun callback(data: JinGangAppCallback) {
        logger.info("jin gang callback>>> $data")
        if (data.status == "0") {
            val xml = downloadXml(data.scanXml)
            val resultJson = convertXml(xml)
            jinGangAppDao.updateTask(dslContext, data.buildId, data.md5, data.status.toInt(), data.taskId.toLong(), data.scanUrl, resultJson)

            LogUtils.addLine(rabbitTemplate, data.buildId, "金刚app扫描完成【<a target='_blank' href='${data.scanUrl}'>查看详情</a>】", data.taskId, 1)

            // 生成元数据
            try {
                val jingangTask = jinGangAppDao.getTask(dslContext, data.taskId.toLong()) ?: throw RuntimeException("no jingang task found for taskId(${data.taskId})")
                val metadatas = mutableListOf<Property>()
                val levelCount = getLevelCount(resultJson)

                val lowCount = levelCount["lowCount"] ?: 0
                val mediumCount = levelCount["mediumCount"] ?: 0
                val highCount = levelCount["highCount"] ?: 0
                metadatas.add(Property(ARCHIVE_PROPS_VS_LEAK_HIGH_COUNT, lowCount.toString()))
                metadatas.add(Property(ARCHIVE_PROPS_VS_LEAK_MIDDLE_COUNT, mediumCount.toString()))
                metadatas.add(Property(ARCHIVE_PROPS_VS_LEAK_LIGHT_COUNT, highCount.toString()))
                metadatas.add(Property(ARCHIVE_PROPS_VS_LEAK_COUNT, (lowCount + mediumCount + highCount).toString()))
                client.get(ServiceMetadataResource::class).create(jingangTask.projectId, jingangTask.pipelineId, jingangTask.buildId, metadatas)
            } catch (e: Exception) {
                logger.error("创建元数据失败: ${e.message}")
            }
        } else {
            jinGangAppDao.updateTask(dslContext, data.buildId, data.md5, data.status.toInt(), data.taskId.toLong(), data.scanUrl, data.msg)
        }
    }

    private fun downloadXml(scanXmlUrl: String): String {
        val request = Request.Builder()
                .url(scanXmlUrl)
                .get()
                .build()
        OkhttpUtils.doHttp(request).use {
            return it.body()!!.string()
        }
    }

    private fun convertXml(xml: String): String {
        return XML.toJSONObject(xml).toString()
    }

    fun scanApp(
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        buildNo: Int,
        elementId: String,
        file: String,
        isCustom: Boolean = false,
        runType: String = "1",
        checkPermission: Boolean = false
    ): String {
        logger.info("scan app: $userId, $projectId, $pipelineId, $buildId, $file")

        if (checkPermission) {
            if (!authPermissionApi.validateUserResourcePermission(userId, pipelineServiceCode, AuthResourceType.PIPELINE_DEFAULT,
                            projectId, pipelineId, AuthPermission.EXECUTE))
                throw PermissionForbiddenException("user($userId) does not has permission for pipeline: $pipelineId")
        }

        val type = if (isCustom) ArtifactoryType.CUSTOM_DIR else ArtifactoryType.PIPELINE

        // 获取文件信息

        val jfrogFile = try {
            client.get(ServiceArtifactoryResource::class).show(projectId, type, file).data!!
        } catch (e: RemoteServiceException) {
            logger.error("client get ServiceArtifactoryResource#show fail for buildId($buildId)", e)
            throw RuntimeException("no file found in path($file)")
        }
        // 扫描
        return scan(jfrogFile, isCustom, userId, runType, projectId, pipelineId, buildId, buildNo, elementId)
    }

    private fun scan(
        jfrogFile: FileDetail,
        isCustom: Boolean,
        userId: String,
        runType: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        buildNo: Int,
        elementId: String
    ): String {
        val fileName = File(jfrogFile.name).name
        val type = when {
            fileName.endsWith(".apk") -> 0
            fileName.endsWith(".ipa") -> 1
            else -> throw RuntimeException("$fileName is not a app")
        }

        val version = jfrogFile.meta[ARCHIVE_PROPS_APP_VERSION] ?: throw RuntimeException("no appVersion found")
        val bundleIdentifier = jfrogFile.meta[ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER]
                ?: throw RuntimeException("no bundleIdentifier found")
        val pipelineName = client.get(ServiceJfrogResource::class).getPipelineNameByIds(projectId, setOf(pipelineId)).data?.get(pipelineId)
                ?: throw RuntimeException("no pipeline name found for $pipelineId")
        val fileUrl = getUrl(projectId, jfrogFile.path, isCustom)
        val projectInfo = client.get(ServiceProjectResource::class).listByProjectCode(setOf(projectId)).data?.firstOrNull()
        val ccId = projectInfo?.ccAppId ?: throw RuntimeException("no ccid found for project: $projectId")
        val starResponse = getStarResponse(projectId, ccId.toString())
        val releaseType = if (starResponse.status == "正在运行") "1" else "0" // 0表示游戏还未上线，1表示该游戏已上线
        val submitUser = starResponse.user.firstOrNull { it.roleId == "37" }?.user ?: ""

        // 记录该次扫描
        val taskId = jinGangAppDao.createTask(dslContext, projectId, pipelineId, buildId, buildNo, userId, jfrogFile.name, jfrogFile.checksums.md5, jfrogFile.size, System.currentTimeMillis(), System.currentTimeMillis(), version, type)

        try {
            val params = mutableMapOf<String, String>()
            params.put("productName", pipelineName)
            params.put("fileName", fileName)
            params.put("fileUrl", fileUrl)
            params.put("fileMd5", jfrogFile.checksums.md5)
            params.put("versionNumber", version)
            params.put("packagename", bundleIdentifier)
            params.put("releasetime", LocalDate.now().toString().replace("-", ""))
            params.put("buildId", buildId)
            params.put("pipelineId", pipelineId)
            params.put("pipelineName", pipelineName)
            params.put("elementId", elementId)
            params.put("pipelineUrl", "${HomeHostUtil.innerServerHost()}/console/pipeline/$projectId/$pipelineId/detail/$buildId")
            params.put("projectId", projectId)
            params.put("extension", type.toString())
            params.put("release_type", releaseType)
            params.put("responseuser", if (submitUser.isBlank()) userId else submitUser) // 产品负责人, 上传人
            params.put("submituser", userId) // 邮件抄送人
            params.put("taskId", taskId.toString()) // 任务id
            params.put("is_run_kingkong_v2", if (type == 1) "3" else runType) // ios只有静态扫描
            params.put("responseUrl", HomeHostUtil.innerApiHost() + "/plugin/api/external/jingang/app/callback") // 任务id
            params.put("bg", getBgName(projectInfo.bgId))
            val json = objectMapper.writeValueAsString(params)
            logger.info("jin gang request json:>>>> $json")

            val request = Request.Builder()
                    .url(jinGangUrl)
                    .post(RequestBody.create(MediaType.parse("application/json;charset=utf-8"), json))
                    .build()

            OkhttpUtils.doHttp(request).use { response ->
                val respJson = response.body()!!.string()
                logger.info("jin gang response: $respJson")

                val obj = JsonParser().parse(respJson).asJsonObject
                if (obj["status"].asString != "1") {
                    throw RuntimeException("fail to start app scan:$respJson")
                } else {
                    logger.info("jin gang app scan successfully")
                }
                jinGangAppMetaDao.incRunCount(dslContext, projectId, pipelineId)
                return taskId.toString()
            }
        } catch (e: Exception) {
            jinGangAppDao.updateTask(dslContext, buildId, jfrogFile.checksums.md5, -1, taskId, "", e.message ?: "")
            throw e
        }
    }

    private fun getBgName(bg_id: Long?): String {
        return when (bg_id) {
            956L -> "IEG"
            953L -> "CDG"
            29294L -> "CSIG"
            14129L -> "WXG"
            29292L -> "PCG"
            958L -> "TEG"
            else -> "OTHER"
        }
    }

    private fun getStarResponse(projectId: String, ccId: String): StarResponse {
        val request = Request.Builder()
                .url("$starUrl?id=$ccId")
                .get()
                .build()
        logger.info("star ccid: $ccId")
        if (ccId == "0") return StarResponse("0", getProjectManager(projectId))
        OkhttpUtils.doHttp(request).use { response ->
            val json = response.body()!!.string()

            logger.info("star response: $json")

            val obj = JsonParser().parse(json).asJsonObject
            val status = obj["status"].asString
            if (status == "1") {
                val data = obj["data"].asJsonObject
                val user = data["user"]?.asJsonArray
                return StarResponse(data["status"].asString,
                        user?.map {
                            val item = it.asJsonObject
                            val userElement = item["user"]
                            val userId = if (!userElement.isJsonNull) userElement.asString else ""
                            StarUser(item["roleName"].asString, item["roleId"].asString, userId)
                        } ?: listOf())
            } else {
                throw RuntimeException("fail to get project from star(ccId= $ccId): $json")
            }
        }
    }

    private fun getProjectManager(projectId: String): List<StarUser> {
        val manager = authProjectApi.getProjectUsers(vsServiceCode, projectId, BkAuthGroup.MANAGER)
        return listOf(StarUser(
                "项目管理员",
                "37",
                manager.joinToString(";")
        ))
    }

    // 获取jfrog传回的url
    private fun getUrl(projectId: String, realPath: String, isCustom: Boolean): String {
        return if (isCustom) {
            "http://$gatewayUrl/jfrog/storage/service/custom/$projectId$realPath"
        } else {
            "http://$gatewayUrl/jfrog/storage/service/archive/$projectId$realPath"
        }
    }

    data class StarResponse(
        val status: String,
        val user: List<StarUser>
    )

    data class StarUser(
        val roleName: String,
        val roleId: String,
        val user: String
    )

    fun getList(projectId: String, page: Int, pageSize: Int): List<JinGangApp> {
        val recordList = jinGangAppDao.getList(dslContext, projectId, page, pageSize)
        val result = mutableListOf<JinGangApp>()
        if (recordList != null) {
            with(TPluginJingang.T_PLUGIN_JINGANG) {
                val pipelineIds = mutableSetOf<String>()

                for (item in recordList) {
                    pipelineIds.add(item.get(PIPELINE_ID))
                }
                val pipelineNames = client.get(ServicePipelineResource::class).getPipelineNameByIds(projectId, pipelineIds).data
                        ?: throw RuntimeException("no pipeline name found for $pipelineIds")
                for (item in recordList) {
                    result.add(
                            JinGangApp(
                                    id = item.get(ID),
                                    projectId = item.get(PROJECT_ID),
                                    pipelineId = item.get(PIPELINE_ID),
                                    pipelineName = pipelineNames[item.get(PIPELINE_ID)] ?: "",
                                    buildId = item.get(BUILD_ID),
                                    buildNo = item.get(BUILD_NO),
                                    fileName = covertJinGangFilePath(item.get(FILE_PATH)),
                                    fileMD5 = item.get(FILE_MD5),
                                    fileSize = item.get(FILE_SIZE),
                                    createTime = item.get(CREATE_TIME).timestampmilli(),
                                    updateTime = item.get(UPDATE_TIME).timestampmilli(),
                                    creator = item.get(USER_ID),
                                    status = covertJinGangStatus(item.get(STATUS)),
                                    type = covertJinGangType(item.get(TYPE)),
                                    version = item.get(VERSION)
                            )
                    )
                }
            }
        }
        return result
    }

    fun getCount(projectId: String): Int {
        return jinGangAppDao.getCount(dslContext, projectId)
    }

    fun getAppResult(userId: String, taskId: Long): JinGangAppResultReponse? {

        val recordResult = jinGangAppDao.getTaskResult(dslContext, taskId)
        val recordTask = jinGangAppDao.getTask(dslContext, taskId)

        // 权限校验
        val projectId = recordTask?.projectId ?: ""
        if (!bkAuthProjectApi.getUserProjects(pipelineServiceCode, userId, null).contains(projectId)) {
            throw PermissionForbiddenException("user($userId) does not has permission for project: $projectId")
        }

        var fileName = ""
        var version = ""
        var scanUrl = ""
        var responseuser = ""
        if (recordTask != null) {
            with(TPluginJingang.T_PLUGIN_JINGANG) {
                fileName = covertJinGangFilePath(recordTask.get(FILE_PATH))
                version = recordTask.get(VERSION)
                scanUrl = recordTask.get(SCAN_URL) ?: ""
                responseuser = recordTask.get(USER_ID)
            }
        }
        if (recordResult != null) {
            with(TPluginJingangResult.T_PLUGIN_JINGANG_RESULT) {
                return JinGangAppResultReponse(
                        id = recordResult.get(ID),
                        buildId = recordResult.get(BUILD_ID),
                        fileMD5 = recordResult.get(FILE_MD5),
                        result = objectMapper.readValue(recordResult.get(RESULT)),
                        taskId = recordResult.get(TASK_ID),
                        fileName = fileName,
                        version = version,
                        scanUrl = scanUrl,
                        responseuser = responseuser
                )
            }
        } else {
            return null
        }
    }

    private fun covertJinGangType(type: Int?): String {
        return when (type) {
            0 -> "android"
            1 -> "ios"
            else -> "其他"
        }
    }

    private fun covertJinGangStatus(status: Int?): String {
        return when (status) {
            0 -> "成功"
            null -> "扫描中"
            else -> "失败"
        }
    }

    private fun covertJinGangFilePath(filePath: String?): String {
        return if (filePath != null) {
            val newFilePath = filePath.trim()
            newFilePath.substring(newFilePath.lastIndexOf("/") + 1)
        } else {
            ""
        }
    }

    fun checkLimit(projectId: String, pipelineId: String): Boolean {
        val runCount = jinGangAppMetaDao.getMeta(dslContext, projectId, pipelineId, "jingang.run.count")?.value?.toInt() ?: 0
        val maxCount = jinGangAppMetaDao.getMeta(dslContext, projectId, pipelineId, "jingang.max.run.count")?.value?.toInt() ?: 10

        return runCount<maxCount
    }

    fun countBug(projectIds: Set<String>): Map<String, JinGangBugCount> {
        val resultMap = mutableMapOf<String, JinGangBugCount>()
        projectIds.forEach { resultMap[it] = JinGangBugCount() }

        val taskList = jinGangAppDao.getTaskList(dslContext, projectIds)
        val taskIds = taskList?.map { it.id } ?: listOf()
        val taskProjectMap = taskList?.map { it.id to it.projectId }?.toMap() ?: mapOf()

        val resultList = jinGangAppDao.getResultList(dslContext, taskIds)
        resultList?.forEach {
            try {
                val projectId = taskProjectMap[it.taskId] ?: ""
                val bugCount = resultMap[projectId]!!
                val levelCount = getLevelCount(it.result)
                bugCount.lowCount += levelCount["lowCount"] ?: 0
                bugCount.mediumCount += levelCount["mediumCount"] ?: 0
                bugCount.highCount += levelCount["highCount"] ?: 0
            } catch (e: Exception) {
                // 失败的则跳过
                logger.info("ignore jin gang result : (${it.id})")
            }
        }

        return resultMap
    }

    private fun getLevelCount(result: String): Map<String, Int> {
        var lowCount = 0
        var mediumCount = 0
        var highCount = 0

        val parser = JsonParser()
        val obj = parser.parse(result).asJsonObject
        val leakGroup = obj["leakcheck_result"].asJsonObject["leak_info"].asJsonObject["group"].asJsonArray
        leakGroup.forEach { leak ->
            val leakObj = leak.asJsonObject
            if (leakObj["result"].asString != "安全") {
                val count = leakObj["result"].asString.removePrefix("发现").removeSuffix("处").toInt()
                val name = leakObj["name"].asString
                when {
                    name.startsWith("【低危】") -> {
                        lowCount += count
                    }
                    name.startsWith("【中危】") -> {
                        mediumCount += count
                    }
                    name.startsWith("【高危】") -> {
                        highCount += count
                    }
                }
            }
        }
        return mapOf("lowCount" to lowCount,
                "mediumCount" to mediumCount,
                "highCount" to highCount)
    }

    fun countRisk(projectIds: Set<String>): Map<String, Int> {
        val resultMap = mutableMapOf<String, Int>()
        projectIds.forEach { resultMap[it] = 0 }

        val taskList = jinGangAppDao.getTaskList(dslContext, projectIds)
        val taskIds = taskList?.map { it.id } ?: listOf()
        val taskProjectMap = taskList?.map { it.id to it.projectId }?.toMap() ?: mapOf()

        val resultList = jinGangAppDao.getResultList(dslContext, taskIds)
        val parser = JsonParser()
        resultList?.forEach {
            try {
                val projectId = taskProjectMap[it.taskId] ?: ""
                val obj = parser.parse(it.result).asJsonObject
                val leakGroup = obj["leakcheck_result"].asJsonObject["risk_info"].asJsonObject["group"].asJsonArray
                leakGroup.forEach { leak ->
                    val leakObj = leak.asJsonObject
                    if (leakObj["result"].asString != "安全") {
                        resultMap[projectId] = resultMap[projectId]!!.plus(1)
                    }
                }
            } catch (e: Exception) {
                // 失败的则跳过
                logger.info("ignore jin gang result : (${it.id})")
            }
        }

        return resultMap
    }

    fun createTask(projectId: String, pipelineId: String, buildId: String, buildNo: Int, userId: String, path: String, md5: String, size: Long, version: String, type: Int): Long {
        // 记录该次扫描
        val taskId = jinGangAppDao.createTask(
            dslContext,
            projectId,
            pipelineId,
            buildId,
            buildNo,
            userId,
            path,
            md5,
            size,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            version,
            type
        )
        jinGangAppMetaDao.incRunCount(dslContext, projectId, pipelineId)
        return taskId
    }

    fun updateTask(buildId: String, md5: String, status: Int, taskId: Long, scanUrl: String, result: String) {
        jinGangAppDao.updateTask(dslContext, buildId, md5, status, taskId, scanUrl, result)
    }
}

/*
fun main(args: Array<String>) {
    scanApp("ddlin","a90","f6d5a4992a5146f28696535dfff18d18","0b0664d599ac4d36bcb9ca86e08ab796", "m2048.ipa", true)
}
fun scanApp(userId: String, projectId: String, pipelineId: String, buildId: String, file: String, isCustom: Boolean = false): String {

    val type = if (isCustom) ArtifactoryType.CUSTOM_DIR else ArtifactoryType.PIPELINE
    val path = if (isCustom) "/${file.removePrefix("/")}" else "/$pipelineId/$buildId/${file.removePrefix("/")}"
    //获取文件信息
    val jfrogFile = FileDetail("m2048.ipa","/m2048.ipa","/m2048.ipa","/m2048.ipa",153515L, 1527669859, 1529028565,
            FileChecksums("","cc7eb25f449391aaa0311d71d61d39046a1a8357","f86e608accff43b4080a1d8d38df5e57"), mapOf("appTitle" to "m2048",
            "appVersion" to "1.1",
            "buildId" to "2a641a00b3324dc8bba44686647422aa",
            "buildNo" to "45",
            "bundleIdentifier" to "com.tencent.m2048",
            "fullImage" to "AppIcon60x60",
            "image" to "AppIcon29x29",
            "pipelineId" to "27feca9bf0164aeca4f718cd6c8a03eb",
            "projectId" to "a90",
            "source" to "pipeline",
            "userId" to "v_zijiawen"))
    //扫描
    return scan(jfrogFile, isCustom, userId)
}


private fun scan(jfrogFile: FileDetail, isCustom: Boolean, userId: String): String {
    val fileName = File(jfrogFile.name).name
    val type = when {
        fileName.endsWith(".apk") -> "0"
        fileName.endsWith(".ipa") -> "1"
        else -> throw RuntimeException("$fileName is not a app")
    }


    val version = jfrogFile.meta[ARCHIVE_PROPS_APP_VERSION]?:throw RuntimeException("no appVersion found")
    val buildId = jfrogFile.meta[ARCHIVE_PROPS_BUILD_ID]?:throw RuntimeException("no buildId found")
    val pipelineId = jfrogFile.meta[ARCHIVE_PROPS_PIPELINE_ID]?:throw RuntimeException("no pipelineId found")
    val projectId = jfrogFile.meta[ARCHIVE_PROPS_PROJECT_ID]?:throw RuntimeException("no projectId found")
    val bundleIdentifier = jfrogFile.meta[ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER]?:throw RuntimeException("no bundleIdentifier found")
    val buildNo = jfrogFile.meta[ARCHIVE_PROPS_BUILD_NO]?:throw RuntimeException("no bundleIdentifier found")
    val pipelineName = "测试产品"
    val fileUrl = getUrl(projectId, jfrogFile.path, isCustom)
    val ccId = "100411"
    //val starResponse = getStarResponse(ccId)
    val starResponse=  JinGangService.StarResponse("正在运行", listOf(JinGangService.StarUser("产品负责人","37","ddlin")))
    val releaseType = if (starResponse.status=="正在运行") "1" else "0"//0表示游戏还未上线，1表示该游戏已上线
    val submitUser = starResponse.user.filter { it.roleId=="37" }[0]

    val params = mutableMapOf<String, String>()
    params.put("productName", pipelineName)
    params.put("fileName", fileName)
    params.put("fileUrl", fileUrl)
    params.put("fileMd5", jfrogFile.checksums.md5)
    params.put("versionNumber", version)
    params.put("packagename", bundleIdentifier)
    params.put("releasetime", LocalDate.now().toString())
    params.put("buildId", buildId)
    params.put("pipelineId", pipelineId)
    params.put("projectId", projectId)
    params.put("extension", type)
    params.put("release_type", releaseType)
    params.put("submituser", submitUser.user)//产品负责人
    params.put("responseuser", userId)//邮件抄送人
    params.put("taskId", "30")//邮件抄送人
    params.put("is_run_kingkong_v2", "3")
    val json = ObjectMapper().writeValueAsString(params)

    println("json:>>>> $json")

    val request = Request.Builder()
            .url("http://100.76.0.3/realphone/landun_audit_new.php")
            .post(RequestBody.create(MediaType.parse("application/json;charset=utf-8"), json))
            .build()
    OkhttpUtils.doHttp(request).use { response->
        val data = response.body()!!.string()
        println(data)
        //记录该次扫描
        //jinGangAppDao.createTask(dslContext, projectId, pipelineId, buildId, buildNo.toInt(), userId, jfrogFile.name, jfrogFile.checksums.md5, jfrogFile.size, System.currentTimeMillis(), System.currentTimeMillis(), type.toShort())
        return data
    }
}



//获取jfrog传回的url
private fun getUrl(projectId: String, realPath: String, isCustom: Boolean): String {
    val gatewayUrl = "dev.gw.open.oa.com"
    return if (isCustom) {
        "http://$gatewayUrl/jfrog/storage/service/custom/$projectId$realPath"
    } else {
        "http://$gatewayUrl/jfrog/storage/service/archive/$projectId$realPath"
    }
}

*/