package cn.alvkeke.dropto.ui.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.mgmt.Global.getFolderImage
import cn.alvkeke.dropto.mgmt.Global.getFolderImageShare
import cn.alvkeke.dropto.storage.FileHelper
import cn.alvkeke.dropto.ui.activity.MainViewModel
import cn.alvkeke.dropto.ui.adapter.AttachmentListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.LinkedList

class MgmtStorageFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var cbAttachment: CheckBox
    private lateinit var cbCache: CheckBox
    private lateinit var buttonClear: Button
    private lateinit var attachmentListAdapter: AttachmentListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_mgmt_storage, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        cbAttachment = view.findViewById(R.id.mgmt_storage_attachment)
        cbCache = view.findViewById(R.id.mgmt_storage_cache)
        buttonClear = view.findViewById(R.id.mgmt_storage_btn_clear)
        val listFilename = view.findViewById<RecyclerView>(R.id.mgmt_storage_list_files)

        val context = requireContext()
        val layoutManager = LinearLayoutManager(context)
        listFilename.setLayoutManager(layoutManager)
        listFilename.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )

        attachmentListAdapter = AttachmentListAdapter(context)
        listFilename.setAdapter(attachmentListAdapter)
        attachmentListAdapter.setItemClickListener(OnItemClickListener())

        buttonClear.setOnClickListener { ignored: View ->
            this.clearSelectedData(
                ignored
            )
        }

        cbAttachment.isChecked = true
        cbCache.isChecked = true

        initFolders()
        viewLifecycleOwner.lifecycleScope.launch(SupervisorJob() + Dispatchers.IO) {
            taskCalcCache()
        }
        viewLifecycleOwner.lifecycleScope.launch(SupervisorJob() + Dispatchers.IO) {
            taskCalcAttachments()
        }
    }

    lateinit var attachmentFolder: File
    lateinit var cacheFolder: File
    private fun initFolders() {
        cacheFolder = getFolderImageShare(requireContext())
        attachmentFolder = getFolderImage(requireContext())
    }

    private fun interface FolderIterator {
        fun on(file: File)
    }

    private fun iterateFolder(folder: File, iterator: FolderIterator) {
        var folder = folder
        if (folder.isFile) {
            iterator.on(folder)
            return
        }
        val folders = LinkedList<File>()
        folders.push(folder)

        while (true) {
            try {
                folder = folders.pop()!!
            } catch (_: NoSuchElementException) {
                return
            }

            val files = folder.listFiles() ?: continue

            for (ff in files) {
                if (ff.isDirectory) {
                    folders.push(ff)
                    continue
                }
                iterator.on(ff)
            }
            iterator.on(folder)
        }
    }

    private fun emptyFolder(folder: File) {
        iterateFolder(folder) { file ->
            if (file === folder) return@iterateFolder
            val ret = file.delete()
            if (!ret) {
                Log.e(this.toString(), "failed to remove " + file.absolutePath)
            }
        }
    }

    private fun clearSelectedData(v: View) {
        buttonClear.isEnabled = false
        if (cbCache.isChecked) {
            viewLifecycleOwner.lifecycleScope.launch(SupervisorJob() + Dispatchers.IO) {
                emptyFolder(cacheFolder)
                taskCalcCache()
            }
        }
        if (cbAttachment.isChecked) {
            viewLifecycleOwner.lifecycleScope.launch(SupervisorJob() + Dispatchers.IO) {
                emptyFolder(attachmentFolder)
                handler.post { attachmentListAdapter.emptyList() }
                taskCalcAttachments()
            }
        }
        buttonClear.isEnabled = true
    }

    private inner class OnItemClickListener : AttachmentListAdapter.OnItemClickListener {
        override fun onClick(index: Int) {
            val name = attachmentListAdapter.get(index)

            val imageFile = File(attachmentFolder, name)
            val fragment = ImageViewerFragment()
            viewModel.setImageFile(imageFile)

            fragment.show(getParentFragmentManager(), null)
        }

        override fun onLongClick(index: Int): Boolean {
            val name = attachmentListAdapter.get(index)
            val imageFile = File(attachmentFolder, name)
            val tmp = imageFile.length()
            if (imageFile.delete()) {
                sizeAttachments -= tmp
                attachmentListAdapter.remove(index)
                setTextSizeString(cbAttachment, R.string.string_image_storage_usage_prompt, sizeAttachments)
            }
            return true
        }
    }


    private val handler = Handler(Looper.getMainLooper())
    private var sizeCache: Long = 0
    private var sizeAttachments: Long = 0
    private fun setTextSizeString(view: TextView, strId: Int, size: Long) {
        var string = resources.getString(strId)
        string += " " + getSizeString(size)
        view.text = string
    }

    private fun taskCalcCache() {
        sizeCache = 0
        iterateFolder(cacheFolder) { file ->
            if (file.isFile) sizeCache += file.length()
            handler.post {
                setTextSizeString(
                    cbCache,
                    R.string.string_cache_storage_usage_prompt, sizeCache
                )
            }
        }
    }

    private fun taskCalcAttachments() {
        sizeAttachments = 0
        iterateFolder(attachmentFolder) { file ->
            if (file.isFile) sizeAttachments += file.length()
            handler.post {
                if (!file.isFile) return@post
                if (file.name.endsWith(FileHelper.FILE_NAME_SUFFIX))
                    return@post
                val fileNames = FileHelper.getAllFileNamesFromMd5File(file)
                if (fileNames == null) {
                    attachmentListAdapter.add(file.name)
                } else {
                    val sb = StringBuffer()
                    sb.append("${file.name}\n")
                    for (fn in fileNames) {
                        sb.append("* $fn\n")
                    }
                    attachmentListAdapter.add(sb.toString())
                }
                setTextSizeString(cbAttachment, R.string.string_image_storage_usage_prompt, sizeAttachments)
            }
        }
    }

    companion object {
        private fun getSizeType(size: Long): Int {
            return if (size < 1000L) 0
            else if (size < 1000000L) 1
            else if (size < 1000000000L) 2
            else 3
        }

        private fun getUnitString(div: Int): String {
            return when (div) {
                0 -> "B"
                1 -> "KB"
                2 -> "MB"
                else -> "GB"
            }
        }

        private fun getDivider(div: Int): Int {
            return when (div) {
                0 -> 1
                1 -> 1000
                2 -> 1000000
                else -> 1000000000
            }
        }

        private fun getSizeString(size: Long): String {
            val type: Int = getSizeType(size)
            val divider: Int = getDivider(type)
            return (size / divider).toString() + getUnitString(type)
        }
    }
}
