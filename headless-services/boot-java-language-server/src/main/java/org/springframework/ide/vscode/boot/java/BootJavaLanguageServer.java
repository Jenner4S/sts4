/*******************************************************************************
 * Copyright (c) 2016, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.springframework.ide.vscode.boot.java.beans.BeansSymbolProvider;
import org.springframework.ide.vscode.boot.java.beans.ComponentSymbolProvider;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaCodeLensEngine;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaCompletionEngine;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaDocumentSymbolHandler;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaHoverProvider;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaReconcileEngine;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaReferencesHandler;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaWorkspaceSymbolHandler;
import org.springframework.ide.vscode.boot.java.handlers.CompletionProvider;
import org.springframework.ide.vscode.boot.java.handlers.HoverProvider;
import org.springframework.ide.vscode.boot.java.handlers.ReferenceProvider;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingHoverProvider;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingSymbolProvider;
import org.springframework.ide.vscode.boot.java.scope.ScopeCompletionProcessor;
import org.springframework.ide.vscode.boot.java.snippets.JavaSnippet;
import org.springframework.ide.vscode.boot.java.snippets.JavaSnippetContext;
import org.springframework.ide.vscode.boot.java.snippets.JavaSnippetManager;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexer;
import org.springframework.ide.vscode.boot.java.utils.SpringLiveHoverWatchdog;
import org.springframework.ide.vscode.boot.java.value.ValueCompletionProcessor;
import org.springframework.ide.vscode.boot.java.value.ValueHoverProvider;
import org.springframework.ide.vscode.boot.java.value.ValuePropertyReferencesProvider;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndexProvider;
import org.springframework.ide.vscode.commons.gradle.GradleCore;
import org.springframework.ide.vscode.commons.gradle.GradleProjectFinderStrategy;
import org.springframework.ide.vscode.commons.languageserver.HighlightParams;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionEngine;
import org.springframework.ide.vscode.commons.languageserver.completion.VscodeCompletionEngineAdapter;
import org.springframework.ide.vscode.commons.languageserver.java.DefaultJavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.IJavaProjectFinderStrategy;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.util.ReferencesHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleWorkspaceService;
import org.springframework.ide.vscode.commons.maven.JavaProjectWithClasspathFileFinderStrategy;
import org.springframework.ide.vscode.commons.maven.MavenCore;
import org.springframework.ide.vscode.commons.maven.MavenProjectFinderStrategy;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

/**
 * Language Server for Spring Boot Application Properties files
 *
 * @author Martin Lippert
 */
public class BootJavaLanguageServer extends SimpleLanguageServer {

	public static final String LANGUAGE_SERVER_PROCESS_PROPERTY = "spring-boot-language-server";

	public static final JavaProjectFinder DEFAULT_PROJECT_FINDER = new DefaultJavaProjectFinder(new IJavaProjectFinderStrategy[] {
			new MavenProjectFinderStrategy(MavenCore.getDefault()),
			new GradleProjectFinderStrategy(GradleCore.getDefault()),
			new JavaProjectWithClasspathFileFinderStrategy()
	});

	private final VscodeCompletionEngineAdapter completionEngine;
	private final SpringIndexer indexer;
	private final SpringLiveHoverWatchdog liveHoverWatchdog;

	private final WordHighlighter testHightlighter = null; //new WordHighlighter("foo");

