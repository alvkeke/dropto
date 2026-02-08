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
        RESTORE,
        UPDATE,
    }

    val target: Target
    @JvmField
    val type: Type
    @JvmField
    val job: Job
    @JvmField
    val taskObj: Any
    @JvmField
    var result: Int = 0

    @Suppress("unused")
    constructor(task: Task) {
        this.target = task.target
        this.type = task.type
        this.job = task.job
        this.taskObj = task.taskObj
        this.result = task.result
    }

    constructor(target: Target, type: Type, job: Job, taskObj: Any) {
        this.target = target
        this.type = type
        this.job = job
        this.taskObj = taskObj
    }

    fun interface ResultListener {
        /**
         * this will be invoked after a task be handled
         * @param task the task instance passed to from caller
         */
        fun onTaskFinish(task: Task)
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
                Job.RESTORE -> Notify.RESTORED
            }
        }

        fun onCategoryStorage(job: Job, taskObj: Any): Task {
            val task = Task(Target.Storage, Type.Category, job, taskObj)
            return task
        }

        @JvmStatic
        fun createCategory(category: Category): Task {
            return onCategoryStorage(Job.CREATE, category)
        }

        @JvmStatic
        fun updateCategory(category: Category): Task {
            return onCategoryStorage(Job.UPDATE, category)
        }

        @JvmStatic
        fun removeCategory(category: Category): Task {
            return onCategoryStorage(Job.REMOVE, category)
        }

        @JvmStatic
        fun onNoteStorage(job: Job, taskObj: Any): Task {
            val task = Task(Target.Storage, Type.NoteItem, job, taskObj)
            return task
        }

        @JvmStatic
        fun createNote(noteItem: NoteItem): Task {
            return onNoteStorage(Job.CREATE, noteItem)
        }

        @JvmStatic
        fun updateNote(noteItem: NoteItem): Task {
            return onNoteStorage(Job.UPDATE, noteItem)
        }

        @JvmStatic
        fun removeNote(noteItem: NoteItem): Task {
            return onNoteStorage(Job.REMOVE, noteItem)
        }

        @JvmStatic
        fun restoreNote(noteItem: NoteItem): Task {
            return onNoteStorage(Job.RESTORE, noteItem)
        }
    }
}
