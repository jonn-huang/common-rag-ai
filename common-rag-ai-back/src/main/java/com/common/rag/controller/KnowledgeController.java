package com.common.rag.controller;

/**
 * @Title: KnowledgeController
 * @Package com.common.rag.controller
 * @Date 2025/2/8 20:35
 * @description: 知识库
 */

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.common.rag.core.ApplicationConstant;
import com.common.rag.core.BaseResponse;
import com.common.rag.core.ErrorCode;
import com.common.rag.core.ResultUtils;
import com.common.rag.entity.AliOssFile;
import com.common.rag.enums.ChunkStrategyEnum;
import com.common.rag.enums.EtlStatusEnum;
import com.common.rag.etl.DocumentChunkService;
import com.common.rag.etl.KnowledgeChunkIndexService;
import com.common.rag.pojo.dto.ChunkConfigDTO;
import com.common.rag.pojo.dto.FileUpdateDTO;
import com.common.rag.pojo.dto.QueryFileDTO;
import com.common.rag.pojo.vo.ChunkPreviewResultVO;
import com.common.rag.pojo.vo.ChunkPreviewVO;
import com.common.rag.service.AliOssFileService;
import com.common.rag.utils.AliOssUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Tag(name = "KnowledgeController", description = "知识库管理接口")
@Slf4j
@RestController
@RequestMapping(ApplicationConstant.API_VERSION + "/knowledge")
public class KnowledgeController {

    /**
     * 未指定知识库分类时的默认分类
     */
    private static final String DEFAULT_CATEGORY = "未分类";

    /**
     * 预览文件大小上限，避免大文件解析与切片占用过多内存
     */
    private static final long MAX_PREVIEW_FILE_SIZE = 20L * 1024 * 1024;

    /**
     * 预览默认返回的切片条数
     */
    private static final int DEFAULT_PREVIEW_LIMIT = 10;

    /**
     * 预览允许返回的最大切片条数
     */
    private static final int MAX_PREVIEW_LIMIT = 50;

    /**
     * 元数据展示顺序：业务字段在前，解析器原始元数据补充在后
     */
    private static final List<String> METADATA_DISPLAY_KEYS = List.of(
            "fileName", "category", "chunkStrategy", "chunkIndex", "chunkTotal", "heading", "fileId");

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private DocumentChunkService documentChunkService;

    @Autowired
    private AliOssFileService aliOssFileService;

    @Autowired
    private KnowledgeChunkIndexService knowledgeChunkIndexService;
    
    /**
     * 上传附件接口
     *
     *  1. 提供不同的分片策略
     *  2. 分片后的预览
     *
     *  text-to-sql:
     *  上传资料--->（用户选择是否聚合）----> tosql---->创建表、插入数据
     * @param
     * @return
     * @throws IOException
     */

    @Operation(summary = "upload", description = "上传附件接口")
    @PostMapping(value = "file/upload", headers = "content-type=multipart/form-data")
    public BaseResponse upload(@RequestParam("file") List<MultipartFile> files,
                               @RequestParam(value = "category", required = false) String category,
                               @RequestParam(value = "chunkStrategy", required = false) String chunkStrategy,
                               @RequestParam(value = "chunkConfig", required = false) String chunkConfig) {
        if (files.isEmpty()) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请上传文件");
        }

        String fileCategory = StringUtils.hasText(category) ? category.trim() : DEFAULT_CATEGORY;
        // 切片策略与参数只解析一次，未知策略回退递归切片，参数缺失或非法回退默认值，不阻断上传
        ChunkStrategyEnum strategy = ChunkStrategyEnum.fromCode(chunkStrategy);
        ChunkConfigDTO config = ChunkConfigDTO.parse(chunkConfig, strategy);
        log.info("文件上传切片配置：strategy={}, config={}", strategy.getCode(), JSON.toJSONString(config));
        int successCount = 0;
        List<String> failFileNames = new ArrayList<>();

