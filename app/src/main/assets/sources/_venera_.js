/**
 * Venera 类型定义
 * 供 IDE 智能提示和源开发者参考
 * 包含 Venera API 标准接口的类定义
 */

// 漫画源基类
class ComicSource {
  name = "";
  key = "";
  version = "1.0.0";
  minAppVersion = "1.0.0";
  url = "";
}

// 搜索结果中的漫画对象
class Comic {
  constructor(data) {
    this.id = data.id;
    this.title = data.title;
    this.cover = data.cover;
    this.subTitle = data.subTitle;
    this.tags = data.tags || [];
    this.description = data.description;
    this.url = data.url;
  }
}

// 漫画详情对象
class ComicDetails {
  constructor(data) {
    this.id = data.id;
    this.title = data.title;
    this.cover = data.cover;
    this.author = data.author;
    this.tags = data.tags || [];
    this.description = data.description;
    this.episodes = data.episodes || [];
    this.chapters = data.chapters || [];
  }
}

// 单个章节对象
class Episode {
  constructor(data) {
    this.id = data.id;
    this.epId = data.epId;
    this.title = data.title;
    this.url = data.url;
  }
}

// 章节图片结果
class ChapterImages {
  constructor(data) {
    this.images = data.images || [];
    this.pics = data.pics || [];
  }
}

// 搜索结果包装对象
class SearchResult {
  constructor(data) {
    this.comics = data.comics || [];
    this.maxPage = data.maxPage || 1;
    this.currentPage = data.currentPage || 1;
  }
}

// 评论对象
class Comment {
  constructor(data) {
    this.userName = data.userName;
    this.avatar = data.avatar;
    this.content = data.content;
    this.time = data.time;
    this.replyCount = data.replyCount;
    this.id = data.id;
  }
}

/**
 * 全局 API 说明：
 *
 * Network.get(url, headers)
 *   - 发送 GET 请求
 *   - 返回 { status, body, headers, error }
 *
 * Network.post(url, headers, body)
 *   - 发送 POST 请求
 *   - 返回 { status, body, headers, error }
 *
 * saveData(key, value)
 *   - 保存数据到本地存储
 *   - 返回 { value: true }
 *
 * loadData(key)
 *   - 从本地存储加载数据
 *   - 返回 { value: "..." } 或 null
 *
 * deleteData(key)
 *   - 删除本地存储中的数据
 *   - 返回 { value: true }
 */