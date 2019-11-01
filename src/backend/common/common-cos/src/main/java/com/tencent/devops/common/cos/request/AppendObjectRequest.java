package com.tencent.devops.common.cos.request;

import com.tencent.devops.common.cos.model.enums.HttpMethodEnum;
import com.tencent.devops.common.cos.model.exception.COSException;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by schellingma on 2017/04/09.
 * Powered By Tencent
 */
public class AppendObjectRequest extends AbstractRequest {
    private final String objectName;
    private final byte[] inputBytes;
    private final MediaType mediaType;
    private final Map<String, String> headerParams;
    private final Map<String, String> queryParams;

    public AppendObjectRequest(final String bucketName,
                               final String objectName,
                               final Map<String, String> objectMetaMap,
                               final byte[] bytes,
                               final long positionAppend,
                               final String contentType) throws COSException {
        super(bucketName);
        if(StringUtils.isBlank(objectName)) {
            throw new COSException("Invalid object name");
        }
        this.objectName = objectName;

        if(bytes == null) {
            throw new COSException("Invalid input bytes");
        }
        this.inputBytes = bytes;
        if(StringUtils.isBlank(contentType)) {
            this.mediaType = DEFAULT_MEDIA_TYPE;
        } else {
            this.mediaType = MediaType.parse(contentType);
        }

        this.headerParams = new HashMap<>();
        if(positionAppend == 0)
        {
            if(objectMetaMap != null && !objectMetaMap.isEmpty()) {
                this.headerParams.putAll(objectMetaMap);
            }
        }

        //此处使用 LinkedHashMap, 因为请求url参数也会进入签名计算过程，顺序相关
        this.queryParams = new LinkedHashMap<>();
        this.queryParams.put("append", "");
        this.queryParams.put("position", String.valueOf(positionAppend));
    }

    @Override
    public Map<String, String> getQueryParams() {
        return this.queryParams;
    }

    @Override
    public Map<String, String> getHeaderParams() {
        Map<String, String> headers = new HashMap<>();
        if (!headerParams.isEmpty()) {
            headerParams.forEach((k, v) -> headers.put(String.format("X-COS-META-%s", k.toUpperCase()), v));
        }
        if(inputBytes != null) {
            headers.put("Content-Length", String.valueOf(inputBytes.length));
        }
        return headers;
    }

    @Override
    public Pair<HttpMethodEnum, RequestBody> getMethod() {
        return Pair.of(HttpMethodEnum.POST, RequestBody.create(mediaType, inputBytes));
    }

    @Override
    public String getPath() {
        return String.format("/%s", StringUtils.strip(objectName, " /"));
    }

}