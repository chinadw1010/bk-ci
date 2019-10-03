package com.tencent.devops.repository.service.scm

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.JsonParser
import com.tencent.devops.common.api.constant.CommonMessageCode
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.api.util.HashUtil
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.OkhttpUtils
import com.tencent.devops.common.api.util.script.CommonScriptUtils
import com.tencent.devops.common.service.utils.MessageCodeUtil
import com.tencent.devops.repository.config.GitConfig
import com.tencent.devops.repository.constant.RepositoryMessageCode
import com.tencent.devops.repository.pojo.Project
import com.tencent.devops.repository.pojo.enums.GitAccessLevelEnum
import com.tencent.devops.repository.pojo.enums.RepoAuthType
import com.tencent.devops.repository.pojo.enums.TokenTypeEnum
import com.tencent.devops.repository.pojo.enums.VisibilityLevelEnum
import com.tencent.devops.repository.pojo.git.GitProjectInfo
import com.tencent.devops.repository.pojo.git.GitUserInfo
import com.tencent.devops.repository.pojo.git.UpdateGitProjectInfo
import com.tencent.devops.repository.pojo.gitlab.GitlabFileInfo
import com.tencent.devops.repository.pojo.oauth.GitToken
import com.tencent.devops.repository.pojo.scm.GitRepositoryResp
import com.tencent.devops.scm.code.git.CodeGitOauthCredentialSetter
import com.tencent.devops.scm.code.git.CodeGitUsernameCredentialSetter
import com.tencent.devops.scm.utils.code.git.GitUtils
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.FileSystemUtils
import org.springframework.util.StringUtils
import java.io.File
import java.net.URLEncoder
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.Base64
import java.util.concurrent.Executors

