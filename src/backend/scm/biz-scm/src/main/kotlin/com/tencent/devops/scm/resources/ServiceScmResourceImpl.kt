package com.tencent.devops.scm.resources

import com.tencent.devops.common.api.enums.ScmType
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.repository.pojo.enums.CodeSvnRegion
import com.tencent.devops.scm.api.ServiceScmResource
import com.tencent.devops.scm.pojo.RevisionInfo
import com.tencent.devops.scm.pojo.TokenCheckResult
import com.tencent.devops.scm.pojo.request.CommitCheckRequest
import com.tencent.devops.scm.services.ScmService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class ServiceScmResourceImpl @Autowired constructor(private val scmService: ScmService) : ServiceScmResource {
    override fun getLatestRevision(
        projectName: String,
        url: String,
        type: ScmType,
        branchName: String?,
        additionalPath: String?,
        privateKey: String?,
        passPhrase: String?,
        token: String?,
        region: CodeSvnRegion?,
        userName: String?
    ): Result<RevisionInfo> {
        logger.info("Start to get the code latest version of (projectName=$projectName, url=$url, type=$type, branch=$branchName, additionalPath=$additionalPath, privateKey=$privateKey, passPhrase=$passPhrase, token=$token, region=$region, username=$userName)")
        return Result(scmService.getLatestRevision(projectName, url, type, branchName, privateKey, passPhrase, token, region, userName))
    }

    override fun listBranches(
        projectName: String,
        url: String,
        type: ScmType,
        privateKey: String?,
        passPhrase: String?,
        token: String?,
        region: CodeSvnRegion?,
        userName: String?
    ): Result<List<String>> {
        logger.info("Start to list the branches of (projectName=$projectName, url=$url, type=$type, privateKey=$privateKey, passPhrase=$passPhrase, token=$token, region=$region, username=$userName)")
        return Result(scmService.listBranches(projectName, url, type, privateKey, passPhrase, token, region, userName))
    }

    override fun listTags(projectName: String, url: String, type: ScmType, token: String, userName: String): Result<List<String>> {
        logger.info("Start to list the branches of (projectName=$projectName, url=$url, type=$type, token=$token, username=$userName)")
        return Result(scmService.listTags(projectName, url, type, token, userName))
    }

    override fun checkPrivateKeyAndToken(
        projectName: String,
        url: String,
        type: ScmType,
        privateKey: String?,
        passPhrase: String?,
        token: String?,
        region: CodeSvnRegion?,
        userName: String
    ): Result<TokenCheckResult> {
        logger.info("Start to check the private key and token of (projectName=$projectName, url=$url, type=$type, privateKey=$privateKey, passPhrase=$passPhrase, token=$token, region=$region, username=$userName)")
        return Result(scmService.checkPrivateKeyAndToken(projectName, url, type, privateKey, passPhrase, token, region, userName))
    }

    override fun checkUsernameAndPassword(
        projectName: String,
        url: String,
        type: ScmType,
        username: String,
        password: String,
        token: String,
        region: CodeSvnRegion?,
        repoUsername: String
    ): Result<TokenCheckResult> {
        logger.info("Start to check the username and password of (projectName=$projectName, url=$url, type=$type, username=$username, token=$token, region=$region, repoUsername=$repoUsername)")
        return Result(scmService.checkUsernameAndPassword(projectName, url, type, username, password, token, region, repoUsername))
    }

    override fun addWebHook(
        projectName: String,
        url: String,
        type: ScmType,
        privateKey: String?,
        passPhrase: String?,
        token: String?,
        region: CodeSvnRegion?,
        userName: String,
        event: String?
    ): Result<Boolean> {
        logger.info("Start to add the web hook of (projectName=$projectName, url=$url, type=$type, token=$token, username=$userName, event=$event)")
        scmService.addWebHook(projectName, url, type, privateKey, passPhrase, token, region, userName, event)
        return Result(true)
    }

    override fun addCommitCheck(
        request: CommitCheckRequest
    ): Result<Boolean> {
        logger.info("Start to add the commit check of request($request)")
        scmService.addCommitCheck(request)
        return Result(true)
    }

    override fun lock(
        projectId: String,
        url: String,
        type: ScmType,
        region: CodeSvnRegion?,
        userName: String
    ): Result<Boolean> {
        logger.info("Start to lock the repo of (projectId=$projectId, url=$url, type=$type, username=$userName)")
        scmService.lock(projectId, url, type, region, userName)
        return Result(true)
    }
    override fun unlock(
        projectName: String,
        url: String,
        type: ScmType,
        region: CodeSvnRegion?,
        userName: String
    ): Result<Boolean> {
        logger.info("Start to unlock the repo of (projectName=$projectName, url=$url, type=$type, username=$userName)")
        scmService.unlock(projectName, url, type, region, userName)
        return Result(true)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServiceScmResourceImpl::class.java)
    }
}