	public BootJavaLanguageServer(JavaProjectFinder javaProjectFinder, SpringPropertyIndexProvider indexProvider) {
		super("vscode-boot-java");

		System.setProperty(LANGUAGE_SERVER_PROCESS_PROPERTY, LANGUAGE_SERVER_PROCESS_PROPERTY);

		SimpleWorkspaceService workspaceService = getWorkspaceService();
		SimpleTextDocumentService documents = getTextDocumentService();

		IReconcileEngine reconcileEngine = new BootJavaReconcileEngine();
		documents.onDidChangeContent(params -> {
			TextDocument doc = params.getDocument();
			validateWith(doc.getId(), reconcileEngine);
		});

		ICompletionEngine bootCompletionEngine = createCompletionEngine(javaProjectFinder, indexProvider);
		completionEngine = createCompletionEngineAdapter(this, bootCompletionEngine);
		completionEngine.setMaxCompletions(100);
		documents.onCompletion(completionEngine::getCompletions);
		documents.onCompletionResolve(completionEngine::resolveCompletion);

		BootJavaHoverProvider hoverInfoProvider = createHoverHandler(javaProjectFinder);
		documents.onHover(hoverInfoProvider);

		liveHoverWatchdog = new SpringLiveHoverWatchdog(this, hoverInfoProvider);
		documents.onDidChangeContent(params -> {
			TextDocument doc = params.getDocument();
			if (testHightlighter!=null) {
				getClient().highlight(new HighlightParams(params.getDocument().getId(), testHightlighter.apply(doc)));
			} else {
				liveHoverWatchdog.watchDocument(doc.getUri());
				liveHoverWatchdog.update(doc.getUri());
			}
		});

		ReferencesHandler referencesHandler = createReferenceHandler(this, javaProjectFinder);
		documents.onReferences(referencesHandler);

		indexer = createAnnotationIndexer(this, javaProjectFinder);
		documents.onDocumentSymbol(new BootJavaDocumentSymbolHandler(indexer));
		workspaceService.onWorkspaceSymbol(new BootJavaWorkspaceSymbolHandler(indexer));

		BootJavaCodeLensEngine codeLensHandler = createCodeLensEngine(this, javaProjectFinder);
		documents.onCodeLens(codeLensHandler::createCodeLenses);
		documents.onCodeLensResolve(codeLensHandler::resolveCodeLens);
	}

	public void setMaxCompletionsNumber(int number) {
		completionEngine.setMaxCompletions(number);
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		CompletableFuture<InitializeResult> result = super.initialize(params);

		this.indexer.initialize(this.getWorkspaceRoot());
		this.liveHoverWatchdog.start();

		return result;
	}

	@Override
	public void initialized() {
		// TODO: due to a missing message from lsp4e this "initialized" is not called in the LSP4E case
		// if this gets fixed, the code should move here (from "initialize" above)

//		this.indexer.initialize(this.getWorkspaceRoot());
//		this.liveHoverWatchdog.start();
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		liveHoverWatchdog.shutdown();
		return super.shutdown();
	}

	protected ICompletionEngine createCompletionEngine(JavaProjectFinder javaProjectFinder, SpringPropertyIndexProvider indexProvider) {
		Map<String, CompletionProvider> providers = new HashMap<>();
		providers.put(org.springframework.ide.vscode.boot.java.scope.Constants.SPRING_SCOPE, new ScopeCompletionProcessor());
		providers.put(org.springframework.ide.vscode.boot.java.value.Constants.SPRING_VALUE, new ValueCompletionProcessor(indexProvider));

		JavaSnippetManager snippetManager = new JavaSnippetManager(this::createSnippetBuilder);
		snippetManager.add(new JavaSnippet(
				"RequestMapping method",
				JavaSnippetContext.BOOT_MEMBERS,
				CompletionItemKind.Method,
				ImmutableList.of(
						"org.springframework.web.bind.annotation.RequestMapping",
						"org.springframework.web.bind.annotation.RequestMethod",
						"org.springframework.web.bind.annotation.RequestParam"
				),
				"@RequestMapping(value=\"${path}\", method=RequestMethod.${GET})\n" +
				"public ${SomeData} ${requestMethodName}(@RequestParam ${String} ${param}) {\n" +
				"	return new ${SomeData}(${cursor});\n" +
				"}\n"
		));
		snippetManager.add(new JavaSnippet(
				"GetMapping method",
				JavaSnippetContext.BOOT_MEMBERS,
				CompletionItemKind.Method,
				ImmutableList.of(
						"org.springframework.web.bind.annotation.GetMapping",
						"org.springframework.web.bind.annotation.RequestParam"
				),
				"@GetMapping(value=\"${path}\")\n" +
				"public ${SomeData} ${getMethodName}(@RequestParam ${String} ${param}) {\n" +
				"	return new ${SomeData}(${cursor});\n" +
				"}\n"
		));
		snippetManager.add(new JavaSnippet(
				"PostMapping method",
				JavaSnippetContext.BOOT_MEMBERS,
				CompletionItemKind.Method,
				ImmutableList.of(
						"org.springframework.web.bind.annotation.PostMapping",
						"org.springframework.web.bind.annotation.RequestBody"
				),
				"@PostMapping(value=\"${path}\")\n" +
				"public ${SomeEnityData} ${postMethodName}(@RequestBody ${SomeEnityData} ${entity}) {\n" +
				"	//TODO: process POST request\n" +
				"	${cursor}\n" +
				"	return ${entity};\n" +
				"}\n"
		));
		snippetManager.add(new JavaSnippet(
				"PutMapping method",
				JavaSnippetContext.BOOT_MEMBERS,
				CompletionItemKind.Method,
				ImmutableList.of(
						"org.springframework.web.bind.annotation.PutMapping",
						"org.springframework.web.bind.annotation.RequestBody",
						"org.springframework.web.bind.annotation.PathVariable"
				),
				"@PutMapping(value=\"${path}/{${id}}\")\n" +
				"public ${SomeEnityData} ${putMethodName}(@PathVariable ${pvt:String} ${id}, @RequestBody ${SomeEnityData} ${entity}) {\n" +
				"	//TODO: process PUT request\n" +
				"	${cursor}\n" +
				"	return ${entity};\n" +
				"}"
		));
		return new BootJavaCompletionEngine(javaProjectFinder, providers, snippetManager);
	}