@Service
class GitService @Autowired constructor(
        private val gitConfig: GitConfig,
        private val objectMapper: ObjectMapper
) {

    companion object {
        private val logger = LoggerFactory.getLogger(GitService::class.java)
    }

    @Value("\${git.url}")
    private lateinit var gitUrl: String

    @Value("\${git.clientId}")
    private lateinit var clientId: String

    @Value("\${git.clientSecret}")
    private lateinit var clientSecret: String

    @Value("\${git.callbackUrl}")
    private lateinit var callbackUrl: String

    @Value("\${git.redirectUrl}")
    private lateinit var redirectUrl: String

    @Value("\${git.public.account}")
    private lateinit var gitPublicAccount: String

    @Value("\${git.public.email}")
    private lateinit var gitPublicEmail: String

    @Value("\${git.public.secret}")
    private lateinit var gitPublicSecret: String

    @Value("\${git.redirectAtomMarketUrl}")
    private lateinit var redirectAtomMarketUrl: String

    @Value("\${git.redirectAtomRepositoryUrl}")
    private lateinit var redirectAtomRepositoryUrl: String

    private val executorService = Executors.newFixedThreadPool(2)

    fun getProject(accessToken: String, userId: String): List<Project> {

        logger.info("Start to get the projects by user $userId with token $accessToken")
        val startEpoch = System.currentTimeMillis()
        try {
            var page = 1

            val result = mutableListOf<Project>()
            while (true) {
                val projectUrl = "${gitConfig.gitApiUrl}/projects?access_token=$accessToken&page=$page&per_page=100"
                page++

                val request = Request.Builder()
                    .url(projectUrl)
                    .get()
                    .build()

                OkhttpUtils.doHttp(request).use { response ->
                    val data = response.body()!!.string()
                    val repoList = JsonParser().parse(data).asJsonArray
                    repoList.forEach {
                        val obj = it.asJsonObject
                        val lastActivityTime = obj["last_activity_at"].asString.removeSuffix("+0000")
                        result.add(
                            Project(
                                obj["id"].asString,
                                obj["name"].asString,
                                obj["name_with_namespace"].asString,
                                obj["ssh_url_to_repo"].asString,
                                obj["http_url_to_repo"].asString,
                                DateTimeUtil.convertLocalDateTimeToTimestamp(LocalDateTime.parse(lastActivityTime)) * 1000L
                            )
                        )
                    }
                    if (repoList.size() < 100) {
                        logger.info("Finish get the project by user with size ${result.size}")
                        return result.sortedBy { 0 - it.lastActivity }
                    } // 倒序排序
                }
            }
        } finally {
            logger.info("It took ${System.currentTimeMillis() - startEpoch}ms to get the project")
        }
    }

    fun refreshToken(userId: String, accessToken: GitToken): GitToken {
        logger.info("Start to refresh the token of user $userId by token $accessToken")
        val startEpoch = System.currentTimeMillis()
        try {
            val url =
                "${gitConfig.gitApiUrl}/oauth/token?client_id=${gitConfig.clientId}&client_secret=${gitConfig.clientSecret}" +
                    "&grant_type=refresh_token&refresh_token=${accessToken.refreshToken}&redirect_uri=${gitConfig.gitHookUrl}"
            val request = Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded;charset=utf-8"), ""))
                .build()
            OkhttpUtils.doHttp(request).use { response ->
                val data = response.body()!!.string()
                logger.info("refreshToken>>> $data")
                return objectMapper.readValue(data, GitToken::class.java)
            }
        } finally {
            logger.info("It took ${System.currentTimeMillis() - startEpoch}ms to refresh the token")
        }
    }

    fun getAuthUrl(userId: String, projectId: String?, repoHashId: String?, redirectUrlType: String?): String {
        val repoId = if (!repoHashId.isNullOrBlank()) HashUtil.decodeOtherIdToLong(repoHashId!!).toString() else ""
        val proId = projectId ?: ""
        val type = redirectUrlType ?: ""
        val state = "$type;$userId;$proId;$repoId;BK_DEVOPS__${RandomStringUtils.randomAlphanumeric(8)}"
        return "${gitConfig.gitApiUrl}/oauth/authorize?client_id=${gitConfig.clientId}&redirect_uri=${gitConfig.gitHookUrl}&response_type=code&state=$state"
    }

    fun getToken(userId: String, code: String): GitToken {
        logger.info("Start to get the token of user $userId by code $code")
        val startEpoch = System.currentTimeMillis()
        try {
            val tokenUrl =
                "${gitConfig.gitApiUrl}/oauth/token?client_id=${gitConfig.clientId}&client_secret=${gitConfig.clientSecret}&code=$code" +
                    "&grant_type=authorization_code&redirect_uri=${gitConfig.redirectUrl}"
            logger.info("getToken url>> $tokenUrl")
            val request = Request.Builder()
                .url(tokenUrl)
                .post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded;charset=utf-8"), ""))
                .build()

            OkhttpUtils.doHttp(request).use { response ->
                val data = response.body()!!.string()
                logger.info("getToken>> $data")
                return objectMapper.readValue(data, GitToken::class.java)
            }
        } finally {
            logger.info("It took ${System.currentTimeMillis() - startEpoch}ms to get the token")
        }
    }

    fun getRedirectUrl(redirectUrlType: String?): String {
        return if ("atomMarket" == redirectUrlType) {
            gitConfig.redirectAtomMarketUrl
        } else {
            gitConfig.redirectUrl
        }
    }

    fun getGitFileContent(
        repoName: String,
        filePath: String,
        authType: RepoAuthType?,
        token: String,
        ref: String
    ): String {
        logger.info("[$repoName|$filePath|$authType|$token|$ref] Start to get the git file content")
        val startEpoch = System.currentTimeMillis()
        try {
            var url =
                "${gitConfig.gitApiUrl}/projects/${URLEncoder.encode(repoName, "UTF-8")}/repository/blobs/" +
                    "${URLEncoder.encode(ref, "UTF-8")}?filepath=${URLEncoder.encode(filePath, "UTF-8")}"
            logger.info("$url ($token)")
            val request = if (authType == RepoAuthType.OAUTH) {
                url += "&access_token=$token"
                Request.Builder()
                    .url(url)
                    .get()
                    .build()
            } else {
                Request.Builder()
                    .url(url)
                    .get()
                    .header("PRIVATE-TOKEN", token)
                    .build()
            }
            OkhttpUtils.doHttp(request).use {
                val data = it.body()!!.string()
                if (!it.isSuccessful) throw RuntimeException("fail to get git file content with: $url($data)")
                return data
            }
        } finally {
            logger.info("It took ${System.currentTimeMillis() - startEpoch}ms to get the git file content")
        }
    }

    fun getGitlabFileContent(repoUrl:String, repoName: String, filePath: String, ref: String, accessToken: String): String {
        val apiUrl = GitUtils.getGitApiUrl(gitConfig.gitlabApiUrl, repoUrl)
        logger.info("[$repoName|$filePath|$ref|$accessToken] Start to get the gitlab file content from $apiUrl")
        val startEpoch = System.currentTimeMillis()
        try {
            val headers = mapOf("PRIVATE-TOKEN" to accessToken)
            // 查询文件内容
            val encodeFilePath = URLEncoder.encode(filePath, "utf-8")
            val encodeRef = URLEncoder.encode(ref, "utf-8")
            val encodeProjectName = URLEncoder.encode(repoName, "utf-8")
            val projectFileUrl =
                "$apiUrl/projects/$encodeProjectName/repository/files/$encodeFilePath?ref=$encodeRef"
            logger.info(projectFileUrl)
            OkhttpUtils.doGet(projectFileUrl, headers).use { response ->
                val body = response.body()!!.string()
                logger.info("get gitlab content response body: $body")
                val fileInfo = objectMapper.readValue(body, GitlabFileInfo::class.java)
                return String(Base64.getDecoder().decode(fileInfo.content))
            }
        } catch (e: Exception) {
            logger.warn(
                "Fail to get the gitlab content of repo($repoName) in path($filePath)/ref($ref): ${e.message}",
                e
            )
            return ""
        } finally {
            logger.info("It took ${System.currentTimeMillis() - startEpoch}ms to get the gitlab file content")
        }
    }

    fun createGitCodeRepository(
            userId: String,
            token: String,
            repositoryName: String,
            sampleProjectPath: String,
            namespaceId: Int?,
            visibilityLevel: VisibilityLevelEnum?,
            tokenType: TokenTypeEnum
    ): Result<GitRepositoryResp?> {
        logger.info("createGitRepository userId is:$userId,token is:$token, repositoryName is:$repositoryName, sampleProjectPath is:$sampleProjectPath")
        logger.info("createGitRepository  namespaceId is:$namespaceId, visibilityLevel is:$visibilityLevel, tokenType is:$tokenType")
        val url = StringBuilder("$gitUrl/api/v3/projects")
        setToken(tokenType, url, token)
        logger.info("createGitRepository token is:$token, url>> $url")
        val params = mutableMapOf<String, Any?>()
        params["name"] = repositoryName
        if (null != visibilityLevel) {
            params["namespace_id"] = namespaceId
        }
        if (null != visibilityLevel) {
            params["visibility_level"] = visibilityLevel.level
            if (visibilityLevel == VisibilityLevelEnum.LOGIN_PUBLIC) {
                params["fork_enabled"] = true // 如果项目设置为开源就打开fork设置开关
            }
        }
        val request = Request.Builder()
                .url(url.toString())
                .post(RequestBody.create(MediaType.parse("application/json;charset=utf-8"), JsonUtil.toJson(params)))
                .build()
        OkhttpUtils.doHttp(request).use { response ->
            val data = response.body()!!.string()
            logger.info("createGitRepository token is:$token, response>> $data")
            val dataMap = JsonUtil.toMap(data)
            val atomRepositoryUrl = dataMap["http_url_to_repo"]
            if (StringUtils.isEmpty(atomRepositoryUrl)) {
                val validateResult: Result<String?> = MessageCodeUtil.generateResponseDataObject(RepositoryMessageCode.USER_CREATE_GIT_CODE_REPOSITORY_FAIL)
                logger.info("createOAuthCodeRepository validateResult>> $validateResult")
                // 把工蜂的错误提示抛出去
                return Result(validateResult.status, "${validateResult.message}（git error:$data）")
            }
            val nameSpaceName = dataMap["name_with_namespace"] as String
            // 把需要创建项目代码库的用户加入为对应项目的owner用户
            executorService.submit<Result<Boolean>> {
                // 添加插件的开发成员
                addGitProjectMember(listOf(userId), nameSpaceName, GitAccessLevelEnum.MASTER, token, tokenType)
                // 把样例工程代码添加到用户的仓库
                initRepositoryInfo(userId, sampleProjectPath, token, tokenType, repositoryName, atomRepositoryUrl as String)
            }
            return Result(GitRepositoryResp(nameSpaceName, atomRepositoryUrl as String))
        }
    }

    fun initRepositoryInfo(
            userId: String,
            sampleProjectPath: String,
            token: String,
            tokenType: TokenTypeEnum,
            repositoryName: String,
            atomRepositoryUrl: String
    ): Result<Boolean> {
        logger.info("initRepositoryInfo userId is:$userId,sampleProjectPath is:$sampleProjectPath,atomRepositoryUrl is:$atomRepositoryUrl")
        logger.info("initRepositoryInfo token is:$token,tokenType is:$tokenType,repositoryName is:$repositoryName")
        val atomTmpWorkspace = Files.createTempDirectory(repositoryName).toFile()
        logger.info("initRepositoryInfo atomTmpWorkspace is:${atomTmpWorkspace.absolutePath}")
        try {
            // 1、clone插件示例工程代码到插件工作空间下
            val credentialSetter = if (tokenType == TokenTypeEnum.OAUTH) {
                CodeGitOauthCredentialSetter(token)
            } else {
                CodeGitUsernameCredentialSetter(gitPublicAccount, gitPublicSecret)
            }
            CommonScriptUtils.execute("git clone ${credentialSetter.getCredentialUrl(sampleProjectPath)}", atomTmpWorkspace)
            // 2、删除下载下来示例工程的git信息
            val atomFileDir = atomTmpWorkspace.listFiles()[0]
            logger.info("initRepositoryInfo atomFileDir is:${atomFileDir.absolutePath}")
            val atomGitFileDir = File(atomFileDir, ".git")
            if (atomGitFileDir.exists()) {
                FileSystemUtils.deleteRecursively(atomGitFileDir)
            }
            // 3、重新生成git信息
            CommonScriptUtils.execute("git init", atomFileDir)
            // 4、添加远程仓库
            CommonScriptUtils.execute("git remote add origin ${credentialSetter.getCredentialUrl(atomRepositoryUrl)}", atomFileDir)
            // 5、给文件添加git信息
            CommonScriptUtils.execute("git config user.email \"$gitPublicEmail\"", atomFileDir)
            CommonScriptUtils.execute("git config user.name \"$gitPublicAccount\"", atomFileDir)
            CommonScriptUtils.execute("git add .", atomFileDir)
            // 6、提交本地文件
            CommonScriptUtils.execute("git commit -m \"init\"", atomFileDir)
            // 7、提交代码到远程仓库
            CommonScriptUtils.execute("git push origin master", atomFileDir)
            logger.info("initRepositoryInfo finish")
        } catch (e: Exception) {
            logger.error("initRepositoryInfo error is:", e)
            return Result(false)
        } finally {
            FileSystemUtils.deleteRecursively(atomTmpWorkspace)
        }
        return Result(true)
    }

    fun addGitProjectMember(userIdList: List<String>, repoName: String, gitAccessLevel: GitAccessLevelEnum, token: String, tokenType: TokenTypeEnum): Result<Boolean> {
        logger.info("addGitProjectMember token is:$token, userIdList is:$userIdList,repoName is:$repoName,gitAccessLevel is:$gitAccessLevel,tokenType is:$tokenType")
        var gitUserInfo: GitUserInfo?
        val encodeProjectName = URLEncoder.encode(repoName, "utf-8") // 为代码库名称字段encode
        val url = StringBuilder("$gitUrl/api/v3/projects/$encodeProjectName/members")
        setToken(tokenType, url, token)
        userIdList.forEach {
            val gitUserInfoResult = getGitUserInfo(it, token, tokenType)
            logger.info("the gitUserInfoResult is :$gitUserInfoResult")
            if (gitUserInfoResult.isNotOk()) {
                return Result(gitUserInfoResult.status, gitUserInfoResult.message, false)
            } else {
                gitUserInfo = gitUserInfoResult.data
            }
            val params = mutableMapOf<String, Any?>()
            params["id"] = repoName
            params["user_id"] = gitUserInfo!!.id
            params["access_level"] = gitAccessLevel.level
            val request = Request.Builder()
                    .url(url.toString())
                    .post(RequestBody.create(MediaType.parse("application/json;charset=utf-8"), JsonUtil.toJson(params)))
                    .build()
            OkhttpUtils.doHttp(request).use { response ->
                val data = response.body()!!.string()
                logger.info("addGitProjectMember token is:$token, response>> $data")
                if (!StringUtils.isEmpty(data)) {
                    val dataMap = JsonUtil.toMap(data)
                    val message = dataMap["message"]
                    if (!StringUtils.isEmpty(message)) {
                        val validateResult: Result<String?> = MessageCodeUtil.generateResponseDataObject(RepositoryMessageCode.USER_ADD_GIT_CODE_REPOSITORY_MEMBER_FAIL, arrayOf(it))
                        logger.info("addGitProjectMember validateResult>> $validateResult")
                        // 把工蜂的错误提示抛出去
                        return Result(validateResult.status, "${validateResult.message}（git error:$message）")
                    }
                }
            }
        }
        return Result(true)
    }

    fun deleteGitProjectMember(userIdList: List<String>, repoName: String, token: String, tokenType: TokenTypeEnum): Result<Boolean> {
        logger.info("deleteGitProjectMember token is:$token, userIdList is:$userIdList,repoName is:$repoName,tokenType is:$tokenType")
        var gitUserInfo: GitUserInfo?
        val encodeProjectName = URLEncoder.encode(repoName, "utf-8") // 为代码库名称字段encode
        val url = StringBuilder("$gitUrl/api/v3/projects/$encodeProjectName/members")
        userIdList.forEach {
            val gitUserInfoResult = getGitUserInfo(it, token, tokenType)
            logger.info("the gitUserInfoResult is :$gitUserInfoResult")
            if (gitUserInfoResult.isNotOk()) {
                return Result(gitUserInfoResult.status, gitUserInfoResult.message, false)
            } else {
                gitUserInfo = gitUserInfoResult.data
            }
            if (null != gitUserInfo) {
                val gitProjectMemberInfoResult = getGitProjectMemberInfo(gitUserInfo!!.id, repoName, token, tokenType)
                logger.info("the gitProjectMemberInfoResult is :$gitProjectMemberInfoResult")
                val gitProjectMemberInfo: GitUserInfo?
                if (gitProjectMemberInfoResult.isNotOk()) {
                    return Result(gitProjectMemberInfoResult.status, gitProjectMemberInfoResult.message, false)
                } else {
                    gitProjectMemberInfo = gitProjectMemberInfoResult.data
                }
                if (null == gitProjectMemberInfo) {
                    return@forEach // 兼容历史插件的成员可能未关联代码库的情况
                }
                url.append("/${gitUserInfo!!.id}")
                setToken(tokenType, url, token)
                val request = Request.Builder()
                        .url(url.toString())
                        .delete()
                        .build()
                OkhttpUtils.doHttp(request).use { response ->
                    val data = response.body()!!.string()
                    logger.info("deleteGitProjectMember token is:$token, response>> $data")
                    if (!StringUtils.isEmpty(data)) {
                        val dataMap = JsonUtil.toMap(data)
                        val message = dataMap["message"]
                        if (!StringUtils.isEmpty(message)) {
                            val validateResult: Result<String?> = MessageCodeUtil.generateResponseDataObject(RepositoryMessageCode.USER_DELETE_GIT_CODE_REPOSITORY_MEMBER_FAIL, arrayOf(it))
                            logger.info("deleteGitProjectMember validateResult>> $validateResult")
                            // 把工蜂的错误提示抛出去
                            return Result(validateResult.status, "${validateResult.message}（git error:$message）")
                        }
                    }
                }
            }
        }
        return Result(true)
    }

    fun getGitProjectMemberInfo(memberId: Int, repoName: String, token: String, tokenType: TokenTypeEnum): Result<GitUserInfo?> {
        logger.info("getGitProjectMemberInfo memberId is:$memberId,repoName is:$repoName,token is:$token,tokenType is:$tokenType")
        val encodeProjectName = URLEncoder.encode(repoName, "utf-8") // 为代码库名称字段encode
        val url = StringBuilder("$gitUrl/api/v3/projects/$encodeProjectName/members/$memberId")
        setToken(tokenType, url, token)
        val request = Request.Builder()
                .url(url.toString())
                .get()
                .build()
        OkhttpUtils.doHttp(request).use {
            val data = it.body()!!.string()
            logger.info("getGitProjectMemberInfo token is:$token, response>> $data")
            if (!StringUtils.isEmpty(data)) {
                val dataMap = JsonUtil.toMap(data)
                val message = dataMap["message"]
                if (StringUtils.isEmpty(message)) {
                    return Result(JsonUtil.to(data, GitUserInfo::class.java))
                }
            }
            return Result(data = null)
        }
    }

    fun getGitUserInfo(userId: String, token: String, tokenType: TokenTypeEnum): Result<GitUserInfo?> {
        logger.info("getGitUserInfo token is:$token, userId is:$userId,tokenType is:$tokenType")
        val url = StringBuilder("$gitUrl/api/v3/users/$userId")
        setToken(tokenType, url, token)
        val request = Request.Builder()
                .url(url.toString())
                .get()
                .build()
        OkhttpUtils.doHttp(request).use {
            val data = it.body()!!.string()
            logger.info("getGitUserInfo token is:$token, response>> $data")
            if (!it.isSuccessful) return MessageCodeUtil.generateResponseDataObject(CommonMessageCode.SYSTEM_ERROR)
            if (!StringUtils.isEmpty(data)) {
                val dataMap = JsonUtil.toMap(data)
                val message = dataMap["message"]
                if (StringUtils.isEmpty(message)) {
                    return Result(JsonUtil.to(data, GitUserInfo::class.java))
                }
            }
            return Result(data = null)
        }
    }

    fun getGitProjectInfo(id: String, token: String, tokenType: TokenTypeEnum): Result<GitProjectInfo?> {
        logger.info("getGitUserInfo token is:$token, id is:$id,tokenType is:$tokenType")
        val encodeId = URLEncoder.encode(id, "utf-8") // 如果id为NAMESPACE_PATH则需要encode
        val url = StringBuilder("${gitConfig.gitApiUrl}/projects/$encodeId")
        setToken(tokenType, url, token)
        val request = Request.Builder()
            .url(url.toString())
            .get()
            .build()
        OkhttpUtils.doHttp(request).use {
            val data = it.body()!!.string()
            logger.info("GitProjectInfo token is:$token, response>> $data")
            if (!it.isSuccessful) return MessageCodeUtil.generateResponseDataObject(CommonMessageCode.SYSTEM_ERROR)
            return Result(JsonUtil.to(data, GitProjectInfo::class.java))
        }
    }

    fun updateGitProjectInfo(
        projectName: String,
        updateGitProjectInfo: UpdateGitProjectInfo,
        token: String,
        tokenType: TokenTypeEnum
    ): Result<Boolean> {
        logger.info("updateGitProjectInfo token is:$token, projectName is:$projectName,updateGitProjectInfo is:$updateGitProjectInfo,tokenType is:$tokenType")
        val encodeProjectName = URLEncoder.encode(projectName, "utf-8")
        val url = StringBuilder("${gitConfig.gitApiUrl}/projects/$encodeProjectName")
        setToken(tokenType, url, token)
        val request = Request.Builder()
            .url(url.toString())
            .put(
                RequestBody.create(
                    MediaType.parse("application/json;charset=utf-8"),
                    JsonUtil.toJson(updateGitProjectInfo)
                )
            )
            .build()
        OkhttpUtils.doHttp(request).use {
            val data = it.body()!!.string()
            logger.info("updateGitProjectInfo token is:$token, response>> $data")
            val dataMap = JsonUtil.toMap(data)
            val message = dataMap["message"]
            if (!StringUtils.isEmpty(message)) {
                val validateResult: Result<String?> =
                    MessageCodeUtil.generateResponseDataObject(RepositoryMessageCode.USER_UPDATE_GIT_CODE_REPOSITORY_FAIL)
                logger.info("updateGitProjectInfo validateResult>> $validateResult")
                // 把工蜂的错误提示抛出去
                return Result(validateResult.status, "${validateResult.message}（git error:$message）")
            }
            return Result(true)
        }
    }

    fun moveProjectToGroup(
        groupCode: String,
        repoName: String,
        token: String,
        tokenType: TokenTypeEnum
    ): Result<GitProjectInfo?> {
        logger.info("updateGitProjectInfo token is:$token, groupCode is:$groupCode,repoName is:$repoName,tokenType is:$tokenType")
        val gitProjectInfo: GitProjectInfo?
        val gitProjectInfoResult = getGitProjectInfo(repoName, token, tokenType)
        logger.info("the gitProjectInfoResult is :$gitProjectInfoResult")
        if (gitProjectInfoResult.isNotOk()) {
            return Result(status = gitProjectInfoResult.status, message = gitProjectInfoResult.message ?: "")
        } else {
            gitProjectInfo = gitProjectInfoResult.data
        }
        if (null == gitProjectInfo) {
            return MessageCodeUtil.generateResponseDataObject(CommonMessageCode.PARAMETER_IS_INVALID, arrayOf(repoName))
        }
        val projectId = gitProjectInfo.id // 获取工蜂项目ID
        val url = StringBuilder("${gitConfig.gitApiUrl}/groups/$groupCode/projects/$projectId")
        setToken(tokenType, url, token)
        val request = Request.Builder()
            .url(url.toString())
            .post(
                RequestBody.create(
                    MediaType.parse("application/json;charset=utf-8"),
                    JsonUtil.toJson(mapOf<String, String>())
                )
            )
            .build()
        OkhttpUtils.doHttp(request).use {
            if (!it.isSuccessful) {
                val data = it.body()!!.string()
                logger.info("moveProjectToGroup token is:$token, response>> $data")
                val dataMap = JsonUtil.toMap(data)
                val message = dataMap["message"]
                return if (!StringUtils.isEmpty(message)) {
                    val validateResult: Result<String?> = MessageCodeUtil.generateResponseDataObject(
                        RepositoryMessageCode.USER_GIT_REPOSITORY_MOVE_GROUP_FAIL,
                        arrayOf(groupCode)
                    )
                    logger.info("moveProjectToGroup validateResult>> $validateResult")
                    // 把工蜂的错误提示抛出去
                    Result(validateResult.status, "${validateResult.message}（git error:$message）")
                } else {
                    MessageCodeUtil.generateResponseDataObject(
                        RepositoryMessageCode.USER_GIT_REPOSITORY_MOVE_GROUP_FAIL,
                        arrayOf(groupCode)
                    )
                }
            }
            return Result(getGitProjectInfo(projectId.toString(), token, tokenType).data)
        }
    }

    private fun setToken(tokenType: TokenTypeEnum, url: StringBuilder, token: String) {
        if (TokenTypeEnum.OAUTH == tokenType) {
            url.append("?access_token=$token")
        } else {
            url.append("?private_token=$token")
        }
    }
}