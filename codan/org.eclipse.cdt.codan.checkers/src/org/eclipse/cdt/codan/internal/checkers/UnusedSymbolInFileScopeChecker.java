/*******************************************************************************
 * Copyright (c) 2011 Andrew Gvozdev and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Gvozdev  - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.codan.internal.checkers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.cdt.codan.checkers.CodanCheckersActivator;
import org.eclipse.cdt.codan.core.cxx.model.AbstractIndexAstChecker;
import org.eclipse.cdt.codan.core.model.IProblemWorkingCopy;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IFunction;
import org.eclipse.cdt.core.dom.ast.IProblemType;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IVariable;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTQualifiedName;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMethod;

/**
 * Checker looking for unused function or variable declarations.
 */
public class UnusedSymbolInFileScopeChecker extends AbstractIndexAstChecker {
	public static final String ER_UNUSED_VARIABLE_DECLARATION_ID = "org.eclipse.cdt.codan.internal.checkers.UnusedVariableDeclarationProblem"; //$NON-NLS-1$
	public static final String ER_UNUSED_FUNCTION_DECLARATION_ID = "org.eclipse.cdt.codan.internal.checkers.UnusedFunctionDeclarationProblem"; //$NON-NLS-1$
	public static final String ER_UNUSED_STATIC_FUNCTION_ID = "org.eclipse.cdt.codan.internal.checkers.UnusedStaticFunctionProblem"; //$NON-NLS-1$

	private Map<IBinding, IASTDeclarator> externFunctionDeclarations = new HashMap<IBinding, IASTDeclarator>();
	private Map<IBinding, IASTDeclarator> staticFunctionDeclarations = new HashMap<IBinding, IASTDeclarator>();
	private Map<IBinding, IASTDeclarator> staticFunctionDefinitions = new HashMap<IBinding, IASTDeclarator>();
	private Map<IBinding, IASTDeclarator> externVariableDeclarations = new HashMap<IBinding, IASTDeclarator>();
	private Map<IBinding, IASTDeclarator> staticVariableDeclarations = new HashMap<IBinding, IASTDeclarator>();

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.eclipse.cdt.codan.core.model.ICheckerWithPreferences#initParameters
	 * (org.eclipse.cdt.codan.core.model.IProblemWorkingCopy)
	 */
	@Override
	public void initPreferences(IProblemWorkingCopy problem) {
		super.initPreferences(problem);
	}

	private void clearCandidates() {
		externFunctionDeclarations.clear();
		staticFunctionDeclarations.clear();
		staticFunctionDefinitions.clear();
		externVariableDeclarations.clear();
		staticVariableDeclarations.clear();
	}

	private boolean isAnyCandidate() {
		return externFunctionDeclarations.size() > 0 ||
			staticFunctionDeclarations.size() > 0 ||
			staticFunctionDefinitions.size() > 0 ||
			externVariableDeclarations.size() > 0 ||
			staticVariableDeclarations.size() > 0;
	}

	public void processAst(IASTTranslationUnit ast) {
		if (ast.isHeaderUnit())
			return;

		clearCandidates();
		collectCandidates(ast);

		if (isAnyCandidate()) {
			filterOutUsedElements(ast);
			reportProblems();
		}
	}

