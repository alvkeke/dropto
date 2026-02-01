package cn.alvkeke.dropto.ui.activity

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import java.io.File

class MainViewModel : ViewModel() {

    override fun onCleared() {
        super.onCleared()

        Log.e(TAG, "MainViewModel onCleared")
    }

    private val _categories = MutableLiveData<ArrayList<Category>>()
    val categories: LiveData<ArrayList<Category>> = _categories
    fun setCategoriesList(list: ArrayList<Category>) : MainViewModel{
        _categories.value = list
        return this
    }

    private val _category = MutableLiveData<Category>()
    val category: LiveData<Category> = _category
    fun setCategory(category: Category) : MainViewModel{
        _category.value = category
        return this
    }

    private val _noteItem = MutableLiveData<NoteItem>()
    val noteItem: LiveData<NoteItem> = _noteItem
    fun setNoteItem(item: NoteItem) : MainViewModel{
        _noteItem.value = item
        return this
    }

    private val _image = MutableLiveData<File>()
    val imageFile: LiveData<File> = _image
    fun setImageFile(file: File) : MainViewModel{
        _image.value = file
        return this
    }

    companion object {
        const val TAG = "MainViewModel"
    }

}
