package com.tencent.devops.plugin.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.common.archive.client.JfrogService
import com.tencent.devops.common.archive.pojo.ArtifactorySearchParam
import com.tencent.devops.common.api.util.OkhttpUtils
import com.tencent.devops.log.utils.LogUtils
import com.tencent.devops.plugin.pojo.migcdn.MigCDNUploadParam
import okhttp3.Request
import okhttp3.internal.Util
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URLEncoder

@Service
class MigCDNService @Autowired constructor(
    private val objectMapper: ObjectMapper,
    private val jfrogService: JfrogService,
    private val rabbitTemplate: RabbitTemplate
) {

    @Value("\${gateway.url}")
    private lateinit var gatewayUrl: String

    val MIG_CDN_URL = "http://up.cdn.qq.com:8600/uploadserver/uploadfile.jsp"

    /*
    curl -v -X 'POST' -H "X-CDN-Authentication:D71748F48D99FEF199DD71FBEA63602D" "http://up.cdn.qq.com:8600/uploadserver/uploadfile.jsp?appname=bkdevops&user=johuang&filename=pom&filetype=xml&filepath=/aaa/&filesize=528&remoteurl=http%3a%2f%2fbk.artifactory.oa.com%2fdocker-service%2fpaas%2fpublic%2ftest%2fv1%2fmanifest.json"
    */

    fun pushFile(migCDNUploadParam: MigCDNUploadParam): String {
        val fileParams = migCDNUploadParam.fileParams
        logger.info("MIG CDN upload param for build(${fileParams.buildId}): $migCDNUploadParam")

        var hasMatchedFile = false
        fileParams.regexPath.split(",").map { it.trim() }.forEach outside@{
            val path = if (fileParams.custom) "/${it.removePrefix("/")}" else "/${fileParams.pipelineId}/${fileParams.buildId}/${it.removePrefix("/")}"

            val param = ArtifactorySearchParam(
                    migCDNUploadParam.fileParams.projectId,
                    migCDNUploadParam.fileParams.pipelineId,
                    migCDNUploadParam.fileParams.buildId,
                    it,
                    migCDNUploadParam.fileParams.custom,
                    migCDNUploadParam.fileParams.executeCount,
                    migCDNUploadParam.fileParams.elementId
            )

            val fileMatched = jfrogService.matchFiles(param).map {
                it.removePrefix("bk-archive/")
                        .removePrefix("bk-custom/")
            }
            if (fileMatched.isEmpty()) {
                logger.info("File matched nothing with regex: $it")
                return@outside
            }

            hasMatchedFile = true
            fileMatched.forEach inside@{ fileIter ->
                val fileNameType = getFileNameType(fileIter)
                if (fileNameType.first.isBlank()) {
                    logger.info("File type invalid, file:$fileIter, skipped")
                    LogUtils.addLine(rabbitTemplate, fileParams.buildId, "该文件不允许上传，文件:$fileIter，将跳过",
                        fileParams.elementId, fileParams.executeCount)
                    return@inside
                }

                val downLoadUrl = getUrl(fileIter, fileParams.custom)
                logger.info("downLoadUrl: $downLoadUrl")

                val url = "$MIG_CDN_URL?appname=${migCDNUploadParam.para.appName}&user=${migCDNUploadParam.operator}&filename=${fileNameType.first}" +
                        "&filetype=${fileNameType.second}&filepath=${migCDNUploadParam.para.destFileDir}&filesize=0&isunzip=${migCDNUploadParam.para.needUnzip}" +
                        "&remoteurl=${URLEncoder.encode(downLoadUrl, "UTF-8")}"
                logger.info("MIG CDN upload request url: $url")
                val request = Request.Builder().url(url).post(Util.EMPTY_REQUEST).addHeader("X-CDN-Authentication", migCDNUploadParam.para.appSecret).build()
                OkhttpUtils.doHttp(request).use { res ->
                    val response = res.body()!!.string()
                    logger.info("MIG CDN upload response for build(${fileParams.buildId}): $response")
                    val jsonMap = objectMapper.readValue<Map<String, Any>>(response)
                    val retCode = jsonMap["ret_code"] as Int
                    if (retCode != 200) {
                        val msg = jsonMap["err_msg"] as String
                        LogUtils.addLine(rabbitTemplate, fileParams.buildId, "上传CDN失败，文件：$path\n错误码：$retCode\n错误信息：$msg",
                            fileParams.elementId, fileParams.executeCount)
                        throw RuntimeException("上传到CDN失败")
                    }
                    val cdnUrl = if (null == jsonMap["cdn_url"]) "" else jsonMap["cdn_url"] as String
                    val fileMd5 = if (null == jsonMap["file_md5"]) "" else jsonMap["file_md5"] as String

                    LogUtils.addLine(rabbitTemplate, fileParams.buildId, "上传CDN成功，文件:$path:\ncdn_url:$cdnUrl\nfileMd5:$fileMd5",
                        fileParams.elementId, fileParams.executeCount)
                }
            }
        }
        if (!hasMatchedFile) {
            logger.info("File matched nothing")
            LogUtils.addLine(rabbitTemplate, fileParams.buildId, "没有匹配到任何文件，请检查版本仓库以及源文件设置",
                fileParams.elementId, fileParams.executeCount)
            throw RuntimeException("上传到CDN失败")
        }

        return "success"
    }

    private fun getFileNameType(realPath: String): Pair<String, String> {
        val destFileFullName = realPath.substringAfterLast('/')
        val destFileName = destFileFullName.substringBeforeLast('.')
        val destFileType: String
        val pos = destFileFullName.lastIndexOf('.')
        destFileType = if (-1 == pos) {
            "undef_ext"
        } else {
            if ("" == destFileFullName.substringAfterLast('.')) "undef_ext" else destFileFullName.substringAfterLast('.')
        }

        return Pair(destFileName, destFileType)
    }

    private fun getUrl(realPath: String, isCustom: Boolean): String {
        return if (isCustom) {
            "http://$gatewayUrl/jfrog/storage/service/custom/$realPath"
        } else {
            "http://$gatewayUrl/jfrog/storage/service/archive/$realPath"
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigCDNService::class.java)
    }
}