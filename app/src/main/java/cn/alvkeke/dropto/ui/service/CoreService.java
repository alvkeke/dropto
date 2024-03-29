package cn.alvkeke.dropto.ui.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.storage.DataBaseHelper;

public class CoreService extends Service {

    public enum TaskResult {
        CATEGORY_CREATED,
        CATEGORY_REMOVED,
        CATEGORY_UPDATED,
        NOTE_CREATED,
        NOTE_REMOVED,
        NOTE_UPDATED,
    }

    public CoreService() {
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return binder;
    }

    private final IBinder binder = new IBinder();
    public class IBinder extends Binder {
        public CoreService getService() {
            return CoreService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(this.toString(), "CoreService start command");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(this.toString(), "Service onDestroy");
    }

    public void handleCategoryCreate(Category category) {
        if (category == null) {
            Log.e(this.toString(), "null category, abort creating");
            return;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            category.setId(helper.insertCategory(category));
            helper.finish();
            ArrayList<Category> categories = Global.getInstance().getCategories();
            categories.add(category);
            int index = categories.indexOf(category);
//            fragmentAdapter.getCategoryFragment().notifyItemListChanged(
//                    ListNotification.Notify.CREATED, index, categories);
            // TODO: use new method to notify UI thread
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to add new category to database");
        }
    }

    public void handleCategoryRemove(Category category) {
        if (category == null) {
            Log.e(this.toString(), "null category, abort deleting");
            return;
        }
        ArrayList<Category> categories = Global.getInstance().getCategories();
        int index;
        if ((index = categories.indexOf(category)) == -1) {
            Log.i(this.toString(), "category not exist in list, abort");
            return;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            if (0 == helper.deleteCategory(category.getId()))
                Log.e(this.toString(), "no category row be deleted in database");
            helper.finish();
            categories.remove(category);
//            fragmentAdapter.getCategoryFragment().notifyItemListChanged(
//                    ListNotification.Notify.REMOVED, index, category);
            // TODO: use new method to notify UI thread
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to remove category with id " +
                    category.getId() + ", exception: " + ex);
        }
    }

    public void handleCategoryUpdate(Category category) {
        if (category == null) {
            Log.e(this.toString(), "null category, abort modifying");
            return;
        }
        ArrayList<Category> categories = Global.getInstance().getCategories();
        int index;
        if (-1 == (index = categories.indexOf(category))) {
            Log.e(this.toString(), "category not exist in list, abort");
            return;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            if (0 == helper.updateCategory(category)) {
                Log.e(this.toString(), "no category row be updated");
            }
            helper.finish();
//            fragmentAdapter.getCategoryFragment().notifyItemListChanged(
//                    ListNotification.Notify.MODIFIED, index, category);
            // TODO: use new method to notify UI thread
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to modify Category: " + ex);
        }
    }

    public void deleteCategoryRecursion(Category category) {
        NoteItem e;
        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            while (null != (e = category.getNoteItem(0))) {
                if (0 == helper.deleteNote(e.getId()))
                    Log.i(this.toString(), "no row be deleted");
                category.delNoteItem(e);
            }
            helper.finish();
        }
        handleCategoryRemove(category);
    }

    public void handleNoteLoad(Category c) {
        if (!c.getNoteItems().isEmpty()) return;

        try (DataBaseHelper dataBaseHelper = new DataBaseHelper(this)) {
            dataBaseHelper.start();
            dataBaseHelper.queryNote(-1, c.getId(), c.getNoteItems());
            dataBaseHelper.finish();
            c.setInitialized(true);
        }
    }

    public void handleNoteCreate(Category c, NoteItem newItem) {
        newItem.setCategoryId(c.getId());
        try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
            dbHelper.start();
            newItem.setId(dbHelper.insertNote(newItem));
            dbHelper.finish();
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to add new item to database!");
            return;
        }

        if (!c.isInitialized()) {
            Log.i(this.toString(), "category not initialized, not add new item in the list");
            return;
        }
        int index = c.addNoteItem(newItem);
//        if (fragmentAdapter == null) {
//            Log.i(this.toString(), "fragmentAdapter not initialized, skip for now");
//            return;
//        }
//        NoteListFragment fragment = fragmentAdapter.getNoteListFragment();
//        if (fragment == null) return;
//        fragment.notifyItemListChanged(ListNotification.Notify.CREATED, index, newItem);
        // TODO: use new method to notify UI thread
    }

    public void handleNoteRemove(Category c, NoteItem e) {
        int index = c.indexNoteItem(e);
        if (index == -1) return;

        try (DataBaseHelper dbHelper = new DataBaseHelper(this)){
            dbHelper.start();
            if (0 == dbHelper.deleteNote(e.getId()))
                Log.e(this.toString(), "no row be deleted");
            dbHelper.finish();
            c.delNoteItem(e);
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to remove item with id " +
                    e.getId() + ", exception: " + e);
            return;
        }

//        fragmentAdapter.getNoteListFragment().notifyItemListChanged(
//                ListNotification.Notify.REMOVED, index, e);
        // TODO: use new method to notify UI thread
    }

    public void handleNoteUpdate(Category c, NoteItem newItem) {
        NoteItem oldItem = c.findNoteItem(newItem.getId());
        if (oldItem == null) {
            Log.e(this.toString(), "Failed to get note item with id "+ newItem.getId());
            return;
        }
        Log.e(this.toString(), "newItem == oldItem: " + newItem.equals(oldItem));
        int index = c.indexNoteItem(oldItem);
        newItem.setId(oldItem.getId());
        try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
            dbHelper.start();
            if (0 == dbHelper.updateNote(newItem)) {
                Log.i(this.toString(), "no item was updated");
                return;
            }
            dbHelper.finish();
        } catch (Exception exception) {
            Log.e(this.toString(), "Failed to update note item in database: " + exception);
            return;
        }

        oldItem.update(newItem, true);
//        fragmentAdapter.getNoteListFragment().notifyItemListChanged(
//                ListNotification.Notify.MODIFIED, index, newItem);
        // TODO: use new method to notify UI thread
    }




}