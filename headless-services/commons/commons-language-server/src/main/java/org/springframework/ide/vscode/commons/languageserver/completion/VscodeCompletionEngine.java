/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.languageserver.completion;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.TextDocumentPositionParams;

/**
 * Interface that needs to be implemented by a 'completion engine' which can be easily
 * wired-up to provide completions for a Vscode language server.
 */
public interface VscodeCompletionEngine {
	CompletableFuture<CompletionList> getCompletions(TextDocumentPositionParams params);
	CompletableFuture<CompletionItem> resolveCompletion(CompletionItem unresolved);
}
