package com.tencent.devops.repository.service.impl

import com.tencent.devops.repository.pojo.Project
import com.tencent.devops.repository.pojo.enums.RepoAuthType
import com.tencent.devops.repository.pojo.oauth.GitToken
import com.tencent.devops.repository.service.RepostioryScmService
import com.tencent.devops.repository.service.scm.GitService
import com.tencent.devops.repository.service.scm.SvnService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RepostioryScmServiceImpl @Autowired constructor(
    private val gitService: GitService,
    private val svnService: SvnService
):RepostioryScmService{

    override fun getProject(accessToken: String, userId: String): List<Project> {
        return gitService.getProject(accessToken, userId)
    }

    override fun getAuthUrl(authParamJsonStr: String): String {
        return gitService.getAuthUrl(authParamJsonStr)
    }

    override fun getToken(userId: String, code: String): GitToken {
        return gitService.getToken(userId, code)
    }

    override fun getRedirectUrl(authParamJsonStr: String): String {
        return gitService.getRedirectUrl(authParamJsonStr)
    }

    override fun refreshToken(userId: String, accessToken: GitToken): GitToken {
        return gitService.refreshToken(userId, accessToken)
    }

    override fun getSvnFileContent(url: String, userId: String, svnType: String, filePath: String, reversion: Long, credential1: String, credential2: String?): String {
        return svnService.getFileContent(url, userId, svnType, filePath, reversion, credential1, credential2)
    }

    override fun getGitFileContent(repoName: String, filePath: String, authType: RepoAuthType?, token: String, ref: String): String {
        return gitService.getGitFileContent(repoName, filePath, authType, token, ref)
    }

    override fun getGitlabFileContent(repoUrl: String, repoName: String, filePath: String, ref: String, accessToken: String): String {
        return gitService.getGitlabFileContent(
                repoUrl = repoUrl,
                repoName = repoName,
                filePath = filePath,
                ref = ref,
                accessToken = accessToken
        )
    }
}