package com.tencent.devops.openapi.exception

open class MicroServiceInvokeFailure(val serviceInterface: String, message: String) : RuntimeException(message) {
    override fun toString(): String {
        return "MicroService($serviceInterface) invoke fail,message:$message"
    }
}