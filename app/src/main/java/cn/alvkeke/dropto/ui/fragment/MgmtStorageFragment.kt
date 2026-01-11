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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.mgmt.Global.getFolderImage
import cn.alvkeke.dropto.mgmt.Global.getFolderImageShare
import cn.alvkeke.dropto.ui.activity.MainViewModel
import cn.alvkeke.dropto.ui.adapter.ImageListAdapter
import java.io.File
import java.util.LinkedList

class MgmtStorageFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var cbImage: CheckBox
    private lateinit var cbCache: CheckBox
    private lateinit var buttonClear: Button
    private lateinit var imageListAdapter: ImageListAdapter

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

        cbImage = view.findViewById(R.id.mgmt_storage_image)
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

        imageListAdapter = ImageListAdapter(context)
        listFilename.setAdapter(imageListAdapter)
        imageListAdapter.setItemClickListener(OnItemClickListener())

        buttonClear.setOnClickListener { ignored: View ->
            this.clearSelectedData(
                ignored
            )
        }

        cbImage.isChecked = true
        cbCache.isChecked = true

        initFolders()
        Thread(taskCalcCache).start()
        Thread(taskCalcImage).start()
    }

    lateinit var folderImage: File
    lateinit var folderCache: File
    private fun initFolders() {
        folderCache = getFolderImageShare(requireContext())
        folderImage = getFolderImage(requireContext())
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
            Thread {
                emptyFolder(folderCache)
                taskCalcCache.run()
            }.start()
        }
        if (cbImage.isChecked) {
            Thread {
                emptyFolder(folderImage)
                handler.post { imageListAdapter.emptyList() }
                taskCalcImage.run()
            }.start()
        }
        buttonClear.isEnabled = true
    }

    private inner class OnItemClickListener : ImageListAdapter.OnItemClickListener {
        override fun onClick(index: Int) {
            val name = imageListAdapter.get(index)

            val imageFile = File(folderImage, name)
            val fragment = ImageViewerFragment()
            viewModel.setImageFile(imageFile)

            fragment.show(getParentFragmentManager(), null)
        }

        override fun onLongClick(index: Int): Boolean {
            val name = imageListAdapter.get(index)
            val imageFile = File(folderImage, name)
            val tmp = imageFile.length()
            if (imageFile.delete()) {
                sizeImage -= tmp
                imageListAdapter.remove(index)
                setTextSizeString(cbImage, R.string.string_image_storage_usage_prompt, sizeImage)
            }
            return true
        }
    }


    private val handler = Handler(Looper.getMainLooper())
    private var sizeCache: Long = 0
    private var sizeImage: Long = 0
    private fun setTextSizeString(view: TextView, strId: Int, size: Long) {
        var string = resources.getString(strId)
        string += " " + getSizeString(size)
        view.text = string
    }

    private val taskCalcCache = Runnable {
        sizeCache = 0
        iterateFolder(folderCache) { file ->
            if (file.isFile) sizeCache += file.length()
            handler.post {
                setTextSizeString(
                    cbCache,
                    R.string.string_cache_storage_usage_prompt, sizeCache
                )
            }
        }
    }

    private val taskCalcImage = Runnable {
        sizeImage = 0
        iterateFolder(folderImage) { file ->
            if (file.isFile) sizeImage += file.length()
            handler.post {
                if (file.isFile) imageListAdapter.add(file.name)
                setTextSizeString(cbImage, R.string.string_image_storage_usage_prompt, sizeImage)
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