	private void collectCandidates(IASTTranslationUnit ast) {
		try {
			ast.accept(new ASTVisitor() {
				{
					shouldVisitDeclarations = true;
				}

				@Override
				public int visit(IASTDeclaration element) {
					if (element instanceof IASTSimpleDeclaration) {
						// declarations
						IASTSimpleDeclaration simpleDeclaration = (IASTSimpleDeclaration) element;

						IASTDeclarator[] declarators = simpleDeclaration.getDeclarators();
						for (IASTDeclarator decl : declarators) {
							IASTName astName = decl.getName();
							if (astName != null) {
								IBinding binding = astName.resolveBinding();
								int storageClass = simpleDeclaration.getDeclSpecifier().getStorageClass();

								if (binding instanceof IFunction) {
									if (storageClass == IASTDeclSpecifier.sc_extern || storageClass == IASTDeclSpecifier.sc_unspecified) {
										externFunctionDeclarations.put(binding, decl);
									} else if (storageClass == IASTDeclSpecifier.sc_static) {
										staticFunctionDeclarations.put(binding, decl);
									}
								} else if (binding instanceof IVariable) {
									if (storageClass == IASTDeclSpecifier.sc_extern) {
										externVariableDeclarations.put(binding, decl);
									} else if (storageClass == IASTDeclSpecifier.sc_static) {
										IType type = ((IVariable) binding).getType();
										// account for class constructor and avoid possible false positive
										if (!(type instanceof ICPPClassType) && !(type instanceof IProblemType)) {
											staticVariableDeclarations.put(binding, decl);
										}
									}
								}
							}
						}
						return PROCESS_SKIP;
					} else if (element instanceof IASTFunctionDefinition) {
						// definitions
						IASTFunctionDefinition definition = (IASTFunctionDefinition) element;

						IASTName astName = definition.getDeclarator().getName();
						if (astName != null) {
							IBinding binding = astName.resolveBinding();

							if (definition.getDeclSpecifier().getStorageClass() == IASTDeclSpecifier.sc_static) {
								if (!(astName instanceof ICPPASTQualifiedName)) {
									staticFunctionDefinitions.put(binding, definition.getDeclarator());
								}
							}

							// externFunctionDeclarators filter out
							externFunctionDeclarations.remove(binding);
							// staticFunctionDeclarators filter out
							staticFunctionDeclarations.remove(binding);
						}
					}
					return PROCESS_SKIP;
				}

			});
		} catch (Exception e) {
			CodanCheckersActivator.log(e);
		}
	}

	private void filterOutUsedElements(IASTTranslationUnit ast) {
		try {
			ast.accept(new ASTVisitor() {
				{
					shouldVisitNames = true;
				}

				@Override
				public int visit(IASTName name) {
					IBinding binding = name.resolveBinding();
					if (binding instanceof ICPPMethod)
						return PROCESS_CONTINUE;

					IASTNode parentNode = name.getParent();

					if (!(parentNode instanceof IASTFunctionDefinition || parentNode instanceof IASTFunctionDeclarator)) {
						externFunctionDeclarations.remove(binding);
						staticFunctionDefinitions.remove(binding);
					}

					if (!(parentNode instanceof IASTDeclarator)) {
						externVariableDeclarations.remove(binding);
						staticVariableDeclarations.remove(binding);
					}

					if (!isAnyCandidate())
						return PROCESS_ABORT;

					return PROCESS_CONTINUE;
				}

			});
		} catch (Exception e) {
			CodanCheckersActivator.log(e);
		}
	}

	private IASTName getAstName(IASTDeclarator decl) {
		IASTName astName = null;
		do {
			astName = decl.getName();
			if (astName != null && astName.getSimpleID().length > 0)
				return astName;

			// resolve parenthesis if need to
			decl = decl.getNestedDeclarator();
		} while (decl != null);

		return astName;
	}

	private void reportProblems() {
		List<IASTDeclarator> funcDeclarators = new ArrayList<IASTDeclarator>();
		funcDeclarators.addAll(externFunctionDeclarations.values());
		funcDeclarators.addAll(staticFunctionDeclarations.values());
		for (IASTDeclarator symbol : funcDeclarators) {
			IASTName astName = getAstName(symbol);
			if (astName != null) {
				String symbolName = new String(astName.getSimpleID());
				reportProblem(ER_UNUSED_FUNCTION_DECLARATION_ID, astName, symbolName);
			}
		}

		List<IASTDeclarator> varDeclarators = new ArrayList<IASTDeclarator>();
		varDeclarators.addAll(externVariableDeclarations.values());
		varDeclarators.addAll(staticVariableDeclarations.values());
		for (IASTDeclarator symbol : varDeclarators) {
			IASTName astName = getAstName(symbol);
			if (astName != null) {
				String symbolName = new String(astName.getSimpleID());
				reportProblem(ER_UNUSED_VARIABLE_DECLARATION_ID, astName, symbolName);
			}
		}

		List<IASTDeclarator> staticFuncDeclarators = new ArrayList<IASTDeclarator>();
		staticFuncDeclarators.addAll(staticFunctionDefinitions.values());
		for (IASTDeclarator symbol : staticFuncDeclarators) {
			IASTName astName = getAstName(symbol);
			if (astName != null) {
				String symbolName = new String(astName.getSimpleID());
				reportProblem(ER_UNUSED_STATIC_FUNCTION_ID, astName, symbolName);
			}
		}

		clearCandidates(); // release memory
	}

	@Override
	public boolean runInEditor() {
		return true;
	}
}
