package io.legado.app.ui.book.read.config

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemHttpTtsBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.ui.association.ImportHttpTtsDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.applyTint
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.gone
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * tts引擎管理
 */
class SpeakEngineDialog() : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val viewModel: SpeakEngineViewModel by viewModels()
    private val ttsUrlKey = "ttsUrlKey"
    private val adapter by lazy { Adapter(requireContext()) }
    private var ttsEngine: String? = ReadAloud.ttsEngine
    private val sysTtsViews = arrayListOf<RadioButton>()
    private val callBack: CallBack? get() = parentFragment as? CallBack
    private val importDocResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportHttpTtsDialog(uri.toString()))
        }
    }
    private val exportDirResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                if (uri.toString().isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(uri.toString())
                }
                customView { alertBinding.root }
                okButton {
                    requireContext().sendToClip(uri.toString())
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initMenu()
        initData()
    }

    private fun initView() = binding.run {
        toolBar.setBackgroundColor(primaryColor)
        toolBar.setTitle(R.string.speak_engine)
        recyclerView.setEdgeEffectColor(primaryColor)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.addHeaderView {
            ItemHttpTtsBinding.inflate(layoutInflater, recyclerView, false).apply {
                sysTtsViews.add(cbName)
                ivEdit.gone()
                ivMenuDelete.gone()
                labelSys.visible()
                cbName.text = "系统默认"
                cbName.tag = ""
                cbName.isChecked = ttsEngine == null || ttsEngine!!.isJsonObject()
                        && GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                    .getOrNull()?.value.isNullOrEmpty()
                cbName.setOnClickListener {
                    upTts(GSON.toJson(SelectItem("系统默认", "")))
                }
            }

        }


        viewModel.sysEngines.forEach { engine ->
            System.out.println("engine.label:"+engine.label)
            adapter.addHeaderView {
                ItemHttpTtsBinding.inflate(layoutInflater, recyclerView, false).apply {
                    sysTtsViews.add(cbName)
                    ivEdit.gone()
                    ivMenuDelete.gone()
                    labelSys.visible()
                    cbName.text = engine.label
                    cbName.tag = engine.name
                    cbName.isChecked = GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                        .getOrNull()?.value == cbName.tag
                    cbName.setOnClickListener {
                        upTts(GSON.toJson(SelectItem(engine.label, engine.name)))
                    }
                }
            }
        }
        adapter.addHeaderView {
            ItemHttpTtsBinding.inflate(layoutInflater, recyclerView, false).apply {
                sysTtsViews.add(cbName)
                ivEdit.visible()
                ivMenuDelete.gone()
                labelSys.gone()
                cbName.text = "Edge大声朗读"
                cbName.tag = "edgeinner"
                cbName.isChecked = GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                    .getOrNull()?.value == cbName.tag
                cbName.setOnClickListener {
                    upTts(GSON.toJson(SelectItem("Edge大声朗读", "edgeinner")))
                }
                // 你的点击事件逻辑
                ivEdit.setOnClickListener {
                    val cacheKey = "tts_edge_voice"
                    val cacheValue = getSharedPrefValue(context, cacheKey, "")
                    var voiceOptions = listOf(
                        "晓晓@zh-CN-XiaoxiaoNeural",
                        "小艺@zh-CN-XiaoyiNeural",
                        "云健@zh-CN-YunjianNeural",
                        "云希@zh-CN-YunxiNeural",
                        "云夏@zh-CN-YunxiaNeural",
                        "云扬@zh-CN-YunyangNeural",
                        "小北（辽宁方言）@zh-CN-liaoning-XiaobeiNeural",
                        "小妮（陕西方言）@zh-CN-shaanxi-XiaoniNeural"
                    )
                    // 1. 创建Spinner控件
                    val spinner = Spinner(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setPadding(
                            dp2px(context, 16),
                            dp2px(context, 12),
                            dp2px(context, 16),
                            dp2px(context, 12)
                        )

                        // 2. 设置Spinner适配器
                        adapter = ArrayAdapter(
                            context,
                            android.R.layout.simple_spinner_item,
                            voiceOptions
                        ).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }

                        // 3. 选中缓存的选项（如果缓存值在列表中）
                        val cacheIndex = voiceOptions.indexOf(cacheValue)
                        if (cacheIndex != -1) {
                            setSelection(cacheIndex)
                        }
                    }

                    // 4. 构建弹窗并显示（含对话音色选择）
                    val dialogueKey = "tts_edge_dialogue_voice"
                    val dialogueValue = getSharedPrefValue(context, dialogueKey, "")
                    val dialogueOptions = listOf("不启用角色切换") + voiceOptions
                    val dialogueSpinner = Spinner(context).apply {
                        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, dialogueOptions).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        val dIdx = if (dialogueValue.isEmpty()) 0 else dialogueOptions.indexOfFirst { it.split("@")[1] == dialogueValue }
                        if (dIdx >= 0) setSelection(dIdx)
                    }
                    val container = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(spinner)
                        addView(dialogueSpinner)
                    }
                    AlertDialog.Builder(context)
                        .setTitle("Edge TTS 音色设置")
                        .setMessage("主音色：叙述部分；对话音色：引号内对话（不启用则统一用主音色）。保存后切换语速或等缓存读完生效。")
                        .setView(container)
                        .setPositiveButton("保存") { dialog, _ ->
                            val selectedValue = spinner.selectedItem.toString()
                            saveToSharedPref(context, cacheKey, selectedValue)
                            val dSel = dialogueSpinner.selectedItem.toString()
                            val dVoice = if (dSel == "不启用角色切换") "" else dSel.split("@")[1]
                            saveToSharedPref(context, dialogueKey, dVoice)
                            Toast.makeText(context, "音色已保存", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                        .setNegativeButton("取消", null)
                        .setCancelable(true)
                        .show()
                }
            }

        }
        // ===== 豆包 TTS 选项 =====
        adapter.addHeaderView {
            ItemHttpTtsBinding.inflate(layoutInflater, recyclerView, false).apply {
                sysTtsViews.add(cbName)
                ivEdit.visible()
                ivMenuDelete.gone()
                labelSys.gone()
                cbName.text = "豆包TTS朗读（需API Key）"
                cbName.tag = "doubaoinner"
                cbName.isChecked = GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                    .getOrNull()?.value == cbName.tag
                cbName.setOnClickListener {
                    upTts(GSON.toJson(SelectItem("豆包TTS朗读", "doubaoinner")))
                }
                ivEdit.setOnClickListener {
                    showDoubaoConfigDialog(context)
                }
            }
        }
        tvFooterLeft.setText(R.string.book)
        tvFooterLeft.visible()
        tvFooterLeft.setOnClickListener {
            ReadBook.book?.setTtsEngine(ttsEngine)
            callBack?.upSpeakEngineSummary()
            ReadAloud.upReadAloudClass()
            dismissAllowingStateLoss()
        }
        tvOk.setText(R.string.general)
        tvOk.visible()
        tvOk.setOnClickListener {
            ReadBook.book?.setTtsEngine(null)
            AppConfig.ttsEngine = ttsEngine
            callBack?.upSpeakEngineSummary()
            ReadAloud.upReadAloudClass()
            dismissAllowingStateLoss()
        }
        tvCancel.visible()
        tvCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun initMenu() = binding.run {
        toolBar.inflateMenu(R.menu.speak_engine)
        toolBar.menu.applyTint(requireContext())
        toolBar.setOnMenuItemClickListener(this@SpeakEngineDialog)
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.httpTTSDao.flowAll().catch {
                AppLog.put("朗读引擎界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                adapter.setItems(it)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> showDialogFragment<HttpTtsEditDialog>()
            R.id.menu_default -> viewModel.importDefault()
            R.id.menu_import_local -> importDocResult.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }

            R.id.menu_import_onLine -> importAlert()
            R.id.menu_export -> exportDirResult.launch {
                mode = HandleFileContract.EXPORT
                fileData = HandleFileContract.FileData(
                    "httpTts.json",
                    GSON.toJson(adapter.getItems()).toByteArray(),
                    "application/json"
                )
            }
        }
        return true
    }

    private fun importAlert() {
        val aCache = ACache.get(cacheDir = false)
        val cacheUrls: MutableList<String> = aCache
            .getAsString(ttsUrlKey)
            ?.splitNotBlank(",")
            ?.toMutableList() ?: mutableListOf()
        alert(R.string.import_on_line) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
                editView.setFilterValues(cacheUrls)
                editView.delCallBack = {
                    cacheUrls.remove(it)
                    aCache.put(ttsUrlKey, cacheUrls.joinToString(","))
                }
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { url ->
                    if (url.isAbsUrl() && !cacheUrls.contains(url)) {
                        cacheUrls.add(0, url)
                        aCache.put(ttsUrlKey, cacheUrls.joinToString(","))
                    }
                    showDialogFragment(ImportHttpTtsDialog(url))
                }
            }
        }
    }

    private fun upTts(tts: String) {
        ttsEngine = tts
        sysTtsViews.forEach {
            it.isChecked = GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                .getOrNull()?.value == it.tag
        }
        adapter.notifyItemRangeChanged(adapter.getHeaderCount(), adapter.itemCount)
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<HttpTTS, ItemHttpTtsBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemHttpTtsBinding {
            return ItemHttpTtsBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemHttpTtsBinding,
            item: HttpTTS,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                cbName.text = item.name
                cbName.isChecked = item.id.toString() == ttsEngine
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemHttpTtsBinding) {
            binding.run {
                cbName.setOnClickListener {
                    getItemByLayoutPosition(holder.layoutPosition)?.let { httpTTS ->
                        val id = httpTTS.id.toString()
                        upTts(id)
                        if (!httpTTS.loginUrl.isNullOrBlank()
                            && httpTTS.getLoginInfo().isNullOrBlank()
                        ) {
                            startActivity<SourceLoginActivity> {
                                putExtra("type", "httpTts")
                                putExtra("key", id)
                            }
                        }
                    }
                }
                ivEdit.setOnClickListener {
                    System.out.println("ivEdit>"+id);
                    val id = getItemByLayoutPosition(holder.layoutPosition)!!.id
                    showDialogFragment(HttpTtsEditDialog(id))
                }
                ivMenuDelete.setOnClickListener {
                    getItemByLayoutPosition(holder.layoutPosition)?.let { httpTTS ->
                        appDb.httpTTSDao.delete(httpTTS)
                    }
                }
            }
        }

    }


    // 辅助方法：dp转px
    fun dp2px(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    // 核心方法1：保存数据到SharedPreferences
    fun saveToSharedPref(context: Context?, key: String, value: String) {
        if (context == null) {
            return
        }
        // 获取SharedPreferences实例（名称自定义，模式用MODE_PRIVATE仅本应用可访问）
        val sp = context.getSharedPreferences("TTS_CONFIG", Context.MODE_PRIVATE)
        sp.edit().putString(key, value).apply() // apply()异步保存，不阻塞线程
    }


    // 核心方法2：从SharedPreferences读取数据
    fun getSharedPrefValue(context: Context?, key: String, defaultValue: String): String {
        if (context == null) {
            return defaultValue
        }
        val sp = context.getSharedPreferences("TTS_CONFIG", Context.MODE_PRIVATE)
        return sp.getString(key, defaultValue) ?: defaultValue // 防止null
    }

    /**
     * 豆包 TTS 配置对话框
     */
    private fun showDoubaoConfigDialog(context: Context) {
        val fetch = io.legado.app.service.DoubaoSpeakFetch()
        val (appId, accessToken, voice) = fetch.getConfig(context)
        val voiceOptions = io.legado.app.service.DoubaoSpeakFetch.VOICE_OPTIONS

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(context, 20), dp2px(context, 12), dp2px(context, 20), dp2px(context, 12))
        }
        val etAppId = EditText(context).apply {
            hint = "App ID（火山引擎控制台获取）"
            setText(appId)
            setPadding(dp2px(context, 12), dp2px(context, 8), dp2px(context, 12), dp2px(context, 8))
        }
        val etToken = EditText(context).apply {
            hint = "Access Token"
            setText(accessToken)
            setPadding(dp2px(context, 12), dp2px(context, 8), dp2px(context, 12), dp2px(context, 8))
        }
        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, voiceOptions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val currentIdx = voiceOptions.indexOfFirst { it.split("@")[1] == voice }
            if (currentIdx >= 0) setSelection(currentIdx)
        }
        layout.addView(etAppId)
        layout.addView(etToken)
        layout.addView(spinner)

        AlertDialog.Builder(context)
            .setTitle("豆包 TTS 配置")
            .setMessage("微软 Edge TTS：免费、无需配置、音色偏播音腔；\n豆包 TTS：需火山引擎 API Key（有免费额度）、音色更自然拟人、支持情绪。\n\nAppID 和 Token 在火山引擎控制台 → 语音合成大模型 → 服务接口认证信息 获取。")
            .setView(layout)
            .setPositiveButton("保存") { dialog, _ ->
                val newAppId = etAppId.text.toString().trim()
                val newToken = etToken.text.toString().trim()
                val newVoice = spinner.selectedItem.toString().split("@")[1]
                fetch.saveConfig(context, newAppId, newToken, newVoice)
                Toast.makeText(context, "豆包TTS配置已保存", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    interface CallBack {
        fun upSpeakEngineSummary()
    }

}