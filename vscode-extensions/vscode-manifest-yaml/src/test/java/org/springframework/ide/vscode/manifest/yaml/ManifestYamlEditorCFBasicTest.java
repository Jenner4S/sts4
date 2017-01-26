/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.manifest.yaml;

import org.junit.Before;
import org.junit.Test;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness;

public class ManifestYamlEditorCFBasicTest {
	LanguageServerHarness harness;
	BasicCfClientHarness basicCfClientHarness = new BasicCfClientHarness();

	@Before
	public void setup() throws Exception {
		harness = new LanguageServerHarness(() -> new ManifestYamlLanguageServer(
				basicCfClientHarness.getBasicClientFactory(), basicCfClientHarness.getParamsProvider()));
		harness.intialize(null);
	}

	@Test
	public void contentAssistBuildpack() throws Exception {
		basicCfClientHarness.addBuildpacks("java_buildpack");
		assertContainsCompletions("buildpack: <*>", "buildpack: java_buildpack<*>");
	}
	
	@Test
	public void contentAssistDoesNotContainBuildpack() throws Exception {
		basicCfClientHarness.addBuildpacks("java_buildpack");
		assertDoesNotContainCompletions("buildpack: <*>", "buildpack: wrong_buildpack<*>");
	}

	// @Test
	// public void contentAssistServices() throws Exception {
	// basicCfClientHarness.addServices("mysql");
	// assertContainsCompletions("services:\n" + " - <*>", "mysql");
	// }
	//
	// @Test
	// public void reconcileCFService() throws Exception {
	// basicCfClientHarness.addServices("myservice");
	// Editor editor = harness.newEditor("applications:\n" //
	// + "- name: foo\n" //
	// + " services:\n" //
	// + " - myservice\n" //
	// );
	// // Should have no problems
	// editor.assertProblems(/* none */);
	// }
	//
	// @Test
	// public void reconcileShowsWarningOnUnknownService() throws Exception {
	// basicCfClientHarness.addServices("myservice");
	// Editor editor = harness.newEditor("applications:\n" //
	// + "- name: foo\n" //
	// + " services:\n" //
	// + " - bad-service\n" //
	//
	// );
	// editor.assertProblems("bad-service|There is no service instance called");
	//
	// Diagnostic problem = editor.assertProblem("bad-service");
	// assertEquals(DiagnosticSeverity.Warning, problem.getSeverity());
	// }
	//
	// @Test
	// public void reconcileShowsWarningOnEmptyServices() throws Exception {
	// // Add empty list of services
	// basicCfClientHarness.addServices();
	// Editor editor = harness.newEditor("applications:\n" //
	// + "- name: foo\n" //
	// + " services:\n" //
	// + " - bad-service\n");//
	// editor.assertProblems("bad-service|There is no service instance called");
	//
	// Diagnostic problem = editor.assertProblem("bad-service");
	// assertEquals(DiagnosticSeverity.Warning, problem.getSeverity());
	// }

	//////////////////////////////////////////////////////////////////////////////

	private void assertContainsCompletions(String textBefore, String... textAfter) throws Exception {
		Editor editor = harness.newEditor(textBefore);
		editor.assertContainsCompletions(textAfter);
	}
	
	private void assertDoesNotContainCompletions(String textBefore, String... notToBeFound) throws Exception {
		Editor editor = harness.newEditor(textBefore);
		editor.assertDoesNotContainCompletions(notToBeFound);
	}

}