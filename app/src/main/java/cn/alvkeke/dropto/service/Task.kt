package cn.alvkeke.dropto.service

import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.ui.intf.ListNotification.Notify

class Task {
    enum class Target {
        Storage,
    }

    enum class Type {
        Category,
        NoteItem,
    }

    enum class Job {
        CREATE,
        REMOVE,
        UPDATE,
    }

    val target: Target
    @JvmField
    val type: Type
    @JvmField
    val job: Job
    @JvmField
    val taskObj: Any
    var callerParam: Any? = null
    @JvmField
    var result: Int = 0

    @Suppress("unused")
    constructor(task: Task) {
        this.target = task.target
        this.type = task.type
        this.job = task.job
        this.taskObj = task.taskObj
        this.callerParam = task.callerParam
        this.result = task.result
    }

    constructor(target: Target, type: Type, job: Job, taskObj: Any) {
        this.target = target
        this.type = type
        this.job = job
        this.taskObj = taskObj
    }

    interface ResultListener {
        /**
         * this will be invoked after a task be handled
         * @param task the task instance passed to from caller
         * @param taskObj param if needed
         */
        fun onTaskFinish(task: Task, taskObj: Any?)
    }

    companion object {
        @Suppress("unused")
        val Targets: Array<Target> = Target.entries.toTypedArray()

        @Suppress("unused")
        val Types: Array<Type> = Type.entries.toTypedArray()

        @Suppress("unused")
        val Jobs: Array<Job> = Job.entries.toTypedArray()
        @JvmStatic
        fun jobToNotify(job: Job): Notify {
            return when (job) {
                Job.CREATE -> Notify.INSERTED
                Job.UPDATE -> Notify.UPDATED
                Job.REMOVE -> Notify.REMOVED
            }
        }

        fun onCategoryStorage(job: Job, taskObj: Any, callerParam: Any?): Task {
            val task = Task(Target.Storage, Type.Category, job, taskObj)
            task.callerParam = callerParam
            return task
        }

        @JvmStatic
        fun createCategory(category: Category, callerParam: Any?): Task {
            return onCategoryStorage(Job.CREATE, category, callerParam)
        }

        @JvmStatic
        fun updateCategory(category: Category, callerParam: Any?): Task {
            return onCategoryStorage(Job.UPDATE, category, callerParam)
        }

        @JvmStatic
        fun removeCategory(category: Category, callerParam: Any?): Task {
            return onCategoryStorage(Job.REMOVE, category, callerParam)
        }

        @JvmStatic
        fun onNoteStorage(job: Job, taskObj: Any, callerParam: Any?): Task {
            val task = Task(Target.Storage, Type.NoteItem, job, taskObj)
            task.callerParam = callerParam
            return task
        }

        @JvmStatic
        fun createNote(noteItem: NoteItem, callerParam: Any?): Task {
            return onNoteStorage(Job.CREATE, noteItem, callerParam)
        }

        @JvmStatic
        fun updateNote(noteItem: NoteItem, callerParam: Any?): Task {
            return onNoteStorage(Job.UPDATE, noteItem, callerParam)
        }

        @JvmStatic
        fun removeNote(noteItem: NoteItem, callerParam: Any?): Task {
            return onNoteStorage(Job.REMOVE, noteItem, callerParam)
        }
    }
}