        // 上传文件
        for (MultipartFile file : files) {

            // 原文件名
            String originalFilename = file.getOriginalFilename();
            // 文件名或后缀缺失时无法拼接 OSS 对象名，直接判失败
            if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
                log.warn("文件名为空或不含后缀，已跳过: {}", originalFilename);
                failFileNames.add(String.valueOf(originalFilename));
                continue;
            }
            // 文件后缀
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            // 随机文件名（OSS)
            String objectName = UUID.randomUUID() + extension;

            // 1. 上传 OSS，只有真正上传成功才落库
            String url;
            try {
                url = aliOssUtil.upload(file.getBytes(), objectName);
            } catch (Exception e) {
                log.error("文件上传OSS失败: {}", originalFilename, e);
                failFileNames.add(originalFilename);
                continue;
            }

            // 2. 先落库并标记为 ETL 中，保证后续向量化失败也有记录可追踪
            long currMillis = System.currentTimeMillis();
            AliOssFile aliOssFile = AliOssFile.builder()
                    .fileName(originalFilename)
                    .url(url)
                    .fileSize(file.getSize())
                    .category(fileCategory)
                    .status(EtlStatusEnum.PROCESSING.getCode())
                    .chunkStrategy(strategy.getCode())
                    .chunkConfig(JSON.toJSONString(config))
                    .createTime(new Date(currMillis))
                    .updateTime(new Date(currMillis))
                    .build();
            aliOssFileService.save(aliOssFile);

