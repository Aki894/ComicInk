/**
 * JM18C 漫画源
 * 示例：展示 Venera API 基本实现
 * 这是一个演示源，实际网站可能不存在或结构不同
 */

class Jm18cSource extends ComicSource {
  name = "JM18C";
  key = "jm18c";
  version = "1.0.0";
  minAppVersion = "1.0.0";
  url = "https://jm18c.com";

  // 搜索功能
  search = {
    load: async (keyword, options, page) => {
      try {
        // 编码关键词
        const encodedKeyword = encodeURIComponent(keyword);
        const searchUrl = `https://jm18c.com/search?q=${encodedKeyword}&page=${page}`;

        // 发送搜索请求
        const res = await Network.get(searchUrl, {
          "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36",
          "Accept": "application/json, text/html",
          "Referer": "https://jm18c.com/"
        });

        if (res.status !== 200) {
          return {
            comics: [],
            maxPage: 1,
            currentPage: page
          };
        }

        // 解析搜索结果
        const comics = this.parseSearchResults(res.body);

        return {
          comics: comics,
          maxPage: 10,
          currentPage: page
        };
      } catch (e) {
        console.error("Search error:", e);
        return {
          comics: [],
          maxPage: 1,
          currentPage: page
        };
      }
    }
  };

  // 漫画详情和章节功能
  comic = {
    // 获取漫画详情
    loadInfo: async (id) => {
      try {
        const detailUrl = `https://jm18c.com/comic/${id}`;
        const res = await Network.get(detailUrl, {
          "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36",
          "Accept": "application/json, text/html",
          "Referer": "https://jm18c.com/"
        });

        if (res.status !== 200) {
          return {
            id: id,
            title: "Error loading comic",
            cover: "",
            author: "",
            tags: [],
            description: "",
            episodes: []
          };
        }

        return this.parseComicInfo(id, res.body);
      } catch (e) {
        console.error("LoadInfo error:", e);
        return {
          id: id,
          title: "Error",
          cover: "",
          author: "",
          tags: [],
          description: e.message || "Failed to load comic info",
          episodes: []
        };
      }
    },

    // 获取章节图片
    loadEp: async (comicId, epId) => {
      try {
        const epUrl = `https://jm18c.com/ep/${comicId}/${epId}`;
        const res = await Network.get(epUrl, {
          "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36",
          "Accept": "application/json, text/html",
          "Referer": `https://jm18c.com/comic/${comicId}`
        });

        if (res.status !== 200) {
          return { images: [] };
        }

        return this.parseChapterImages(res.body);
      } catch (e) {
        console.error("LoadEp error:", e);
        return { images: [] };
      }
    }
  };

  /**
   * 解析搜索结果
   * 从 HTML 或 JSON 响应中提取漫画列表
   * @param {string} body - 响应内容
   * @returns {Array} 漫画数组
   */
  parseSearchResults(body) {
    // 尝试解析 JSON
    try {
      const data = JSON.parse(body);
      if (Array.isArray(data)) {
        return data.map(item => ({
          id: item.id || item.comic_id || "",
          title: item.title || item.name || "",
          cover: item.cover || item.thumb || "",
          subTitle: item.author || item.subtitle || "",
          tags: item.tags || [],
          description: item.description || "",
          url: item.url || ""
        }));
      }
      if (data.comics && Array.isArray(data.comics)) {
        return data.comics.map(item => ({
          id: item.id || "",
          title: item.title || "",
          cover: item.cover || "",
          subTitle: item.author || "",
          tags: item.tags || [],
          description: item.description || "",
          url: item.url || ""
        }));
      }
    } catch (e) {
      // JSON 解析失败，尝试 HTML 解析
      console.log("JSON parse failed, trying HTML parsing");
    }

    // 返回空数组（实际需要根据网站 HTML 结构实现）
    return [];
  }

  /**
   * 解析漫画详情
   * @param {string} id - 漫画 ID
   * @param {string} body - 响应内容
   * @returns {Object} 漫画详情
   */
  parseComicInfo(id, body) {
    // 尝试解析 JSON
    try {
      const data = JSON.parse(body);
      if (data) {
        return {
          id: id,
          title: data.title || "",
          cover: data.cover || data.image || "",
          author: data.author || "",
          tags: data.tags || [],
          description: data.description || "",
          episodes: (data.episodes || data.chapters || []).map(ep => ({
            id: ep.id || ep.epId || "",
            title: ep.title || ep.name || "",
            url: ep.url || ""
          }))
        };
      }
    } catch (e) {
      console.log("JSON parse failed for comic info");
    }

    // 返回默认结构
    return {
      id: id,
      title: "Unknown",
      cover: "",
      author: "",
      tags: [],
      description: "Failed to parse comic info",
      episodes: []
    };
  }

  /**
   * 解析章节图片
   * @param {string} body - 响应内容
   * @returns {Object} 图片数组
   */
  parseChapterImages(body) {
    // 尝试解析 JSON
    try {
      const data = JSON.parse(body);
      if (data) {
        // 兼容 images 和 pics 字段名
        const images = data.images || data.pics || data.pages || [];
        return { images: images };
      }
    } catch (e) {
      console.log("JSON parse failed for chapter images");
    }

    // 返回空数组
    return { images: [] };
  }
}

// 导出源实例
// Venera 加载器会实例化这个类
new Jm18cSource();