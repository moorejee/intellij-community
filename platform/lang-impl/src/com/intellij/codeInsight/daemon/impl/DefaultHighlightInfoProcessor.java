// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DefaultHighlightInfoProcessor extends HighlightInfoProcessor {
  @Override
  public void highlightsInsideVisiblePartAreProduced(@NotNull HighlightingSession session,
                                                     @Nullable Editor editor,
                                                     @NotNull List<? extends HighlightInfo> infos,
                                                     @NotNull TextRange priorityRange,
                                                     @NotNull TextRange restrictRange,
                                                     int groupId) {
    PsiFile psiFile = session.getPsiFile();
    Project project = psiFile.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document == null) return;
    long modificationStamp = document.getModificationStamp();
    TextRange priorityIntersection = priorityRange.intersection(restrictRange);
    List<? extends HighlightInfo> infoCopy = new ArrayList<>(infos);
    ((HighlightingSessionImpl)session).applyInEDT(() -> {
      if (modificationStamp != document.getModificationStamp()) return;
      if (priorityIntersection != null) {
        MarkupModel markupModel = DocumentMarkupModel.forDocument(document, project, true);

        EditorColorsScheme scheme = session.getColorsScheme();
        UpdateHighlightersUtil.setHighlightersInRange(project, psiFile, document, priorityIntersection, scheme, infoCopy, (MarkupModelEx)markupModel, groupId);
      }
      if (editor != null && !editor.isDisposed()) {
        // usability: show auto import popup as soon as possible
        if (!DumbService.isDumb(project)) {
          showAutoImportHints(editor, psiFile, session.getProgressIndicator());
        }

        repaintErrorStripeAndIcon(editor, project);
      }
    });
  }

  static void showAutoImportHints(@NotNull Editor editor, @NotNull PsiFile psiFile, @NotNull ProgressIndicator progressIndicator) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      ShowAutoImportPassFactory siFactory = TextEditorHighlightingPassRegistrarImpl.EP_NAME.findExtensionOrFail(ShowAutoImportPassFactory.class);
      TextEditorHighlightingPass highlightingPass = siFactory.createHighlightingPass(psiFile, editor);
      if (highlightingPass != null) {
        highlightingPass.doApplyInformationToEditor();
      }
    }, progressIndicator);
  }

  static void repaintErrorStripeAndIcon(@NotNull Editor editor, @NotNull Project project) {
    MarkupModel markup = editor.getMarkupModel();
    if (markup instanceof EditorMarkupModelImpl) {
      ((EditorMarkupModelImpl)markup).repaintTrafficLightIcon();
      ErrorStripeUpdateManager.getInstance(project).repaintErrorStripePanel(editor);
    }
  }

  @Override
  public void highlightsOutsideVisiblePartAreProduced(@NotNull HighlightingSession session,
                                                      @Nullable Editor editor,
                                                      @NotNull List<? extends HighlightInfo> infos,
                                                      @NotNull TextRange priorityRange,
                                                      @NotNull TextRange restrictedRange,
                                                      int groupId) {
    PsiFile psiFile = session.getPsiFile();
    Project project = psiFile.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document == null) return;
    long modificationStamp = document.getModificationStamp();
    ((HighlightingSessionImpl)session).applyInEDT(() -> {
      if (project.isDisposed() || modificationStamp != document.getModificationStamp()) return;

      EditorColorsScheme scheme = session.getColorsScheme();

      UpdateHighlightersUtil.setHighlightersOutsideRange(project, document, psiFile, infos, scheme,
                                                         restrictedRange.getStartOffset(), restrictedRange.getEndOffset(),
                                                         ProperTextRange.create(priorityRange),
                                                         groupId);
      if (editor != null) {
        repaintErrorStripeAndIcon(editor, project);
      }
    });
  }

  @Override
  public void allHighlightsForRangeAreProduced(@NotNull HighlightingSession session,
                                               long elementRange,
                                               @Nullable List<? extends HighlightInfo> infos) {
    killAbandonedHighlightsUnder(session.getProject(), session.getDocument(), elementRange, infos, session);
  }

  private static void killAbandonedHighlightsUnder(@NotNull Project project,
                                                   @NotNull Document document,
                                                   long range,
                                                   @Nullable List<? extends HighlightInfo> infos,
                                                   @NotNull HighlightingSession highlightingSession) {
    DaemonCodeAnalyzerEx.processHighlights(document, project, null, TextRangeScalarUtil.startOffset(range), TextRangeScalarUtil.endOffset(range), existing -> {
      if (existing.getGroup() == Pass.UPDATE_ALL && range == existing.getVisitingTextRange()) {
        if (infos != null) {
          for (HighlightInfo created : infos) {
            if (existing.equalsByActualOffset(created)) return true;
          }
        }
        RangeHighlighterEx highlighter = existing.highlighter;
        if (highlighter != null && UpdateHighlightersUtil.shouldRemoveHighlighter(highlightingSession.getPsiFile(), highlighter)) {
          // seems that highlight info 'existing' is going to disappear; remove it earlier
          ((HighlightingSessionImpl)highlightingSession).queueDisposeHighlighter(existing);
        }
      }
      return true;
    });
  }

  @Override
  public void infoIsAvailable(@NotNull HighlightingSession session,
                              @NotNull HighlightInfo info,
                              @NotNull TextRange priorityRange,
                              @NotNull TextRange restrictedRange,
                              int groupId) {
    ((HighlightingSessionImpl)session).queueHighlightInfo(info, restrictedRange, groupId);
  }

  @Override
  public void progressIsAdvanced(@NotNull HighlightingSession highlightingSession,
                                 @Nullable Editor editor,
                                 double progress) {
    PsiFile file = highlightingSession.getPsiFile();
    repaintTrafficIcon(file, editor, progress);
  }

  private final Alarm repaintIconAlarm = new Alarm();
  private void repaintTrafficIcon(@NotNull PsiFile file, @Nullable Editor editor, double progress) {
    if (ApplicationManager.getApplication().isCommandLine()) return;

    if (repaintIconAlarm.isEmpty() || progress >= 1) {
      repaintIconAlarm.addRequest(() -> {
        Project myProject = file.getProject();
        if (myProject.isDisposed()) return;
        Editor myeditor = editor;
        if (myeditor == null) {
          myeditor = PsiEditorUtil.findEditor(file);
        }
        if (myeditor != null && !myeditor.isDisposed()) {
          repaintErrorStripeAndIcon(myeditor, myProject);
        }
      }, 50, null);
    }
  }
}