            // 3. 向量化
            try {
                // 读取文件 txt pdf docx doc，按用户选择的策略切片并补齐向量库需要的元数据
                List<Document> splitDocuments = splitDocuments(file.getResource(), strategy, config, aliOssFile);
                // 向量化并保存向量 自动调用向量模型向量化方法
                vectorStore.add(splitDocuments);
                // 同步写入关键词索引，供 BM25 全文检索使用
                knowledgeChunkIndexService.replaceChunks(aliOssFile, splitDocuments);

                // 4. 回写向量ID并标记 ETL 成功
                aliOssFile.setVectorId(toVectorIdsJson(splitDocuments));
                aliOssFile.setStatus(EtlStatusEnum.SUCCESS.getCode());
                aliOssFile.setUpdateTime(new Date());
                aliOssFileService.updateById(aliOssFile);
                successCount++;
            } catch (Exception e) {
                log.error("文件向量化失败: {}", originalFilename, e);
                aliOssFile.setStatus(EtlStatusEnum.FAILED.getCode());
                aliOssFile.setUpdateTime(new Date());
                aliOssFileService.updateById(aliOssFile);
                failFileNames.add(originalFilename);
            }
        }

        if (failFileNames.isEmpty()) {
            return ResultUtils.success("成功上传" + successCount + "个文件");
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR,
                "上传完成：成功" + successCount + "个，失败" + failFileNames.size() + "个（" + String.join("、", failFileNames) + "），失败的可在列表中查看状态");
    }


    /**
     * 切片预览：只解析与切片，不落库、不写向量库，供用户上传前确认切片效果
     */
    @Operation(summary = "preview", description = "切片预览接口")
    @PostMapping(value = "file/preview", headers = "content-type=multipart/form-data")
    public BaseResponse preview(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "chunkStrategy", required = false) String chunkStrategy,
                                @RequestParam(value = "chunkConfig", required = false) String chunkConfig,
                                @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit) {
        if (file == null || file.isEmpty()) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请选择要预览的文件");
        }
        if (file.getSize() > MAX_PREVIEW_FILE_SIZE) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "预览文件不能超过 20MB，可直接上传后查看切片结果");
        }
        String originalFilename = file.getOriginalFilename();
        ChunkStrategyEnum strategy = ChunkStrategyEnum.fromCode(chunkStrategy);
        ChunkConfigDTO config = ChunkConfigDTO.parse(chunkConfig, strategy);
        int previewLimit = normalizePreviewLimit(limit);

        // 1. 解析文件：预览不落 OSS，直接读上传流
        List<Document> documents;
        try {
            documents = new TikaDocumentReader(file.getResource()).read();
        } catch (Exception e) {
            log.error("切片预览文件解析失败: {}", originalFilename, e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "文件解析失败，请确认文件格式是否受支持");
        }

        // 2. 按所选策略切片，预览不落库，这里用临时文件对象承载文件名等元数据
        AliOssFile previewFile = AliOssFile.builder()
                .fileName(originalFilename)
                .category(DEFAULT_CATEGORY)
                .build();
        List<Document> chunks = documentChunkService.split(documents, strategy, config, previewFile);
        if (chunks.isEmpty()) {
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "未解析到可切片的内容，请确认文件是否为空或为扫描件");
        }

        // 3. 只返回前 previewLimit 条，避免大文件响应体过大
        log.info("切片预览完成：file={}, strategy={}, total={}", originalFilename, strategy.getCode(), chunks.size());
        return ResultUtils.success(buildPreviewResult(chunks, strategy, originalFilename, previewLimit));
    }

    /**
     * 文件详情：返回文件基础信息与切片策略、分片数量等展示字段
     */
    @Operation(summary = "detail", description = "文件详情接口")
    @GetMapping("/file/detail")
    public BaseResponse detail(@RequestParam("id") Long id) {
        AliOssFile file = aliOssFileService.getFileDetail(id);
        if (file == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "文件不存在");
        }
        return ResultUtils.success(file);
    }

    /**
     * 分片预览：按文件已保存的切片策略重新计算，不修改已有向量；
     * 传入 chunkStrategy/chunkConfig 时按新参数试算，用于重新切片前预览效果
     */
    @Operation(summary = "chunks", description = "已入库文件的分片预览接口")
    @GetMapping("/file/chunks")
    public BaseResponse previewStoredChunks(@RequestParam("id") Long id,
                                            @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit,
                                            @RequestParam(value = "chunkStrategy", required = false) String chunkStrategy,
                                            @RequestParam(value = "chunkConfig", required = false) String chunkConfig) {
        AliOssFile file = aliOssFileService.getFileDetail(id);
        if (file == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "文件不存在");
        }
        // 处理中的文件分片仍在变化，其余状态（含 ETL 失败）都允许按参数试算预览，便于重切前确认效果
        if (EtlStatusEnum.PROCESSING.getCode().equals(file.getStatus())) {
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "文件正在 ETL 处理中，请稍后再试");
        }

        // 1. 从 OSS 取回原始文件，文件过大时引导用户下载查看，避免服务端内存与耗时失控
        byte[] bytes;
        try {
            bytes = aliOssUtil.getObjectBytes(aliOssUtil.resolveObjectName(file.getUrl()));
        } catch (Exception e) {
            log.error("分片预览读取 OSS 失败: {}", file.getFileName(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "读取原始文件失败，请稍后重试");
        }
        if (bytes.length > MAX_PREVIEW_FILE_SIZE) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "文件超过 20MB，暂不支持在线分片预览，可下载后查看");
        }

        // 2. 按传入的新参数或已保存的策略切片，结果用于预览，不落库
        ChunkStrategyEnum strategy = StringUtils.hasText(chunkStrategy)
                ? ChunkStrategyEnum.fromCode(chunkStrategy)
                : ChunkStrategyEnum.fromCode(file.getChunkStrategy());
        ChunkConfigDTO config = ChunkConfigDTO.parse(
                StringUtils.hasText(chunkStrategy) ? chunkConfig : file.getChunkConfig(), strategy);
        List<Document> chunks;
        try {
            List<Document> documents = new TikaDocumentReader(new NamedByteArrayResource(bytes, file.getFileName())).read();
            chunks = documentChunkService.split(documents, strategy, config, file);
        } catch (Exception e) {
            log.error("分片预览解析失败: {}", file.getFileName(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "文件解析失败，请确认文件格式是否受支持");
        }
        if (chunks.isEmpty()) {
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "未解析到可切片的内容，请确认文件是否为空或为扫描件");
        }

        int previewLimit = normalizePreviewLimit(limit);
        log.info("分片预览完成：file={}, strategy={}, total={}", file.getFileName(), strategy.getCode(), chunks.size());
        return ResultUtils.success(buildPreviewResult(chunks, strategy, file.getFileName(), previewLimit));
    }

    /**
     * 编辑文件：可修改分类与切片策略，策略或参数变化时按原始文件重新切片并向量化
     */
    @Operation(summary = "update", description = "文件编辑接口")
    @PostMapping("/file/update")
    public BaseResponse update(@RequestBody FileUpdateDTO request) {
        if (request == null || request.getId() == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "文件 id 不能为空");
        }
        AliOssFile file = aliOssFileService.getById(request.getId());
        if (file == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "文件不存在");
        }
        // 只拒绝正在处理中的文件，避免并发重复向量化
        if (EtlStatusEnum.PROCESSING.getCode().equals(file.getStatus())) {
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "文件正在 ETL 处理中，请稍后再试");
        }

        String category = StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : DEFAULT_CATEGORY;
        ChunkStrategyEnum strategy = ChunkStrategyEnum.fromCode(request.getChunkStrategy());
        ChunkConfigDTO config = ChunkConfigDTO.parse(request.getChunkConfig(), strategy);

        // 切片策略与参数都没变化时只更新分类，避免无意义的重复向量化；
        // 仅对 ETL 成功的文件生效，失败或未完成的文件必须按新参数强制重切才能恢复
        boolean paramsUnchanged = strategy.getCode().equalsIgnoreCase(file.getChunkStrategy())
                && sameChunkConfig(file.getChunkConfig(), config);
        if (paramsUnchanged && EtlStatusEnum.SUCCESS.getCode().equals(file.getStatus())) {
            file.setCategory(category);
            file.setUpdateTime(new Date());
            aliOssFileService.updateById(file);
            return ResultUtils.success("已更新文件信息");
        }

        // 1. 取回 OSS 上的原始文件，读取失败时保持原记录不变
        byte[] bytes;
        try {
            bytes = aliOssUtil.getObjectBytes(aliOssUtil.resolveObjectName(file.getUrl()));
        } catch (Exception e) {
            log.error("编辑文件时读取 OSS 失败: {}", file.getFileName(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "读取原始文件失败，请稍后重试");
        }

        // 2. 先标记 ETL 中并保存分类，便于列表及时反映编辑状态
        // 记录原状态，重切失败时回写，避免把可重试的旧数据误标为失败
        String previousStatus = file.getStatus();
        List<String> oldVectorIds = parseVectorIds(file.getVectorId());
        file.setCategory(category);
        file.setStatus(EtlStatusEnum.PROCESSING.getCode());
        file.setUpdateTime(new Date());
        aliOssFileService.updateById(file);

        List<String> newVectorIds = new ArrayList<>();
        try {
            // 3. 按新策略切片并写入向量库，成功后替换旧分片
            List<Document> chunks = splitDocuments(new NamedByteArrayResource(bytes, file.getFileName()), strategy, config, file);
            newVectorIds = chunks.stream().map(Document::getId).collect(Collectors.toList());
            vectorStore.add(chunks);
            // 关键词索引跟随切片策略一起更新
            knowledgeChunkIndexService.replaceChunks(file, chunks);
            deleteVectorsQuietly(oldVectorIds);

            file.setChunkStrategy(strategy.getCode());
            file.setChunkConfig(JSON.toJSONString(config));
            file.setVectorId(toVectorIdsJson(chunks));
            file.setStatus(EtlStatusEnum.SUCCESS.getCode());
            file.setUpdateTime(new Date());
            aliOssFileService.updateById(file);
            log.info("文件重新切片完成：file={}, strategy={}, chunks={}", file.getFileName(), strategy.getCode(), chunks.size());
            return ResultUtils.success("已按新策略重新切片，共 " + chunks.size() + " 个分片");
        } catch (Exception e) {
            log.error("文件重新切片失败: {}", file.getFileName(), e);
            // 新向量已写入但后续步骤失败时清掉本次写入，避免向量库残留脏数据
            deleteVectorsQuietly(newVectorIds);
            file.setStatus(StringUtils.hasText(previousStatus) ? previousStatus : EtlStatusEnum.FAILED.getCode());
            file.setUpdateTime(new Date());
            aliOssFileService.updateById(file);
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "重新切片失败，原有分片已保留，请稍后重试");
        }
    }

    /**
     * 组装切片预览结果，只返回前 limit 条
     */
    private ChunkPreviewResultVO buildPreviewResult(List<Document> chunks, ChunkStrategyEnum strategy,
                                                    String fileName, int limit) {
        List<ChunkPreviewVO> items = new ArrayList<>();
        for (int index = 0; index < chunks.size() && index < limit; index++) {
            Document chunk = chunks.get(index);
            items.add(ChunkPreviewVO.builder()
                    .index(index + 1)
                    .length(chunk.getText().length())
                    .heading(metadataText(chunk, "heading"))
                    .text(chunk.getText())
                    .metadata(orderedMetadata(chunk))
                    .build());
        }
        return ChunkPreviewResultVO.builder()
                .fileName(fileName)
                .chunkStrategy(strategy.getCode())
                .chunkStrategyDesc(strategy.getDesc())
                .total(chunks.size())
                .truncated(chunks.size() > items.size())
                .chunks(items)
                .build();
    }

    /**
     * 元数据按关键字段优先输出，其余原始元数据追加在后，保证前端展示顺序稳定
     */
    private Map<String, Object> orderedMetadata(Document document) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (String key : METADATA_DISPLAY_KEYS) {
            Object value = document.getMetadata().get(key);
            if (value != null) {
                metadata.put(key, value);
            }
        }
        document.getMetadata().forEach(metadata::putIfAbsent);
        return metadata;
    }

    /**
     * 预览条数收敛到 [1, MAX_PREVIEW_LIMIT]
     */
    private int normalizePreviewLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PREVIEW_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_PREVIEW_LIMIT);
    }

    /**
     * 解析文件并按策略切片，切片结果为空视为失败，由调用方决定状态回写
     */
    private List<Document> splitDocuments(Resource resource, ChunkStrategyEnum strategy,
                                          ChunkConfigDTO config, AliOssFile file) {
        List<Document> documents = new TikaDocumentReader(resource).read();
        List<Document> chunks = documentChunkService.split(documents, strategy, config, file);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("切片结果为空，文件内容可能无法解析");
        }
        return chunks;
    }

    /**
     * 切片结果的向量ID序列化，用于回写 vector_id 列
     */
    private String toVectorIdsJson(List<Document> documents) {
        return JSON.toJSONString(documents.stream().map(Document::getId).collect(Collectors.toList()));
    }

    /**
     * 解析已入库的向量ID列表，解析失败返回空集合
     */
    private List<String> parseVectorIds(String vectorId) {
        if (!StringUtils.hasText(vectorId)) {
            return new ArrayList<>();
        }
        try {
            List<String> ids = JSON.parseArray(vectorId, String.class);
            return ids == null ? new ArrayList<>() : ids;
        } catch (Exception e) {
            log.warn("向量ID解析失败，跳过分片清理: {}", vectorId);
            return new ArrayList<>();
        }
    }

    /**
     * 清理旧分片，失败只记录日志，不影响新分片已生效的结果
     */
    private void deleteVectorsQuietly(List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(vectorIds);
        } catch (Exception e) {
            log.warn("旧分片清理失败，需人工确认向量库残留: {}", vectorIds, e);
        }
    }

    /**
     * 比较已保存的切片参数与新参数是否一致，忽略空值差异；任一方解析失败都按"已变更"处理
     */
    private boolean sameChunkConfig(String storedJson, ChunkConfigDTO config) {
        if (!StringUtils.hasText(storedJson)) {
            return false;
        }
        try {
            JSONObject stored = JSON.parseObject(storedJson);
            JSONObject current = JSON.parseObject(JSON.toJSONString(config));
            removeBlankValues(stored);
            removeBlankValues(current);
            return current.equals(stored);
        } catch (Exception e) {
            log.warn("切片参数比较失败，按参数已变更处理", e);
            return false;
        }
    }

    /**
     * 移除空值参数，避免前端提交空字符串与库中 null 被误判为参数变更
     */
    private void removeBlankValues(JSONObject object) {
        if (object == null) {
            return;
        }
        object.entrySet().removeIf(entry -> entry.getValue() == null
                || (entry.getValue() instanceof String value && value.isBlank()));
    }

    /**
     * 带文件名的内存资源，Tika 需要文件名后缀辅助识别文件格式
     */
    private static class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    /**
     * 读取切片元数据中的字符串值，不存在时返回 null
     */
    private String metadataText(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? null : value.toString();
    }


    @Operation(summary = "contents",description = "文件查询")
    @GetMapping("/contents")
    public BaseResponse queryFiles(QueryFileDTO request){
        if(request.getPage() == null || request.getPageSize() == null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,"page 或 pageSize为空");
        }
        return aliOssFileService.queryPage(request);
    }

    @Operation(summary = "delete",description = "文件删除")
    @DeleteMapping("/delete")
    public BaseResponse deleteFiles(@RequestParam List<Long> ids){
        return aliOssFileService.deleteFiles(ids);
    }


    @Operation(summary = "download", description = "文件下载")
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFiles(@RequestParam List<Long> ids) {
        List<AliOssFile> files = aliOssFileService.listForDownload(ids);
        if (files.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            // 单个文件直接返回文件内容，多个文件打包成 zip，浏览器收到后会自动保存
            if (files.size() == 1) {
                AliOssFile file = files.get(0);
                byte[] bytes = aliOssUtil.getObjectBytes(aliOssUtil.resolveObjectName(file.getUrl()));
                return buildAttachment(bytes, file.getFileName());
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Set<String> usedNames = new HashSet<>();
            try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                for (AliOssFile file : files) {
                    byte[] bytes = aliOssUtil.getObjectBytes(aliOssUtil.resolveObjectName(file.getUrl()));
                    zip.putNextEntry(new ZipEntry(uniqueEntryName(file.getFileName(), usedNames)));
                    zip.write(bytes);
                    zip.closeEntry();
                }
            }
            String zipName = "知识库文件_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip";
            return buildAttachment(buffer.toByteArray(), zipName);
        } catch (Exception e) {
            log.error("文件下载失败 ids={}", ids, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 构造"浏览器另存为"响应，文件名按 RFC 5987 编码，中文不会乱码
     */
    private ResponseEntity<byte[]> buildAttachment(byte[] bytes, String fileName) {
        String downloadName = StringUtils.hasText(fileName) ? fileName : "download";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(downloadName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(bytes);
    }

    /**
     * zip 内部重名文件补序号，避免 putNextEntry 报重复条目
     */
    private String uniqueEntryName(String fileName, Set<String> usedNames) {
        String name = StringUtils.hasText(fileName) ? fileName : "file";
        if (usedNames.add(name)) {
            return name;
        }
        int dotIndex = name.lastIndexOf('.');
        String base = dotIndex > 0 ? name.substring(0, dotIndex) : name;
        String extension = dotIndex > 0 ? name.substring(dotIndex) : "";
        int index = 1;
        String candidate = base + "(" + index + ")" + extension;
        while (!usedNames.add(candidate)) {
            index++;
            candidate = base + "(" + index + ")" + extension;
        }
        return candidate;
    }




}