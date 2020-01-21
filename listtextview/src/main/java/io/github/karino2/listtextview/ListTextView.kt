package io.github.karino2.listtextview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.*

class ListTextView(val cont: Context, attrs: AttributeSet) : RelativeLayout(cont, attrs) {

    // user of this view should inject these fields
    lateinit var owner: Activity
    var editActivityRequestCode: Int = 0


    val textSplitter: TextSplitter = TextSplitter()
    var listView : ListView

    var onDatasetChangedListener : ()-> Unit = {}

    init {
        inflate(context, R.layout.list_text_view, this)
        listView = findViewById<ListView>(R.id.listView)
        listView.adapter = textSplitter.createAdapter(context)

        listView.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE_MODAL
        listView.setMultiChoiceModeListener(createMultiChoiceModeListener())

        listView.setOnItemClickListener{ adapterView, view, id, pos ->
            startEditCellActivityForResult(id, listView.adapter.getItem(id) as String)
        }

        findViewById<Button>(R.id.buttonNew).setOnClickListener {
            startEditCellActivityForResult(-1, "")
        }
    }


    var text: String
    set(newValue) {
        textSplitter.text = newValue
        listView.adapter = textSplitter.createAdapter(context)
    }
    get() = textSplitter.mergedContent

    val adapter: ArrayAdapter<String>
    get() = listView.adapter as ArrayAdapter<String>


    private fun startEditCellActivityForResult(cellId: Int, content: String) {
        Intent(owner, EditCellActivity::class.java).apply {
            this.putExtra("CELL_ID", cellId)
            this.putExtra("CELL_CONTENT", content)
        }.also {
            owner.startActivityForResult(it, editActivityRequestCode)
        }
    }

    fun handleOnActivityResult(requestCode: Int, resultCode: Int, data: Intent) : Boolean {
        if(requestCode == editActivityRequestCode) {
            if(resultCode == Activity.RESULT_OK) {
                val cellId = data.getIntExtra("CELL_ID", -1)
                val content = data.getStringExtra("CELL_CONTENT")!!
                if(cellId == -1) {
                    // Caution! adapter's back must be textSplitter.textList.
                    adapter.add(content)
                    adapter.notifyDataSetInvalidated()
                } else {
                    textSplitter.textList[cellId] = content
                    adapter.notifyDataSetChanged()
                }
                onDatasetChangedListener()
            }
            return true
        }
        return false
    }

    fun createMultiChoiceModeListener(): AbsListView.MultiChoiceModeListener {
        return object : AbsListView.MultiChoiceModeListener {
            override fun onActionItemClicked(actionMode: ActionMode, item: MenuItem): Boolean {
                return when(item.itemId) {
                    R.id.delete_item -> {
                        val positions = listView.checkedItemPositions.clone()
                        actionMode.finish()
                        deleteItems(positions)
                        true
                    }
                    R.id.insert_item -> {
                        val insertAt = listView.checkedItemPositions.keyAt(0)
                        actionMode.finish()
                        insertItemAt(insertAt)
                        true
                    }
                    else -> false
                }
            }

            override fun onItemCheckedStateChanged(
                actionMode: ActionMode,
                p1: Int,
                p2: Long,
                p3: Boolean
            ) {
                if(listView.checkedItemCount == 1) {
                    actionMode.menu.findItem(R.id.insert_item).setVisible(true)
                } else {
                    actionMode.menu.findItem(R.id.insert_item).setVisible(false)
                }
            }

            override fun onCreateActionMode(actionMode: ActionMode, menu: Menu): Boolean {
                actionMode.menuInflater.inflate(R.menu.context_menu_list_item, menu)
                return true
            }

            override fun onPrepareActionMode(p0: ActionMode?, p1: Menu?) = false

            override fun onDestroyActionMode(p0: ActionMode?) {
            }

        }
    }

    private fun insertItemAt(insertAt: Int) {
        textSplitter.textList.add(insertAt, "(empty)")
        adapter.notifyDataSetChanged()
        onDatasetChangedListener()
    }

    private fun deleteItems(cellIds: SparseBooleanArray) {
        var count = 0
        repeat(cellIds.size()) {
            val key = cellIds.keyAt(it)
            if(cellIds.valueAt(it)) {
                textSplitter.textList.removeAt(key-count)
                count++
            }
        }
        adapter.notifyDataSetChanged()
        onDatasetChangedListener()
    }


    // save instance state related code
    class SavedState : BaseSavedState {
        var content= ""

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeString(content)
        }


        constructor(source: Parcel) : super(source) {
            content = source.readString()!!
        }

        constructor(superState: Parcelable) : super(superState)

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()!!
        val state = SavedState(superState)
        state.content = text
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if(state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            text = state.content
        } else {
            super.onRestoreInstanceState(state)
        }
    }
}