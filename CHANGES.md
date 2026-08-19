# 听书 App 改造说明（基于 legado-tts 二次开发）

## 已完成的功能改造

### 1. 双 TTS 引擎：微软 Edge + 豆包
- **微软 Edge TTS**：免费、无需配置、8 种中文神经语音（晓晓、云希、云扬等）
- **豆包 TTS**：需火山引擎 API Key（有免费额度）、16 种更自然的拟人音色
- 在「语音引擎」设置中可切换，配置弹窗中有两者区别提示

### 2. 前进/后退 15 秒按钮
- 朗读控制栏新增 `-15s` 和 `+15s` 按钮（模拟微信听书）
- Edge/豆包 TTS 使用 ExoPlayer 精确 seek，跨段落自动跳转
- 系统 TTS 回退为按段落跳转

### 3. 听书同步显示原文
- Legado 原生支持：朗读时阅读界面高亮当前段落
- 朗读控制栏可随时收起查看原文

### 4. 角色音色自动切换（Edge TTS）
- 自动识别中文引号「」"" 中的对话
- 叙述用主音色，对话用可配置的副音色
- 在 Edge TTS 音色设置中选择「对话音色」，可选「不启用角色切换」

### 5. 已有功能（原项目自带）
- 章节预加载：自动预下载下一章前 10 段音频
- 睡眠定时器：0-180 分钟可调，通知栏可一键加 10 分钟
- 蓝牙耳机控制：MediaSession 支持播放/暂停/上一首/下一首
- 后台播放、锁屏控制、来电自动暂停

## 新增/修改的文件

### 新增文件
- `app/src/main/java/io/legado/app/service/DoubaoSpeakFetch.kt` — 豆包 TTS API 客户端
- `app/src/main/java/io/legado/app/service/TTSDoubaoAloudService.kt` — 豆包朗读服务

### 修改文件
- `app/src/main/java/io/legado/app/model/ReadAloud.kt` — 豆包路由 + 15s 控制方法
- `app/src/main/java/io/legado/app/constant/IntentAction.kt` — 新增 rewind15/fastForward15
- `app/src/main/java/io/legado/app/service/BaseReadAloudService.kt` — 15s 前进/后退基类
- `app/src/main/java/io/legado/app/service/TTSEdgeAloudService.kt` — 精确 15s seek
- `app/src/main/java/io/legado/app/service/EdgeSpeakFetch.kt` — 角色音色多 voice SSML
- `app/src/main/java/io/legado/app/ui/book/read/config/ReadAloudDialog.kt` — 绑定 15s 按钮
- `app/src/main/java/io/legado/app/ui/book/read/config/SpeakEngineDialog.kt` — 豆包选项 + 区别提示 + 对话音色
- `app/src/main/res/layout/dialog_read_aloud.xml` — 新增 -15s/+15s 按钮
- `app/src/main/AndroidManifest.xml` — 注册 TTSDoubaoAloudService

## 编译方法

### 环境要求
- JDK 17
- Android SDK (compileSdk 35, buildTools 34.0.0)
- Gradle 8.11.1（项目自带 wrapper）

### 编译命令
```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
```
APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### 用 Android Studio 编译
1. 用 Android Studio 打开项目目录
2. 等待 Gradle sync 完成
3. Build → Build Bundle(s) / APK(s) → Build APK(s)

## 使用说明

### 首次配置
1. 安装 APK，打开 App
2. 导入本地小说（TXT/EPUB）或添加书源
3. 进入阅读界面，点击中心呼出菜单
4. 点击「朗读」按钮（喇叭图标）
5. 点击「设置」→「语音引擎」
6. 选择「Edge大声朗读」（免费即用）或「豆包TTS朗读」（需配置 API Key）

### 豆包 TTS 配置
1. 访问火山引擎控制台：https://console.volcengine.com/speech/service/8
2. 开通「语音合成大模型」服务
3. 在「服务接口认证信息」获取 AppID 和 Access Token
4. 在 App 语音引擎设置中点击豆包 TTS 右侧的编辑图标
5. 填入 AppID、Access Token，选择音色
6. 保存后选择「豆包TTS朗读」即可

### 角色音色切换（Edge TTS）
1. 语音引擎设置 → Edge 大声朗读 → 编辑图标
2. 选择主音色（叙述用）
3. 选择对话音色（引号内对话用），或选「不启用角色切换」
4. 保存后切换一下语速触发缓存重建

### 朗读控制
- 播放/暂停：中间按钮
- -15s / +15s：快退/快进 15 秒
- 上一句/下一句：段落跳转
- 上一章/下一章：章节跳转
- 定时器：拖动进度条或点击时间文字选择预设
- 语速：拖动进度条或点 +/- 按钮

## 注意事项
- 豆包 TTS 需联网，文本会发送到火山引擎服务器合成
- Edge TTS 需联网，文本会发送到微软服务器
- 本地电子书数据完全存储在手机本地，不上传
- 建议首次使用先试 Edge TTS（免费免配置），满意后再考虑豆包
