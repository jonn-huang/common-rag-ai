/**
 * 后端API地址
 */
export const enum KnowApi {
  UploadFile = "/knowledge/file/upload",
  PreviewChunk = "/knowledge/file/preview",
  FileDetail = "/knowledge/file/detail",
  FileChunks = "/knowledge/file/chunks",
  ChunkList = "/knowledge/chunks",
  ChunkUpdate = "/knowledge/chunks/update",
  ChunkDelete = "/knowledge/chunks/delete",
  UpdateFile = "/knowledge/file/update",
  QueryFile = "/knowledge/contents",
  DeleteFile = "/knowledge/delete",
  DownloadFile = "/knowledge/download",
  RetrievalConfig = "/knowledge/retrieval/config",
  RetrievalCategories = "/knowledge/retrieval/categories",
  RetrievalRebuildIndex = "/knowledge/retrieval/rebuild-index",
  RetrievalPreview = "/knowledge/retrieval/preview",
  AnswerConfig = "/knowledge/answer/config",
  AnswerDefault = "/knowledge/answer/default",
}

export const enum ChatApi {
  StreamChat = "/chat/stream",
  SimpleChat = "/chat/simple",
  Models = "/chat/models",
}

export const enum RagApi {
  StreamRag = "/ai/rag",
}

export const enum OneApi {
  AddOneApi = "/one-api",
  QueryApi = "/select",
  ChangeApi = "/change/",
  QueryOneApi = "/select/",
  DeleteOneApi = "/delete/",
  DeleteApi = "/delete",
}

export const enum DrawApi {
  DrawApi = "/draw/",
}