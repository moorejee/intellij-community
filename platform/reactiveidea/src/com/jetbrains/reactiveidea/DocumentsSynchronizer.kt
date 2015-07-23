/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.reactiveidea

import com.github.krukow.clj_ds.PersistentMap
import com.github.krukow.clj_lang.PersistentHashMap
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.UIUtil
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import com.jetbrains.reactivemodel.util.get
import com.jetbrains.reactivemodel.util.host

public class DocumentsSynchronizer(val project: Project, val serverEditorTracker: ServerEditorTracker) : ProjectComponent {
  val lifetime = Lifetime.create(Lifetime.Eternal)
  val startupManager = StartupManager.getInstance(project)
  // TODO need to think about synchronization
  val reactiveModels: VariableSignal<PersistentMap<String, ReactiveModel>>
      = VariableSignal(lifetime, "reactive models", PersistentHashMap.emptyMap<String, ReactiveModel>())


  override fun getComponentName(): String = "DocumentsSynchronizer"

  private fun guessDataContext(contextHint: MapModel, model: MapModel): DataContext {
    if (contextHint.get("editor") == null) {
      return DataContext.EMPTY_CONTEXT;
    }

    val path = toPath((contextHint.get("editor") as ListModel))

    return ServerDataManagerImpl.Companion.getInstance().getDataContext(path, ReactiveModel.current()!!)
  }

  override fun initComponent() {
    initTracker()

    serverModel(lifetime.lifetime, 12346, reactiveModels) { reactiveModel ->
      UIUtil.invokeLaterIfNeeded {
        reactiveModel.registerHandler(lifetime.lifetime, "invoke-action") { args: MapModel, model ->
          val actionName = (args["name"] as PrimitiveModel<*>).value as String
          val contextHint = args["context"] as MapModel
          val anAction = ActionManager.getInstance().getAction(actionName)
          if (anAction != null) {
            val dataContext = guessDataContext(contextHint, model)
            EdtInvocationManager.getInstance().invokeLater {
              anAction.actionPerformed(AnActionEvent.createFromDataContext("ide-frontend", Presentation(), dataContext))
            }
          } else {
            println("can't find idea action $args")
          }
          model
        }

        reactiveModel.registerHandler(lifetime.lifetime, "open-file") { args: MapModel, model ->
          val path = toPath(args["path"] as ListModel)
          val psiPtr = path.getIn(model)!!.meta["psi"]
          if (psiPtr is SmartPsiElementPointer<*>) {
            EdtInvocationManager.getInstance().invokeLater {
              FileEditorManager.getInstance(project).openFile(psiPtr.getVirtualFile(), true)
            }
          }
          model
        }

        reactiveModel.registerHandler(lifetime.lifetime, "type-a") { args: MapModel, model ->
          val path = toPath((args["path"] as ListModel))
          val mapModel = path.getIn(model) as MapModel
          val editorHost = mapModel.meta.host() as EditorHost
          val actionManager = EditorActionManager.getInstance()

          CommandProcessor.getInstance().executeCommand(project, object : Runnable {
            override fun run() {
              CommandProcessor.getInstance().setCurrentCommandGroupId(editorHost.editor.getDocument())
              val dataContext = ServerDataManagerImpl.getInstance().getDataContext(path, reactiveModel)
              ActionManagerEx.getInstanceEx().fireBeforeEditorTyping('a', dataContext)
              actionManager.getTypedAction().actionPerformed(editorHost.editor, 'a', dataContext)
            }
          }, null, DocCommandGroupId.noneGroupId(editorHost.editor.getDocument()))

          model
        }

        startupManager.runWhenProjectIsInitialized {
          reactiveModel.host(Path("project-view")) { path, lifetime, initializer ->
            ProjectViewHost(project, reactiveModel, path, lifetime, initializer)
          }
        }
        reactiveModel.host(Path("tab-view")) { path, lifetime, initializer ->
          TabViewHost(project, reactiveModel, path)
        }
      }
    }
  }

  private fun toPath(listModel: ListModel) = listModel.map { (it as PrimitiveModel<*>).value }.drop(1).fold(Path(), { path, part -> path / part })

  public fun initTracker() {
    var activeList: VariableSignal<List<Signal<Model?>>> =
        reaction(true, "models", reactiveModels) { map ->
          map.values().map {
            it.subscribe(it.lifetime, Path("tab-view"))
          }
        }

    var signalList: VariableSignal<Signal<List<Model?>>> = reaction(true, "editor models", activeList) { list ->
      unlist(list)
    }

    var flattenList: VariableSignal<List<Model?>?> = flatten(signalList)

    var activeEditorModls = reaction(true, "tabs", flattenList) { list: List<Model?>? ->
      if (list != null) {
        list.map { model -> model?.meta?.valAt("host") as? TabViewHost }
            .filterNotNull()
            .map { host ->
              host.reactiveModel.subscribe(lifetime.lifetime, host.path / TabViewHost.editorsPath)
            };
      } else {
        emptyList()
      }
    }

    var activeEditors: VariableSignal<List<Editor>> = reaction(true, "editors", activeEditorModls) { tabs ->
      tabs.filter { it.value != null }
          .flatMap { editors ->
            val value = editors.value as MapModel
            value.values().filter {
              it as MapModel
              val isActive = (it[EditorHost.activePath] as PrimitiveModel<*>?)?.value
              if (isActive == null) false else isActive as Boolean
            }.map {
              (it!!.meta["host"] as EditorHost).editor
            }
          }
    }

    serverEditorTracker.setActiveEditors(activeEditors)
  }

  override fun disposeComponent() {
    lifetime.terminate()
  }

  override fun projectOpened() {
  }

  override fun projectClosed() {
  }
}
