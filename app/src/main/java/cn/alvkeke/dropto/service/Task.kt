package cn.alvkeke.dropto.service;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.ui.intf.ListNotification;

public class Task {

    public enum Target {
        Storage,
    }
    @SuppressWarnings("unused")
    public static final Target[] Targets = Target.values();

    public enum Type {
        Category,
        NoteItem,
    }
    @SuppressWarnings("unused")
    public static final Type[] Types = Type.values();

    public enum Job {
        CREATE,
        REMOVE,
        UPDATE,
    }
    @SuppressWarnings("unused")
    public static final Job[] Jobs = Job.values();
    public static ListNotification.Notify jobToNotify(Job job) {
        switch (job) {
            case CREATE:
                return ListNotification.Notify.INSERTED;
            case UPDATE:
                return ListNotification.Notify.UPDATED;
            case REMOVE:
                return ListNotification.Notify.REMOVED;
        }
        assert false;   // should not reach here
        return null;
    }

    public final Target target;
    public final Type type;
    public final Job job;
    public final Object param;
    public Object callerParam;
    public int result;

    @SuppressWarnings("unused")
    public Task(Task task) {
        this.target = task.target;
        this.type = task.type;
        this.job = task.job;
        this.param = task.param;
        this.callerParam = task.callerParam;
        this.result = task.result;
    }

    public Task(Target target, Type type, Job job, Object param) {
        this.target = target;
        this.type = type;
        this.job = job;
        this.param = param;
    }

    public static Task onCategoryStorage(Job job, Object param, Object callerParam) {
        Task task = new Task(Target.Storage, Type.Category, job, param);
        task.callerParam = callerParam;
        return task;
    }
    public static Task createCategory(Category category, Object callerParam) {
        return onCategoryStorage(Job.CREATE, category, callerParam);
    }
    public static Task updateCategory(Category category, Object callerParam) {
        return onCategoryStorage(Job.UPDATE, category, callerParam);
    }
    public static Task removeCategory(Category category, Object callerParam) {
        return onCategoryStorage(Job.REMOVE, category, callerParam);
    }

    public static Task onNoteStorage(Job job, Object param, Object callerParam) {
        Task task = new Task(Target.Storage, Type.NoteItem, job, param);
        task.callerParam = callerParam;
        return task;
    }
    public static Task createNote(NoteItem noteItem, Object callerParam) {
        return onNoteStorage(Job.CREATE, noteItem, callerParam);
    }
    public static Task updateNote(NoteItem noteItem, Object callerParam) {
        return onNoteStorage(Job.UPDATE, noteItem, callerParam);
    }
    public static Task removeNote(NoteItem noteItem, Object callerParam) {
        return onNoteStorage(Job.REMOVE, noteItem, callerParam);
    }

    public interface ResultListener {

        /**
         * this will be invoked after a task be handled
         * @param task the task instance passed to from caller
         * @param param param if needed
         */
        void onTaskFinish(Task task, Object param);
    }

}