	protected BootJavaHoverProvider createHoverHandler(JavaProjectFinder javaProjectFinder) {
		HashMap<String, HoverProvider> providers = new HashMap<>();
		providers.put(org.springframework.ide.vscode.boot.java.value.Constants.SPRING_VALUE, new ValueHoverProvider());
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_REQUEST_MAPPING, new RequestMappingHoverProvider());
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_GET_MAPPING, new RequestMappingHoverProvider());
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_POST_MAPPING, new RequestMappingHoverProvider());
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_PUT_MAPPING, new RequestMappingHoverProvider());
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_DELETE_MAPPING, new RequestMappingHoverProvider());
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_PATCH_MAPPING, new RequestMappingHoverProvider());

		return new BootJavaHoverProvider(this, javaProjectFinder, providers);
	}

	protected SpringIndexer createAnnotationIndexer(SimpleLanguageServer server, JavaProjectFinder projectFinder) {
		HashMap<String, SymbolProvider> providers = new HashMap<>();
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_REQUEST_MAPPING, new RequestMappingSymbolProvider());
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_GET_MAPPING, new RequestMappingSymbolProvider());
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_POST_MAPPING, new RequestMappingSymbolProvider());
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_PUT_MAPPING, new RequestMappingSymbolProvider());
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_DELETE_MAPPING, new RequestMappingSymbolProvider());
		providers.put(org.springframework.ide.vscode.boot.java.requestmapping.Constants.SPRING_PATCH_MAPPING, new RequestMappingSymbolProvider());

		providers.put(org.springframework.ide.vscode.boot.java.beans.Constants.SPRING_BEAN, new BeansSymbolProvider());
		providers.put(org.springframework.ide.vscode.boot.java.beans.Constants.SPRING_COMPONENT, new ComponentSymbolProvider());

		return new SpringIndexer(this, projectFinder, providers);
	}

	protected ReferencesHandler createReferenceHandler(SimpleLanguageServer server, JavaProjectFinder projectFinder) {
		Map<String, ReferenceProvider> providers = new HashMap<>();
		providers.put(org.springframework.ide.vscode.boot.java.value.Constants.SPRING_VALUE, new ValuePropertyReferencesProvider(server));

		return new BootJavaReferencesHandler(server, projectFinder, providers);
	}

	protected BootJavaCodeLensEngine createCodeLensEngine(SimpleLanguageServer server, JavaProjectFinder projectFinder) {
		return new BootJavaCodeLensEngine(server, projectFinder);
	}


}
