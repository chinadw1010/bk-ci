package com.tencent.devops.repository.utils.scm

import com.tencent.devops.common.api.enums.ScmType
import com.tencent.devops.common.service.utils.SpringContextUtil
import com.tencent.devops.repository.config.GitConfig
import com.tencent.devops.repository.config.SVNConfig
import com.tencent.devops.repository.iscm.CodeGitScmImpl
import com.tencent.devops.repository.iscm.CodeGitlabScmImpl
import com.tencent.devops.repository.iscm.CodeSvnScmImpl
import com.tencent.devops.repository.iscm.CodeTGitScmImpl
import com.tencent.devops.repository.pojo.enums.CodeSvnRegion
import com.tencent.devops.scm.IScm
import com.tencent.devops.scm.code.git.CodeGitWebhookEvent

object ScmFactory {

    fun getScm(
            projectName: String,
            url: String,
            type: ScmType,
            branchName: String?,
            privateKey: String?,
            passPhrase: String?,
            token: String?,
            region: CodeSvnRegion?,
            userName: String?,
            event: String? = null
    ): IScm {
        return when (type) {
            ScmType.CODE_SVN -> {
                if (region == null) {
                    throw RuntimeException("The svn region is null")
                }

                if (userName == null) {
                    throw RuntimeException("The svn username is null")
                }

                if (privateKey == null) {
                    throw RuntimeException("The svn private key is null")
                }
                val svnConfig = SpringContextUtil.getBean(SVNConfig::class.java)
                CodeSvnScmImpl(projectName,
                        branchName,
                        url,
                        userName,
                        privateKey,
                        passPhrase,
                        svnConfig)
            }
            ScmType.CODE_GIT -> {
                if (token == null) {
                    throw RuntimeException("The git token is null")
                }
                if (event != null && CodeGitWebhookEvent.find(event) == null) {
                    throw RuntimeException("The git event is invalid")
                }
                val gitConfig = SpringContextUtil.getBean(GitConfig::class.java)
                CodeGitScmImpl(projectName,
                        branchName,
                        url,
                        privateKey,
                        passPhrase,
                        token,
                        gitConfig,
                        event
                )
            }
            ScmType.CODE_TGIT -> {
                if (token == null) {
                    throw RuntimeException("The git token is null")
                }
                if (event != null && CodeGitWebhookEvent.find(event) == null) {
                    throw RuntimeException("The git event is invalid")
                }
                val gitConfig = SpringContextUtil.getBean(GitConfig::class.java)
                CodeTGitScmImpl(projectName,
                        branchName,
                        url,
                        privateKey,
                        passPhrase,
                        token,
                        gitConfig,
                        event)
            }
            ScmType.CODE_GITLAB -> {
                if (token == null) {
                    throw RuntimeException("The gitlab access token is null")
                }
                val gitConfig = SpringContextUtil.getBean(GitConfig::class.java)
                CodeGitlabScmImpl(projectName, branchName, url, token, gitConfig)
            }
            else -> throw RuntimeException("Unknown repo($type)")
        }
    }
}