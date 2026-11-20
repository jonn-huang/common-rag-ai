/**
 * 知识库分类预置项：文档管理上传/编辑与文档切片管理筛选共用同一份，
 * 避免各处单独维护导致下拉选项不一致
 */
export const KNOWLEDGE_CATEGORY_OPTIONS = ["产品文档", "技术文档", "制度规范", "其他"];

/**
 * 上传时留空分类的文件在库中统一存为该分类
 */
export const UNCATEGORIZED_CATEGORY = "未分类";

/**
 * 合并预置分类与库中已有分类：预置项在前保持固定顺序，库中分类去重追加；
 * 空值与“未分类”不参与合并，由调用方决定是否单独追加
 */
export const mergeCategoryOptions = (existing: string[] = []): string[] => {
  const result = [...KNOWLEDGE_CATEGORY_OPTIONS];
  existing.forEach((item) => {
    const value = item?.trim();
    if (!value || value === UNCATEGORIZED_CATEGORY || result.includes(value)) {
      return;
    }
    result.push(value);
  });
  return result;
};
