package com.common.rag.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;

@Data
@AllArgsConstructor
@Slf4j
public class AliOssUtil {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    /**
     * 文件上传
     *
     * @param bytes
     * @param objectName
     * @return
     */
    public String upload(byte[] bytes, String objectName) {

        if (bucketName == null || bucketName.isBlank()
                || endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("OSS 配置缺失: endpoint/bucketName 未注入，请检查 aliyun.alioss 配置");
        }

        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 创建PutObject请求。
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(bytes));
            log.info("OSS上传成功 bucket={} endpoint={} object={}", bucketName, endpoint, objectName);
        } catch (OSSException oe) {
            log.error("OSS上传失败(服务端拒绝) bucket={} endpoint={} object={} code={} msg={} requestId={}",
                    bucketName, endpoint, objectName,
                    oe.getErrorCode(), oe.getErrorMessage(), oe.getRequestId(), oe);
            throw new RuntimeException("OSS上传失败: " + oe.getErrorCode() + " " + oe.getErrorMessage(), oe);
        } catch (ClientException ce) {
            log.error("OSS上传失败(客户端/网络) bucket={} endpoint={} object={}",
                    bucketName, endpoint, objectName, ce);
            throw new RuntimeException("OSS上传失败: " + ce.getMessage(), ce);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        //文件访问路径规则 https://BucketName.Endpoint/ObjectName
        StringBuilder stringBuilder = new StringBuilder("https://");
        stringBuilder
                .append(bucketName)
                .append(".")
                .append(endpoint)
                .append("/")
                .append(objectName);

        log.info("文件访问地址:{}", stringBuilder);

        return stringBuilder.toString();
    }



    /**
     * 删除文件
     */
    /**
     * @param objectName
     * @Method deleteOss
     * @Author lofxve
     * @Description 删除oss文件
     * @Date 2021/4/21 9:02
     * @Return boolean
     */
    public boolean deleteOss(String objectName) {
        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            URL url = new URL(objectName);
            String fileName = url.getPath().replaceFirst("/", "");
            // 删除文件
            ossClient.deleteObject(bucketName, fileName);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }

        }
        return true;
    }

    /**
     * 下载文件：直接把 OSS 对象内容读成字节数组，交给接口以流的方式返回给浏览器
     *
     * @param objectName OSS 对象名
     * @return 文件内容
     */
    public byte[] getObjectBytes(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("OSS 对象名为空，无法下载");
        }
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (OSSObject ossObject = ossClient.getObject(bucketName, objectName)) {
            return ossObject.getObjectContent().readAllBytes();
        } catch (OSSException oe) {
            log.error("OSS下载失败(服务端拒绝) bucket={} object={} code={} msg={} requestId={}",
                    bucketName, objectName, oe.getErrorCode(), oe.getErrorMessage(), oe.getRequestId(), oe);
            throw new RuntimeException("OSS下载失败: " + oe.getErrorCode() + " " + oe.getErrorMessage(), oe);
        } catch (ClientException ce) {
            log.error("OSS下载失败(客户端/网络) bucket={} object={}", bucketName, objectName, ce);
            throw new RuntimeException("OSS下载失败: " + ce.getMessage(), ce);
        } catch (Exception e) {
            log.error("OSS下载失败 bucket={} object={}", bucketName, objectName, e);
            throw new RuntimeException("OSS下载失败: " + e.getMessage(), e);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    /**
     * 从完整访问地址中解析出 OSS 对象名
     *
     * @param url 文件访问地址
     * @return OSS 对象名，解析失败返回 null
     */
    public String resolveObjectName(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String cleanUrl = url.split("\\?")[0];
        String prefix = "https://" + bucketName + "." + endpoint + "/";
        if (cleanUrl.startsWith(prefix)) {
            return cleanUrl.substring(prefix.length());
        }
        int schemeIndex = cleanUrl.indexOf("://");
        int pathIndex = cleanUrl.indexOf('/', schemeIndex > 0 ? schemeIndex + 3 : 0);
        return pathIndex > 0 ? cleanUrl.substring(pathIndex + 1) : cleanUrl;
    }




}
